package com.example.bettereve.phase;

import net.minecraftforge.eventbus.api.Event;

/**
 * 阶段播报事件 — 当阶段切换时需要广播消息时触发。
 */
public class PhaseBroadcastEvent extends Event {
    private final String message;

    public PhaseBroadcastEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
