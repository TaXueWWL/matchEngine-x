/**
 * K-line Chart Component using TradingView Lightweight Charts
 * K线图表组件，使用TradingView Lightweight Charts
 */

class KlineChart {
    constructor(containerId, options = {}) {
        this.containerId = containerId;
        this.container = document.getElementById(containerId);
        this.chart = null;
        this.candlestickSeries = null;
        this.volumeSeries = null;
        this.symbol = options.symbol || 'BTCUSDT';
        this.timeframe = options.timeframe || '1m';
        this.stompClient = options.stompClient || null;
        this.sessionId = options.sessionId || this.generateSessionId();
        // 保存订阅对象引用以便正确取消订阅
        this.updateSubscription = null;
        this.initialSubscription = null;

        // Chart configuration - Dark theme chart configuration
        this.chartOptions = {
            width: this.container.clientWidth,
            height: options.height || 400,
            layout: {
                backgroundColor: '#181A20',
                textColor: '#EAECEF',
            },
            grid: {
                vertLines: {
                    color: '#2B2F36',
                },
                horzLines: {
                    color: '#2B2F36',
                },
            },
            crosshair: {
                mode: 0, // CrosshairMode.Normal
                vertLine: {
                    color: '#F0B90B',
                    width: 1,
                    labelBackgroundColor: '#F0B90B',
                },
                horzLine: {
                    color: '#F0B90B',
                    width: 1,
                    labelBackgroundColor: '#F0B90B',
                },
            },
            rightPriceScale: {
                borderColor: '#2B2F36',
                textColor: '#EAECEF',
            },
            timeScale: {
                borderColor: '#2B2F36',
                textColor: '#EAECEF',
                timeVisible: true,
                secondsVisible: false,
            },
        };

        this.init();
    }

    /**
     * Initialize chart - 初始化图表
     */
    init() {
        try {
            // Check if LightweightCharts is available - 检查LightweightCharts是否可用
            if (typeof LightweightCharts === 'undefined') {
                throw new Error('LightweightCharts library not loaded');
            }

            console.log('LightweightCharts available:', !!LightweightCharts);
            console.log('createChart available:', typeof LightweightCharts.createChart);

            // Create chart - 创建图表
            this.chart = LightweightCharts.createChart(this.container, this.chartOptions);

            console.log('Chart created:', !!this.chart);
            console.log('Chart methods:', Object.getOwnPropertyNames(Object.getPrototypeOf(this.chart)));

            // Add candlestick series - 添加蜡烛图系列
            console.log('Available methods on chart:', Object.getOwnPropertyNames(this.chart));

            // Try different method names for different versions
            if (typeof this.chart.addCandlestickSeries === 'function') {
                this.candlestickSeries = this.chart.addCandlestickSeries({
                    upColor: '#02C076',       // Bitget green for up candles
                    downColor: '#F6465D',     // Bitget red for down candles
                    borderVisible: false,
                    wickUpColor: '#02C076',   // Green wicks for up candles
                    wickDownColor: '#F6465D', // Red wicks for down candles
                });
            } else if (typeof this.chart.addSeries === 'function') {
                this.candlestickSeries = this.chart.addSeries('candlestick', {
                    upColor: '#02C076',       // Bitget green for up candles
                    downColor: '#F6465D',     // Bitget red for down candles
                    borderVisible: false,
                    wickUpColor: '#02C076',   // Green wicks for up candles
                    wickDownColor: '#F6465D', // Red wicks for down candles
                });
            } else {
                throw new Error('No suitable method found to add candlestick series');
            }

            // Handle resize - 处理窗口大小调整
            this.handleResize();

            // Load initial data - 加载初始数据
            this.loadInitialData();

            // Subscribe to real-time updates if WebSocket is available - 如果WebSocket可用则订阅实时更新
            if (this.stompClient && this.stompClient.connected) {
                this.subscribeToUpdates();
            } else {
                console.log('WebSocket not ready, K-line chart will show historical data only');
            }

            console.log('K-line chart initialized for', this.symbol, this.timeframe);

        } catch (error) {
            console.error('Failed to initialize K-line chart:', error);
            this.showError('图表初始化失败');
        }
    }

    /**
     * Load initial K-line data - 加载初始K线数据
     */
    async loadInitialData() {
        try {
            const response = await fetch(`/api/kline/${this.symbol}?timeframe=${this.timeframe}&limit=100`);
            if (!response.ok) {
                throw new Error('Failed to fetch initial K-line data');
            }

            const klines = await response.json();
            if (klines && klines.length > 0 && this.candlestickSeries) {
                const candleData = this.transformKlineData(klines);
                this.candlestickSeries.setData(candleData);

                // Auto-fit visible range - 自动适应可见范围
                if (this.chart) {
                    this.chart.timeScale().fitContent();
                }

                console.log(`Loaded ${klines.length} initial K-line data points`);
            } else {
                console.log('No initial K-line data available');
                this.showNoData();
            }

        } catch (error) {
            console.error('Error loading initial K-line data:', error);
            this.showError('加载K线数据失败');
        }
    }

    /**
     * Subscribe to real-time K-line updates via WebSocket - 通过WebSocket订阅实时K线更新
     */
    subscribeToUpdates() {
        if (!this.stompClient) {
            console.log('WebSocket client not available, skipping K-line subscription');
            return;
        }

        if (!this.stompClient.connected) {
            console.warn('WebSocket not connected, cannot subscribe to K-line updates');
            return;
        }

        try {
            // 先取消之前的订阅
            this.unsubscribeFromWebSocketTopics();

            // Subscribe to real-time K-line updates - 订阅实时K线更新
            const updateTopic = `/topic/kline/${this.symbol}/${this.timeframe}`;
            console.log(`🔔 Subscribing to K-line updates: ${updateTopic}`);
            this.updateSubscription = this.stompClient.subscribe(updateTopic, (message) => {
                console.log(`📈 Received K-line update for ${this.symbol}/${this.timeframe}:`, message);
                const kline = JSON.parse(message.body);
                console.log(`📊 Parsed K-line data:`, {
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

            // Subscribe to initial data push - 订阅初始数据推送
            const initialTopic = `/topic/kline/${this.symbol}/${this.timeframe}/initial`;
            console.log(`🔔 Subscribing to initial K-line data: ${initialTopic}`);
            this.initialSubscription = this.stompClient.subscribe(initialTopic, (message) => {
                console.log(`📊 Received initial K-line data for ${this.symbol}/${this.timeframe}:`, message);
                const klines = JSON.parse(message.body);
                console.log(`📈 Initial K-line data count: ${klines ? klines.length : 0}`, klines);
                if (klines && klines.length > 0 && this.candlestickSeries) {
                    const candleData = this.transformKlineData(klines);
                    console.log(`📊 Transformed candle data:`, candleData);
                    this.candlestickSeries.setData(candleData);
                    if (this.chart) {
                        this.chart.timeScale().fitContent();
                    }
                    console.log(`✅ Initial K-line chart updated with ${candleData.length} data points`);
                }
            });

            // Send subscription request - 发送订阅请求
            const subscriptionData = {
                symbol: this.symbol,
                timeframe: this.timeframe,
                sessionId: this.sessionId
            };
            console.log(`📤 Sending K-line subscription request:`, subscriptionData);
            this.stompClient.send('/app/kline/subscribe', {}, JSON.stringify(subscriptionData));

            console.log('Subscribed to K-line updates:', this.symbol, this.timeframe);

        } catch (error) {
            console.error('Error subscribing to K-line updates:', error);
        }
    }

    /**
     * Update chart with new K-line data - 使用新的K线数据更新图表
     */
    updateChart(kline) {
        try {
            console.log(`🔄 Updating K-line chart with new data:`, kline);
            const candleData = this.transformKlineData([kline])[0];
            console.log(`🔄 Transformed candle data for chart:`, candleData);

            if (candleData && this.candlestickSeries) {
                this.candlestickSeries.update(candleData);
                console.log(`✅ K-line chart updated successfully:`, {
                    symbol: this.symbol,
                    timeframe: this.timeframe,
                    time: new Date(candleData.time * 1000),
                    ohlc: {
                        open: candleData.open,
                        high: candleData.high,
                        low: candleData.low,
                        close: candleData.close
                    }
                });
            } else {
                console.warn('⚠️ Cannot update chart: missing candleData or candlestickSeries', {
                    candleData: !!candleData,
                    candlestickSeries: !!this.candlestickSeries
                });
            }
        } catch (error) {
            console.error('❌ Error updating K-line chart:', error);
        }
    }

    /**
     * Transform K-line data to chart format - 将K线数据转换为图表格式
     */
    transformKlineData(klines) {
        return klines.map(kline => ({
            time: kline.timestamp,
            open: parseFloat(kline.open),
            high: parseFloat(kline.high),
            low: parseFloat(kline.low),
            close: parseFloat(kline.close),
        }));
    }

    /**
     * Change timeframe - 更改时间框架
     */
    changeTimeframe(newTimeframe) {
        if (this.timeframe === newTimeframe) {
            return;
        }

        console.log('Changing timeframe from', this.timeframe, 'to', newTimeframe);

        // Unsubscribe from current timeframe - 取消订阅当前时间框架
        this.unsubscribeFromUpdates();

        // Update timeframe - 更新时间框架
        this.timeframe = newTimeframe;

        // Clear chart data - 清空图表数据
        if (this.candlestickSeries) {
            this.candlestickSeries.setData([]);
        }

        // Load new data - 加载新数据
        this.loadInitialData();

        // Subscribe to new timeframe only if WebSocket is connected - 仅在WebSocket连接时订阅新时间框架
        if (this.stompClient && this.stompClient.connected) {
            this.subscribeToUpdates();
        } else {
            console.log('WebSocket not connected, will subscribe when connection is established');
        }
    }

    /**
     * Change symbol - 更改交易对
     */
    changeSymbol(newSymbol) {
        if (this.symbol === newSymbol) {
            return;
        }

        console.log('Changing symbol from', this.symbol, 'to', newSymbol);

        // Unsubscribe from current symbol - 取消订阅当前交易对
        this.unsubscribeFromUpdates();

        // Update symbol - 更新交易对
        this.symbol = newSymbol;

        // Clear chart data - 清空图表数据
        if (this.candlestickSeries) {
            this.candlestickSeries.setData([]);
        }

        // Load new data - 加载新数据
        this.loadInitialData();

        // Subscribe to new symbol - 订阅新交易对
        this.subscribeToUpdates();
    }

    /**
     * 取消WebSocket主题订阅（仅取消客户端订阅，不发送后端取消订阅请求）
     */
    unsubscribeFromWebSocketTopics() {
        try {
            if (this.updateSubscription) {
                console.log('🔕 Unsubscribing from K-line update topic');
                this.updateSubscription.unsubscribe();
                this.updateSubscription = null;
            }
            if (this.initialSubscription) {
                console.log('🔕 Unsubscribing from K-line initial topic');
                this.initialSubscription.unsubscribe();
                this.initialSubscription = null;
            }
        } catch (error) {
            console.error('❌ Error unsubscribing from WebSocket topics:', error);
        }
    }

    /**
     * Unsubscribe from real-time updates - 取消订阅实时更新
     */
    unsubscribeFromUpdates() {
        // 取消WebSocket主题订阅
        this.unsubscribeFromWebSocketTopics();

        // 发送后端取消订阅请求
        if (this.stompClient && this.stompClient.connected) {
            try {
                this.stompClient.send('/app/kline/unsubscribe', {}, JSON.stringify({
                    symbol: this.symbol,
                    timeframe: this.timeframe,
                    sessionId: this.sessionId
                }));
                console.log('📤 Sent unsubscribe request to backend:', this.symbol, this.timeframe);
            } catch (error) {
                console.error('❌ Error sending unsubscribe request:', error);
            }
        }
    }

    /**
     * Handle window resize - 处理窗口大小调整
     */
    handleResize() {
        const resizeObserver = new ResizeObserver(entries => {
            if (entries.length === 0 || entries[0].target !== this.container) {
                return;
            }

            const newRect = entries[0].contentRect;
            this.chart.applyOptions({
                width: newRect.width,
                height: newRect.height
            });
        });

        resizeObserver.observe(this.container);
    }

    /**
     * Show error message - 显示错误信息
     */
    showError(message) {
        this.container.innerHTML = `
            <div class="d-flex align-items-center justify-content-center h-100 text-danger">
                <div class="text-center">
                    <i class="fas fa-exclamation-triangle fa-2x mb-2"></i>
                    <p>${message}</p>
                </div>
            </div>
        `;
    }

    /**
     * Show no data message - 显示无数据信息
     */
    showNoData() {
        this.container.innerHTML = `
            <div class="d-flex align-items-center justify-content-center h-100 text-muted">
                <div class="text-center">
                    <i class="fas fa-chart-line fa-2x mb-2"></i>
                    <p>暂无K线数据</p>
                    <small>开始交易后将显示K线图</small>
                </div>
            </div>
        `;
    }

    /**
     * Generate unique session ID - 生成唯一会话ID
     */
    generateSessionId() {
        return 'kline_' + Math.random().toString(36).substr(2, 9) + '_' + Date.now();
    }

    /**
     * Enable real-time updates when WebSocket becomes available - WebSocket可用时启用实时更新
     */
    enableRealtimeUpdates(stompClient) {
        console.log(`🚀 Enabling real-time updates for K-line chart:`, {
            symbol: this.symbol,
            timeframe: this.timeframe,
            hasStompClient: !!stompClient,
            isConnected: stompClient && stompClient.connected
        });

        if (stompClient && stompClient.connected) {
            this.stompClient = stompClient;
            this.subscribeToUpdates();
            console.log('✅ Real-time updates enabled for K-line chart');
        } else {
            console.warn('⚠️ Cannot enable real-time updates: WebSocket not connected');
        }
    }

    /**
     * Destroy chart and cleanup - 销毁图表并清理
     */
    destroy() {
        // 取消订阅
        this.unsubscribeFromUpdates();

        if (this.chart) {
            this.chart.remove();
            this.chart = null;
        }

        console.log('K-line chart destroyed');
    }
}

// Export for global use - 导出供全局使用
window.KlineChart = KlineChart;