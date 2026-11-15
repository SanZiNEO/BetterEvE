package com.sanzineo.bettereve.capture;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 捕获用 {@link VertexConsumer}：把顶点逐条记下来（pos + uv + color）。
 * <p>
 * 只记数据，不碰 GPU —— 所以可以在渲染循环之外被调用。
 * 每个顶点 9 个 float：{@code x,y,z,u,v,r,g,b,a}。
 */
@OnlyIn(Dist.CLIENT)
public final class CaptureConsumer implements VertexConsumer {

    /** 已提交的顶点（endVertex 之后）。 */
    public final List<float[]> vertices = new ArrayList<>();

    private float x, y, z, u, v;
    private int r = 255, g = 255, b = 255, a = 255;
    private boolean colorSet;
    private int defR = 255, defG = 255, defB = 255, defA = 255;

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        this.colorSet = true;
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this;
    }

    @Override
    public void endVertex() {
        if (!colorSet) {
            r = defR;
            g = defG;
            b = defB;
            a = defA;
        }
        vertices.add(new float[]{x, y, z, u, v, r, g, b, a});
        colorSet = false;
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        defR = r;
        defG = g;
        defB = b;
        defA = a;
    }

    @Override
    public void unsetDefaultColor() {
        defR = 255;
        defG = 255;
        defB = 255;
        defA = 255;
    }
}
