package com.echohorror.client.render;

import com.echohorror.entity.SilentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SilentRenderer extends HumanoidMobRenderer<SilentEntity, PlayerModel<SilentEntity>> {
    private static final ResourceLocation TEX = PhantomRenderer.tex("silent");

    /** A statue. She never moves while you can see her. */
    static class StatueModel extends PlayerModel<SilentEntity> {
        StatueModel(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(SilentEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            // each one is frozen in its own broken pose
            float v = (e.getId() * 0.618f) % 1f;
            head.yRot = 0.2f * (v - 0.5f);
            head.xRot = 0.3f;                     // looking down at you
            head.zRot = 0.12f + 0.12f * v;       // only slightly wrong
            head.yScale = 1.1f;
            head.y = 0f;
            body.xRot = 0.06f;
            body.yRot = 0f;
            rightArm.xRot = -1.05f - 0.3f * v;    // one hand reaching for you
            rightArm.yRot = -0.1f;
            rightArm.zRot = 0.05f;
            leftArm.xRot = 0.1f;
            leftArm.yRot = 0.05f;
            leftArm.zRot = -0.05f;
            rightArm.yScale = leftArm.yScale = 1.4f;

            rightLeg.xRot = 0f;
            leftLeg.xRot = 0f;
            rightLeg.yRot = leftLeg.yRot = 0f;
            rightLeg.zRot = leftLeg.zRot = 0f;
            hat.copyFrom(head);
            jacket.copyFrom(body);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
            rightPants.copyFrom(rightLeg);
            leftPants.copyFrom(leftLeg);
        }
    }

    public SilentRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new StatueModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new GlowLayer<>(this, e -> PhantomRenderer.tex("silent_eyes")));
    }

    @Override
    protected void scale(SilentEntity e, PoseStack ps, float partial) {
        ps.scale(0.88f, 1.38f, 0.88f); // too tall, too thin
    }

    @Override
    public ResourceLocation getTextureLocation(SilentEntity e) {
        return TEX;
    }
}
