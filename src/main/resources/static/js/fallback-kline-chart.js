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
        this.lastKnownPrice = null; // 用于心跳数据的价格参考
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
        this.timeframe = newTimeframe;

        // Clear data
        this.chart.data.labels = [];
        this.chart.data.datasets[0].data = [];
        this.chart.update();

        this.loadInitialData();
    }

    changeSymbol(newSymbol) {
        if (this.symbol === newSymbol) {
            return;
        }

        console.log('Changing symbol (fallback):', this.symbol, '->', newSymbol);
        this.symbol = newSymbol;

        // Clear data
        this.chart.data.labels = [];
        this.chart.data.datasets[0].data = [];
        this.chart.update();

        this.loadInitialData();
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
            try {
                // 检查页面是否可见，避免在后台标签页中进行不必要的刷新
                if (document.hidden) {
                    console.log('🔄 [FALLBACK] Page is hidden, skipping auto-refresh');
                    return;
                }

                this.refreshKlineData().catch(error => {
                    console.error('❌ [FALLBACK] Auto-refresh error:', error);
                });
            } catch (error) {
                console.error('❌ [FALLBACK] Auto-refresh timer error:', error);
            }
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
    async refreshKlineData(forceReloadAll = false) {
        if (this.isRefreshing) {
            console.log('🔄 [FALLBACK] Refresh already in progress, skipping...');
            return;
        }

        try {
            this.isRefreshing = true;
            console.log('🔄 [FALLBACK] Refreshing K-line data...', {
                symbol: this.symbol,
                timeframe: this.timeframe,
                forceReloadAll: forceReloadAll
            });

            if (forceReloadAll) {
                // 完全重新加载所有数据
                await this.loadInitialData();
            } else {
                // 只更新最新数据点
                await this.updateLatestKlineData();
            }

            console.log('✅ [FALLBACK] K-line data refreshed successfully');

        } catch (error) {
            console.error('❌ [FALLBACK] Error refreshing K-line data:', error);
        } finally {
            this.isRefreshing = false;
        }
    }

    /**
     * Update only the latest K-line data - 只更新最新的K线数据
     */
    async updateLatestKlineData() {
        console.log(`📊 [FALLBACK] Updating latest K-line data for ${this.symbol}/${this.timeframe}...`);

        try {
            const response = await fetch(`/api/kline/${this.symbol}?timeframe=${this.timeframe}&limit=1`);

            if (!response.ok) {
                throw new Error(`Failed to fetch latest K-line data: ${response.status} ${response.statusText}`);
            }

            const klines = await response.json();

            if (klines && klines.length > 0 && this.chart) {
                const kline = klines[0];
                const timestamp = new Date(kline.timestamp * 1000);
                const price = parseFloat(kline.close) || this.lastKnownPrice || 0;

                // 更新最后已知价格
                if (price > 0) {
                    this.lastKnownPrice = price;
                }

                // 检查是否需要添加新数据点还是更新现有数据点
                if (this.chart.data.labels.length > 0) {
                    const lastTimestamp = this.chart.data.labels[this.chart.data.labels.length - 1];

                    // 如果时间戳相同，更新现有数据点；否则添加新数据点
                    if (lastTimestamp.getTime() === timestamp.getTime()) {
                        // 更新最后一个数据点
                        this.chart.data.datasets[0].data[this.chart.data.datasets[0].data.length - 1] = price;
                    } else {
                        // 添加新数据点
                        this.chart.data.labels.push(timestamp);
                        this.chart.data.datasets[0].data.push(price);

                        // 保持最多50个数据点
                        if (this.chart.data.labels.length > 50) {
                            this.chart.data.labels.shift();
                            this.chart.data.datasets[0].data.shift();
                        }
                    }
                } else {
                    // 如果没有数据，添加第一个数据点
                    this.chart.data.labels.push(timestamp);
                    this.chart.data.datasets[0].data.push(price);
                }

                this.chart.update('none'); // 使用 'none' 动画模式提高性能
                console.log(`✅ [FALLBACK] Latest K-line data updated successfully`);
            }

        } catch (error) {
            console.error('❌ [FALLBACK] Error updating latest K-line data:', error);
            throw error;
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

        if (this.chart) {
            this.chart.destroy();
            this.chart = null;
        }

        console.log('Fallback K-line chart destroyed');
    }
}

// Export for global use
window.FallbackKlineChart = FallbackKlineChart;