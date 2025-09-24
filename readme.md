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

# todo
- 修复bug，订单簿展示
- k线展示
- 数据结构优化 agrona


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

关键特性：

1. 完整的订单生命周期管理: 下单 → 查看 → 修改 → 撤销
2. 实时数据更新: 所有操作后自动刷新相关数据
3. 用户友好的界面:
   - 状态徽章显示订单状态
   - 颜色区分买卖方向
   - 操作按钮仅对可操作订单显示
4. 错误处理: 完整的错误提示和异常处理
5. 数据一致性: 操作后同时刷新订单列表、余额等相关数据

现在交易页面具备了完整的订单管理功能，用户可以：
- 实时查看当前所有未完成订单
- 方便地撤销不需要的订单
- 快速修改订单价格和数量
- 看到订单簿的实时变化