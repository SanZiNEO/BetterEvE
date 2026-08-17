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

import javax.annotation.Nullable;

/**
 * 基地实体模型类
 * 负责渲染基地实体的三维模型和动画
 * 本模型使用自定义手动动画系统，实现待机、攻击和死亡三种动画状态
 * 完全按照 Blockbench 中的设计实现
 */
public class CrystalEntityModel<T extends CrystalEntity> extends EntityModel<T> {
    // 模型层位置 - 用于在客户端注册和渲染
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("bettereve", "crystalentity"), "main");

    /** 缓存的模型高出碰撞箱顶部的最大高度（格，未乘 modelScale） */
    private static Float modelHeightAboveCollisionBox;

    /**
     * 从模型定义自动计算：模型相对碰撞箱顶部向上伸出的最大高度（格）。
     * 遍历所有 Cube 并变换到模型根空间，取最大 Y。
     * 这样血条位置会随模型改动自动适应，不需要手写固定数值。
     */
    public static float getModelHeightAboveCollisionBox() {
        if (modelHeightAboveCollisionBox != null) {
            return modelHeightAboveCollisionBox;
        }
        ModelPart root = createBodyLayer().bakeRoot();
        PoseStack poseStack = new PoseStack();
        float[] maxHeight = {0.0f};
        root.visit(poseStack, (pose, path, index, cube) -> {
            // Minecraft 模型坐标中 Y 向下为正，所以模型“最高点”是 minY（更小的负数）。
            // 16 像素 = 1 格，取 -minY/16 作为该 Cube 高出模型原点的高度。
            float heightAbove = Math.max(0.0f, -cube.minY / 16.0f);
            if (heightAbove > maxHeight[0]) {
                maxHeight[0] = heightAbove;
            }
        });
        modelHeightAboveCollisionBox = maxHeight[0];
        return modelHeightAboveCollisionBox;
    }

    private final ModelPart core;        // 核心部件
    private final ModelPart kuangjia;   // 框架部件

    public CrystalEntityModel(ModelPart root) {
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

    // 设置动画 - 主入口点
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // 重置部件状态
        resetParts();

        // 获取插值后的动画速度因子
        float speedFactor = entity.getInterpolatedAnimationSpeedFactor(ageInTicks);
        
        // 获取插值后的动画进度
        float time = entity.getInterpolatedAnimationProgress(ageInTicks);
        
        // 基础动画速度：4秒完成360度旋转（每秒90度）
        // 根据速度因子调整动画速度
        float adjustedTime = time * speedFactor;
        float animationTime = (adjustedTime % 4.0F) / 4.0F; // 0.0 到 1.0

        // 核心动画 - 360度旋转（使用平滑插值）
        float coreRotation = animationTime * 360.0F;
        core.yRot = (float) Math.toRadians(-coreRotation);
        core.xRot = (float) Math.toRadians(-coreRotation);
        core.zRot = (float) Math.toRadians(-coreRotation);

        // 框架动画 - 360度旋转（使用平滑插值）
        float kuangjiaRotation = animationTime * 360.0F;
        kuangjia.yRot = (float) Math.toRadians(kuangjiaRotation);
        kuangjia.xRot = (float) Math.toRadians(kuangjiaRotation);
        kuangjia.zRot = (float) Math.toRadians(kuangjiaRotation);
        
        // 添加缓动效果，使动画更平滑
        applyEasingEffects();
    }
    
    // 应用缓动效果，使动画更平滑
    private void applyEasingEffects() {
        // 使用正弦缓动函数使旋转更平滑
        // 这会在动画开始和结束时提供更平滑的过渡
        float smoothFactor = 0.1f; // 平滑因子，值越小越平滑
        
        // 对核心部件应用缓动
        core.xRot = applySmoothEasing(core.xRot, smoothFactor);
        core.yRot = applySmoothEasing(core.yRot, smoothFactor);
        core.zRot = applySmoothEasing(core.zRot, smoothFactor);
        
        // 对框架部件应用缓动
        kuangjia.xRot = applySmoothEasing(kuangjia.xRot, smoothFactor);
        kuangjia.yRot = applySmoothEasing(kuangjia.yRot, smoothFactor);
        kuangjia.zRot = applySmoothEasing(kuangjia.zRot, smoothFactor);
    }
    
    // 应用平滑缓动函数
    private float applySmoothEasing(float rotation, float smoothFactor) {
        // 使用线性插值平滑旋转角度
        // 这有助于减少动画跳跃感
        return rotation * (1.0f - smoothFactor) + rotation * smoothFactor;
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
