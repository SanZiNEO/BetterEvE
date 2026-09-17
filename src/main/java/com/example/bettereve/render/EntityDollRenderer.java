package com.example.bettereve.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 纸娃娃渲染 —— 把生物模型画进格斗血条的圆形端头。
 * <p>
 * 直接复用原版背包的实体渲染器
 * （{@link InventoryScreen#renderEntityInInventoryFollowsAngle}）：它内部会保存并还原
 * 实体的旋转状态，因此不会污染世界里的实体。Forge 在该方法上加了直接传角度的重载，
 * 无需伪造鼠标坐标。
 * <p>
 * 注意：渲染的是<b>活实体本身</b>，所以动画跟随其真实状态（走路/攻击都会动）。
 */
@OnlyIn(Dist.CLIENT)
public final class EntityDollRenderer {

    /** 纸娃娃在圆内的目标像素尺寸（圆直径 60，留出余量避免溢出圆形） */
    private static final int FIT_PX = 40;
    /** 观察角度：左右偏转（0 = 正对镜头，稍偏一点更有立体感） */
    private static final float ANGLE_X = 0.2f;
    /** 观察角度：上下俯仰 */
    private static final float ANGLE_Y = 0.0f;

    private EntityDollRenderer() {
    }

    /**
     * 在圆形区域内渲染生物纸娃娃。
     * <p>
     * 调用方需保证当前 pose 未做翻转（负缩放会反转三角形绕序导致被背面剔除）。
     *
     * @param graphics GuiGraphics
     * @param cx       圆心 X（屏幕坐标）
     * @param cy       圆心 Y（屏幕坐标）
     * @param entity   要渲染的生物
     */
    public static void renderInCircle(GuiGraphics graphics, int cx, int cy, LivingEntity entity) {
        if (entity == null || Minecraft.getInstance().level == null) {
            return;
        }

        // 按包围盒自适应缩放：原版 renderEntityInInventory 的 scale 单位是"像素/格"
        float bbH = Math.max(0.1f, entity.getBbHeight());
        float bbW = Math.max(0.1f, entity.getBbWidth());
        int scale = Math.max(1, Math.round(FIT_PX / Math.max(bbH, bbW)));

        // 该 API 的 y 是"脚底"位置，需上移半个身高让生物在圆内垂直居中
        int feetY = cy + Math.round(bbH * scale / 2.0f);

        InventoryScreen.renderEntityInInventoryFollowsAngle(
                graphics, cx, feetY, scale, ANGLE_X, ANGLE_Y, entity);
    }
}
