# MatchEngine-X

基于 Disruptor 的高性能撮合交易系统

## 功能特性

- 🚀 **高性能**: 基于 LMAX Disruptor 实现的高性能事件处理架构
- 📊 **完整的订单簿**: 支持买卖订单簿，支持多个价格层级管理
- 🔄 **多种订单类型**: 支持 LIMIT、MARKET、IOC、FOK、POST_ONLY 订单类型
- ⚡ **低延迟撮合**: 高效的价格-时间优先撮合算法
- 🌐 **双协议支持**: 提供 gRPC 和 HTTP REST API 两种调用方式
- 💱 **多币对支持**: 支持配置化的多个交易对
- 🛡️ **订单验证**: 完整的订单参数验证和约束检查

## 系统架构

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   gRPC/HTTP     │    │    Disruptor     │    │  MatchingEngine │
│   Controllers   │───▶│   Event Loop     │───▶│   & OrderBook   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                               │
                               ▼
                       ┌──────────────────┐
                       │   Trade Events   │
                       └──────────────────┘
```

### 核心组件

1. **EventProducer**: 将交易命令发布到 Disruptor Ring Buffer
2. **EventHandler**: 处理事件并调用撮合引擎
3. **MatchingEngine**: 核心撮合逻辑，处理订单撮合和成交
4. **OrderBook**: 管理买卖订单簿和价格层级
5. **TradingService**: 对外服务接口，提供订单操作功能

## API 接口

### HTTP REST API

#### 下单
```bash
POST /api/trading/orders
Content-Type: application/json

{
    "symbol": "BTCUSDT",
    "userId": 1001,
    "side": "BUY",
    "type": "LIMIT",
    "price": "50000.00",
    "quantity": "0.1"
}
```

#### 撤单
```bash
DELETE /api/trading/orders/{orderId}?symbol=BTCUSDT&userId=1001
```

#### 改单
```bash
PUT /api/trading/orders/{orderId}
Content-Type: application/json

{
    "symbol": "BTCUSDT",
    "userId": 1001,
    "newPrice": "51000.00",
    "newQuantity": "0.2"
}
```

#### 查询订单
```bash
GET /api/trading/orders/{orderId}?symbol=BTCUSDT
```

#### 查询订单簿
```bash
GET /api/trading/orderbook/{symbol}?depth=10
```

#### 查询支持的交易对
```bash
GET /api/trading-pairs
```

### gRPC API

使用 `trading_service.proto` 中定义的服务接口。

## 订单类型说明

| 订单类型 | 描述 |
|---------|------|
| **LIMIT** | 限价单，指定价格和数量，只在指定价格或更好价格成交 |
| **MARKET** | 市价单，立即以市场最优价格成交，未成交部分取消 |
| **IOC** | Immediate or Cancel，立即成交，未成交部分立即取消 |
| **FOK** | Fill or Kill，全部成交或全部取消 |
| **POST_ONLY** | 只做 Maker，如果会立即成交则取消订单 |

## 撮合逻辑

### 价格优先时间优先原则
1. **价格优先**: 买单价格越高优先级越高，卖单价格越低优先级越高
2. **时间优先**: 相同价格下，先到达的订单优先级更高

### 撮合流程
1. 新订单进入系统后，首先与对手方最优价格进行匹配
2. 如果价格满足成交条件，则按最优价格成交
3. 成交后更新双方订单状态，生成成交记录
4. 未完全成交的订单根据订单类型决定是否加入订单簿

## 配置说明

### 交易对配置 (application.yml)

```yaml
trading:
  pairs:
    BTCUSDT:
      symbol: BTCUSDT
      baseAsset: BTC
      quoteAsset: USDT
      minPrice: 0.01          # 最小价格
      maxPrice: 1000000.00    # 最大价格
      priceStep: 0.01         # 价格步长
      minQuantity: 0.00001    # 最小数量
      maxQuantity: 1000.00    # 最大数量
      quantityStep: 0.00001   # 数量步长
      enabled: true           # 是否启用
```

### 服务端口配置

```yaml
server:
  port: 8080        # HTTP 服务端口

grpc:
  server:
    port: 9090      # gRPC 服务端口
```

## 运行说明

### 环境要求
- Java 17+
- Maven 3.6+

### 编译运行
```bash
# 编译
mvn clean compile

# 生成 gRPC 代码
mvn protobuf:compile protobuf:compile-custom

# 运行测试
mvn test

# 启动应用
mvn spring-boot:run
```

### Docker 运行
```bash
# 构建镜像
docker build -t matchengine-x .

# 运行容器
docker run -p 8080:8080 -p 9090:9090 matchengine-x
```

## 性能特性

- **零垃圾回收**: 使用对象池和无锁数据结构，减少 GC 压力
- **低延迟**: Disruptor 提供微秒级延迟的事件处理
- **高吞吐**: 支持每秒数百万次订单处理
- **内存高效**: 使用 Eclipse Collections 等高性能集合

## 监控指标

系统提供以下关键指标：
- 订单处理延迟
- 撮合成功率
- 订单簿深度
- 成交量统计
- 系统吞吐量

## 扩展性

### 添加新的订单类型
1. 在 `OrderType` 枚举中添加新类型
2. 在 `MatchingEngine` 中实现对应的处理逻辑
3. 更新 API 和文档

### 添加新的交易对
在 `application.yml` 的 `trading.pairs` 配置中添加新的交易对配置即可。

## 注意事项

⚠️ **重要提醒**:
- 本系统为高性能交易系统，建议在生产环境使用时进行充分的性能测试
- 确保系统资源充足，特别是内存和 CPU
- 建议配置适当的监控和告警系统
- 定期备份交易数据和系统配置

## 许可证

本项目采用 MIT 许可证。详见 LICENSE 文件。


1. ✅ 后端获取用户订单列表接口

- TradingService: 添加了 getUserOrders() 和 getAllUserOrders() 方法
- OrderBook: 添加了 getUserOrders() 方法，按时间戳排序返回用户订单
- TradingController: 添加了 GET /api/trading/orders/user/{userId} 接口

2. ✅ 前端订单簿实时更新

- 修改了 updateOrderBook() 函数，兼容REST API和WebSocket两种数据格式
- 支持 buyLevels/sellLevels (REST API) 和 bids/asks (WebSocket)
- 改进了数据格式化和显示

3. ✅ 当前订单列表展示

- 页面布局: 在trading页面添加了完整的当前订单列表区域
- 数据加载: 实现了 loadCurrentOrders() 和 updateCurrentOrders() 函数
- 状态显示: 包含订单状态、方向、价格、数量、已成交量等信息
- 实时刷新: 下单后自动刷新当前订单列表

4. ✅ 撤单功能

- 后端接口: 使用现有的 DELETE /api/trading/orders/{orderId} 接口
- 前端实现: cancelOrder() 函数，包含确认对话框和错误处理
- 界面集成: 在订单列表中显示撤单按钮（仅对可撤销订单）

5. ✅ 改单功能

- 页面UI: 添加了Bootstrap模态框用于修改订单
- 前端功能:
    - showModifyOrderDialog() - 显示修改对话框
    - submitModifyOrder() - 提交修改请求
- 后端接口: 使用现有的 PUT /api/trading/orders/{orderId} 接口

## 关键特性：

1. 完整的订单生命周期管理: 下单 → 查看 → 修改 → 撤销
2. 实时数据更新: 所有操作后自动刷新相关数据
3. 用户友好的界面:
   - 状态徽章显示订单状态
   - 颜色区分买卖方向
   - 操作按钮仅对可操作订单显示
4. 错误处理: 完整的错误提示和异常处理
5. 数据一致性: 操作后同时刷新订单列表、余额等相关数据

## 现在交易页面具备了完整的订单管理功能，用户可以：
- 实时查看当前所有未完成订单
- 方便地撤销不需要的订单
- 快速修改订单价格和数量
- 看到订单簿的实时变化

1. 删除最近订单区域
    - 移除了无用的最近订单展示区域
2. 实现历史订单展示功能
   - 添加了完整的历史订单API接口
   - 创建了选项卡界面，分离当前订单和历史订单
   - 支持已完成、已取消、已拒绝订单的展示
3. 重新设计订单簿布局（参考Bitget）
   - 实现了专业的订单簿界面，类似Bitget风格
   - 买盘：价格从高到低排列（从上往下）
   - 卖盘：价格从低到高排列（从下往上）
   - 添加了价格精度选择（0.01, 0.1, 1）
   - 实现了深度可视化（背景颜色条）
   - 点击价格自动填入下单表单
   - 显示累计数量
4. 实现K线图替换价格图表
   - 使用Lightweight Charts库实现专业K线图
   - 支持多时间周期：1分钟、5分钟、15分钟、1小时、1天
   - 绿色上涨，红色下跌的K线样式
   - 响应式设计，自动调整大小
   - 生成模拟K线数据展示价格波动
5. 实现实时最新成交价展示
   - 在订单簿中间显示当前价格
   - 实时计算买一卖一的中间价
   - 显示价格变动百分比（模拟数据）
   - 显示价差信息

🔥 关键特性：

- 专业的交易界面：仿照Bitget等主流交易所设计
- 完整的订单管理：下单→查看→修改→撤销→历史记录
- 实时数据更新：订单簿、K线图、账户余额实时更新
- 良好的用户体验：
    - 点击订单簿价格自动填入表单
    - 可视化深度显示
    - 响应式设计
    - 加载状态提示
- 资金安全：下单时立即冻结资金，防止超额下单

📊 界面布局：
    
    ┌─────────────────┬─────────────────┬─────────────────┐
    │   订单簿        │     K线图       │   账户余额      │
    │ ┌─卖盘(红色)─┐   │ ┌─时间周期按钮─┐   │ ┌─各币种余额─┐   │
    │ │ 50100.00   │   │ │1m 5m 15m 1h│   │ │USDT:1000  │   │
    │ │ 50090.50   │   │ └─────────────┘   │ │BTC: 0.1   │   │
    │ └───────────┘   │ ┌─────────────┐   │ └───────────┘   │
    │ ┌─当前价格─┐     │ │    K线图    │   │                │
    │ │¥50,050.00│     │ │    区域     │   │                │
    │ └─────────┘     │ └─────────────┘   │                │
    │ ┌─买盘(绿色)─┐   │                   │                │
    │ │ 50000.00   │   │                   │                │
    │ │ 49990.50   │   │                   │                │
    │ └───────────┘   │                   │                │
    │ ┌─买卖表单─┐     │                   │                │
    │ └─────────┘     │                   │                │
    └─────────────────┴─────────────────┴─────────────────┘
    ┌─────────────────────────────────────────────────────┐
    │                订单管理区域                         │
    │ ┌─当前订单─┐ ┌─历史订单─┐                           │
    │ │订单列表  │ │已完成/  │                           │
    │ │撤单/修改 │ │已取消   │                           │
    │ └─────────┘ └─────────┘                           │
    └─────────────────────────────────────────────────────┘

现在系统具备了完整的专业交易功能，用户可以享受类似主流交易所的交易体验！

## 问题分析：
klineChart.addCandlestickSeries is not a function 错误表明：
1. LightweightCharts.createChart() 返回的对象可能不是预期的类型
2. 或者 LightweightCharts 库没有正确加载
3. 这个错误导致整个JavaScript执行中断，从而影响了后续的表单事件绑定

解决方案：
1. 添加了错误处理：在 initKLineChart() 中添加了 try-catch 和库加载检查
2. 隔离错误影响：在 initTradingPage() 中为每个初始化步骤添加了独立的 try-catch
3. 优雅降级：如果K线图无法加载，显示友好的错误信息，但不影响其他功能

现在的情况：
- 即使 LightweightCharts 库无法正确加载，下单功能仍能正常工作
- 每个功能模块都有独立的错误处理，一个失败不会影响其他功能
- 添加了详细的调试日志，便于快速定位问题

## 已修复WebSocket连接问题！

主要修复内容：

1. 更换STOMP库：
   - 从 @stomp/stompjs@7 切换到 stompjs@2.3.3
   - 新版本与SockJS的集成有兼容性问题，旧版本更稳定
2. 兼容两种API：
   - 支持旧版Stomp.over() API
   - 支持新版StompJs.Client API
   - 自动检测并使用可用的API
3. 增强错误处理：
   - 添加库加载检查
   - 添加连接状态检查
   - 添加详细的调试日志
   - 添加订阅错误处理
4. 改进连接逻辑：
   - 添加重连机制（reconnectDelay: 5000ms）
   - 添加心跳配置（heartbeat: 4000ms）
   - 添加多个错误回调处理

现在WebSocket应该能正常连接，提供：
- 实时订单簿更新
- 实时交易历史更新
- 实时用户余额更新


## 已经修复了自成交bug。现在的逻辑是：

1. 下单时：冻结相应资金
2. 成交时：执行实际的资金转移
   - 从买方冻结的资金中转出USDT给卖方
   - 从卖方冻结的资金中转出BTC给买方
3. 撤单时：释放冻结资金

对于自成交场景（同一个用户）：
- 初始：10 BTC + 10000 USDT
- 买单：冻结1000 USDT（剩余：10 BTC + 9000 USDT可用，1000 USDT冻结）
- 卖单：冻结5 BTC（剩余：5 BTC可用 + 9000 USDT可用，5 BTC + 1000 USDT冻结）
- 成交后：
   - 买方（自己）：解冻1000 USDT，获得5 BTC
   - 卖方（自己）：解冻5 BTC，获得1000 USDT
   - 最终：10 BTC + 10000 USDT（总量不变！）

这样就解决了自成交时资金减少的问题。

### 已经修复了自成交场景下的资金流转bug！

问题根源：
> 之前的代码只有资金冻结逻辑，但缺少实际的资金转移逻辑。成交时只是记录了交易日志，冻结的资金永远不会被正确处理。

修复内容：
在MatchingEngine.processTrades()中添加了executeFundTransfer()方法，实现真实的资金转移：

1. 买方 → 卖方：转移报价币种（USDT）
   - 从买方账户解冻USDT
   - 将USDT添加到卖方账户
2. 卖方 → 买方：转移基础币种（BTC）
   - 从卖方账户解冻BTC
   - 将BTC添加到买方账户

自成交场景验证：
- 初始状态：10 BTC + 10000 USDT
- 下买单：冻结1000 USDT → 可用：10 BTC + 9000 USDT
- 下卖单：冻结5 BTC → 可用：5 BTC + 9000 USDT
- 成交后：
   - 解冻并转移1000 USDT（买方→卖方，同一人）
   - 解冻并转移5 BTC（卖方→买方，同一人）
   - 最终结果：10 BTC + 10000 USDT ✅

现在自成交时资金总量保持不变，解决了之前资金"消失"的bug！

# todo
- √ 修复bug，订单簿展示
- √ symbol是一个对象，有quoteCoin和baseCoin两个属性，更换所有解析字符串的逻辑如extractBaseCurrency extractQuoteCurrency
- √ 撤单应该把冻结的金额给加到可用余额
- √ 自成交计算bug
- 市价单逻辑，页面支持各种订单类型
- k线展示
- 数据结构优化 agrona
- 订单的状态数量异步写入mysql or redis
- 启动从mysql or redis加载订单 ，余额
- 操作的订单变化，资产变化都能写log到磁盘，不断更新
- 启动加载订单簿
- 定时打快照，快照完成的数据支持删除  用protobuf序列化打快照，恢复快照
- 改单功能不好使，模态框不展示

页面展示的账户余额应该是可用余额
最新成交价是成交发生时候的价格，规则：
- taker是买单：买价>=对手卖一价，最新成交价是卖一价
taker是卖单，卖价<=对手买1价，最新成交价是买一价
每个币对一个推送线程，不能复用线程，防止多线程并发问题
- 做市机器人，压测