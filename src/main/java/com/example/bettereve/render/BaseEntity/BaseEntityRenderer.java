package com.example.bettereve.render.BaseEntity;

import com.example.bettereve.entity.BaseEntity;
import com.example.bettereve.entity.BaseEntityModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class BaseEntityRenderer extends MobRenderer<BaseEntity, BaseEntityModel<BaseEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("bettereve", "textures/entity/baseentity.png");

    public BaseEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new BaseEntityModel<>(context.bakeLayer(BaseEntityModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(BaseEntity entity) {
        return TEXTURE;
    }

    @Override
    protected RenderType getRenderType(BaseEntity entity, boolean visible, boolean shadowVisible, boolean outlineVisible) {
        // 确保基地实体始终使用正常的渲染类型，不受可见性判定影响
        return RenderType.entityCutout(getTextureLocation(entity));
    }

    @Override
    protected boolean isBodyVisible(BaseEntity entity) {
        // 确保基地实体始终可见，不受任何遮挡判定影响
        return true;
    }
    
    @Override
    protected boolean shouldShowName(BaseEntity entity) {
        // 确保基地实体的名称始终可见
        return super.shouldShowName(entity);
    }

    @Override
    protected void scale(BaseEntity entity, PoseStack poseStack, float partialTicks) {
        // 使用模型缩放属性控制模型大小
        double modelScale = entity.getModelScale();
        poseStack.scale((float) modelScale, (float) modelScale, (float) modelScale);

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
    }

    @Override
    public void render(BaseEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 渲染攻击粒子效果
        if (entity.level().isClientSide && entity.hasActiveAttackTarget()) {
            renderAttackParticles(entity, partialTicks);
        }
        
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
    
    // 渲染攻击粒子效果
    private void renderAttackParticles(BaseEntity entity, float partialTicks) {
        // 获取攻击目标
        if (entity.getActiveAttackTarget() == null) {
            return;
        }
        
        // 计算攻击动画进度
        float attackProgress = entity.getAttackAnimationScale(partialTicks);
        double d5 = (double) attackProgress;
        
        // 计算攻击目标的位置
        double d0 = entity.getActiveAttackTarget().getX() - entity.getX();
        double d1 = entity.getActiveAttackTarget().getY(0.5D) - entity.getEyeY();
        double d2 = entity.getActiveAttackTarget().getZ() - entity.getZ();
        double d3 = Mth.sqrt((float)(d0 * d0 + d1 * d1 + d2 * d2));
        d0 /= d3;
        d1 /= d3;
        d2 /= d3;
        
        // 生成粒子效果
        double d4 = entity.level().random.nextDouble();
        while(d4 < d3) {
            d4 += 1.8D - d5 + entity.level().random.nextDouble() * (1.7D - d5);
            entity.level().addParticle(ParticleTypes.DRAGON_BREATH, 
                    entity.getX() + d0 * d4, 
                    entity.getEyeY() + d1 * d4, 
                    entity.getZ() + d2 * d4, 0.0D, 0.0D, 0.0D);
        }
    }
}
