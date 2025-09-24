package com.thunder.matchenginex.event;

import com.lmax.disruptor.RingBuffer;
import com.thunder.matchenginex.model.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class OrderEventProducer {

    private final RingBuffer<OrderEvent> ringBuffer;

    public void publishCommand(Command command) {
        long sequence = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(sequence);
            event.copyFrom(command);
            command.setSequence(sequence);
            log.debug("Published command: {} with sequence: {}", command.getCommandType(), sequence);
        } catch (Exception e) {
            log.error("Error publishing command: {}", e.getMessage(), e);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    public boolean tryPublishCommand(Command command) {
        try {
            long sequence = ringBuffer.tryNext();
            try {
                OrderEvent event = ringBuffer.get(sequence);
                event.copyFrom(command);
                command.setSequence(sequence);
                return true;
            } finally {
                ringBuffer.publish(sequence);
            }
        } catch (Exception e) {
            log.error("Error trying to publish command: {}", e.getMessage(), e);
        }
        return false;
    }
}