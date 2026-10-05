package com.echohorror.client.render;

import com.echohorror.entity.EchoBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class BossRenderer extends HumanoidMobRenderer<EchoBossEntity, PlayerModel<EchoBossEntity>> {
    private static final ResourceLocation TEX = PhantomRenderer.tex("boss"), EYES = PhantomRenderer.tex("boss_eyes");

    static class BossModel extends PlayerModel<EchoBossEntity> {
        BossModel(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(EchoBossEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            float twitch = e.isStunned() ? 0.5f : 0.15f;
            head.zRot = Mth.sin(age * (e.isStunned() ? 2.3f : 0.3f)) * twitch;
            head.xRot += Mth.sin(age * 1.1f) * 0.05f;
            rightArm.yScale = leftArm.yScale = 1.75f; // arms dragging on the floor
            head.yScale = 0.85f;
            rightArm.zRot += 0.15f + Mth.sin(age * 0.2f) * 0.1f;
            leftArm.zRot -= 0.15f + Mth.cos(age * 0.2f) * 0.1f;
            if (e.isStunned()) {
                rightArm.xRot = -2.6f;
                leftArm.xRot = -2.6f;
            }
            hat.copyFrom(head);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
        }
    }

    public BossRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BossModel(ctx.bakeLayer(ModelLayers.PLAYER)), 1.4f);
        addLayer(new GlowLayer<>(this, e -> EYES));
    }

    @Override
    protected void scale(EchoBossEntity e, PoseStack ps, float partial) {
        ps.scale(1.4f, 2.3f, 1.4f);
    }

    @Override
    protected float getWhiteOverlayProgress(EchoBossEntity e, float partial) {
        if (!e.isStunned()) return 0f;
        return 0.25f + 0.25f * Mth.sin((e.tickCount + partial) * 0.8f);
    }

    @Override
    public ResourceLocation getTextureLocation(EchoBossEntity e) {
        return e.getFace().map(id -> Skins.of(id).texture()).orElse(TEX);
    }
}
