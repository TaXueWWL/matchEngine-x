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
        // 保存订阅对象引用以便正确取消订阅
        this.updateSubscription = null;

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
                        borderColor: '#02C076',
                        backgroundColor: 'rgba(2, 192, 118, 0.1)',
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

            console.log('Fallback K-line chart initialized successfully');

        } catch (error) {
            console.error('Failed to initialize fallback K-line chart:', error);
            this.showError('图表初始化失败');
        }
    }

    async loadInitialData() {
        try {
            const response = await fetch(`/api/kline/${this.symbol}?timeframe=${this.timeframe}&limit=50`);
            if (!response.ok) {
                throw new Error('Failed to fetch initial K-line data');
            }

            const klines = await response.json();
            if (klines && klines.length > 0) {
                const labels = klines.map(k => new Date(k.timestamp * 1000));
                const prices = klines.map(k => parseFloat(k.close));

                this.chart.data.labels = labels;
                this.chart.data.datasets[0].data = prices;
                this.chart.update();

                console.log(`Loaded ${klines.length} K-line data points (fallback)`);
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
                    tradeCount: kline.tradeCount
                });
                this.updateChart(kline);
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

    destroy() {
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