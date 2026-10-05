package com.echohorror.client.render;

import com.echohorror.entity.MimicEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class MimicRenderer extends HumanoidMobRenderer<MimicEntity, PlayerModel<MimicEntity>> {
    /** Normal player animation until revealed — then the head twists and the arms reach. */
    static class MimicModel extends PlayerModel<MimicEntity> {
        MimicModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(MimicEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
            head.yScale = 1f;
            head.y = 0f;
            rightArm.yScale = leftArm.yScale = 1f;
            rightArm.xScale = leftArm.xScale = rightArm.zScale = leftArm.zScale = 1f;
            body.xRot = 0f;
            if (!e.isRevealed()) {
                hat.copyFrom(head);
                rightSleeve.copyFrom(rightArm);
                leftSleeve.copyFrom(leftArm);
                return;
            }
            // jerky, like a film with frames missing
            float t = (float) Math.floor(age * 6f) / 6f;
            head.zRot = Mth.sin(t * 1.7f) * 0.45f + (e.getId() % 2 == 0 ? 0.6f : -0.6f);
            head.xRot += Mth.sin(t * 3.1f) * 0.25f;
            head.yScale = 1.12f;
            head.y = 0f;
            body.xRot = 0.3f;                          // hunched, leaning at you
            rightArm.yScale = leftArm.yScale = 1.65f;  // hands below the knees

            rightArm.xRot = -0.25f + Mth.cos(t * 0.9f) * 0.12f;  // arms hang, swinging out of step
            leftArm.xRot = -0.1f + Mth.sin(t * 0.9f) * 0.12f;
            rightArm.zRot = -0.1f;
            leftArm.zRot = 0.1f;
            hat.copyFrom(head);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
        }
    }

    private static final ResourceLocation MASK = PhantomRenderer.tex("mimic_mask");
    private final PlayerModel<MimicEntity> wide, slim;

    public MimicRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MimicModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        wide = model;
        slim = new MimicModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new MaskLayer<>(this, e -> e.isRevealed() ? MASK : null));
        addLayer(new GlowLayer<>(this, e -> e.isRevealed() ? PhantomRenderer.MIMIC_EYES : null));
    }

    @Override
    public void render(MimicEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        model = Skins.of(e.getSkin().orElse(null)).slim() ? slim : wide;
        super.render(e, yaw, partial, ps, buf, light);
    }

    @Override
    protected void scale(MimicEntity e, PoseStack ps, float partial) {
        if (e.isRevealed()) ps.scale(0.88f, 1.25f, 0.88f);
        else ps.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    public ResourceLocation getTextureLocation(MimicEntity e) {
        // it keeps your face; only the eyes and the mouth are gone
        return e.getSkin().isPresent() ? Skins.of(e.getSkin().get()).texture() : PhantomRenderer.MIMIC;
    }
}
