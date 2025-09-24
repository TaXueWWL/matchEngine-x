// Trading Engine JavaScript

// Global variables
let stompClient = null;
let currentUserId = 1;
let currentSymbol = 'BTCUSDT';
let priceChart = null;

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
    // Get current user ID from the page
    const userIdElement = document.getElementById('currentUserId');
    if (userIdElement) {
        currentUserId = parseInt(userIdElement.textContent);
    }

    // Initialize WebSocket connection
    connectWebSocket();

    // Initialize page-specific functionality
    const path = window.location.pathname;
    if (path === '/trading') {
        initTradingPage();
    } else if (path === '/account') {
        initAccountPage();
    } else if (path === '/') {
        initHomePage();
    }
});

// WebSocket connection
function connectWebSocket() {
    const socket = new SockJS('/ws');
    stompClient = new StompJs.Client({
        webSocketFactory: () => socket,
        debug: function (str) {
            console.log('STOMP: ' + str);
        },
        onConnect: function() {
            console.log('WebSocket connected');
            updateConnectionStatus(true);
            subscribeToUpdates();
        },
        onDisconnect: function() {
            console.log('WebSocket disconnected');
            updateConnectionStatus(false);
        },
        onStompError: function(frame) {
            console.error('STOMP error', frame);
            updateConnectionStatus(false);
        }
    });

    stompClient.activate();
}

function updateConnectionStatus(connected) {
    const statusElement = document.getElementById('connection-status');
    if (statusElement) {
        statusElement.className = `badge ${connected ? 'connected' : 'disconnected'}`;
        statusElement.textContent = connected ? '已连接' : '未连接';
    }
}

function subscribeToUpdates() {
    if (stompClient && stompClient.connected) {
        // Subscribe to order book updates
        stompClient.subscribe('/topic/orderbook/' + currentSymbol, function(message) {
            const orderBook = JSON.parse(message.body);
            updateOrderBook(orderBook);
        });

        // Subscribe to trade updates
        stompClient.subscribe('/topic/trades/' + currentSymbol, function(message) {
            const trades = JSON.parse(message.body);
            updateTradeHistory(trades);
        });

        // Subscribe to user balance updates for all currencies
        stompClient.subscribe('/user/queue/balance', function(message) {
            const balance = JSON.parse(message.body);
            updateUserBalance(balance);
        });

        // Subscribe to balance updates for specific currencies
        fetch('/api/trading-pairs/currencies')
            .then(response => response.json())
            .then(currencies => {
                currencies.forEach(currency => {
                    stompClient.subscribe(`/user/queue/balance/${currency}`, function(message) {
                        const balanceData = JSON.parse(message.body);
                        updateUserBalance({ currency: currency, balance: balanceData.balance || balanceData.availableBalance });
                    });
                });
            })
            .catch(error => {
                console.error('Error loading currencies for WebSocket subscription:', error);
                // Fallback to default currencies
                const defaultCurrencies = ['USDT', 'BTC', 'ETH', 'ADA'];
                defaultCurrencies.forEach(currency => {
                    stompClient.subscribe(`/user/queue/balance/${currency}`, function(message) {
                        const balanceData = JSON.parse(message.body);
                        updateUserBalance({ currency: currency, balance: balanceData.balance || balanceData.availableBalance });
                    });
                });
            });
    }
}

// User management
function changeUser(event) {
    event.preventDefault();
    const newUserId = document.getElementById('newUserId').value;
    if (newUserId && newUserId > 0) {
        // Update session
        fetch('/api/session/user', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ userId: parseInt(newUserId) })
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                currentUserId = parseInt(newUserId);
                document.getElementById('currentUserId').textContent = newUserId;
                location.reload(); // Reload to update all data
            }
        })
        .catch(error => console.error('Error updating user:', error));
    }
}

// Home page initialization
function initHomePage() {
    loadTradingPairs();
}

function loadTradingPairs() {
    fetch('/api/trading-pairs')
        .then(response => response.json())
        .then(symbols => {
            const container = document.getElementById('trading-pairs-container');
            if (container && symbols) {
                symbols.forEach(symbol => {
                    createTradingPairCard(symbol, container);
                });
            }
        })
        .catch(error => console.error('Error loading trading pairs:', error));
}

function createTradingPairCard(symbol, container) {
    const card = document.createElement('div');
    card.className = 'col-md-3 col-sm-6 mb-3';
    card.innerHTML = `
        <div class="card trading-pair-card h-100" onclick="goToTrading('${symbol}')">
            <div class="card-body text-center">
                <h5 class="card-title">${symbol}</h5>
                <p class="card-text" id="price-${symbol}">Loading...</p>
            </div>
        </div>
    `;
    container.appendChild(card);

    // Load current price
    fetch(`/api/trading/orderbook/${symbol}/summary`)
        .then(response => response.json())
        .then(data => {
            const priceElement = document.getElementById(`price-${symbol}`);
            if (priceElement && data.lastPrice) {
                priceElement.textContent = `¥${data.lastPrice}`;
            }
        })
        .catch(error => {
            const priceElement = document.getElementById(`price-${symbol}`);
            if (priceElement) {
                priceElement.textContent = '暂无数据';
            }
        });
}

function goToTrading(symbol) {
    window.location.href = `/trading?symbol=${symbol}`;
}

// Trading page initialization
function initTradingPage() {
    // Get symbol from URL
    const urlParams = new URLSearchParams(window.location.search);
    const symbol = urlParams.get('symbol');
    if (symbol) {
        currentSymbol = symbol;
        document.getElementById('current-symbol').textContent = symbol;
    }

    // Initialize components
    loadOrderBook();
    loadOrderHistory();
    loadUserBalance();
    loadCurrentOrders();
    initPriceChart();

    // Set up form handlers
    setupOrderForms();
}

function loadOrderBook() {
    fetch(`/api/trading/orderbook/${currentSymbol}?depth=20`)
        .then(response => response.json())
        .then(orderBook => {
            updateOrderBook(orderBook);
        })
        .catch(error => console.error('Error loading order book:', error));
}

function updateOrderBook(orderBook) {
    // Update buy orders
    const buyOrdersBody = document.getElementById('buy-orders-body');
    const sellOrdersBody = document.getElementById('sell-orders-body');

    // Handle both API response formats (buyLevels/sellLevels from REST API, bids/asks from WebSocket)
    const buyLevels = orderBook.buyLevels || orderBook.bids || [];
    const sellLevels = orderBook.sellLevels || orderBook.asks || [];

    if (buyOrdersBody) {
        buyOrdersBody.innerHTML = '';
        buyLevels.slice(0, 10).forEach(level => {
            const price = level.price;
            const quantity = level.totalQuantity || level.quantity;
            const total = price * quantity;

            const row = document.createElement('tr');
            row.innerHTML = `
                <td class="buy-order">¥${parseFloat(price).toFixed(2)}</td>
                <td>${parseFloat(quantity).toFixed(6)}</td>
                <td>¥${total.toFixed(2)}</td>
            `;
            buyOrdersBody.appendChild(row);
        });
    }

    if (sellOrdersBody) {
        sellOrdersBody.innerHTML = '';
        sellLevels.slice(0, 10).forEach(level => {
            const price = level.price;
            const quantity = level.totalQuantity || level.quantity;
            const total = price * quantity;

            const row = document.createElement('tr');
            row.innerHTML = `
                <td class="sell-order">¥${parseFloat(price).toFixed(2)}</td>
                <td>${parseFloat(quantity).toFixed(6)}</td>
                <td>¥${total.toFixed(2)}</td>
            `;
            sellOrdersBody.appendChild(row);
        });
    }
}

function setupOrderForms() {
    const buyForm = document.getElementById('buy-order-form');
    const sellForm = document.getElementById('sell-order-form');

    if (buyForm) {
        buyForm.addEventListener('submit', function(e) {
            e.preventDefault();
            placeOrder('BUY');
        });
    }

    if (sellForm) {
        sellForm.addEventListener('submit', function(e) {
            e.preventDefault();
            placeOrder('SELL');
        });
    }
}

function placeOrder(side) {
    const prefix = side.toLowerCase();
    const price = document.getElementById(`${prefix}-price`).value;
    const quantity = document.getElementById(`${prefix}-quantity`).value;

    if (!price || !quantity) {
        alert('请填写价格和数量');
        return;
    }

    const order = {
        userId: currentUserId,
        symbol: currentSymbol,
        side: side,
        type: 'LIMIT',
        price: parseFloat(price),
        quantity: parseFloat(quantity)
    };

    fetch('/api/trading/orders', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(order)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success || data.orderId) {
            alert(`${side === 'BUY' ? '买入' : '卖出'}订单提交成功！`);
            // Clear form
            document.getElementById(`${prefix}-price`).value = '';
            document.getElementById(`${prefix}-quantity`).value = '';
            // Refresh data
            loadOrderHistory();
            loadUserBalance();
            loadCurrentOrders();
        } else {
            alert(`订单提交失败: ${data.message || '未知错误'}`);
        }
    })
    .catch(error => {
        console.error('Error placing order:', error);
        alert('订单提交失败，请重试');
    });
}

function initPriceChart() {
    const ctx = document.getElementById('price-chart');
    if (ctx) {
        priceChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: '价格',
                    data: [],
                    borderColor: 'rgb(75, 192, 192)',
                    backgroundColor: 'rgba(75, 192, 192, 0.1)',
                    tension: 0.1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: {
                        beginAtZero: false
                    }
                }
            }
        });

        // Load initial price data (mock data for now)
        updatePriceChart();
    }
}

function updatePriceChart() {
    // This would typically load real price history data
    // For now, we'll generate some mock data
    if (priceChart) {
        const now = new Date();
        const labels = [];
        const data = [];

        for (let i = 23; i >= 0; i--) {
            const time = new Date(now.getTime() - i * 60000); // Every minute
            labels.push(time.toLocaleTimeString());
            data.push(50000 + Math.random() * 1000 - 500); // Mock price data
        }

        priceChart.data.labels = labels;
        priceChart.data.datasets[0].data = data;
        priceChart.update();
    }
}

// Account page initialization
function initAccountPage() {
    loadUserBalance();
    loadOrderHistory();
}

function loadUserBalance() {
    // Get supported currencies from API first
    fetch('/api/trading-pairs/currencies')
        .then(response => response.json())
        .then(currencies => {
            currencies.forEach(currency => {
                loadCurrencyBalance(currency, currentUserId);
            });
        })
        .catch(error => {
            console.error('Error loading supported currencies:', error);
            // Fallback to default currencies if API fails
            const defaultCurrencies = ['USDT', 'BTC', 'ETH', 'ADA'];
            defaultCurrencies.forEach(currency => {
                loadCurrencyBalance(currency, currentUserId);
            });
        });
}

function loadCurrencyBalance(currency, userId) {
    fetch(`/api/account/balance/${userId}?currency=${currency}`)
        .then(response => response.json())
        .then(data => {
            updateUserBalance({ currency: currency, balance: data.availableBalance });
        })
        .catch(error => {
            console.error(`Error loading ${currency} balance:`, error);
            updateUserBalance({ currency: currency, balance: 0 });
        });
}

function updateUserBalance(balanceInfo) {
    const balanceElement = document.getElementById(`balance-${balanceInfo.currency}`);
    if (balanceElement) {
        // Format the balance nicely
        const balance = balanceInfo.balance || 0;
        const formattedBalance = balanceInfo.currency === 'USDT' ?
            balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) :
            balance.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 8 });

        balanceElement.textContent = formattedBalance;
        balanceElement.classList.add('flash-update');
        setTimeout(() => {
            balanceElement.classList.remove('flash-update');
        }, 500);
    }
}

function loadOrderHistory() {
    // This would load order history from the backend
    // For now, we'll show a placeholder
    const tbody = document.getElementById('order-history-body');
    if (tbody) {
        // Mock data - replace with actual API call
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center text-muted">
                    暂无订单记录
                </td>
            </tr>
        `;
    }
}

function updateTradeHistory(trades) {
    // Update recent trades display
    console.log('Trade updates:', trades);
}

// Add balance functionality
function loadCurrentOrders() {
    fetch(`/api/trading/orders/user/${currentUserId}?symbol=${currentSymbol}`)
        .then(response => response.json())
        .then(orders => {
            updateCurrentOrders(orders);
        })
        .catch(error => {
            console.error('Error loading current orders:', error);
            const tbody = document.getElementById('current-orders-body');
            if (tbody) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="10" class="text-center text-muted">
                            加载订单失败，请重试
                        </td>
                    </tr>
                `;
            }
        });
}

function updateCurrentOrders(orders) {
    const tbody = document.getElementById('current-orders-body');
    if (!tbody) return;

    if (orders.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="10" class="text-center text-muted">
                    暂无当前订单
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = '';
    orders.forEach(order => {
        const row = document.createElement('tr');
        const sideClass = order.side === 'BUY' ? 'text-success' : 'text-danger';
        const sideText = order.side === 'BUY' ? '买入' : '卖出';
        const statusClass = getStatusClass(order.status);
        const statusText = getStatusText(order.status);

        row.innerHTML = `
            <td>${order.orderId}</td>
            <td>${order.symbol}</td>
            <td class="${sideClass}">${sideText}</td>
            <td>${order.type}</td>
            <td>¥${parseFloat(order.price).toFixed(2)}</td>
            <td>${parseFloat(order.quantity).toFixed(6)}</td>
            <td>${parseFloat(order.filledQuantity).toFixed(6)}</td>
            <td><span class="badge ${statusClass}">${statusText}</span></td>
            <td>${formatTimestamp(order.timestamp)}</td>
            <td>
                <div class="btn-group btn-group-sm" role="group">
                    ${order.status === 'NEW' || order.status === 'PARTIALLY_FILLED' ? `
                        <button class="btn btn-outline-warning btn-sm"
                                onclick="showModifyOrderDialog(${order.orderId}, '${order.symbol}', ${order.price}, ${order.remainingQuantity})"
                                title="修改订单">
                            <i class="fas fa-edit"></i>
                        </button>
                        <button class="btn btn-outline-danger btn-sm"
                                onclick="cancelOrder(${order.orderId}, '${order.symbol}')"
                                title="取消订单">
                            <i class="fas fa-times"></i>
                        </button>
                    ` : ''}
                </div>
            </td>
        `;
        tbody.appendChild(row);
    });
}

function getStatusClass(status) {
    switch (status) {
        case 'NEW': return 'bg-primary';
        case 'PARTIALLY_FILLED': return 'bg-info';
        case 'FILLED': return 'bg-success';
        case 'CANCELLED': return 'bg-secondary';
        case 'REJECTED': return 'bg-danger';
        default: return 'bg-secondary';
    }
}

function getStatusText(status) {
    switch (status) {
        case 'NEW': return '新订单';
        case 'PARTIALLY_FILLED': return '部分成交';
        case 'FILLED': return '完全成交';
        case 'CANCELLED': return '已取消';
        case 'REJECTED': return '已拒绝';
        default: return status;
    }
}

function formatTimestamp(timestamp) {
    return new Date(timestamp).toLocaleString('zh-CN');
}

function cancelOrder(orderId, symbol) {
    if (!confirm('确定要取消这个订单吗？')) {
        return;
    }

    fetch(`/api/trading/orders/${orderId}?symbol=${symbol}&userId=${currentUserId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            alert('订单取消成功！');
            // 刷新当前订单列表
            loadCurrentOrders();
            // 刷新余额
            loadUserBalance();
        } else {
            alert('订单取消失败: ' + (data.message || '未知错误'));
        }
    })
    .catch(error => {
        console.error('Error canceling order:', error);
        alert('订单取消失败，请重试');
    });
}

function addBalance() {
    const currency = document.getElementById('add-currency').value;
    const amount = document.getElementById('add-amount').value;

    if (!currency || !amount) {
        alert('请填写币种和金额');
        return;
    }

    fetch('/api/account/balance', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            userId: currentUserId,
            currency: currency,
            amount: amount
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success !== false) {
            alert('余额添加成功！');
            document.getElementById('add-amount').value = '';
            // Refresh all currency balances
            loadUserBalance();
            // Also refresh the specific currency that was added
            setTimeout(() => {
                loadCurrencyBalance(currency, currentUserId);
            }, 500);
        } else {
            alert('余额添加失败: ' + (data.message || '未知错误'));
        }
    })
    .catch(error => {
        console.error('Error adding balance:', error);
        alert('余额添加失败，请重试');
    });
}

// Utility functions
function formatNumber(num, decimals = 2) {
    return parseFloat(num).toFixed(decimals);
}

function formatCurrency(amount, currency = '¥') {
    return currency + formatNumber(amount);
}

function showModifyOrderDialog(orderId, symbol, currentPrice, currentQuantity) {
    // 填充表单数据
    document.getElementById('modify-order-id').value = orderId;
    document.getElementById('modify-order-symbol').value = symbol;
    document.getElementById('modify-order-price').value = currentPrice;
    document.getElementById('modify-order-quantity').value = currentQuantity;

    // 显示对话框
    const modal = new bootstrap.Modal(document.getElementById('modifyOrderModal'));
    modal.show();
}

function submitModifyOrder() {
    const orderId = document.getElementById('modify-order-id').value;
    const symbol = document.getElementById('modify-order-symbol').value;
    const newPrice = document.getElementById('modify-order-price').value;
    const newQuantity = document.getElementById('modify-order-quantity').value;

    if (!newPrice || !newQuantity) {
        alert('请填写新价格和新数量');
        return;
    }

    const modifyData = {
        symbol: symbol,
        userId: currentUserId,
        newPrice: newPrice,
        newQuantity: newQuantity
    };

    fetch(`/api/trading/orders/${orderId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(modifyData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            alert('订单修改成功！');
            // 关闭对话框
            const modal = bootstrap.Modal.getInstance(document.getElementById('modifyOrderModal'));
            modal.hide();
            // 刷新当前订单列表
            loadCurrentOrders();
            // 刷新余额
            loadUserBalance();
        } else {
            alert('订单修改失败: ' + (data.message || '未知错误'));
        }
    })
    .catch(error => {
        console.error('Error modifying order:', error);
        alert('订单修改失败，请重试');
    });
}