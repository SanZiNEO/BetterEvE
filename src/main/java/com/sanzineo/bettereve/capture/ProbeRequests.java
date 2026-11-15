package com.sanzineo.bettereve.capture;

import net.minecraft.resources.ResourceLocation;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 两端共享的探针请求队列。
 * <p>
 * 集成服里命令跑在 <b>Server thread</b>、渲染跑在 <b>Render thread</b>（计划 §01 §1.4），
 * 所以用 {@link ConcurrentLinkedQueue} 而不是普通集合。
 */
public final class ProbeRequests {

    private static final Queue<ResourceLocation> PENDING = new ConcurrentLinkedQueue<>();

    private ProbeRequests() {
    }

    /** 服务端侧：排一个探针请求。 */
    public static void request(ResourceLocation id) {
        PENDING.add(id);
    }

    /** 客户端侧：取一个待办（没有则 null）。 */
    public static ResourceLocation poll() {
        return PENDING.poll();
    }

    public static int pendingCount() {
        return PENDING.size();
    }
}
