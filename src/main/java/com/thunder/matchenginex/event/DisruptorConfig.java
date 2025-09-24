package com.thunder.matchenginex.event;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class DisruptorConfig {

    private final OrderEventHandler orderEventHandler;

    @Bean
    public Disruptor<OrderEvent> disruptor() {
        int ringBufferSize = 1024 * 16; // Must be power of 2

        Disruptor<OrderEvent> disruptor = new Disruptor<>(
                new OrderEventFactory(),
                ringBufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new BlockingWaitStrategy()
        );

        disruptor.handleEventsWith(orderEventHandler);
        disruptor.start();

        return disruptor;
    }

    @Bean
    public RingBuffer<OrderEvent> ringBuffer(Disruptor<OrderEvent> disruptor) {
        return disruptor.getRingBuffer();
    }

    @Bean
    public OrderEventProducer orderEventProducer(RingBuffer<OrderEvent> ringBuffer) {
        return new OrderEventProducer(ringBuffer);
    }
}