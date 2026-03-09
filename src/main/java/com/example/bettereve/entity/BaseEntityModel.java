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
 */
public class BaseEntityModel<T extends BaseEntity> extends EntityModel<T> {
    // 模型层位置 - 用于在客户端注册和渲染
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("bettereve", "baseentity"), "main");
    private final ModelPart core;        // 核心部件
    private final ModelPart kuangjia;   // 框架部件
    private final ModelPart root;       // 根部件

    public BaseEntityModel(ModelPart root) {
        this.root = root;
        this.core = root.getChild("core");
        this.kuangjia = root.getChild("kuangjia");
    }

    // 创建模型图层定义
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

    // 设置动画
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float time = entity.getAnimationProgress(ageInTicks);

        switch (entity.getAnimationState()) {
            case STANDBY:
                // 待机动画：核心和框架反向旋转
                // 旋转速度：1秒/圈 (360度/20tick)
                float standbyRotation = time * 120.0F;
                core.yRot = (float) Math.toRadians(-standbyRotation); // 核心Y轴旋转(顺时针)
                core.xRot = (float) Math.toRadians(-standbyRotation); // 核心X轴旋转(顺时针)
                core.zRot = (float) Math.toRadians(-standbyRotation); // 核心Z轴旋转(顺时针)

                kuangjia.yRot = (float) Math.toRadians(standbyRotation); // 框架Y轴旋转(逆时针)
                kuangjia.xRot = (float) Math.toRadians(standbyRotation); // 框架X轴旋转(逆时针)
                kuangjia.zRot = (float) Math.toRadians(standbyRotation); // 框架Z轴旋转(逆时针)
                break;

            case ATTACK:
                // 攻击动画：核心快速旋转并缩放
                // 旋转速度：3秒/圈 (1080度/20tick) - 比待机快3倍
                float attackRotation = time * 540.0F;
                core.yRot = (float) Math.toRadians(-attackRotation);    // 核心Y轴快速旋转(顺时针)
                core.xRot = (float) Math.toRadians(-attackRotation * 0.5F); // 核心X轴半速旋转(顺时针)
                core.zRot = (float) Math.toRadians(-attackRotation * 0.5F); // 核心Z轴半速旋转(顺时针)

                kuangjia.yRot = (float) Math.toRadians(attackRotation);    // 框架Y轴快速旋转(逆时针)
                kuangjia.xRot = (float) Math.toRadians(attackRotation * 0.5F); // 框架X轴半速旋转(逆时针)
                kuangjia.zRot = (float) Math.toRadians(attackRotation * 0.5F); // 框架Z轴半速旋转(逆时针)
                break;

            case DEATH:
                // 死亡动画：核心快速旋转并向上移动然后消失
                // 旋转速度：10秒/圈 (3600度/20tick) - 比待机快10倍
                float deathRotation = time * 1080.0F;
                core.yRot = (float) Math.toRadians(-deathRotation); // 核心Y轴极速旋转(顺时针)
                core.xRot = (float) Math.toRadians(-deathRotation); // 核心X轴极速旋转(顺时针)
                core.zRot = (float) Math.toRadians(deathRotation);  // 核心Z轴极速旋转(逆时针)

                kuangjia.yRot = (float) Math.toRadians(deathRotation * 2);    // 框架Y轴超高速旋转(逆时针)
                kuangjia.xRot = (float) Math.toRadians(deathRotation * 2);    // 框架X轴超高速旋转(逆时针)
                kuangjia.zRot = (float) Math.toRadians(-deathRotation * 2);   // 框架Z轴超高速旋转(顺时针)

                // 死亡动画：位置 - 向上移动61个单位
                float deathY = time * 90.0F;
                core.setPos(0, deathY, 0);
                break;
        }
    }

    // 渲染模型
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}