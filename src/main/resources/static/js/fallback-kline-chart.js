/**
 * Fallback K-line Chart using Chart.js with OHLC plugin
 * 使用Chart.js和OHLC插件的备用K线图
 */

class FallbackKlineChart {
    constructor(containerId, options = {}) {
        this.containerId = containerId;
        this.container = document.getElementById(containerId);
        this.chart = null;
        this.symbol = options.symbol || 'BTCUSDT';
        this.timeframe = options.timeframe || '1m';
        this.stompClient = options.stompClient || null;
        this.sessionId = options.sessionId || this.generateSessionId();
        this.lastKnownPrice = null; // 用于心跳数据的价格参考
        // 保存订阅对象引用以便正确取消订阅
        this.updateSubscription = null;
        // 定时刷新相关
        this.refreshTimer = null;
        this.refreshInterval = 3000; // 3秒刷新一次
        this.isRefreshing = false;
        this.autoRefreshEnabled = true;

        console.log('Using fallback Chart.js implementation');
        this.init();
    }

    init() {
        try {
            // Check if Chart.js is available
            if (typeof Chart === 'undefined') {
                throw new Error('Chart.js library not loaded');
            }

            // Create canvas element
            const canvas = document.createElement('canvas');
            canvas.width = this.container.clientWidth;
            canvas.height = 400;
            this.container.innerHTML = '';
            this.container.appendChild(canvas);

            // Create line chart for now (simplified)
            this.chart = new Chart(canvas, {
                type: 'line',
                data: {
                    labels: [],
                    datasets: [{
                        label: `${this.symbol} Price`,
                        data: [],
                        borderColor: '#00C851',
                        backgroundColor: 'rgba(0, 200, 81, 0.1)',
                        tension: 0.1
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    backgroundColor: '#181A20',
                    scales: {
                        x: {
                            type: 'time',
                            time: {
                                unit: 'minute',
                                displayFormats: {
                                    minute: 'HH:mm'
                                }
                            },
                            ticks: {
                                color: '#EAECEF'
                            },
                            grid: {
                                color: '#2B2F36'
                            }
                        },
                        y: {
                            beginAtZero: false,
                            ticks: {
                                color: '#EAECEF'
                            },
                            grid: {
                                color: '#2B2F36'
                            }
                        }
                    },
                    plugins: {
                        title: {
                            display: true,
                            text: `${this.symbol} - ${this.timeframe}`,
                            color: '#EAECEF'
                        },
                        legend: {
                            labels: {
                                color: '#EAECEF'
                            }
                        }
                    }
                }
            });

            // Load initial data
            this.loadInitialData();

            // Subscribe to real-time updates if WebSocket is available
            if (this.stompClient && this.stompClient.connected) {
                this.subscribeToUpdates();
            } else {
                console.log('WebSocket not ready, fallback chart will show historical data only');
            }

            // Start auto-refresh timer - 启动自动刷新定时器
            this.startAutoRefresh();

            console.log('Fallback K-line chart initialized successfully');

        } catch (error) {
            console.error('Failed to initialize fallback K-line chart:', error);
            this.showError('图表初始化失败');
        }
    }

    async loadInitialData() {
        console.log(`📊 [FALLBACK] Loading initial K-line data for ${this.symbol}/${this.timeframe}...`);

        try {
            const response = await fetch(`/api/kline/${this.symbol}?timeframe=${this.timeframe}&limit=50`);
            console.log(`📡 [FALLBACK] K-line API response status:`, response.status);

            if (!response.ok) {
                throw new Error(`Failed to fetch initial K-line data: ${response.status} ${response.statusText}`);
            }

            const klines = await response.json();
            console.log(`📈 [FALLBACK] Received K-line data:`, {
                count: klines ? klines.length : 0,
                firstData: klines && klines.length > 0 ? klines[0] : null,
                lastData: klines && klines.length > 0 ? klines[klines.length - 1] : null
            });

            if (klines && klines.length > 0) {
                console.log(`📊 [FALLBACK] Processing ${klines.length} initial K-line records...`);
                const labels = [];
                const prices = [];

                klines.forEach((k, index) => {
                    const timestamp = new Date(k.timestamp * 1000);
                    let price = parseFloat(k.close);

                    // 处理价格为0的情况
                    if (price === 0) {
                        if (this.lastKnownPrice && this.lastKnownPrice > 0) {
                            price = this.lastKnownPrice;
                            console.log(`💓 [FALLBACK] Using last known price ${price} for zero-price data at index ${index}`);
                        } else {
                            price = 0.001;
                            console.log(`💓 [FALLBACK] Using default price 0.001 for initial zero-price data at index ${index}`);
                        }
                    } else if (price > 0) {
                        this.lastKnownPrice = price;
                    }

                    labels.push(timestamp);
                    prices.push(price);
                });

                this.chart.data.labels = labels;
                this.chart.data.datasets[0].data = prices;
                this.chart.update();

                console.log(`✅ [FALLBACK] Loaded ${klines.length} K-line data points successfully (including zero-price handling)`);
            } else {
                console.log('⚠️ [FALLBACK] No initial K-line data available');
            }

        } catch (error) {
            console.error('Error loading initial K-line data:', error);
            this.showError('加载K线数据失败');
        }
    }

    subscribeToUpdates() {
        if (!this.stompClient) {
            console.log('WebSocket client not available, skipping K-line subscription (fallback)');
            return;
        }

        if (!this.stompClient.connected) {
            console.warn('WebSocket not connected, cannot subscribe to K-line updates');
            return;
        }

        try {
            // 先取消之前的订阅
            if (this.updateSubscription) {
                console.log('🔕 [FALLBACK] Unsubscribing from previous K-line topic');
                this.updateSubscription.unsubscribe();
                this.updateSubscription = null;
            }

            const updateTopic = `/topic/kline/${this.symbol}/${this.timeframe}`;
            console.log(`🔔 [FALLBACK] Subscribing to K-line updates: ${updateTopic}`);
            this.updateSubscription = this.stompClient.subscribe(updateTopic, (message) => {
                console.log(`📈 [FALLBACK] Received K-line update for ${this.symbol}/${this.timeframe}:`, message);
                console.log(`📈 [FALLBACK] Message body length:`, message.body ? message.body.length : 0);
                console.log(`📈 [FALLBACK] Raw message body:`, message.body);

                try {
                    const kline = JSON.parse(message.body);
                    console.log(`📊 [FALLBACK] Parsed K-line data:`, {
                        symbol: kline.symbol,
                        timeframe: kline.timeframe,
                        timestamp: new Date(kline.timestamp * 1000),
                        open: kline.open,
                        high: kline.high,
                        low: kline.low,
                        close: kline.close,
                        volume: kline.volume,
                        amount: kline.amount,
                        tradeCount: kline.tradeCount,
                        allPricesZero: (kline.open == 0 && kline.high == 0 && kline.low == 0 && kline.close == 0)
                    });

                    // 检查是否是价格为0的数据
                    const isZeroPriceData = kline.open == 0 && kline.high == 0 && kline.low == 0 && kline.close == 0;
                    if (isZeroPriceData) {
                        console.log(`🔍 [FALLBACK] Received zero-price K-line data - processing anyway:`, {
                            symbol: kline.symbol,
                            timeframe: kline.timeframe,
                            timestamp: kline.timestamp,
                            volume: kline.volume
                        });
                    }

                    this.updateChart(kline);
                } catch (error) {
                    console.error(`❌ [FALLBACK] Error parsing K-line message:`, error);
                    console.error(`❌ [FALLBACK] Problematic message body:`, message.body);
                }
            });

            const subscriptionData = {
                symbol: this.symbol,
                timeframe: this.timeframe,
                sessionId: this.sessionId
            };
            console.log(`📤 [FALLBACK] Sending K-line subscription request:`, subscriptionData);
            this.stompClient.send('/app/kline/subscribe', {}, JSON.stringify(subscriptionData));

            console.log('Subscribed to K-line updates (fallback)');

        } catch (error) {
            console.error('Error subscribing to K-line updates:', error);
        }
    }

    updateChart(kline) {
        try {
            console.log(`🔄 [FALLBACK] Updating K-line chart with new data:`, kline);

            if (this.chart) {
                const timestamp = new Date(kline.timestamp * 1000);
                const price = parseFloat(kline.close);

                console.log(`📊 [FALLBACK] Processing data point:`, {
                    timestamp: timestamp,
                    price: price,
                    originalTimestamp: kline.timestamp
                });

                // 更新最后已知价格（用于心跳数据）
                if (price > 0) {
                    this.lastKnownPrice = price;
                } else if (this.lastKnownPrice && this.lastKnownPrice > 0) {
                    // 如果价格为0（心跳数据），使用最后已知价格
                    price = this.lastKnownPrice;
                    console.log(`💓 [FALLBACK] Using last known price ${price} for heartbeat data`);
                }

                // Add new data point
                this.chart.data.labels.push(timestamp);
                this.chart.data.datasets[0].data.push(price);

                // Keep only last 50 points
                if (this.chart.data.labels.length > 50) {
                    this.chart.data.labels.shift();
                    this.chart.data.datasets[0].data.shift();
                    console.log(`📊 [FALLBACK] Trimmed data to last 50 points`);
                }

                this.chart.update();
                console.log(`✅ [FALLBACK] K-line chart updated successfully with ${this.chart.data.labels.length} data points`);
            } else {
                console.warn('⚠️ [FALLBACK] Cannot update chart: chart instance not available');
            }
        } catch (error) {
            console.error('❌ [FALLBACK] Error updating K-line chart:', error);
        }
    }

    changeTimeframe(newTimeframe) {
        if (this.timeframe === newTimeframe) {
            return;
        }

        console.log('Changing timeframe (fallback):', this.timeframe, '->', newTimeframe);
        this.unsubscribeFromUpdates();
        this.timeframe = newTimeframe;

        // Clear data
        this.chart.data.labels = [];
        this.chart.data.datasets[0].data = [];
        this.chart.update();

        this.loadInitialData();

        // Subscribe to new timeframe only if WebSocket is connected
        if (this.stompClient && this.stompClient.connected) {
            this.subscribeToUpdates();
        } else {
            console.log('WebSocket not connected, will subscribe when connection is established (fallback)');
        }
    }

    changeSymbol(newSymbol) {
        if (this.symbol === newSymbol) {
            return;
        }

        console.log('Changing symbol (fallback):', this.symbol, '->', newSymbol);
        this.unsubscribeFromUpdates();
        this.symbol = newSymbol;

        // Clear data
        this.chart.data.labels = [];
        this.chart.data.datasets[0].data = [];
        this.chart.update();

        this.loadInitialData();
        this.subscribeToUpdates();
    }

    unsubscribeFromUpdates() {
        // 取消WebSocket主题订阅
        if (this.updateSubscription) {
            console.log('🔕 [FALLBACK] Unsubscribing from K-line update topic');
            this.updateSubscription.unsubscribe();
            this.updateSubscription = null;
        }

        // 发送后端取消订阅请求
        if (this.stompClient && this.stompClient.connected) {
            try {
                this.stompClient.send('/app/kline/unsubscribe', {}, JSON.stringify({
                    symbol: this.symbol,
                    timeframe: this.timeframe,
                    sessionId: this.sessionId
                }));
                console.log('📤 [FALLBACK] Sent unsubscribe request to backend:', this.symbol, this.timeframe);
            } catch (error) {
                console.error('❌ [FALLBACK] Error sending unsubscribe request:', error);
            }
        }
    }

    showError(message) {
        this.container.innerHTML = `
            <div class="d-flex align-items-center justify-content-center h-100 text-danger">
                <div class="text-center">
                    <i class="fas fa-exclamation-triangle fa-2x mb-2"></i>
                    <p>${message}</p>
                    <small>使用备用图表显示</small>
                </div>
            </div>
        `;
    }

    enableRealtimeUpdates(stompClient) {
        console.log(`🚀 [FALLBACK] Enabling real-time updates for K-line chart:`, {
            symbol: this.symbol,
            timeframe: this.timeframe,
            hasStompClient: !!stompClient,
            isConnected: stompClient && stompClient.connected
        });

        if (stompClient && stompClient.connected) {
            this.stompClient = stompClient;
            this.subscribeToUpdates();
            console.log('✅ [FALLBACK] Real-time updates enabled for fallback K-line chart');
        } else {
            console.warn('⚠️ [FALLBACK] Cannot enable real-time updates: WebSocket not connected');
        }
    }

    generateSessionId() {
        return 'fallback_kline_' + Math.random().toString(36).substr(2, 9) + '_' + Date.now();
    }

    /**
     * Start auto-refresh timer - 启动自动刷新定时器
     */
    startAutoRefresh() {
        if (!this.autoRefreshEnabled) {
            console.log('🔄 [FALLBACK] Auto-refresh is disabled');
            return;
        }

        // 清除现有定时器
        if (this.refreshTimer) {
            clearInterval(this.refreshTimer);
        }

        console.log(`🔄 [FALLBACK] Starting auto-refresh every ${this.refreshInterval/1000} seconds`);

        this.refreshTimer = setInterval(() => {
            this.refreshKlineData();
        }, this.refreshInterval);
    }

    /**
     * Stop auto-refresh timer - 停止自动刷新定时器
     */
    stopAutoRefresh() {
        if (this.refreshTimer) {
            clearInterval(this.refreshTimer);
            this.refreshTimer = null;
            console.log('🔄 [FALLBACK] Auto-refresh stopped');
        }
    }

    /**
     * Manual refresh K-line data - 手动刷新K线数据
     */
    async refreshKlineData() {
        if (this.isRefreshing) {
            console.log('🔄 [FALLBACK] Refresh already in progress, skipping...');
            return;
        }

        try {
            this.isRefreshing = true;
            console.log('🔄 [FALLBACK] Refreshing K-line data...');

            // 重新加载数据
            await this.loadInitialData();

            console.log('✅ [FALLBACK] K-line data refreshed successfully');

        } catch (error) {
            console.error('❌ [FALLBACK] Error refreshing K-line data:', error);
        } finally {
            this.isRefreshing = false;
        }
    }

    /**
     * Toggle auto-refresh - 切换自动刷新
     */
    toggleAutoRefresh() {
        this.autoRefreshEnabled = !this.autoRefreshEnabled;

        if (this.autoRefreshEnabled) {
            this.startAutoRefresh();
            console.log('✅ [FALLBACK] Auto-refresh enabled');
        } else {
            this.stopAutoRefresh();
            console.log('🔄 [FALLBACK] Auto-refresh disabled');
        }

        return this.autoRefreshEnabled;
    }

    /**
     * Set refresh interval - 设置刷新间隔
     */
    setRefreshInterval(intervalMs) {
        this.refreshInterval = intervalMs;

        if (this.autoRefreshEnabled && this.refreshTimer) {
            // 重启定时器以应用新间隔
            this.stopAutoRefresh();
            this.startAutoRefresh();
        }

        console.log(`🔄 [FALLBACK] Refresh interval set to ${intervalMs/1000} seconds`);
    }

    destroy() {
        // 停止自动刷新
        this.stopAutoRefresh();

        // 取消订阅
        this.unsubscribeFromUpdates();

        if (this.chart) {
            this.chart.destroy();
            this.chart = null;
        }

        console.log('Fallback K-line chart destroyed');
    }
}

// Export for global use
window.FallbackKlineChart = FallbackKlineChart;