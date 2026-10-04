package com.echohorror.client.render;

import com.echohorror.entity.SilentEntity;
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
            head.yRot = 0f;
            head.xRot = 0.25f;
            head.zRot = 0.35f;
            body.xRot = 0.08f;
            body.yRot = 0f;
            rightArm.xRot = -1.45f;
            rightArm.yRot = -0.12f;
            rightArm.zRot = 0f;
            leftArm.xRot = -1.45f;
            leftArm.yRot = 0.12f;
            leftArm.zRot = 0f;
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
    }

    @Override
    public ResourceLocation getTextureLocation(SilentEntity e) {
        return TEX;
    }
}
