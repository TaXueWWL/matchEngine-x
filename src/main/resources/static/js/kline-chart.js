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
        this.lastKnownPrice = null; // 用于心跳数据的价格参考
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
        console.log(`📊 Loading initial K-line data for ${this.symbol}/${this.timeframe}...`);

        try {
            const response = await fetch(`/api/kline/${this.symbol}?timeframe=${this.timeframe}&limit=100`);
            console.log(`📡 K-line API response status:`, response.status);

            if (!response.ok) {
                throw new Error(`Failed to fetch initial K-line data: ${response.status} ${response.statusText}`);
            }

            const klines = await response.json();
            console.log(`📈 Received K-line data:`, {
                responseType: typeof klines,
                isArray: Array.isArray(klines),
                count: klines ? klines.length : 0,
                firstData: klines && klines.length > 0 ? klines[0] : null,
                lastData: klines && klines.length > 0 ? klines[klines.length - 1] : null,
                rawResponse: klines
            });

            if (klines && klines.length > 0) {
                console.log(`📊 Processing ${klines.length} initial K-line records...`);
                const candleData = this.transformKlineData(klines);
                console.log(`🔄 Transformed candle data:`, {
                    count: candleData.length,
                    firstCandle: candleData[0],
                    lastCandle: candleData[candleData.length - 1]
                });

                if (candleData && candleData.length > 0 && this.candlestickSeries) {
                    try {
                        // 最后一次数据验证
                        const validData = candleData.filter(candle =>
                            candle &&
                            typeof candle === 'object' &&
                            candle.time &&
                            candle.time > 0 &&
                            !isNaN(candle.open) &&
                            !isNaN(candle.high) &&
                            !isNaN(candle.low) &&
                            !isNaN(candle.close)
                        );

                        if (validData.length === 0) {
                            console.warn('⚠️ All candle data was invalid after final validation');
                            this.showNoData();
                            return;
                        }

                        console.log(`📊 Setting ${validData.length} validated candles to chart (filtered from ${candleData.length})`);
                        this.candlestickSeries.setData(validData);

                        // 更新最后已知价格
                        const lastCandle = validData[validData.length - 1];
                        if (lastCandle && lastCandle.close > 0) {
                            this.lastKnownPrice = lastCandle.close;
                        }

                        // Auto-fit visible range - 自动适应可见范围
                        if (this.chart) {
                            this.chart.timeScale().fitContent();
                        }

                        console.log(`✅ Loaded ${validData.length} initial K-line data points successfully (including zero-price data)`);
                        this.hideLoading();

                    } catch (chartError) {
                        console.error('❌ Error setting data to chart:', chartError);
                        console.error('❌ Chart error details:', {
                            error: chartError.message,
                            stack: chartError.stack,
                            dataLength: candleData.length,
                            firstCandle: candleData[0],
                            lastCandle: candleData[candleData.length - 1]
                        });
                        this.showError('图表数据设置失败: ' + chartError.message);
                    }
                } else {
                    console.warn('⚠️ No valid candle data after transformation');
                    this.showNoData();
                }
            } else {
                console.log('⚠️ No initial K-line data available from API');
                this.showNoData();
            }

        } catch (error) {
            console.error('❌ Error loading initial K-line data:', error);
            this.showError('加载K线数据失败: ' + error.message);
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
                console.log(`📈 [MAIN CHART] Received K-line update for ${this.symbol}/${this.timeframe}:`, message);
                console.log(`📈 [MAIN CHART] Message body length:`, message.body ? message.body.length : 0);
                console.log(`📈 [MAIN CHART] Raw message body:`, message.body);

                try {
                    const kline = JSON.parse(message.body);
                    console.log(`📊 [MAIN CHART] Parsed K-line data:`, {
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
                        console.log(`🔍 [MAIN CHART] Received zero-price K-line data - processing anyway:`, {
                            symbol: kline.symbol,
                            timeframe: kline.timeframe,
                            timestamp: kline.timestamp,
                            volume: kline.volume
                        });
                    }

                    this.updateChart(kline);
                } catch (error) {
                    console.error(`❌ [MAIN CHART] Error parsing K-line message:`, error);
                    console.error(`❌ [MAIN CHART] Problematic message body:`, message.body);
                }
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

            // 验证输入数据
            if (!kline || typeof kline !== 'object') {
                console.error('❌ Invalid kline data for update:', kline);
                return;
            }

            const candleData = this.transformKlineData([kline])[0];
            console.log(`🔄 Transformed candle data for chart:`, candleData);

            // 验证转换后的数据
            if (!candleData || typeof candleData !== 'object') {
                console.error('❌ Failed to transform kline data or got null result');
                return;
            }

            // 验证必需的数据属性
            if (!candleData.time || candleData.time <= 0) {
                console.error('❌ Invalid or missing timestamp in candle data:', candleData);
                return;
            }

            const prices = [candleData.open, candleData.high, candleData.low, candleData.close];
            if (prices.some(price => price === null || price === undefined || isNaN(price))) {
                console.error('❌ Invalid prices in candle data:', candleData);
                return;
            }

            if (candleData && this.candlestickSeries) {
                try {
                    this.candlestickSeries.update(candleData);

                    // 更新最后已知价格（用于心跳数据）
                    if (candleData.close > 0) {
                        this.lastKnownPrice = candleData.close;
                    }

                    console.log(`✅ K-line chart updated successfully:`, {
                        symbol: this.symbol,
                        timeframe: this.timeframe,
                        time: new Date(candleData.time * 1000),
                        ohlc: {
                            open: candleData.open,
                            high: candleData.high,
                            low: candleData.low,
                            close: candleData.close
                        },
                        lastKnownPrice: this.lastKnownPrice
                    });

                } catch (updateError) {
                    console.error('❌ Error updating chart with candle data:', updateError);
                    console.error('❌ Update error details:', {
                        error: updateError.message,
                        stack: updateError.stack,
                        candleData: candleData
                    });
                }
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
     * Get the last price from the current chart data - 从当前图表数据获取最后价格
     */
    getLastPrice() {
        try {
            if (this.candlestickSeries) {
                // Try to get data from the series (this might not be available in all versions)
                const lastData = this.lastKnownPrice;
                if (lastData && lastData > 0) {
                    return lastData;
                }
            }
            return null;
        } catch (error) {
            console.debug('Could not get last price from series:', error);
            return null;
        }
    }

    /**
     * Transform K-line data to chart format - 将K线数据转换为图表格式
     */
    transformKlineData(klines) {
        if (!klines || !Array.isArray(klines)) {
            console.warn('⚠️ Invalid klines data provided to transform');
            return [];
        }

        return klines.map((kline, index) => {
            // 验证输入数据
            if (!kline || typeof kline !== 'object') {
                console.warn(`⚠️ Invalid kline object at index ${index}:`, kline);
                return null;
            }

            // 确保timestamp是有效的数字
            const timestamp = kline.timestamp;
            if (!timestamp || isNaN(timestamp) || timestamp <= 0) {
                console.warn(`⚠️ Invalid timestamp at index ${index}:`, timestamp);
                return null;
            }

            // 验证时间戳格式 - TradingView需要Unix时间戳（秒）
            const now = Date.now() / 1000; // 当前时间（秒）
            const oneYearAgo = now - (365 * 24 * 60 * 60); // 一年前（秒）
            const oneYearLater = now + (365 * 24 * 60 * 60); // 一年后（秒）

            if (timestamp < oneYearAgo || timestamp > oneYearLater) {
                console.warn(`⚠️ Timestamp seems to be in wrong format at index ${index}:`, {
                    timestamp: timestamp,
                    asDate: new Date(timestamp * 1000).toISOString(),
                    now: now,
                    nowAsDate: new Date(now * 1000).toISOString()
                });
            }

            // 解析价格数据并确保是有效数字
            const parsePrice = (value) => {
                const num = parseFloat(value);
                return isNaN(num) ? 0 : num;
            };

            const transformedData = {
                time: timestamp,
                open: parsePrice(kline.open),
                high: parsePrice(kline.high),
                low: parsePrice(kline.low),
                close: parsePrice(kline.close),
            };

            console.log(`📊 Transforming K-line data at index ${index}:`, {
                original: kline,
                transformed: transformedData
            });

            // 处理价格全为0的情况（心跳K线数据）
            const allPricesZero = transformedData.open === 0 && transformedData.high === 0 &&
                                 transformedData.low === 0 && transformedData.close === 0;

            if (allPricesZero) {
                console.log(`💓 Heartbeat K-line data (all prices zero) for timestamp ${kline.timestamp} - displaying as flat line`);
                // 获取前一个K线的收盘价作为水平线价格，如果没有则使用很小的值
                const lastPrice = this.getLastPrice();
                if (lastPrice && lastPrice > 0) {
                    transformedData.open = lastPrice;
                    transformedData.high = lastPrice;
                    transformedData.low = lastPrice;
                    transformedData.close = lastPrice;
                    console.log(`💓 Using last known price ${lastPrice} for heartbeat line`);
                } else {
                    // 如果没有历史价格，使用0.001作为起始值
                    transformedData.open = 0.001;
                    transformedData.high = 0.001;
                    transformedData.low = 0.001;
                    transformedData.close = 0.001;
                    console.log(`💓 Using default price 0.001 for initial heartbeat line`);
                }
            } else {
                // 验证数据有效性（仅对非零数据进行验证）
                if (transformedData.high < transformedData.low) {
                    console.warn('⚠️ Invalid K-line data: high < low', kline);
                    transformedData.high = Math.max(transformedData.open, transformedData.close);
                    transformedData.low = Math.min(transformedData.open, transformedData.close);
                }

                // 确保high至少等于max(open, close)，low至少等于min(open, close)
                if (transformedData.high < Math.max(transformedData.open, transformedData.close)) {
                    transformedData.high = Math.max(transformedData.open, transformedData.close);
                }
                if (transformedData.low > Math.min(transformedData.open, transformedData.close)) {
                    transformedData.low = Math.min(transformedData.open, transformedData.close);
                }
            }

            return transformedData;
        }).filter((data, index) => {
            // 过滤掉null值和无效数据
            if (data === null || data === undefined) {
                console.warn(`⚠️ Filtered out null data at index ${index}`);
                return false;
            }

            // 过滤掉无效时间戳的数据
            if (!data.time || data.time <= 0) {
                console.warn(`⚠️ Filtered out invalid timestamp data:`, data);
                return false;
            }

            // 确保价格数据不包含null、undefined或NaN
            const prices = [data.open, data.high, data.low, data.close];
            const hasInvalidPrice = prices.some(price =>
                price === null || price === undefined || isNaN(price)
            );

            if (hasInvalidPrice) {
                console.warn(`⚠️ Filtered out data with invalid prices:`, data);
                return false;
            }

            return true;
        });
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
     * Hide loading indicator - 隐藏加载指示器
     */
    hideLoading() {
        const loadingElement = this.container.querySelector('#kline-loading');
        if (loadingElement) {
            loadingElement.style.display = 'none';
        }
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
            isConnected: stompClient && stompClient.connected,
            stompState: stompClient && stompClient.state,
            connectionDetails: stompClient ? {
                connected: stompClient.connected,
                state: stompClient.state
            } : null
        });

        // Check connection with both legacy and new API compatibility
        const isConnected = stompClient && (
            stompClient.connected ||
            (stompClient.state && stompClient.state === 'CONNECTED')
        );

        if (stompClient && isConnected) {
            this.stompClient = stompClient;
            console.log('🔔 Starting K-line WebSocket subscription...');
            this.subscribeToUpdates();

            // 强制重新加载数据以确保与WebSocket推送同步
            console.log('🔄 Refreshing K-line data to sync with WebSocket...');
            setTimeout(() => {
                this.loadInitialData();
            }, 1000);

            console.log('✅ Real-time updates enabled for K-line chart');
        } else {
            console.warn('⚠️ Cannot enable real-time updates: WebSocket not connected', {
                hasClient: !!stompClient,
                connected: stompClient && stompClient.connected,
                state: stompClient && stompClient.state
            });
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