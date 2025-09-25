// Trading Engine JavaScript

// Global variables
let stompClient = null;
let currentUserId = 1;
let currentSymbol = 'BTCUSDT';
let priceChart = null;
let klineChart = null;

// Current orders refresh variables - 当前订单刷新变量
let currentOrdersRefreshInterval = null;
let isCurrentOrdersRefreshEnabled = true;

// Recent trades refresh variables - 成交记录刷新变量
let recentTradesRefreshInterval = null;
let isTradesRefreshEnabled = true;

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
    console.log('🔌 Attempting WebSocket connection to /ws endpoint...');
    console.log('🔍 Current URL:', window.location.href);
    console.log('🔍 Base URL for WebSocket:', window.location.protocol + '//' + window.location.host);

    // Check if SockJS is available
    if (typeof SockJS === 'undefined') {
        console.error('❌ SockJS library not loaded');
        updateConnectionStatus(false);
        return;
    }

    // Check if Stomp is available
    if (typeof Stomp === 'undefined' && typeof StompJs === 'undefined') {
        console.error('❌ STOMP library not loaded');
        updateConnectionStatus(false);
        return;
    }

    console.log('✅ WebSocket libraries loaded successfully');
    console.log('🔍 SockJS version:', SockJS.version || 'unknown');
    console.log('🔍 Stomp available:', typeof Stomp);
    console.log('🔍 StompJs available:', typeof StompJs);

    try {
        console.log('🔗 Creating SockJS socket...');
        const socket = new SockJS('/ws');
        console.log('🔗 SockJS socket created:', socket);

        // Use Stomp.over if available (older API), otherwise use StompJs
        if (typeof Stomp !== 'undefined' && Stomp.over) {
            console.log('🔗 Using legacy Stomp.js API');
            stompClient = Stomp.over(socket);
            stompClient.debug = function(str) {
                console.log('📡 STOMP: ' + str);
            };

            stompClient.connect({}, function(frame) {
                console.log('✅ WebSocket connected successfully:', frame);
                console.log('🔍 Connection frame details:', {
                    command: frame.command,
                    headers: frame.headers,
                    body: frame.body
                });
                updateConnectionStatus(true);
                subscribeToUpdates();
            }, function(error) {
                console.error('❌ WebSocket connection error:', error);
                console.error('🔍 Error details:', {
                    message: error.message || error,
                    stack: error.stack,
                    type: typeof error
                });
                updateConnectionStatus(false);
            });
        } else {
            // Use newer StompJs API
            console.log('🔗 Using newer StompJs API');
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
                console.log('✅ WebSocket connected successfully:', frame);
                console.log('🔍 Connection frame details:', {
                    command: frame.command,
                    headers: frame.headers,
                    body: frame.body
                });
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
            console.log('Received order book update:', message);
            const orderBook = JSON.parse(message.body);
            console.log('Parsed order book data:', orderBook);
            updateOrderBook(orderBook);
        });

        // Subscribe to price updates
        console.log('Subscribing to price updates for', currentSymbol);
        stompClient.subscribe('/topic/price/' + currentSymbol, function(message) {
            console.log('Received price update:', message);
            const priceData = JSON.parse(message.body);
            console.log('Parsed price data:', priceData);
            updateCurrentPrice(priceData);
        });

        // Subscribe to trade updates
        console.log('Subscribing to trade updates for', currentSymbol);
        stompClient.subscribe('/topic/trades/' + currentSymbol, function(message) {
            console.log('Received trade update:', message);
            const trade = JSON.parse(message.body);
            console.log('Parsed trade data:', trade);
            updateRecentTrades(trade);
        });

        // Subscribe to user balance updates for all currencies
        console.log('Subscribing to balance updates');
        stompClient.subscribe('/user/queue/balance', function(message) {
            console.log('Received balance update');
            const balance = JSON.parse(message.body);
            updateUserBalance(balance);
        });

        // Subscribe to individual order updates - 订阅个人订单更新
        console.log('📡 Subscribing to order updates for user', currentUserId, 'on topic: /user/' + currentUserId + '/queue/orders');
        stompClient.subscribe('/user/' + currentUserId + '/queue/orders', function(message) {
            console.log('✅ Received order update:', message);
            const orderUpdate = JSON.parse(message.body);
            console.log('📋 Parsed order update data:', orderUpdate);
            updateSingleOrder(orderUpdate);
        });

        // Subscribe to current orders list updates - 订阅当前订单列表更新
        console.log('📡 Subscribing to current orders updates for user', currentUserId, 'on topic: /user/' + currentUserId + '/queue/current-orders');
        stompClient.subscribe('/user/' + currentUserId + '/queue/current-orders', function(message) {
            console.log('✅ Received current orders update:', message);
            const currentOrdersUpdate = JSON.parse(message.body);
            console.log('📋 Parsed current orders update data:', currentOrdersUpdate);
            refreshCurrentOrdersList(currentOrdersUpdate.orders);
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

        // Notify backend that we want to receive updates for this symbol
        if (stompClient && (stompClient.connected || (stompClient.state && stompClient.state === 'CONNECTED'))) {
            console.log('Sending subscription request to backend for', currentSymbol);
            stompClient.send('/app/orderbook/subscribe', {}, currentSymbol);

            // Subscribe to order updates for this user - 为当前用户订阅订单更新
            console.log('📤 Sending order subscription request for user', currentUserId, 'to /app/orders/subscribe');
            stompClient.send('/app/orders/subscribe', {}, currentUserId.toString());
        }
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

    console.log('Loading recent trades...');
    try {
        loadRecentTrades();
    } catch (error) {
        console.error('Error loading recent trades:', error);
    }

    console.log('Initializing K-line chart with periodic refresh only...');
    try {
        initKLineChart();
    } catch (error) {
        console.error('Error initializing K-line chart:', error);
        const chartContainer = document.getElementById('kline-chart');
        if (chartContainer) {
            chartContainer.innerHTML = '<div class="text-center text-muted p-4">K线图初始化失败</div>';
        }
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
            // Start auto-refresh when current orders tab is shown - 当前订单标签显示时启动自动刷新
            startCurrentOrdersAutoRefresh();
        });
    }

    // Start auto-refresh on page load - 页面加载时启动自动刷新
    startCurrentOrdersAutoRefresh();
    startTradesAutoRefresh();
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
    console.log('Updating order book with data:', orderBook);
    const buyOrdersBody = document.getElementById('buy-orders-body');
    const sellOrdersBody = document.getElementById('sell-orders-body');

    if (!buyOrdersBody || !sellOrdersBody) {
        console.error('Order book containers not found');
        return;
    }

    // Handle both API response formats (buyLevels/sellLevels from REST API, bids/asks from WebSocket)
    const buyLevels = orderBook.buyLevels || orderBook.bids || [];
    const sellLevels = orderBook.sellLevels || orderBook.asks || [];

    console.log('Buy levels:', buyLevels);
    console.log('Sell levels:', sellLevels);

    // Update sell orders (asks) - lowest price first, displayed from bottom to top
    if (sellOrdersBody) {
        sellOrdersBody.innerHTML = '';

        // Reverse the sell levels to show lowest price at bottom (closer to spread)
        const reversedSellLevels = [...sellLevels].reverse();
        let cumulativeSellQuantity = 0;

        reversedSellLevels.slice(0, 10).forEach(level => {
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

        buyLevels.slice(0, 10).forEach(level => {
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
    updateCurrentPriceFromOrderBook(buyLevels, sellLevels);
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

function updateCurrentPriceFromOrderBook(buyLevels, sellLevels) {
    const lastPriceElement = document.getElementById('last-price');
    const priceChangeElement = document.getElementById('price-change');
    const priceLabelElement = document.getElementById('price-label');

    if (buyLevels.length > 0 && sellLevels.length > 0) {
        const bestBid = parseFloat(buyLevels[0].price);
        const bestAsk = parseFloat(sellLevels[0].price);
        const spread = ((bestAsk - bestBid) / bestBid * 100);

        if (priceLabelElement) {
            priceLabelElement.textContent = `价差: ${spread.toFixed(3)}%`;
        }
    }

    // 移除有问题的价格重置逻辑
    // 最新成交价应该保持显示直到有新的成交价更新，不应该被订单簿更新重置
    // REMOVED: Problematic price reset logic
    // Latest price should persist until new trade price is received, not reset by order book updates
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

function initKLineChart() {
    console.log('Initializing K-line chart with periodic refresh only...');

    try {
        // Try TradingView Lightweight Charts first
        if (typeof KlineChart !== 'undefined') {
            klineChart = new KlineChart('kline-chart', {
                symbol: currentSymbol,
                timeframe: '1m',
                height: 400
            });

            console.log('TradingView K-line chart initialized successfully');
        } else {
            throw new Error('KlineChart class not loaded, trying fallback');
        }

    } catch (error) {
        console.warn('TradingView chart failed, trying fallback:', error);

        // Fallback to Chart.js implementation
        try {
            if (typeof FallbackKlineChart !== 'undefined') {
                klineChart = new FallbackKlineChart('kline-chart', {
                    symbol: currentSymbol,
                    timeframe: '1m'
                });

                console.log('Fallback K-line chart initialized successfully');
            } else {
                throw new Error('No chart implementation available');
            }

        } catch (fallbackError) {
            console.error('Both chart implementations failed:', fallbackError);
            const chartContainer = document.getElementById('kline-chart');
            if (chartContainer) {
                chartContainer.innerHTML = `
                    <div class="text-center text-muted p-4">
                        <i class="fas fa-chart-line fa-2x mb-2"></i>
                        <p>K线图暂时不可用</p>
                        <small>请刷新页面重试</small>
                    </div>
                `;
            }
            return;
        }
    }

    // Set up timeframe buttons
    setupTimeframeButtons();

    // Hide loading indicator
    const loadingElement = document.getElementById('kline-loading');
    if (loadingElement) {
        loadingElement.style.display = 'none';
    }
}

// Function to reinitialize K-line chart
function reinitializeKlineChart() {
    if (klineChart && typeof klineChart.destroy === 'function') {
        klineChart.destroy();
    }
    initKLineChart();
}

function setupTimeframeButtons() {
    const buttons = document.querySelectorAll('[data-timeframe]');
    buttons.forEach(button => {
        button.addEventListener('click', function() {
            // Update active button
            buttons.forEach(b => b.classList.remove('active'));
            this.classList.add('active');

            // Change timeframe in K-line chart
            const timeframe = this.dataset.timeframe;
            if (klineChart && typeof klineChart.changeTimeframe === 'function') {
                klineChart.changeTimeframe(timeframe);
            }
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

// Recent trades functionality
function loadRecentTrades() {
    return fetch(`/api/trading/trades/${currentSymbol}?limit=30`)
        .then(response => response.json())
        .then(trades => {
            updateRecentTradesDisplay(trades);
            console.log(`Recent trades loaded: ${trades.length} trades for ${currentSymbol}`);
        })
        .catch(error => {
            console.error('Error loading recent trades:', error);
            const tradesBody = document.getElementById('recent-trades-body');
            if (tradesBody) {
                tradesBody.innerHTML = `
                    <div class="text-center text-muted py-4">
                        <small>加载成交记录失败</small>
                    </div>
                `;
            }
        });
}

function updateRecentTrades(newTrade) {
    // Add single new trade to the beginning of the list
    const tradesBody = document.getElementById('recent-trades-body');
    if (!tradesBody) return;

    // Get current trades
    let currentTrades = getCurrentTradesFromDisplay();

    // Add new trade at the beginning
    currentTrades.unshift(newTrade);

    // Keep only 30 most recent trades
    if (currentTrades.length > 30) {
        currentTrades = currentTrades.slice(0, 30);
    }

    // Update display
    updateRecentTradesDisplay(currentTrades);

    // Add flash effect to the new trade
    const firstRow = tradesBody.querySelector('.trade-row:first-child');
    if (firstRow) {
        firstRow.style.backgroundColor = '#ffffcc';
        setTimeout(() => {
            firstRow.style.backgroundColor = '';
        }, 1000);
    }
}

function getCurrentTradesFromDisplay() {
    // This is a simplified version - in production, you'd maintain a trades array
    // For now, return empty array as we'll reload from server
    return [];
}

function updateRecentTradesDisplay(trades) {
    const tradesBody = document.getElementById('recent-trades-body');
    if (!tradesBody) return;

    if (trades.length === 0) {
        tradesBody.innerHTML = `
            <div class="text-center text-muted py-4">
                <small>暂无成交记录</small>
            </div>
        `;
        return;
    }

    tradesBody.innerHTML = '';

    // 每行显示两笔交易（左右各一笔），最多15行
    const maxRows = 15;
    const actualRows = Math.min(maxRows, Math.ceil(trades.length / 2));

    for (let row = 0; row < actualRows; row++) {
        const leftIndex = row * 2;
        const rightIndex = row * 2 + 1;

        const leftTrade = trades[leftIndex];
        const rightTrade = rightIndex < trades.length ? trades[rightIndex] : null;

        const tradeRow = document.createElement('div');
        tradeRow.className = 'row trade-row py-1 small border-bottom m-0';

        // 左列交易
        const leftContent = leftTrade ? formatTradeCell(leftTrade) : '<div class="col-3"></div><div class="col-3"></div><div class="col-3"></div><div class="col-3"></div>';

        // 右列交易
        const rightContent = rightTrade ? formatTradeCell(rightTrade) : '<div class="col-3"></div><div class="col-3"></div><div class="col-3"></div><div class="col-3"></div>';

        tradeRow.innerHTML = `
            <div class="col-6 border-end">
                <div class="row">
                    ${leftContent}
                </div>
            </div>
            <div class="col-6">
                <div class="row">
                    ${rightContent}
                </div>
            </div>
        `;

        tradesBody.appendChild(tradeRow);
    }
}

function formatTradeCell(trade) {
    // Determine if this trade is a buy or sell from trade data
    const isBuy = trade.side === 'BUY';
    const sideClass = isBuy ? 'text-success' : 'text-danger';
    const sideText = isBuy ? '买' : '卖';
    const priceClass = isBuy ? 'text-success' : 'text-danger';

    // Format time
    const tradeTime = new Date(trade.timestamp);
    const timeStr = tradeTime.toLocaleTimeString('zh-CN', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });

    return `
        <div class="col-3 text-center">${timeStr}</div>
        <div class="col-3 text-center ${sideClass} fw-bold">${sideText}</div>
        <div class="col-3 text-center ${priceClass}">${parseFloat(trade.price).toFixed(2)}</div>
        <div class="col-3 text-center">${parseFloat(trade.quantity).toFixed(4)}</div>
    `;
}

function updateTradeHistory(trades) {
    // Legacy function - redirect to new function
    if (Array.isArray(trades)) {
        updateRecentTradesDisplay(trades);
    } else {
        updateRecentTrades(trades);
    }
}

// Add balance functionality
function loadCurrentOrders() {
    fetch(`/api/trading/orders/user/${currentUserId}/current?symbol=${currentSymbol}`)
        .then(response => response.json())
        .then(orders => {
            updateCurrentOrders(orders);
            console.log(`✅ Current orders loaded: ${orders.length} orders for user ${currentUserId}`);
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

/**
 * Start automatic refresh of current orders - 开始自动刷新当前订单
 */
function startCurrentOrdersAutoRefresh() {
    // Clear existing interval if any - 清除现有的定时器
    if (currentOrdersRefreshInterval) {
        clearInterval(currentOrdersRefreshInterval);
    }

    if (!isCurrentOrdersRefreshEnabled) {
        console.log('⏸️ Current orders auto-refresh is disabled');
        return;
    }

    // Set up new interval - 设置新的定时器
    currentOrdersRefreshInterval = setInterval(() => {
        // Only refresh if current orders tab is active - 只在当前订单标签激活时刷新
        const activeTab = document.querySelector('#orderTabs .nav-link.active');
        if (activeTab && activeTab.getAttribute('data-bs-target') === '#current-orders') {
            console.log('🔄 Auto-refreshing current orders...');
            loadCurrentOrders();
        }
    }, 2000); // 2秒刷新一次

    console.log('⏰ Current orders auto-refresh started (every 2 seconds)');
}

/**
 * Stop automatic refresh of current orders - 停止自动刷新当前订单
 */
function stopCurrentOrdersAutoRefresh() {
    if (currentOrdersRefreshInterval) {
        clearInterval(currentOrdersRefreshInterval);
        currentOrdersRefreshInterval = null;
        console.log('⏹️ Current orders auto-refresh stopped');
    }
}

/**
 * Toggle automatic refresh of current orders - 切换自动刷新状态
 */
function toggleCurrentOrdersAutoRefresh() {
    isCurrentOrdersRefreshEnabled = !isCurrentOrdersRefreshEnabled;

    if (isCurrentOrdersRefreshEnabled) {
        startCurrentOrdersAutoRefresh();
    } else {
        stopCurrentOrdersAutoRefresh();
    }

    // Update UI indicator if exists - 更新UI指示器
    updateAutoRefreshIndicator();

    console.log(`🔄 Current orders auto-refresh ${isCurrentOrdersRefreshEnabled ? 'enabled' : 'disabled'}`);
}

/**
 * Update auto-refresh indicator in UI - 更新UI中的自动刷新指示器
 */
function updateAutoRefreshIndicator() {
    const indicator = document.getElementById('current-orders-auto-refresh-status');
    const toggleBtn = document.getElementById('auto-refresh-toggle-btn');

    if (indicator) {
        indicator.textContent = isCurrentOrdersRefreshEnabled ? '自动刷新: 开启' : '自动刷新: 关闭';
        indicator.className = isCurrentOrdersRefreshEnabled ? 'text-success' : 'text-muted';
    }

    if (toggleBtn) {
        const icon = toggleBtn.querySelector('i');
        if (icon) {
            icon.className = isCurrentOrdersRefreshEnabled ? 'fas fa-pause' : 'fas fa-play';
        }
        toggleBtn.className = isCurrentOrdersRefreshEnabled ?
            'btn btn-outline-success' : 'btn btn-outline-warning';
    }
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
            <td><span class="${statusClass}">${statusText}</span></td>
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
        case 'NEW': return 'order-status status-pending';
        case 'PARTIALLY_FILLED': return 'order-status status-pending';
        case 'FILLED': return 'order-status status-filled';
        case 'CANCELLED': return 'order-status status-cancelled';
        case 'REJECTED': return 'order-status status-cancelled';
        default: return 'order-status status-cancelled';
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
            <td><span class="${statusClass}">${statusText}</span></td>
            <td>${formatTimestamp(order.timestamp)}</td>
        `;
        tbody.appendChild(row);
    });
}

// WebSocket order update functions - WebSocket订单更新功能

/**
 * Update a single order in the current orders table when receiving real-time updates
 * 收到实时更新时更新当前订单表中的单个订单
 */
function updateSingleOrder(orderUpdate) {
    console.log('Updating single order:', orderUpdate);

    const tbody = document.getElementById('current-orders-body');
    if (!tbody) {
        console.log('Current orders table not found, ignoring update');
        return;
    }

    // Find the order row by orderId
    const orderRow = Array.from(tbody.querySelectorAll('tr')).find(row => {
        const orderIdCell = row.querySelector('td:first-child');
        return orderIdCell && orderIdCell.textContent == orderUpdate.orderId;
    });

    if (orderRow) {
        // Update existing order row - 更新现有订单行
        const sideClass = orderUpdate.side === 'BUY' ? 'text-success' : 'text-danger';
        const sideText = orderUpdate.side === 'BUY' ? '买入' : '卖出';
        const statusClass = getStatusClass(orderUpdate.status);
        const statusText = getStatusText(orderUpdate.status);

        orderRow.innerHTML = `
            <td>${orderUpdate.orderId}</td>
            <td>${orderUpdate.symbol}</td>
            <td class="${sideClass}">${sideText}</td>
            <td>${orderUpdate.type}</td>
            <td>¥${parseFloat(orderUpdate.price).toFixed(2)}</td>
            <td>${parseFloat(orderUpdate.quantity).toFixed(6)}</td>
            <td>${parseFloat(orderUpdate.filledQuantity).toFixed(6)}</td>
            <td><span class="${statusClass}">${statusText}</span></td>
            <td>${formatTimestamp(orderUpdate.timestamp)}</td>
            <td>
                <div class="btn-group btn-group-sm" role="group">
                    ${orderUpdate.status === 'NEW' || orderUpdate.status === 'PARTIALLY_FILLED' ? `
                        <button class="btn btn-outline-warning btn-sm"
                                onclick="showModifyOrderDialog(${orderUpdate.orderId}, '${orderUpdate.symbol}', ${orderUpdate.price}, ${orderUpdate.remainingQuantity})"
                                title="修改订单">
                            <i class="fas fa-edit"></i>
                        </button>
                        <button class="btn btn-outline-danger btn-sm"
                                onclick="cancelOrder(${orderUpdate.orderId}, '${orderUpdate.symbol}')"
                                title="取消订单">
                            <i class="fas fa-times"></i>
                        </button>
                    ` : ''}
                </div>
            </td>
        `;

        console.log('Updated order row for orderId:', orderUpdate.orderId);

        // Add visual feedback for the updated row - 为更新的行添加视觉反馈
        orderRow.style.backgroundColor = '#ffffcc';
        setTimeout(() => {
            orderRow.style.backgroundColor = '';
        }, 2000);
    } else {
        console.log('Order row not found for orderId:', orderUpdate.orderId, 'refreshing current orders...');
        // If order not found, refresh the entire table - 如果找不到订单，刷新整个表格
        loadCurrentOrders();
    }
}

/**
 * Refresh the entire current orders list when receiving bulk updates
 * 收到批量更新时刷新整个当前订单列表
 */
function refreshCurrentOrdersList(orders) {
    console.log('Refreshing current orders list with', orders.length, 'orders');

    // Filter orders for current symbol only - 只显示当前交易对的订单
    const filteredOrders = orders.filter(order => order.symbol === currentSymbol);

    // Use the existing updateCurrentOrders function - 使用现有的updateCurrentOrders函数
    updateCurrentOrders(filteredOrders);

    console.log('Current orders list refreshed');
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

function formatCurrency(amount, currency = '$') {
    return currency + formatNumber(amount);
}

function showModifyOrderDialog(orderId, symbol, currentPrice, currentQuantity) {
    // 检查必需的元素是否存在
    const orderIdElement = document.getElementById('modify-order-id');
    const orderSymbolElement = document.getElementById('modify-order-symbol');
    const orderPriceElement = document.getElementById('modify-order-price');
    const orderQuantityElement = document.getElementById('modify-order-quantity');
    const modalElement = document.getElementById('modifyOrderModal');

    if (!orderIdElement || !orderSymbolElement || !orderPriceElement || !orderQuantityElement || !modalElement) {
        console.error('Modify order dialog elements not found');
        alert('修改订单对话框未加载，请刷新页面重试');
        return;
    }

    // 填充表单数据
    orderIdElement.value = orderId;
    orderSymbolElement.value = symbol;
    orderPriceElement.value = currentPrice;
    orderQuantityElement.value = currentQuantity;

    // 显示对话框
    const modal = new bootstrap.Modal(modalElement);
    modal.show();
}

function submitModifyOrder() {
    // 检查必需的元素是否存在
    const orderIdElement = document.getElementById('modify-order-id');
    const orderSymbolElement = document.getElementById('modify-order-symbol');
    const orderPriceElement = document.getElementById('modify-order-price');
    const orderQuantityElement = document.getElementById('modify-order-quantity');

    if (!orderIdElement || !orderSymbolElement || !orderPriceElement || !orderQuantityElement) {
        console.error('Modify order dialog elements not found in submitModifyOrder');
        alert('修改订单对话框元素未找到，请刷新页面重试');
        return;
    }

    const orderId = orderIdElement.value;
    const symbol = orderSymbolElement.value;
    const newPrice = orderPriceElement.value;
    const newQuantity = orderQuantityElement.value;

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

// Store previous price for comparison
let previousPrice = null;

/**
 * Update current price from WebSocket price data
 */
function updateCurrentPrice(priceData) {
    console.log('Updating current price with:', priceData);
    const lastPriceElement = document.getElementById('last-price');
    const priceChangeElement = document.getElementById('price-change');
    const priceLabelElement = document.getElementById('price-label');

    if (priceData && priceData.price) {
        const currentPrice = parseFloat(priceData.price);

        // Update price display
        if (lastPriceElement) {
            lastPriceElement.textContent = `＄${currentPrice.toFixed(2)}`;

            // Add flash animation for price changes
            lastPriceElement.classList.add('flash-update');
            setTimeout(() => {
                lastPriceElement.classList.remove('flash-update');
            }, 500);
        }

        // Calculate and display price change if we have previous price
        if (priceChangeElement && previousPrice !== null) {
            const priceChange = currentPrice - previousPrice;
            const changePercent = (priceChange / previousPrice) * 100;

            priceChangeElement.textContent = `${changePercent >= 0 ? '+' : ''}${changePercent.toFixed(2)}%`;
            priceChangeElement.className = `price-change ms-2 ${changePercent >= 0 ? 'positive' : 'negative'}`;
        } else if (priceChangeElement) {
            // Default display when no previous price
            priceChangeElement.textContent = '0.00%';
            priceChangeElement.className = 'price-change ms-2';
        }

        if (priceLabelElement) {
            const timestamp = new Date(priceData.timestamp || Date.now());
            priceLabelElement.textContent = `更新时间: ${timestamp.toLocaleTimeString()}`;
        }

        // Store current price as previous price for next update
        previousPrice = currentPrice;
    }
}


// K-line refresh control functions - K线刷新控制函数

/**
 * Manual refresh K-line data - 手动刷新K线数据
 */
function manualRefreshKline() {
    console.log('🔄 Manual K-line refresh triggered');

    const refreshBtn = document.getElementById('manual-refresh-btn');
    if (refreshBtn) {
        // 添加旋转动画
        refreshBtn.classList.add('disabled');
        const icon = refreshBtn.querySelector('i');
        if (icon) {
            icon.classList.add('fa-spin');
        }
    }

    // 刷新K线图（手动刷新时完全重新加载数据）
    if (klineChart && typeof klineChart.refreshKlineData === 'function') {
        klineChart.refreshKlineData(true).finally(() => {
            // 恢复按钮状态
            if (refreshBtn) {
                refreshBtn.classList.remove('disabled');
                const icon = refreshBtn.querySelector('i');
                if (icon) {
                    icon.classList.remove('fa-spin');
                }
            }
        });
    } else {
        console.warn('⚠️ K-line chart or refresh method not available');
        // 恢复按钮状态
        setTimeout(() => {
            if (refreshBtn) {
                refreshBtn.classList.remove('disabled');
                const icon = refreshBtn.querySelector('i');
                if (icon) {
                    icon.classList.remove('fa-spin');
                }
            }
        }, 1000);
    }
}

/**
 * Toggle auto-refresh for K-line chart - 切换K线图自动刷新
 */
function toggleAutoRefresh() {
    console.log('🔄 Toggle auto-refresh triggered');

    const toggleBtn = document.getElementById('auto-refresh-toggle');

    if (klineChart && typeof klineChart.toggleAutoRefresh === 'function') {
        const isEnabled = klineChart.toggleAutoRefresh();

        // 更新按钮状态
        if (toggleBtn) {
            const icon = toggleBtn.querySelector('i');
            if (isEnabled) {
                toggleBtn.classList.remove('btn-outline-danger');
                toggleBtn.classList.add('btn-outline-success', 'active');
                toggleBtn.title = '自动刷新已启用 - 点击禁用';
                if (icon) {
                    icon.classList.remove('fa-pause');
                    icon.classList.add('fa-play');
                }
            } else {
                toggleBtn.classList.remove('btn-outline-success', 'active');
                toggleBtn.classList.add('btn-outline-danger');
                toggleBtn.title = '自动刷新已禁用 - 点击启用';
                if (icon) {
                    icon.classList.remove('fa-play');
                    icon.classList.add('fa-pause');
                }
            }
        }

        console.log(isEnabled ? '✅ Auto-refresh enabled' : '🔄 Auto-refresh disabled');
    } else {
        console.warn('⚠️ K-line chart or toggle method not available');
    }
}

/**
 * Set refresh interval for K-line chart - 设置K线图刷新间隔
 */
function setKlineRefreshInterval(intervalMs) {
    if (klineChart && typeof klineChart.setRefreshInterval === 'function') {
        klineChart.setRefreshInterval(intervalMs);
        console.log(`🔄 K-line refresh interval set to ${intervalMs/1000} seconds`);
    } else {
        console.warn('⚠️ K-line chart or setRefreshInterval method not available');
    }
}

/**
 * Update refresh interval from input field - 从输入框更新刷新间隔
 */
function updateRefreshInterval() {
    const input = document.getElementById('refresh-interval-input');
    if (!input) {
        console.warn('⚠️ Refresh interval input not found');
        return;
    }

    const intervalSeconds = parseInt(input.value);
    if (isNaN(intervalSeconds) || intervalSeconds < 1 || intervalSeconds > 300) {
        alert('刷新间隔必须在1-300秒之间');
        input.value = 3; // 重置为默认值
        return;
    }

    const intervalMs = intervalSeconds * 1000;

    // 更新主图表
    if (klineChart && typeof klineChart.setRefreshInterval === 'function') {
        klineChart.setRefreshInterval(intervalMs);
        console.log(`✅ Main K-line refresh interval updated to ${intervalSeconds} seconds`);
    }

    // 更新备用图表
    if (window.fallbackKlineChart && typeof window.fallbackKlineChart.setRefreshInterval === 'function') {
        window.fallbackKlineChart.setRefreshInterval(intervalMs);
        console.log(`✅ Fallback K-line refresh interval updated to ${intervalSeconds} seconds`);
    }

    // 显示成功提示
    const button = document.querySelector('button[onclick="updateRefreshInterval()"]');
    if (button) {
        const icon = button.querySelector('i');
        if (icon) {
            // 临时改变图标表示成功
            icon.classList.remove('fa-check');
            icon.classList.add('fa-check-circle', 'text-success');
            setTimeout(() => {
                icon.classList.remove('fa-check-circle', 'text-success');
                icon.classList.add('fa-check');
            }, 1500);
        }
    }

    console.log(`🔄 K-line refresh interval updated to ${intervalSeconds} seconds`);
}

/**
 * Start automatic refresh of recent trades - 开始自动刷新成交记录
 */
function startTradesAutoRefresh() {
    // Clear existing interval if any - 清除现有的定时器
    if (recentTradesRefreshInterval) {
        clearInterval(recentTradesRefreshInterval);
    }

    if (!isTradesRefreshEnabled) {
        console.log('⏸️ Recent trades auto-refresh is disabled');
        return;
    }

    // Set up new interval - 设置新的定时器
    recentTradesRefreshInterval = setInterval(() => {
        console.log('🔄 Auto-refreshing recent trades...');
        loadRecentTrades();
    }, 2000); // 2秒刷新一次

    console.log('⏰ Recent trades auto-refresh started (every 2 seconds)');
}

/**
 * Stop automatic refresh of recent trades - 停止自动刷新成交记录
 */
function stopTradesAutoRefresh() {
    if (recentTradesRefreshInterval) {
        clearInterval(recentTradesRefreshInterval);
        recentTradesRefreshInterval = null;
        console.log('⏹️ Recent trades auto-refresh stopped');
    }
}

/**
 * Toggle automatic refresh of recent trades - 切换成交记录自动刷新状态
 */
function toggleTradesAutoRefresh() {
    isTradesRefreshEnabled = !isTradesRefreshEnabled;

    if (isTradesRefreshEnabled) {
        startTradesAutoRefresh();
    } else {
        stopTradesAutoRefresh();
    }

    // Update UI indicator
    updateTradesAutoRefreshIndicator();

    console.log(`🔄 Recent trades auto-refresh ${isTradesRefreshEnabled ? 'enabled' : 'disabled'}`);
}

/**
 * Manual refresh recent trades - 手动刷新成交记录
 */
function refreshRecentTrades() {
    console.log('🔄 Manual recent trades refresh triggered');

    const refreshIcon = document.getElementById('trades-refresh-icon');
    if (refreshIcon) {
        refreshIcon.classList.add('fa-spin');
    }

    loadRecentTrades().finally(() => {
        // Remove spin animation
        if (refreshIcon) {
            refreshIcon.classList.remove('fa-spin');
        }
    });
}

/**
 * Update auto-refresh indicator in UI - 更新UI中的成交记录自动刷新指示器
 */
function updateTradesAutoRefreshIndicator() {
    const toggleBtn = document.getElementById('trades-auto-refresh-toggle');

    if (toggleBtn) {
        const icon = toggleBtn.querySelector('i');
        if (icon) {
            icon.className = isTradesRefreshEnabled ? 'fas fa-pause' : 'fas fa-play';
        }

        if (isTradesRefreshEnabled) {
            toggleBtn.classList.remove('btn-outline-danger');
            toggleBtn.classList.add('btn-outline-success', 'active');
            toggleBtn.title = '自动刷新已启用 - 点击禁用';
        } else {
            toggleBtn.classList.remove('btn-outline-success', 'active');
            toggleBtn.classList.add('btn-outline-danger');
            toggleBtn.title = '自动刷新已禁用 - 点击启用';
        }
    }
}

// Make refresh functions globally available
window.manualRefreshKline = manualRefreshKline;
window.toggleAutoRefresh = toggleAutoRefresh;
window.setKlineRefreshInterval = setKlineRefreshInterval;
window.updateRefreshInterval = updateRefreshInterval;
window.refreshRecentTrades = refreshRecentTrades;
window.toggleTradesAutoRefresh = toggleTradesAutoRefresh;