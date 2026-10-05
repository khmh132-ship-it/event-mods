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

        // Geometry of the hunch: the body pivots at the neck; shoulders, hips and head are placed where the
        // rotated body actually puts them, so nothing floats or detaches.
        private static final float PITCH = 0.9f, NECK_Y = 4.5f;

        @Override
        public void setupAnim(CrawlerEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            crouching = false;
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            float twitch = Mth.sin(age * 9.1f) * Mth.sin(age * 2.3f) > 0.85f ? 0.45f : 0f;
            rightArm.yScale = leftArm.yScale = 1.55f;       // arms long enough to walk on
            head.xScale = head.yScale = head.zScale = 1.1f;
            if (e.onClimbable()) {                           // clinging to a wall
                rightArm.xRot = leftArm.xRot = -2.9f;
                rightLeg.yScale = leftLeg.yScale = 1f;
                head.zRot = twitch;
                copyOverlays();
                return;
            }
            float c = Mth.cos(PITCH), s = Mth.sin(PITCH);
            body.xRot = PITCH;
            body.y = NECK_Y;
            body.z = 0f;
            head.y = NECK_Y;
            head.z = -0.5f;
            head.xRot = -0.25f + Mth.sin(age * 0.6f) * 0.05f; // head up, staring at you
            head.zRot = 0.12f + twitch;
            head.yRot = headYaw * ((float) Math.PI / 180f) + twitch * 0.5f;
            float shoulderY = NECK_Y + 2f * c, shoulderZ = 2f * s;
            rightArm.y = leftArm.y = shoulderY;
            rightArm.z = leftArm.z = shoulderZ;
            float hipY = NECK_Y + 12f * c, hipZ = 12f * s;
            rightLeg.y = leftLeg.y = hipY;
            rightLeg.z = leftLeg.z = hipZ;
            rightLeg.yScale = leftLeg.yScale = (24f - hipY) / 12f; // legs reach the ground exactly
            float sw = limbSwing * 1.1f;
            rightArm.xRot = -0.1f + Mth.cos(sw) * 0.45f * limbAmount;           // front legs
            leftArm.xRot = -0.1f + Mth.cos(sw + (float) Math.PI) * 0.45f * limbAmount;
            rightArm.zRot = 0.12f;
            leftArm.zRot = -0.12f;
            rightLeg.xRot = Mth.cos(sw + (float) Math.PI) * 0.45f * limbAmount;
            leftLeg.xRot = Mth.cos(sw) * 0.45f * limbAmount;
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
        ps.scale(1.4f, 1.4f, 1.4f); // bigger than you think
    }

    @Override
    public ResourceLocation getTextureLocation(CrawlerEntity e) {
        return TEX;
    }
}
