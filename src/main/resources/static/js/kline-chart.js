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
        this.lastKnownPrice = null; // 用于心跳数据的价格参考
        // 定时刷新相关
        this.refreshTimer = null;
        this.refreshInterval = 3000; // 3秒刷新一次
        this.isRefreshing = false;
        this.autoRefreshEnabled = true;

        // Chart configuration - Dark theme chart configuration
        // 延迟获取容器尺寸，避免在DOM未准备好时访问
        const getContainerWidth = () => {
            if (!this.container) return 800; // 默认宽度
            const width = this.container.clientWidth;
            return width > 0 ? width : 800; // 确保有最小宽度
        };

        this.chartOptions = {
            width: getContainerWidth(),
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

            // 验证容器和配置
            console.log('🔍 Pre-chart creation validation:', {
                container: this.container,
                containerTagName: this.container ? this.container.tagName : null,
                containerId: this.containerId,
                containerInDOM: this.container ? document.contains(this.container) : false,
                chartOptions: this.chartOptions,
                optionsValid: this.chartOptions && typeof this.chartOptions === 'object'
            });

            // 验证chartOptions中的关键字段
            if (!this.chartOptions || typeof this.chartOptions !== 'object') {
                throw new Error('Invalid chart options');
            }

            if (!this.container || !this.container.tagName) {
                throw new Error('Invalid container element');
            }

            // Create chart - 创建图表
            console.log('🎯 Creating chart with options:', this.chartOptions);

            try {
                this.chart = LightweightCharts.createChart(this.container, this.chartOptions);
                console.log('✅ Chart created successfully:', !!this.chart);
            } catch (chartCreationError) {
                console.error('❌ Failed to create chart:', chartCreationError);
                console.error('❌ Chart creation error details:', {
                    error: chartCreationError.message,
                    stack: chartCreationError.stack,
                    container: this.container,
                    containerWidth: this.container ? this.container.clientWidth : 'N/A',
                    containerHeight: this.container ? this.container.clientHeight : 'N/A',
                    chartOptions: this.chartOptions
                });
                throw chartCreationError;
            }

            console.log('Chart created:', !!this.chart);
            console.log('Chart methods:', Object.getOwnPropertyNames(Object.getPrototypeOf(this.chart)));

            // Add candlestick series - 添加蜡烛图系列
            console.log('Available methods on chart:', Object.getOwnPropertyNames(this.chart));

            // Try different method names for different versions
            try {
                const seriesOptions = {
                    upColor: '#00C851',       // Green for up candles (涨)
                    downColor: '#FF4444',     // Red for down candles (跌)
                    borderVisible: false,
                    wickUpColor: '#00C851',   // Green wicks for up candles
                    wickDownColor: '#FF4444', // Red wicks for down candles
                };

                console.log('🔧 Adding candlestick series with options:', seriesOptions);

                if (typeof this.chart.addCandlestickSeries === 'function') {
                    console.log('📊 Using addCandlestickSeries method');
                    this.candlestickSeries = this.chart.addCandlestickSeries(seriesOptions);
                } else if (typeof this.chart.addSeries === 'function') {
                    console.log('📊 Using addSeries method');
                    this.candlestickSeries = this.chart.addSeries('candlestick', seriesOptions);
                } else {
                    throw new Error('No suitable method found to add candlestick series');
                }

                console.log('✅ Candlestick series created successfully:', !!this.candlestickSeries);

            } catch (seriesCreationError) {
                console.error('❌ Failed to create candlestick series:', seriesCreationError);
                console.error('❌ Series creation error details:', {
                    error: seriesCreationError.message,
                    stack: seriesCreationError.stack,
                    chartMethods: this.chart ? Object.getOwnPropertyNames(this.chart) : 'No chart'
                });
                throw seriesCreationError;
            }

            // Handle resize - 处理窗口大小调整
            this.handleResize();

            // Load initial data - 加载初始数据
            this.loadInitialData();

            // Start auto-refresh timer - 启动自动刷新定时器
            this.startAutoRefresh();

            // 监听页面可见性变化，当页面重新变为可见时立即刷新一次
            this.setupVisibilityChangeHandler();

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

            if (candleData && this.candlestickSeries && this.validateCandleData(candleData)) {
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
            let timestamp = kline.timestamp;
            if (timestamp === null || timestamp === undefined) {
                console.warn(`⚠️ Null/undefined timestamp at index ${index}:`, timestamp);
                return null;
            }
            timestamp = parseFloat(timestamp);
            if (!timestamp || isNaN(timestamp) || !isFinite(timestamp) || timestamp <= 0) {
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
                if (value === null || value === undefined) return 0;
                const num = parseFloat(value);
                return isNaN(num) || !isFinite(num) ? 0 : num;
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
                if (lastPrice && typeof lastPrice === 'number' && lastPrice > 0 && isFinite(lastPrice)) {
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
                price === null || price === undefined || isNaN(price) || !isFinite(price)
            );

            if (hasInvalidPrice) {
                console.warn(`⚠️ Filtered out data with invalid prices:`, data);
                return false;
            }

            // 最后验证所有属性都不为null
            if (data.time === null || data.time === undefined ||
                data.open === null || data.open === undefined ||
                data.high === null || data.high === undefined ||
                data.low === null || data.low === undefined ||
                data.close === null || data.close === undefined) {
                console.warn(`⚠️ Filtered out data with null values:`, data);
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

        // Update timeframe - 更新时间框架
        this.timeframe = newTimeframe;

        // Clear chart data - 清空图表数据
        if (this.candlestickSeries) {
            this.candlestickSeries.setData([]);
        }

        // Load new data - 加载新数据
        this.loadInitialData();
    }

    /**
     * Change symbol - 更改交易对
     */
    changeSymbol(newSymbol) {
        if (this.symbol === newSymbol) {
            return;
        }

        console.log('Changing symbol from', this.symbol, 'to', newSymbol);

        // Update symbol - 更新交易对
        this.symbol = newSymbol;

        // Clear chart data - 清空图表数据
        if (this.candlestickSeries) {
            this.candlestickSeries.setData([]);
        }

        // Load new data - 加载新数据
        this.loadInitialData();
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
     * Start auto-refresh timer - 启动自动刷新定时器
     */
    startAutoRefresh() {
        if (!this.autoRefreshEnabled) {
            console.log('🔄 Auto-refresh is disabled');
            return;
        }

        // 清除现有定时器
        if (this.refreshTimer) {
            clearInterval(this.refreshTimer);
            this.refreshTimer = null;
        }

        console.log(`🔄 Starting auto-refresh every ${this.refreshInterval/1000} seconds`);

        this.refreshTimer = setInterval(() => {
            try {
                // 检查页面是否可见，避免在后台标签页中进行不必要的刷新
                if (document.hidden) {
                    console.log('🔄 Page is hidden, skipping auto-refresh');
                    return;
                }

                this.refreshKlineData().catch(error => {
                    console.error('❌ Auto-refresh error:', error);
                });
            } catch (error) {
                console.error('❌ Auto-refresh timer error:', error);
            }
        }, this.refreshInterval);

        console.log('✅ Auto-refresh timer started successfully');
    }

    /**
     * Stop auto-refresh timer - 停止自动刷新定时器
     */
    stopAutoRefresh() {
        if (this.refreshTimer) {
            clearInterval(this.refreshTimer);
            this.refreshTimer = null;
            console.log('🔄 Auto-refresh stopped');
        }
    }

    /**
     * Manual refresh K-line data - 手动刷新K线数据
     */
    async refreshKlineData(forceReloadAll = false) {
        if (this.isRefreshing) {
            console.log('🔄 Refresh already in progress, skipping...');
            return;
        }

        try {
            this.isRefreshing = true;
            const startTime = Date.now();
            console.log('🔄 Refreshing K-line data...', {
                symbol: this.symbol,
                timeframe: this.timeframe,
                autoRefreshEnabled: this.autoRefreshEnabled,
                hasTimer: !!this.refreshTimer,
                forceReloadAll: forceReloadAll
            });

            // 显示刷新指示器
            this.showRefreshIndicator();

            if (forceReloadAll) {
                // 完全重新加载所有数据（用于手动刷新或初始加载）
                await this.loadInitialData();
            } else {
                // 只更新最新数据（用于定时刷新）
                await this.updateLatestKlineData();
            }

            const duration = Date.now() - startTime;
            console.log(`✅ K-line data refreshed successfully in ${duration}ms`);

        } catch (error) {
            console.error('❌ Error refreshing K-line data:', error);
            // 可选：显示用户友好的错误提示
            this.showError('数据刷新失败，请稍后重试');
        } finally {
            this.isRefreshing = false;
            // 隐藏刷新指示器
            this.hideRefreshIndicator();
        }
    }

    /**
     * Update only the latest K-line data - 只更新最新的K线数据
     */
    async updateLatestKlineData() {
        console.log(`📊 Updating latest K-line data for ${this.symbol}/${this.timeframe}...`);

        try {
            // 获取最新的几个K线数据点（用于更新当前周期）
            const response = await fetch(`/api/kline/${this.symbol}?timeframe=${this.timeframe}&limit=2`);
            console.log(`📡 Latest K-line API response status:`, response.status);

            if (!response.ok) {
                throw new Error(`Failed to fetch latest K-line data: ${response.status} ${response.statusText}`);
            }

            const klines = await response.json();
            console.log(`📈 Received latest K-line data:`, {
                responseType: typeof klines,
                isArray: Array.isArray(klines),
                count: klines ? klines.length : 0,
                data: klines
            });

            if (klines && klines.length > 0 && this.candlestickSeries) {
                // 转换最新的K线数据
                const candleData = this.transformKlineData(klines);
                console.log(`🔄 Transformed latest candle data:`, candleData);

                if (candleData && candleData.length > 0) {
                    // 更新最新的数据点（通常是当前未完成的K线）
                    const latestCandle = candleData[candleData.length - 1];

                    if (latestCandle && this.validateCandleData(latestCandle)) {
                        try {
                            console.log(`📊 Updating latest candle:`, latestCandle);
                            this.candlestickSeries.update(latestCandle);

                            // 更新最后已知价格
                            if (latestCandle.close > 0) {
                                this.lastKnownPrice = latestCandle.close;
                            }

                            console.log(`✅ Latest K-line data updated successfully`);
                        } catch (updateError) {
                            console.error('❌ Error updating latest candle:', updateError);
                            // 如果更新失败，可能是因为时间戳问题，尝试重新加载最近的数据
                            console.log('🔄 Update failed, trying to add as new data point...');

                            // 如果有多个数据点，添加之前的数据点
                            if (candleData.length > 1) {
                                const prevCandle = candleData[candleData.length - 2];
                                this.candlestickSeries.update(prevCandle);
                            }
                            this.candlestickSeries.update(latestCandle);
                        }
                    }
                }
            } else {
                console.log('⚠️ No latest K-line data available from API');
            }

        } catch (error) {
            console.error('❌ Error updating latest K-line data:', error);
            throw error; // 重新抛出错误以便上层处理
        }
    }

    /**
     * Validate candle data before passing to LightweightCharts - 验证K线数据
     */
    validateCandleData(candle) {
        if (!candle || typeof candle !== 'object') {
            console.warn('⚠️ Invalid candle object:', candle);
            return false;
        }

        // 检查必需的属性
        const requiredProps = ['time', 'open', 'high', 'low', 'close'];
        for (const prop of requiredProps) {
            const value = candle[prop];
            if (value === null || value === undefined || isNaN(value) || !isFinite(value)) {
                console.warn(`⚠️ Invalid ${prop} value in candle:`, value, candle);
                return false;
            }
        }

        // 检查时间戳是否有效
        if (candle.time <= 0) {
            console.warn('⚠️ Invalid timestamp in candle:', candle.time);
            return false;
        }

        // 检查OHLC关系是否有效
        if (candle.high < candle.low) {
            console.warn('⚠️ Invalid OHLC: high < low:', candle);
            return false;
        }

        return true;
    }

    /**
     * Show refresh indicator - 显示刷新指示器
     */
    showRefreshIndicator() {
        // 在图表容器上添加刷新指示器
        if (!this.container) return;

        let refreshIndicator = this.container.querySelector('.refresh-indicator');
        if (!refreshIndicator) {
            refreshIndicator = document.createElement('div');
            refreshIndicator.className = 'refresh-indicator';
            refreshIndicator.innerHTML = `
                <div class="refresh-spinner">
                    <i class="fas fa-sync-alt fa-spin"></i>
                    <span>刷新中...</span>
                </div>
            `;
            refreshIndicator.style.cssText = `
                position: absolute;
                top: 10px;
                right: 10px;
                z-index: 1000;
                background: rgba(0, 0, 0, 0.7);
                color: white;
                padding: 8px 12px;
                border-radius: 4px;
                font-size: 12px;
                display: flex;
                align-items: center;
                gap: 6px;
            `;
            this.container.style.position = 'relative';
            this.container.appendChild(refreshIndicator);
        } else {
            refreshIndicator.style.display = 'flex';
        }
    }

    /**
     * Hide refresh indicator - 隐藏刷新指示器
     */
    hideRefreshIndicator() {
        if (!this.container) return;

        const refreshIndicator = this.container.querySelector('.refresh-indicator');
        if (refreshIndicator) {
            refreshIndicator.style.display = 'none';
        }
    }

    /**
     * Toggle auto-refresh - 切换自动刷新
     */
    toggleAutoRefresh() {
        this.autoRefreshEnabled = !this.autoRefreshEnabled;

        if (this.autoRefreshEnabled) {
            this.startAutoRefresh();
            console.log('✅ Auto-refresh enabled');
        } else {
            this.stopAutoRefresh();
            console.log('🔄 Auto-refresh disabled');
        }

        return this.autoRefreshEnabled;
    }

    /**
     * Set refresh interval - 设置刷新间隔
     */
    setRefreshInterval(intervalMs) {
        if (!intervalMs || intervalMs < 1000 || intervalMs > 300000) {
            console.warn('⚠️ Invalid refresh interval, must be between 1-300 seconds');
            return;
        }

        this.refreshInterval = intervalMs;

        // 如果自动刷新已启用，重启定时器以应用新间隔
        if (this.autoRefreshEnabled) {
            console.log(`🔄 Restarting auto-refresh with new interval: ${intervalMs/1000} seconds`);
            this.stopAutoRefresh();
            this.startAutoRefresh();
        }

        console.log(`✅ Refresh interval set to ${intervalMs/1000} seconds`);
    }

    /**
     * Setup page visibility change handler - 设置页面可见性变化处理
     */
    setupVisibilityChangeHandler() {
        if (typeof document.addEventListener === 'undefined') {
            return; // 不支持addEventListener的旧浏览器
        }

        this.visibilityChangeHandler = () => {
            if (!document.hidden && this.autoRefreshEnabled) {
                console.log('🔄 Page became visible, refreshing K-line data...');
                // 页面变为可见时立即刷新一次（完全重新加载数据）
                setTimeout(() => {
                    this.refreshKlineData(true).catch(error => {
                        console.error('❌ Visibility refresh error:', error);
                    });
                }, 100); // 稍微延迟以确保页面完全加载
            }
        };

        document.addEventListener('visibilitychange', this.visibilityChangeHandler);
        console.log('✅ Page visibility change handler setup complete');
    }

    /**
     * Remove page visibility change handler - 移除页面可见性变化处理
     */
    removeVisibilityChangeHandler() {
        if (this.visibilityChangeHandler && typeof document.removeEventListener !== 'undefined') {
            document.removeEventListener('visibilitychange', this.visibilityChangeHandler);
            this.visibilityChangeHandler = null;
            console.log('✅ Page visibility change handler removed');
        }
    }

    /**
     * Destroy chart and cleanup - 销毁图表并清理
     */
    destroy() {
        // 停止自动刷新
        this.stopAutoRefresh();

        // 移除页面可见性处理器
        this.removeVisibilityChangeHandler();

        if (this.chart) {
            this.chart.remove();
            this.chart = null;
        }

        console.log('K-line chart destroyed');
    }
}

// Export for global use - 导出供全局使用
window.KlineChart = KlineChart;