// Trading Engine JavaScript

// Global variables
let stompClient = null;
let currentUserId = 1;
let currentSymbol = 'BTCUSDT';
let priceChart = null;

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
    console.log('DOMContentLoaded fired, initializing app...');

    // Get current user ID from the page
    const userIdElement = document.getElementById('currentUserId');
    if (userIdElement) {
        currentUserId = parseInt(userIdElement.textContent);
        console.log('Current user ID:', currentUserId);
    }

    // Initialize page-specific functionality first
    const path = window.location.pathname;
    console.log('Current path:', path);

    if (path === '/trading') {
        console.log('Initializing trading page...');
        initTradingPage();
    } else if (path === '/account') {
        console.log('Initializing account page...');
        initAccountPage();
    } else if (path === '/') {
        console.log('Initializing home page...');
        initHomePage();
    }

    // Initialize WebSocket connection after page initialization
    try {
        console.log('Attempting WebSocket connection...');
        connectWebSocket();
    } catch (error) {
        console.error('WebSocket connection failed, but page functionality should still work:', error);
    }
});

// WebSocket connection
function connectWebSocket() {
    console.log('Connecting to WebSocket...');

    // Check if SockJS is available
    if (typeof SockJS === 'undefined') {
        console.error('SockJS library not loaded');
        updateConnectionStatus(false);
        return;
    }

    // Check if Stomp is available
    if (typeof Stomp === 'undefined' && typeof StompJs === 'undefined') {
        console.error('STOMP library not loaded');
        updateConnectionStatus(false);
        return;
    }

    try {
        const socket = new SockJS('/ws');

        // Use Stomp.over if available (older API), otherwise use StompJs
        if (typeof Stomp !== 'undefined' && Stomp.over) {
            stompClient = Stomp.over(socket);
            stompClient.debug = function(str) {
                console.log('STOMP: ' + str);
            };

            stompClient.connect({}, function(frame) {
                console.log('WebSocket connected:', frame);
                updateConnectionStatus(true);
                subscribeToUpdates();
            }, function(error) {
                console.error('WebSocket connection error:', error);
                updateConnectionStatus(false);
            });
        } else {
            // Use newer StompJs API
            stompClient = new StompJs.Client({
                webSocketFactory: () => socket,
                debug: function (str) {
                    console.log('STOMP: ' + str);
                },
                reconnectDelay: 5000,
                heartbeatIncoming: 4000,
                heartbeatOutgoing: 4000,
            });

            stompClient.onConnect = function(frame) {
                console.log('WebSocket connected:', frame);
                updateConnectionStatus(true);
                subscribeToUpdates();
            };

            stompClient.onDisconnect = function() {
                console.log('WebSocket disconnected');
                updateConnectionStatus(false);
            };

            stompClient.onStompError = function(frame) {
                console.error('STOMP error:', frame.headers['message']);
                console.error('Additional details:', frame.body);
                updateConnectionStatus(false);
            };

            stompClient.onWebSocketError = function(error) {
                console.error('WebSocket error:', error);
                updateConnectionStatus(false);
            };

            console.log('Activating STOMP client...');
            stompClient.activate();
        }
    } catch (error) {
        console.error('Error setting up WebSocket connection:', error);
        updateConnectionStatus(false);
    }
}

function updateConnectionStatus(connected) {
    const statusElement = document.getElementById('connection-status');
    if (statusElement) {
        statusElement.className = `badge ${connected ? 'connected' : 'disconnected'}`;
        statusElement.textContent = connected ? '已连接' : '未连接';
    }
}

function subscribeToUpdates() {
    console.log('Subscribing to WebSocket updates...');

    // Check if client is connected (compatible with both old and new API)
    const isConnected = stompClient && (stompClient.connected || (stompClient.state && stompClient.state === 'CONNECTED'));

    if (!isConnected) {
        console.error('STOMP client not connected, cannot subscribe');
        return;
    }

    try {
        // Subscribe to order book updates
        console.log('Subscribing to order book updates for', currentSymbol);
        stompClient.subscribe('/topic/orderbook/' + currentSymbol, function(message) {
            console.log('Received order book update');
            const orderBook = JSON.parse(message.body);
            updateOrderBook(orderBook);
        });

        // Subscribe to trade updates
        console.log('Subscribing to trade updates for', currentSymbol);
        stompClient.subscribe('/topic/trades/' + currentSymbol, function(message) {
            console.log('Received trade update');
            const trades = JSON.parse(message.body);
            updateTradeHistory(trades);
        });

        // Subscribe to user balance updates for all currencies
        console.log('Subscribing to balance updates');
        stompClient.subscribe('/user/queue/balance', function(message) {
            console.log('Received balance update');
            const balance = JSON.parse(message.body);
            updateUserBalance(balance);
        });

        // Subscribe to balance updates for specific currencies
        fetch('/api/trading-pairs/currencies')
            .then(response => response.json())
            .then(currencies => {
                console.log('Subscribing to balance updates for currencies:', currencies);
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
                console.log('Using default currencies:', defaultCurrencies);
                defaultCurrencies.forEach(currency => {
                    stompClient.subscribe(`/user/queue/balance/${currency}`, function(message) {
                        const balanceData = JSON.parse(message.body);
                        updateUserBalance({ currency: currency, balance: balanceData.balance || balanceData.availableBalance });
                    });
                });
            });

        console.log('Successfully subscribed to all updates');
    } catch (error) {
        console.error('Error subscribing to updates:', error);
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
    console.log('initTradingPage called');

    // Get symbol from URL
    const urlParams = new URLSearchParams(window.location.search);
    const symbol = urlParams.get('symbol');
    if (symbol) {
        currentSymbol = symbol;
        const symbolElement = document.getElementById('current-symbol');
        if (symbolElement) {
            symbolElement.textContent = symbol;
        }
        console.log('Symbol set to:', currentSymbol);
    }

    // Initialize components
    console.log('Loading order book...');
    try {
        loadOrderBook();
    } catch (error) {
        console.error('Error loading order book:', error);
    }

    console.log('Loading user balance...');
    try {
        loadUserBalance();
    } catch (error) {
        console.error('Error loading user balance:', error);
    }

    console.log('Loading current orders...');
    try {
        loadCurrentOrders();
    } catch (error) {
        console.error('Error loading current orders:', error);
    }

    console.log('Initializing K-line chart...');
    try {
        initKLineChart();
    } catch (error) {
        console.error('Error initializing K-line chart:', error);
    }

    // Set up form handlers (most important - should always work)
    console.log('Setting up order forms...');
    try {
        setupOrderForms();
    } catch (error) {
        console.error('Error setting up order forms:', error);
    }

    // Setup total amount calculations
    function updateTotal(side) {
        const price = parseFloat(document.getElementById(side + '-price').value) || 0;
        const quantity = parseFloat(document.getElementById(side + '-quantity').value) || 0;
        const total = price * quantity;
        document.getElementById(side + '-total').textContent = total.toFixed(2);
    }

    ['buy-price', 'buy-quantity'].forEach(id => {
        const element = document.getElementById(id);
        if (element) {
            element.addEventListener('input', () => updateTotal('buy'));
        }
    });

    ['sell-price', 'sell-quantity'].forEach(id => {
        const element = document.getElementById(id);
        if (element) {
            element.addEventListener('input', () => updateTotal('sell'));
        }
    });

    // Setup tab switch event listeners
    const historyTab = document.getElementById('order-history-tab');
    if (historyTab) {
        historyTab.addEventListener('shown.bs.tab', function() {
            loadOrderHistory();
        });
    }

    const currentTab = document.getElementById('current-orders-tab');
    if (currentTab) {
        currentTab.addEventListener('shown.bs.tab', function() {
            loadCurrentOrders();
        });
    }
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
    const buyOrdersBody = document.getElementById('buy-orders-body');
    const sellOrdersBody = document.getElementById('sell-orders-body');

    // Handle both API response formats (buyLevels/sellLevels from REST API, bids/asks from WebSocket)
    const buyLevels = orderBook.buyLevels || orderBook.bids || [];
    const sellLevels = orderBook.sellLevels || orderBook.asks || [];

    // Update sell orders (asks) - lowest price first, displayed from bottom to top
    if (sellOrdersBody) {
        sellOrdersBody.innerHTML = '';

        // Reverse the sell levels to show lowest price at bottom (closer to spread)
        const reversedSellLevels = [...sellLevels].reverse();
        let cumulativeSellQuantity = 0;

        reversedSellLevels.slice(0, 8).forEach(level => {
            const price = parseFloat(level.price);
            const quantity = parseFloat(level.totalQuantity || level.quantity);
            cumulativeSellQuantity += quantity;
            const maxQuantity = Math.max(...sellLevels.map(l => parseFloat(l.totalQuantity || l.quantity)));
            const depthPercentage = (quantity / maxQuantity) * 100;

            const levelDiv = document.createElement('div');
            levelDiv.className = 'orderbook-level sell-level';
            levelDiv.onclick = () => fillOrderForm('sell', price);
            levelDiv.innerHTML = `
                <div class="orderbook-level-background" style="width: ${depthPercentage}%"></div>
                <span class="orderbook-price sell-price">${price.toFixed(2)}</span>
                <span class="orderbook-size">${quantity.toFixed(6)}</span>
                <span class="orderbook-total text-muted">${cumulativeSellQuantity.toFixed(6)}</span>
            `;
            sellOrdersBody.appendChild(levelDiv);
        });
    }

    // Update buy orders (bids) - highest price first
    if (buyOrdersBody) {
        buyOrdersBody.innerHTML = '';
        let cumulativeBuyQuantity = 0;
        const maxQuantity = Math.max(...buyLevels.map(l => parseFloat(l.totalQuantity || l.quantity)));

        buyLevels.slice(0, 8).forEach(level => {
            const price = parseFloat(level.price);
            const quantity = parseFloat(level.totalQuantity || level.quantity);
            cumulativeBuyQuantity += quantity;
            const depthPercentage = (quantity / maxQuantity) * 100;

            const levelDiv = document.createElement('div');
            levelDiv.className = 'orderbook-level buy-level';
            levelDiv.onclick = () => fillOrderForm('buy', price);
            levelDiv.innerHTML = `
                <div class="orderbook-level-background" style="width: ${depthPercentage}%"></div>
                <span class="orderbook-price buy-price">${price.toFixed(2)}</span>
                <span class="orderbook-size">${quantity.toFixed(6)}</span>
                <span class="orderbook-total text-muted">${cumulativeBuyQuantity.toFixed(6)}</span>
            `;
            buyOrdersBody.appendChild(levelDiv);
        });
    }

    // Update current price and spread
    updateCurrentPrice(buyLevels, sellLevels);
}

function fillOrderForm(side, price) {
    const priceInput = document.getElementById(`${side}-price`);
    if (priceInput) {
        priceInput.value = price.toFixed(2);
        // Trigger the total calculation
        const event = new Event('input');
        priceInput.dispatchEvent(event);
    }
}

function updateCurrentPrice(buyLevels, sellLevels) {
    const lastPriceElement = document.getElementById('last-price');
    const priceChangeElement = document.getElementById('price-change');
    const priceLabelElement = document.getElementById('price-label');

    if (buyLevels.length > 0 && sellLevels.length > 0) {
        const bestBid = parseFloat(buyLevels[0].price);
        const bestAsk = parseFloat(sellLevels[0].price);
        const midPrice = (bestBid + bestAsk) / 2;
        const spread = ((bestAsk - bestBid) / bestBid * 100);

        if (lastPriceElement) {
            lastPriceElement.textContent = `¥${midPrice.toFixed(2)}`;
        }

        if (priceLabelElement) {
            priceLabelElement.textContent = `价差: ${spread.toFixed(3)}%`;
        }

        // Mock price change - in real implementation, this would come from historical data
        if (priceChangeElement) {
            const mockChange = (Math.random() - 0.5) * 5; // Random change between -2.5% and +2.5%
            priceChangeElement.textContent = `${mockChange >= 0 ? '+' : ''}${mockChange.toFixed(2)}%`;
            priceChangeElement.className = `price-change ms-2 ${mockChange >= 0 ? 'positive' : 'negative'}`;
        }
    }
}

function setupOrderForms() {
    console.log('Setting up order forms...');
    const buyForm = document.getElementById('buy-order-form');
    const sellForm = document.getElementById('sell-order-form');

    console.log('Buy form element:', buyForm);
    console.log('Sell form element:', sellForm);

    if (buyForm) {
        console.log('Adding buy form event listener');
        buyForm.addEventListener('submit', function(e) {
            e.preventDefault();
            console.log('Buy form submitted');
            placeOrder('BUY');
        });
    } else {
        console.error('Buy form not found!');
    }

    if (sellForm) {
        console.log('Adding sell form event listener');
        sellForm.addEventListener('submit', function(e) {
            e.preventDefault();
            console.log('Sell form submitted');
            placeOrder('SELL');
        });
    } else {
        console.error('Sell form not found!');
    }
}

function placeOrder(side) {
    console.log('placeOrder called with side:', side);
    const prefix = side.toLowerCase();
    const priceElement = document.getElementById(`${prefix}-price`);
    const quantityElement = document.getElementById(`${prefix}-quantity`);

    console.log('Price element:', priceElement);
    console.log('Quantity element:', quantityElement);

    const price = priceElement ? priceElement.value : '';
    const quantity = quantityElement ? quantityElement.value : '';

    console.log('Price value:', price);
    console.log('Quantity value:', quantity);

    if (!price || !quantity) {
        console.log('Missing price or quantity');
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

let klineChart = null;
let candlestickSeries = null;

function initKLineChart() {
    console.log('Initializing K-line chart...');
    const chartContainer = document.getElementById('kline-chart');

    if (!chartContainer) {
        console.error('Chart container not found');
        return;
    }

    // Check if LightweightCharts is available
    if (typeof LightweightCharts === 'undefined') {
        console.error('LightweightCharts library not loaded');
        chartContainer.innerHTML = '<div class="text-center text-muted p-4">K线图库未加载</div>';
        return;
    }

    try {
        console.log('Creating chart with LightweightCharts...');
        // Create the chart
        klineChart = LightweightCharts.createChart(chartContainer, {
            width: chartContainer.clientWidth,
            height: chartContainer.clientHeight,
            layout: {
                background: { type: 'solid', color: 'white' },
                textColor: 'black',
            },
            grid: {
                vertLines: { color: '#f0f0f0' },
                horzLines: { color: '#f0f0f0' },
            },
            crosshair: {
                mode: LightweightCharts.CrosshairMode.Normal,
            },
            rightPriceScale: {
                borderColor: '#cccccc',
                scaleMargins: {
                    top: 0.1,
                    bottom: 0.1,
                },
            },
            timeScale: {
                borderColor: '#cccccc',
                timeVisible: true,
                secondsVisible: false,
            },
        });

        console.log('Chart created, adding candlestick series...');
        // Create candlestick series
        candlestickSeries = klineChart.addCandlestickSeries({
            upColor: '#26a69a',
            downColor: '#ef5350',
            borderVisible: false,
            wickUpColor: '#26a69a',
            wickDownColor: '#ef5350',
        });

        console.log('K-line chart initialized successfully');

        // Handle resize
        const resizeObserver = new ResizeObserver(entries => {
            if (entries.length === 0 || entries[0].target !== chartContainer) return;
            const newRect = entries[0].contentRect;
            if (klineChart) {
                klineChart.applyOptions({ width: newRect.width, height: newRect.height });
            }
        });
        resizeObserver.observe(chartContainer);

        // Load initial K-line data
        loadKLineData('1m');

        // Set up timeframe buttons
        setupTimeframeButtons();

        // Hide loading indicator
        const loadingElement = document.getElementById('kline-loading');
        if (loadingElement) {
            loadingElement.style.display = 'none';
        }
    } catch (error) {
        console.error('Error initializing K-line chart:', error);
        chartContainer.innerHTML = '<div class="text-center text-muted p-4">K线图初始化失败</div>';
    }
}

function setupTimeframeButtons() {
    const buttons = document.querySelectorAll('[data-timeframe]');
    buttons.forEach(button => {
        button.addEventListener('click', function() {
            // Update active button
            buttons.forEach(b => b.classList.remove('active'));
            this.classList.add('active');

            // Load new data for selected timeframe
            const timeframe = this.dataset.timeframe;
            loadKLineData(timeframe);
        });
    });
}

function loadKLineData(timeframe) {
    // In a real implementation, this would fetch actual K-line data from the backend
    // For now, we'll generate mock data
    const now = new Date();
    const mockData = [];

    // Generate mock K-line data
    let basePrice = 50000;
    const intervals = timeframe === '1m' ? 100 :
                     timeframe === '5m' ? 50 :
                     timeframe === '15m' ? 30 :
                     timeframe === '1h' ? 24 : 30;

    const timeInterval = timeframe === '1m' ? 60000 :
                        timeframe === '5m' ? 300000 :
                        timeframe === '15m' ? 900000 :
                        timeframe === '1h' ? 3600000 : 86400000;

    for (let i = intervals; i >= 0; i--) {
        const time = Math.floor((now.getTime() - i * timeInterval) / 1000);
        const open = basePrice + (Math.random() - 0.5) * 100;
        const volatility = Math.random() * 200 + 50;
        const high = open + Math.random() * volatility;
        const low = open - Math.random() * volatility;
        const close = low + Math.random() * (high - low);

        mockData.push({
            time: time,
            open: open,
            high: high,
            low: low,
            close: close
        });

        basePrice = close; // Next candle starts where this one ends
    }

    if (candlestickSeries) {
        candlestickSeries.setData(mockData);
    }
}

function updateKLineChart(newCandle) {
    if (candlestickSeries && newCandle) {
        candlestickSeries.update(newCandle);
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
    fetch(`/api/trading/orders/user/${currentUserId}/current?symbol=${currentSymbol}`)
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

function loadOrderHistory() {
    fetch(`/api/trading/orders/user/${currentUserId}/history?symbol=${currentSymbol}`)
        .then(response => response.json())
        .then(orders => {
            updateOrderHistory(orders);
        })
        .catch(error => {
            console.error('Error loading order history:', error);
            const tbody = document.getElementById('order-history-body');
            if (tbody) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="9" class="text-center text-muted">
                            加载历史订单失败，请重试
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

function updateOrderHistory(orders) {
    const tbody = document.getElementById('order-history-body');
    if (!tbody) return;

    if (orders.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="9" class="text-center text-muted">
                    暂无历史订单
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
        `;
        tbody.appendChild(row);
    });
}

function refreshActiveTab() {
    const activeTab = document.querySelector('#orderTabs .nav-link.active');
    if (activeTab) {
        const target = activeTab.getAttribute('data-bs-target');
        if (target === '#current-orders') {
            loadCurrentOrders();
        } else if (target === '#order-history') {
            loadOrderHistory();
        }
    }
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