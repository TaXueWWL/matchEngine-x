#!/usr/bin/env node

// 简化版本，使用内置模块测试K线推送
const https = require('https');
const http = require('http');

console.log('🚀 K线WebSocket推送测试客户端 (Node.js)');
console.log('='.repeat(60));

let stompClient = null;
let messageCount = 0;
let startTime = Date.now();
let lastMessageTime = null;

// 统计信息
const stats = {
    connected: false,
    messagesReceived: 0,
    lastMessageTime: null,
    connectionDuration: 0,
    messagesPerMinute: 0,
    messageTimestamps: []
};

function log(message, level = 'INFO') {
    const timestamp = new Date().toISOString();
    const levelColors = {
        'INFO': '\x1b[36m',    // Cyan
        'SUCCESS': '\x1b[32m', // Green
        'ERROR': '\x1b[31m',   // Red
        'WARN': '\x1b[33m',    // Yellow
        'DATA': '\x1b[35m'     // Magenta
    };
    const color = levelColors[level] || '\x1b[0m';
    const reset = '\x1b[0m';
    console.log(`${color}[${timestamp}] [${level}] ${message}${reset}`);
}

function connectWebSocket() {
    log('🔌 连接到WebSocket服务器: http://localhost:8080/ws', 'INFO');

    try {
        // 创建SockJS连接
        const socket = new SockJS('http://localhost:8080/ws');
        stompClient = Stomp.over(socket);

        // 启用调试（可选）
        stompClient.debug = (str) => {
            // log(`STOMP: ${str}`, 'INFO');
        };

        // 连接到STOMP服务器
        stompClient.connect({},
            (frame) => {
                log('✅ WebSocket连接成功!', 'SUCCESS');
                log(`📋 连接信息: ${JSON.stringify({
                    command: frame.command,
                    headers: frame.headers
                })}`, 'DATA');

                stats.connected = true;
                startTime = Date.now();

                // 订阅K线推送
                subscribeToKline('BTCUSDT', '1m');

                // 启动统计更新
                startStatsUpdater();
            },
            (error) => {
                log(`❌ WebSocket连接失败: ${error}`, 'ERROR');
                stats.connected = false;
                process.exit(1);
            }
        );

    } catch (error) {
        log(`❌ 创建WebSocket连接异常: ${error.message}`, 'ERROR');
        process.exit(1);
    }
}

function subscribeToKline(symbol, timeframe) {
    if (!stompClient || !stats.connected) {
        log('❌ WebSocket未连接，无法订阅', 'ERROR');
        return;
    }

    const sessionId = `test_nodejs_${Date.now()}`;
    log(`🔔 订阅K线推送: ${symbol} ${timeframe}`, 'INFO');

    // 订阅实时更新
    const updateTopic = `/topic/kline/${symbol}/${timeframe}`;
    log(`📡 监听主题: ${updateTopic}`, 'INFO');

    stompClient.subscribe(updateTopic, (message) => {
        messageCount++;
        stats.messagesReceived++;
        lastMessageTime = new Date();
        stats.lastMessageTime = lastMessageTime;
        stats.messageTimestamps.push(Date.now());

        log(`📈 收到K线推送 #${messageCount}`, 'SUCCESS');

        try {
            const klineData = JSON.parse(message.body);
            const timeStr = new Date(klineData.timestamp * 1000).toLocaleString();

            log(`📊 K线数据详情:`, 'DATA');
            log(`   Symbol: ${klineData.symbol}`, 'DATA');
            log(`   Timeframe: ${klineData.timeframe}`, 'DATA');
            log(`   Time: ${timeStr}`, 'DATA');
            log(`   OHLC: [O:${klineData.open}, H:${klineData.high}, L:${klineData.low}, C:${klineData.close}]`, 'DATA');
            log(`   Volume: ${klineData.volume}`, 'DATA');
            log(`   Amount: ${klineData.amount}`, 'DATA');
            log(`   Trades: ${klineData.tradeCount}`, 'DATA');
            log('-'.repeat(60), 'INFO');

        } catch (error) {
            log(`❌ 解析K线数据失败: ${error.message}`, 'ERROR');
            log(`🔍 原始消息: ${message.body}`, 'DATA');
        }
    });

    // 订阅初始数据
    const initialTopic = `/topic/kline/${symbol}/${timeframe}/initial`;
    stompClient.subscribe(initialTopic, (message) => {
        log('📊 收到初始K线数据', 'SUCCESS');
        try {
            const klines = JSON.parse(message.body);
            log(`📈 初始数据包含 ${klines.length} 条K线记录`, 'DATA');
            if (klines.length > 0) {
                const latest = klines[klines.length - 1];
                const timeStr = new Date(latest.timestamp * 1000).toLocaleString();
                log(`🕒 最新K线时间: ${timeStr}`, 'DATA');
            }
        } catch (error) {
            log(`❌ 解析初始K线数据失败: ${error.message}`, 'ERROR');
        }
    });

    // 发送订阅请求
    const subscriptionData = {
        symbol: symbol,
        timeframe: timeframe,
        sessionId: sessionId
    };

    log(`📤 发送订阅请求: ${JSON.stringify(subscriptionData)}`, 'INFO');
    stompClient.send('/app/kline/subscribe', {}, JSON.stringify(subscriptionData));

    log('✅ K线订阅请求已发送，等待推送数据...', 'SUCCESS');
}

function startStatsUpdater() {
    setInterval(() => {
        if (stats.connected) {
            stats.connectionDuration = Math.floor((Date.now() - startTime) / 1000);

            // 计算每分钟消息数
            const now = Date.now();
            const oneMinuteAgo = now - 60000;
            const recentMessages = stats.messageTimestamps.filter(ts => ts > oneMinuteAgo);
            stats.messagesPerMinute = recentMessages.length;

            // 清理旧时间戳（保留最近5分钟）
            stats.messageTimestamps = stats.messageTimestamps.filter(ts => ts > now - 300000);

            // 输出统计信息
            console.log('\n📊 连接统计:');
            console.log(`   连接状态: ${stats.connected ? '✅ 已连接' : '❌ 未连接'}`);
            console.log(`   连接时长: ${stats.connectionDuration}秒`);
            console.log(`   接收消息: ${stats.messagesReceived}条`);
            console.log(`   推送频率: ${stats.messagesPerMinute}条/分钟`);
            if (stats.lastMessageTime) {
                console.log(`   最后消息: ${stats.lastMessageTime.toLocaleTimeString()}`);
            }
            console.log('='.repeat(60));
        }
    }, 10000); // 每10秒更新一次统计
}

// 优雅退出处理
process.on('SIGINT', () => {
    log('\n🛑 接收到退出信号，正在关闭连接...', 'WARN');
    if (stompClient && stats.connected) {
        stompClient.disconnect();
    }
    log(`📊 测试结束，共接收 ${stats.messagesReceived} 条K线推送`, 'INFO');
    process.exit(0);
});

// 启动测试
log('📋 测试配置:', 'INFO');
log('   服务器: http://localhost:8080', 'INFO');
log('   测试交易对: BTCUSDT', 'INFO');
log('   测试时间框架: 1m', 'INFO');
log('   按 Ctrl+C 退出测试', 'INFO');
log('='.repeat(60), 'INFO');

connectWebSocket();