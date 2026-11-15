package com.sanzineo.bettereve.capture;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 捕获版 {@link MultiBufferSource}：按 {@link RenderType} 各挂一个 {@link CaptureConsumer}。
 * <p>
 * 按计划 §02 §1.3 的<b>禁区</b>：<b>不实现</b> Sodium 的 {@code VertexBufferWriter}、
 * <b>不继承</b> {@link MultiBufferSource.BufferSource} —— 否则会被 Embeddium/Oculus 的
 * fast-path 取消掉 vanilla 顶点路径，导致空捕获。
 */
@OnlyIn(Dist.CLIENT)
public final class CaptureBuffer implements MultiBufferSource {

    /** 保持插入顺序 —— 逐帧一致（计划 §02 §7）。 */
    private final Map<RenderType, CaptureConsumer> buffers = new LinkedHashMap<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return buffers.computeIfAbsent(type, t -> new CaptureConsumer());
    }

    public Map<RenderType, CaptureConsumer> buffers() {
        return buffers;
    }
}
