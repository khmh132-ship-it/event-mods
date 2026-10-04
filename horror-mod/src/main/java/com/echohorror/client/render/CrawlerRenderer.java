package com.echohorror.client.render;

import com.echohorror.entity.CrawlerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CrawlerRenderer extends HumanoidMobRenderer<CrawlerEntity, PlayerModel<CrawlerEntity>> {
    private static final ResourceLocation TEX = PhantomRenderer.tex("crawler");

    static class CrawlerModel extends PlayerModel<CrawlerEntity> {
        CrawlerModel(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(CrawlerEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            if (e.onClimbable()) return;
            float sw = limbSwing * 1.3f;
            rightArm.xRot = (float) -Math.PI + Mth.cos(sw) * 0.9f * limbAmount;
            leftArm.xRot = (float) -Math.PI + Mth.cos(sw + (float) Math.PI) * 0.9f * limbAmount;
            rightArm.zRot = 0.25f;
            leftArm.zRot = -0.25f;
            rightLeg.xRot = Mth.cos(sw + (float) Math.PI) * 0.5f * limbAmount;
            leftLeg.xRot = Mth.cos(sw) * 0.5f * limbAmount;
            head.xRot = -1.2f + Mth.sin(age * 0.6f) * 0.08f;
            head.zRot = Mth.sin(age * 0.23f) * 0.3f;
            hat.copyFrom(head);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
            rightPants.copyFrom(rightLeg);
            leftPants.copyFrom(leftLeg);
        }
    }

    public CrawlerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CrawlerModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.4f);
    }

    @Override
    protected void setupRotations(CrawlerEntity e, PoseStack ps, float age, float yaw, float partial) {
        super.setupRotations(e, ps, age, yaw, partial);
        if (!e.onClimbable() && e.deathTime == 0) {
            ps.mulPose(Axis.XP.rotationDegrees(-90f));
            ps.translate(0.0F, -1.0F, 0.3F);
        }
    }

    @Override
    protected void scale(CrawlerEntity e, PoseStack ps, float partial) {
        ps.scale(0.9f, 1.05f, 0.9f);
    }

    @Override
    public ResourceLocation getTextureLocation(CrawlerEntity e) {
        return TEX;
    }
}
