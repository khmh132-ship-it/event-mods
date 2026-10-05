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
            crouching = !e.onClimbable(); // vanilla's crouch pose bends the spine for us
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            rightArm.yScale = leftArm.yScale = 1.75f;   // knuckles on the ground
            rightArm.xScale = leftArm.xScale = rightArm.zScale = leftArm.zScale = 0.62f; // bone-thin
            rightLeg.xScale = leftLeg.xScale = rightLeg.zScale = leftLeg.zScale = 0.7f;
            head.xScale = head.yScale = head.zScale = 1.12f;
            rightLeg.yScale = leftLeg.yScale = 0.85f;
            body.xScale = 0.72f;
            float twitch = Mth.sin(age * 9.1f) * Mth.sin(age * 2.3f) > 0.85f ? 0.5f : 0f;
            head.zRot = 1.4f + Mth.sin(age * 0.23f) * 0.2f + twitch; // the head lies on its shoulder, sideways
            head.yRot += twitch * 0.6f;
            if (!e.onClimbable()) {
                float sw = limbSwing * 1.1f;
                body.xRot = 0.95f;
                head.xRot = -0.75f + Mth.sin(age * 0.6f) * 0.08f; // craning up to stare at you
                head.y = 5.5f;
                head.z = -3.5f;
                rightArm.y = leftArm.y = 5.5f;
                rightArm.z = leftArm.z = -2.5f;
                rightArm.xRot = -0.35f + Mth.cos(sw) * 0.7f * limbAmount;            // walking on its hands
                leftArm.xRot = -0.35f + Mth.cos(sw + (float) Math.PI) * 0.7f * limbAmount;
                rightArm.zRot = 0.18f;
                leftArm.zRot = -0.18f;
                rightLeg.xRot = -0.6f + Mth.cos(sw + (float) Math.PI) * 0.5f * limbAmount;
                leftLeg.xRot = -0.6f + Mth.cos(sw) * 0.5f * limbAmount;
            }
            copyOverlays();
        }

        private void copyOverlays() {
            hat.copyFrom(head);
            jacket.copyFrom(body);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
            rightPants.copyFrom(rightLeg);
            leftPants.copyFrom(leftLeg);
        }
    }

    public CrawlerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CrawlerModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.4f);
        addLayer(new GlowLayer<>(this, e -> PhantomRenderer.tex("crawler_eyes")));
    }

    @Override
    protected void scale(CrawlerEntity e, PoseStack ps, float partial) {
        ps.scale(0.95f, 0.95f, 0.95f);
    }

    @Override
    public ResourceLocation getTextureLocation(CrawlerEntity e) {
        return TEX;
    }
}
