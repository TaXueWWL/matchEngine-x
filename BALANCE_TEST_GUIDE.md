# 余额冻结功能测试指南

本指南说明如何验证已修复的余额冻结bug，确保用户不能下超过可用余额的订单。

## Bug描述
**修复前的问题：** 用户余额为1000，下了价值500的订单后，仍可以继续下价值1000的订单，因为系统没有立即冻结资金。

**修复后的行为：** 下订单时立即冻结对应资金，确保 `新订单金额 + 已冻结金额 ≤ 总余额`

## 测试方法

### 方法1: 使用JUnit测试（推荐）

```bash
# 运行专门的余额测试
mvn test -Dtest=TradingServiceBalanceTest

# 查看详细输出
mvn test -Dtest=TradingServiceBalanceTest -Dspring.profiles.active=test
```

### 方法2: 手动测试程序

```bash
# 启动专门的测试程序
mvn spring-boot:run -Dspring-boot.run.profiles=manual-test -Dspring-boot.run.main-class=com.thunder.matchenginex.ManualBalanceTest
```

### 方法3: HTTP API测试

1. 启动应用程序：
```bash
mvn spring-boot:run
```

2. 使用HTTP客户端（如Postman、IntelliJ HTTP Client等）执行 `test-balance-freeze.http` 中的请求

## 预期测试结果

### 买单测试场景
```
初始状态：1000 USDT
├─ 订单1：100*5=500 USDT ✅ 成功
│  └─ 余额状态：可用500，冻结500，总计1000
├─ 订单2：100*6=600 USDT ❌ 失败（余额不足）
│  └─ 余额状态：可用500，冻结500，总计1000（无变化）
├─ 订单3：100*5=500 USDT ✅ 成功
│  └─ 余额状态：可用0，冻结1000，总计1000
└─ 订单4：1*1=1 USDT ❌ 失败（余额不足）
   └─ 余额状态：可用0，冻结1000，总计1000（无变化）
```

### 卖单测试场景
```
初始状态：10 BTC
├─ 订单1：卖出5 BTC ✅ 成功
│  └─ 余额状态：可用5，冻结5，总计10
├─ 订单2：卖出6 BTC ❌ 失败（余额不足）
│  └─ 余额状态：可用5，冻结5，总计10（无变化）
├─ 订单3：卖出5 BTC ✅ 成功
│  └─ 余额状态：可用0，冻结10，总计10
└─ 订单4：卖出1 BTC ❌ 失败（余额不足）
   └─ 余额状态：可用0，冻结10，总计10（无变化）
```

## 关键验证点

1. **立即冻结**：下单时立即冻结资金，而不是等订单处理完成
2. **准确计算**：可用余额 = 总余额 - 冻结余额
3. **防止超卖**：无法下超过可用余额的订单
4. **买卖分离**：买单冻结报价货币（如USDT），卖单冻结基础货币（如BTC）
5. **异常处理**：订单提交失败时正确解冻资金

## 日志关键词

查看应用日志，关注以下关键信息：
- `Frozen balance for buy order` - 买单资金冻结
- `Frozen balance for sell order` - 卖单资金冻结
- `Insufficient balance` - 余额不足错误
- `Failed to freeze balance` - 冻结资金失败
- `Unfrozen balance` - 资金解冻（失败回滚时）

## 修复的核心代码

主要修改在 `TradingService.java` 中：

1. **placeOrder方法**：下单前调用 `freezeOrderFunds()` 立即冻结资金
2. **freezeOrderFunds方法**：根据买卖方向冻结对应货币
3. **unfreezeOrderFunds方法**：订单提交失败时解冻资金
4. **tryPlaceOrder方法**：支持失败回滚的下单方式

这确保了订单资金占用的原子性和一致性。