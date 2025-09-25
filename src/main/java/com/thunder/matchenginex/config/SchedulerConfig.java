package com.thunder.matchenginex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10); // Increased pool size to handle concurrent WebSocket pushes - 增加线程池大小以处理并发WebSocket推送
        scheduler.setThreadNamePrefix("WebSocket-Scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()); // Handle overflow gracefully
        return scheduler;
    }

    /**
     * Dedicated thread pool for WebSocket message pushing to avoid blocking the matching engine
     * 专用于WebSocket消息推送的线程池，避免阻塞撮合引擎
     */
    @Bean("webSocketPushExecutor")
    public ThreadPoolTaskExecutor webSocketPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("WebSocket-Push-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}