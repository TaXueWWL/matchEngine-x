package com.thunder.matchenginex;

import com.thunder.matchenginex.enums.OrderSide;
import com.thunder.matchenginex.enums.OrderType;
import com.thunder.matchenginex.service.AccountService;
import com.thunder.matchenginex.service.TradingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;

/**
 * 手动测试类，用于验证余额冻结功能
 * 使用 --spring.profiles.active=manual-test 运行
 */
@SpringBootApplication
@Profile("manual-test")
public class ManualBalanceTest implements CommandLineRunner {

    @Autowired
    private TradingService tradingService;

    @Autowired
    private AccountService accountService;

    public static void main(String[] args) {
        // 设置 profile 为 manual-test
        System.setProperty("spring.profiles.active", "manual-test");
        SpringApplication.run(ManualBalanceTest.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== 开始余额冻结功能测试 ===");

        long userId = 999L; // 使用特殊用户ID进行测试
        String symbol = "BTCUSDT";

        // 1. 添加初始余额
        System.out.println("1. 添加初始余额 1000 USDT");
        accountService.addBalance(userId, "USDT", new BigDecimal("1000"));
        printBalanceStatus(userId, "USDT");

        // 2. 第一笔订单：500 USDT
        System.out.println("\n2. 下第一笔买单：价格100，数量5 (总计500 USDT)");
        try {
            long orderId1 = tradingService.placeOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100"), new BigDecimal("5"));
            System.out.println("✅ 订单1成功提交，订单ID: " + orderId1);
            printBalanceStatus(userId, "USDT");
        } catch (Exception e) {
            System.out.println("❌ 订单1失败: " + e.getMessage());
        }

        // 3. 第二笔订单：尝试600 USDT（应该失败）
        System.out.println("\n3. 下第二笔买单：价格100，数量6 (总计600 USDT) - 应该失败");
        try {
            long orderId2 = tradingService.placeOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100"), new BigDecimal("6"));
            System.out.println("❌ 意外：订单2竟然成功了，订单ID: " + orderId2);
            printBalanceStatus(userId, "USDT");
        } catch (Exception e) {
            System.out.println("✅ 预期结果：订单2被拒绝 - " + e.getMessage());
            printBalanceStatus(userId, "USDT");
        }

        // 4. 第三笔订单：500 USDT（应该成功，用完所有余额）
        System.out.println("\n4. 下第三笔买单：价格100，数量5 (总计500 USDT) - 应该成功");
        try {
            long orderId3 = tradingService.placeOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("100"), new BigDecimal("5"));
            System.out.println("✅ 订单3成功提交，订单ID: " + orderId3);
            printBalanceStatus(userId, "USDT");
        } catch (Exception e) {
            System.out.println("❌ 订单3失败: " + e.getMessage());
            printBalanceStatus(userId, "USDT");
        }

        // 5. 第四笔订单：任何金额都应该失败
        System.out.println("\n5. 下第四笔买单：价格1，数量1 (总计1 USDT) - 应该失败");
        try {
            long orderId4 = tradingService.placeOrder(symbol, userId, OrderSide.BUY, OrderType.LIMIT,
                new BigDecimal("1"), new BigDecimal("1"));
            System.out.println("❌ 意外：订单4竟然成功了，订单ID: " + orderId4);
            printBalanceStatus(userId, "USDT");
        } catch (Exception e) {
            System.out.println("✅ 预期结果：订单4被拒绝 - " + e.getMessage());
            printBalanceStatus(userId, "USDT");
        }

        // 6. 测试卖单
        System.out.println("\n=== 开始卖单测试 ===");
        System.out.println("6. 添加BTC余额并测试卖单");
        accountService.addBalance(userId, "BTC", new BigDecimal("10"));
        printBalanceStatus(userId, "BTC");

        // 第一笔卖单：5 BTC
        System.out.println("\n7. 下第一笔卖单：5 BTC");
        try {
            long sellOrderId1 = tradingService.placeOrder(symbol, userId, OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("5"));
            System.out.println("✅ 卖单1成功提交，订单ID: " + sellOrderId1);
            printBalanceStatus(userId, "BTC");
        } catch (Exception e) {
            System.out.println("❌ 卖单1失败: " + e.getMessage());
        }

        // 第二笔卖单：6 BTC（应该失败）
        System.out.println("\n8. 下第二笔卖单：6 BTC - 应该失败");
        try {
            long sellOrderId2 = tradingService.placeOrder(symbol, userId, OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("6"));
            System.out.println("❌ 意外：卖单2竟然成功了，订单ID: " + sellOrderId2);
            printBalanceStatus(userId, "BTC");
        } catch (Exception e) {
            System.out.println("✅ 预期结果：卖单2被拒绝 - " + e.getMessage());
            printBalanceStatus(userId, "BTC");
        }

        System.out.println("\n=== 测试完成 ===");

        // 打印最终状态
        System.out.println("\n=== 最终余额状态 ===");
        printBalanceStatus(userId, "USDT");
        printBalanceStatus(userId, "BTC");

        // 等待一会儿再退出，以便查看日志
        Thread.sleep(2000);
        System.exit(0);
    }

    private void printBalanceStatus(long userId, String currency) {
        BigDecimal available = accountService.getAvailableBalance(userId, currency);
        BigDecimal total = accountService.getTotalBalance(userId, currency);
        BigDecimal frozen = total.subtract(available);

        System.out.println(String.format("💰 %s余额状态 - 可用: %s, 冻结: %s, 总计: %s",
            currency, available, frozen, total));
    }
}