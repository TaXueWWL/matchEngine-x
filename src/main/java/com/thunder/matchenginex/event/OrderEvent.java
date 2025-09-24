package com.thunder.matchenginex.event;

import com.thunder.matchenginex.model.Command;
import com.thunder.matchenginex.model.Order;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderEvent {
    private Command command;
    private Order order;
    private long sequence;

    public void clear() {
        this.command = null;
        this.order = null;
        this.sequence = 0;
    }

    public void copyFrom(Command command) {
        this.command = command;
        this.sequence = command.getSequence();
    }
}