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
    public RenderType getRenderType(BaseEntity entity, boolean visible, boolean shadowVisible, boolean outlineVisible) {
        return RenderType.entityCutout(getTextureLocation(entity));
    }

    @Override
    protected void scale(BaseEntity entity, PoseStack poseStack, float partialTicks) {
        // 计算缩放比例，使模型与碰撞箱大小匹配
        double collisionSize = entity.getCollisionSize();
        double modelSize = 6.0; // 新模型的默认大小
        double scale = collisionSize / modelSize;
        poseStack.scale((float) scale, (float) scale, (float) scale);
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
