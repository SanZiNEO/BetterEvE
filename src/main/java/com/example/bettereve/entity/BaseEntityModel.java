package com.example.bettereve.entity;

// Made with Blockbench 5.0.7
// Exported for Minecraft version 1.19 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.model.EntityModel;

/**
 * 基地实体模型类
 * 负责渲染基地实体的三维模型和动画
 * 本模型使用自定义手动动画系统，实现待机、攻击和死亡三种动画状态
 * 完全按照 Blockbench 中的设计实现
 */
public class BaseEntityModel<T extends BaseEntity> extends EntityModel<T> {
    // 模型层位置 - 用于在客户端注册和渲染
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("bettereve", "baseentity"), "main");
    private final ModelPart core;        // 核心部件
    private final ModelPart kuangjia;   // 框架部件

    public BaseEntityModel(ModelPart root) {
        this.core = root.getChild("core");
        this.kuangjia = root.getChild("kuangjia");
    }

    // 创建模型图层定义 - 与 Blockbench 导出完全一致
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition core = partdefinition.addOrReplaceChild("core", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.9138F, -1.9431F, -0.0115F, 0.0524F, -0.1745F, 0.0F));

        PartDefinition core_r1 = core.addOrReplaceChild("core_r1", CubeListBuilder.create().texOffs(0, 64).addBox(-16.0F, -40.0F, -16.0F, 32.0F, 32.0F, 32.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 23.9772F, -1.0469F, -0.0443F, 0.1744F, -0.0077F));

        PartDefinition kuangjia = partdefinition.addOrReplaceChild("kuangjia", CubeListBuilder.create().texOffs(0, 0).addBox(-24.0F, 22.0F, -24.0F, 48.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 5).addBox(22.0F, 22.0F, -24.0F, 2.0F, 2.0F, 48.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-24.0F, -24.0F, 22.0F, 48.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 5).addBox(-24.0F, -24.0F, -24.0F, 2.0F, 2.0F, 48.0F, new CubeDeformation(0.0F))
                .texOffs(120, 46).addBox(22.0F, -24.0F, -24.0F, 2.0F, 48.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(120, 46).addBox(-24.0F, -24.0F, 22.0F, 2.0F, 48.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

        PartDefinition right2_r1 = kuangjia.addOrReplaceChild("right2_r1", CubeListBuilder.create().texOffs(120, 46).addBox(-1.0F, -20.0F, -1.0F, 2.0F, 48.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(23.0F, 4.0F, 23.0F, 0.0F, 0.0F, -3.1416F));

        PartDefinition left2_r1 = kuangjia.addOrReplaceChild("left2_r1", CubeListBuilder.create().texOffs(120, 46).addBox(-1.0F, -20.0F, -1.0F, 2.0F, 48.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-23.0F, 4.0F, -23.0F, 0.0F, 0.0F, -3.1416F));

        PartDefinition up3_r1 = kuangjia.addOrReplaceChild("up3_r1", CubeListBuilder.create().texOffs(0, 5).addBox(-1.0F, -51.0F, -24.0F, 2.0F, 2.0F, 48.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(23.0F, 27.0F, 0.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition up1_r1 = kuangjia.addOrReplaceChild("up1_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-24.0F, -5.0F, -1.0F, 48.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -19.0F, -23.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition under3_r1 = kuangjia.addOrReplaceChild("under3_r1", CubeListBuilder.create().texOffs(0, 5).addBox(22.5F, -5.0F, -13.0F, 2.0F, 2.0F, 48.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-23.5F, -5.0F, -13.0F, 48.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 27.0F, 11.0F, 0.0F, 3.1416F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    // 实现待机动画 - 4秒循环
    private void animateStandby(T entity, float partialTicks) {
        float time = entity.getAnimationProgress(partialTicks);
        float animationTime = (time % 4.0F) / 4.0F; // 0.0 到 1.0

        // 核心动画 - 360度旋转
        float coreRotation = animationTime * 360.0F;
        core.yRot = (float) Math.toRadians(-coreRotation);
        core.xRot = (float) Math.toRadians(-coreRotation);
        core.zRot = (float) Math.toRadians(-coreRotation);

        // 框架动画 - 360度旋转
        float kuangjiaRotation = animationTime * 360.0F;
        kuangjia.yRot = (float) Math.toRadians(kuangjiaRotation);
        kuangjia.xRot = (float) Math.toRadians(kuangjiaRotation);
        kuangjia.zRot = (float) Math.toRadians(kuangjiaRotation);
    }

    // 实现攻击动画 - 1秒
    private void animateAttack(T entity, float partialTicks) {
        float attackTime = entity.getAttackAnimationScale(partialTicks);
        float animationTime = Math.min(attackTime, 1.0F);

        // 核心动画 - 360度Y轴旋转，XZ轴180度旋转
        float coreYRotation = animationTime * 360.0F;
        float coreXZRotation = animationTime * 180.0F;
        core.yRot = (float) Math.toRadians(-coreYRotation);
        core.xRot = (float) Math.toRadians(-coreXZRotation);
        core.zRot = (float) Math.toRadians(-coreXZRotation);

        // 框架动画 - 360度Y轴旋转，XZ轴-180度旋转
        float kuangjiaYRotation = animationTime * 360.0F;
        float kuangjiaXZRotation = animationTime * -180.0F;
        kuangjia.yRot = (float) Math.toRadians(kuangjiaYRotation);
        kuangjia.xRot = (float) Math.toRadians(kuangjiaXZRotation);
        kuangjia.zRot = (float) Math.toRadians(kuangjiaXZRotation);
    }

    // 实现死亡动画 - 5秒
    private void animateDeath(T entity, float partialTicks) {
        float time = entity.getAnimationProgress(partialTicks);
        float animationTime = Math.min(time / 5.0F, 1.0F);

        // 核心动画 - 快速旋转，向上移动
        float coreRotation = animationTime * 1800.0F;
        core.yRot = (float) Math.toRadians(-coreRotation);
        core.xRot = (float) Math.toRadians(-coreRotation);
        core.zRot = (float) Math.toRadians(coreRotation);

        // 核心位置向上移动
        if (animationTime <= 0.2F) {
            float moveTime = animationTime / 0.2F;
            float yOffset = moveTime * 61.0F;
            core.setPos(core.x, core.y + yOffset, core.z);
        }

        // 框架动画 - 快速旋转
        float kuangjiaRotation = animationTime * 720.0F;
        kuangjia.yRot = (float) Math.toRadians(-kuangjiaRotation);
        kuangjia.xRot = (float) Math.toRadians(-kuangjiaRotation);
        kuangjia.zRot = (float) Math.toRadians(kuangjiaRotation);
    }

    // 设置动画 - 主入口点
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // 重置部件状态
        resetParts();

        // 根据动画状态选择相应的动画
        switch (entity.getAnimationState()) {
            case STANDBY:
                animateStandby(entity, ageInTicks);
                break;
            case ATTACK:
                animateAttack(entity, ageInTicks);
                break;
            case DEATH:
                animateDeath(entity, ageInTicks);
                break;
        }
    }

    // 重置部件状态 - 确保每次动画都有干净的起点
    private void resetParts() {
        core.yRot = 0;
        core.xRot = 0;
        core.zRot = 0;
        core.setPos(-0.9138F, -1.9431F, -0.0115F);

        kuangjia.yRot = 0;
        kuangjia.xRot = 0;
        kuangjia.zRot = 0;
        kuangjia.setPos(0.0F, -4.0F, 0.0F);
    }

    // 渲染模型
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        core.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        kuangjia.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}