package com.thunder.matchenginex.event;

import com.lmax.disruptor.EventHandler;
import com.thunder.matchenginex.engine.MatchingEngine;
import com.thunder.matchenginex.enums.CommandType;
import com.thunder.matchenginex.model.Command;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderEventHandler implements EventHandler<OrderEvent> {

    @Autowired
    @Lazy
    private MatchingEngine matchingEngine;

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) throws Exception {
        try {
            Command command = event.getCommand();
            if (command == null) {
                log.warn("Received null command in event with sequence {}", sequence);
                return;
            }

            log.debug("Processing command: {} for order: {}", command.getCommandType(), command.getOrderId());

            switch (command.getCommandType()) {
                case PLACE_ORDER:
                    matchingEngine.placeOrder(command);
                    break;
                case CANCEL_ORDER:
                    matchingEngine.cancelOrder(command);
                    break;
                case MODIFY_ORDER:
                    matchingEngine.modifyOrder(command);
                    break;
                case QUERY_ORDER:
                    matchingEngine.queryOrder(command);
                    break;
                case QUERY_ORDERBOOK:
                    matchingEngine.queryOrderBook(command);
                    break;
                default:
                    log.warn("Unknown command type: {}", command.getCommandType());
            }

        } catch (Exception e) {
            log.error("Error processing event with sequence {}: {}", sequence, e.getMessage(), e);
        } finally {
            event.clear();
        }
    }
}