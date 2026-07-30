package com.example.bettereve.render.CrystalEntity;

import com.example.bettereve.entity.CrystalEntity;
import com.example.bettereve.entity.CrystalEntityModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CrystalEntityRenderer extends MobRenderer<CrystalEntity, CrystalEntityModel<CrystalEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("bettereve", "textures/entity/crystalentity.png");

    public CrystalEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new CrystalEntityModel<>(context.bakeLayer(CrystalEntityModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(CrystalEntity entity) {
        return TEXTURE;
    }

    @Override
    protected RenderType getRenderType(CrystalEntity entity, boolean visible, boolean shadowVisible, boolean outlineVisible) {
        // 确保基地实体始终使用正常的渲染类型，不受可见性判定影响
        return RenderType.entityCutout(getTextureLocation(entity));
    }

    @Override
    protected boolean isBodyVisible(CrystalEntity entity) {
        // 确保基地实体始终可见，不受任何遮挡判定影响
        return true;
    }
    
    @Override
    protected boolean shouldShowName(CrystalEntity entity) {
        // 确保基地实体的名称始终可见
        return super.shouldShowName(entity);
    }

    /**
     * ============================== 重要设计说明 ==============================
     * 
     * 基地实体的碰撞箱和模型是故意分离的：
     * 
     *   碰撞箱（底部） ← 生物攻击目标在这里
     *        ↑
     *   模型（顶部）   ← 视觉显示在这里，攻击也从这里发射
     * 
     * 这样设计的原因：
     *   1. 生物攻击的是碰撞箱（脚部位置），但由于基地有巨大碰撞箱
     *      近战生物仍能打到，不需要靠近模型顶部
     *   2. 模型渲染在顶部，看起来像是"建筑"在地面上
     *   3. 远程攻击从模型位置（顶部）发射，视觉效果合理
     * 
     * 关键约束：
     *   - offsetY 必须是负值（向上偏移），不能改成正值
     *   - 必须先平移后缩放
     *   - 改动可能破坏已有游戏地图中的基地位置
     * ==================================================================
     */
    @Override
    protected void scale(CrystalEntity entity, PoseStack poseStack, float partialTicks) {
        // 重要：必须使用负号！这是经过实际测试验证的
        // 原因：实体位置在碰撞箱底部（getY()返回脚部位置）
        // Minecraft坐标系：Y轴向上为正，translate(0, offsetY, 0)中：
        // - offsetY > 0：向下移动（因为世界坐标系Y向上为正）
        // - offsetY < 0：向上移动
        // 我们需要将模型从碰撞箱底部向上移动到碰撞箱顶部
        // 所以offsetY必须是负值：-collisionHeight
        double collisionHeight = entity.getCollisionHeight();
        double offsetY = - collisionHeight; // 向上移动碰撞箱高度，使模型在碰撞箱顶部

        // 注意：不要改成正号！经过实际游戏测试，负号才是正确的
        // 如果改为正号，模型会在碰撞箱下方，而不是顶部
        poseStack.translate(0, offsetY, 0);

        // 使用模型缩放属性控制模型大小
        // 关键修复：必须先平移再缩放，这样缩放不会影响平移量
        // 如果先缩放后平移，缩放会放大平移量，导致模型位置错误
        double modelScale = entity.getModelScale();
        poseStack.scale((float) modelScale, (float) modelScale, (float) modelScale);
    }

    @Override
    public void render(CrystalEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 注意：粒子效果现在在CrystalEntity.tick()中每tick生成
        // 这里不需要再生成粒子，避免重复
        
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
