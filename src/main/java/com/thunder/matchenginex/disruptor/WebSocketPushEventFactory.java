package com.thunder.matchenginex.disruptor;

import com.lmax.disruptor.EventFactory;

/**
 * WebSocket推送事件工厂
 * WebSocket Push Event Factory for Disruptor
 */
public class WebSocketPushEventFactory implements EventFactory<WebSocketPushEvent> {

    @Override
    public WebSocketPushEvent newInstance() {
        return new WebSocketPushEvent();
    }
}