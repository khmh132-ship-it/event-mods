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
            if (!e.isRevealed()) return;
            head.zRot = Mth.sin(age * 1.7f) * 0.35f + (e.getId() % 2 == 0 ? 0.6f : -0.6f);
            head.xRot += Mth.sin(age * 3.1f) * 0.15f;
            rightArm.xRot = -1.6f + Mth.cos(age * 0.9f) * 0.15f;
            leftArm.xRot = -1.6f + Mth.sin(age * 0.9f) * 0.15f;
            rightArm.zRot = -0.1f;
            leftArm.zRot = 0.1f;
            hat.copyFrom(head);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
        }
    }

    private final PlayerModel<MimicEntity> wide, slim;

    public MimicRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MimicModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        wide = model;
        slim = new MimicModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new GlowLayer<>(this, e -> e.isRevealed() ? PhantomRenderer.MIMIC_EYES : null));
    }

    @Override
    public void render(MimicEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        model = !e.isRevealed() && Skins.of(e.getSkin().orElse(null)).slim() ? slim : wide;
        super.render(e, yaw, partial, ps, buf, light);
    }

    @Override
    protected void scale(MimicEntity e, PoseStack ps, float partial) {
        if (e.isRevealed()) ps.scale(0.95f, 1.08f, 0.95f);
        else ps.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    public ResourceLocation getTextureLocation(MimicEntity e) {
        return e.isRevealed() ? PhantomRenderer.MIMIC : Skins.of(e.getSkin().orElse(null)).texture();
    }
}
