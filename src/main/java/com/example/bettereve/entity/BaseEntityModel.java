package com.example.bettereve.entity;

// Made with Blockbench 5.0.7
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
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

public class BaseEntityModel<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("bettereve", "baseentity"), "main");
	private final ModelPart core;
	private final ModelPart kuangjia;

	public BaseEntityModel(ModelPart root) {
		this.core = root.getChild("core");
		this.kuangjia = root.getChild("kuangjia");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition core = partdefinition.addOrReplaceChild("core", CubeListBuilder.create(), PartPose.offsetAndRotation(0.6715F, 11.063F, -0.3849F, 0.0524F, -0.1745F, 0.0F));

		PartDefinition core_r1 = core.addOrReplaceChild("core_r1", CubeListBuilder.create().texOffs(46, 87).addBox(-11.0F, -14.5F, -5.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.4335F, 6.3422F, -3.7556F, -0.0443F, 0.1744F, -0.0077F));

		PartDefinition kuangjia = partdefinition.addOrReplaceChild("kuangjia", CubeListBuilder.create().texOffs(19, 58).addBox(-12.5F, 10.5F, -12.5F, 25.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(120, 0).addBox(10.5F, -12.5F, 10.5F, 2.0F, 25.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(30, 0).addBox(10.5F, 10.5F, -12.5F, 2.0F, 2.0F, 25.0F, new CubeDeformation(0.0F))
		.texOffs(30, 0).addBox(-12.5F, 10.5F, -12.5F, 2.0F, 2.0F, 25.0F, new CubeDeformation(0.0F))
		.texOffs(120, 0).addBox(-12.5F, -12.5F, -12.5F, 2.0F, 25.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(19, 58).addBox(-12.5F, -12.5F, 10.5F, 25.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(30, 0).addBox(-12.5F, -12.5F, -12.5F, 2.0F, 2.0F, 25.0F, new CubeDeformation(0.0F))
		.texOffs(30, 0).addBox(10.5F, -12.5F, -12.5F, 2.0F, 2.0F, 25.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 11.5F, -0.5F));

		PartDefinition r10_r1 = kuangjia.addOrReplaceChild("10_r1", CubeListBuilder.create().texOffs(19, 58).addBox(-12.5F, -1.0F, -1.0F, 25.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -11.5F, -11.5F, 0.0F, 3.1416F, 0.0F));

		PartDefinition r7_r1 = kuangjia.addOrReplaceChild("7_r1", CubeListBuilder.create().texOffs(120, 0).addBox(-1.0F, -12.5F, -1.0F, 2.0F, 25.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-11.5F, 0.0F, 11.5F, 0.0F, 0.0F, -3.1416F));

		PartDefinition r3_r1 = kuangjia.addOrReplaceChild("3_r1", CubeListBuilder.create().texOffs(120, 0).addBox(-1.0F, -12.5F, -1.0F, 2.0F, 25.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(11.5F, 0.0F, -11.5F, 0.0F, 0.0F, -3.1416F));

		PartDefinition r1_r1 = kuangjia.addOrReplaceChild("1_r1", CubeListBuilder.create().texOffs(19, 58).addBox(-12.5F, -1.0F, -1.0F, 25.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.5F, 11.5F, 0.0F, 3.1416F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		core.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		kuangjia.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}