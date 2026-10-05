package com.echohorror.client.render;

import com.echohorror.entity.PhantomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Watchers and shades: the tall thing made of sticks and threads. */
public class ScriptRenderer extends MobRenderer<PhantomEntity, ScriptModel> {
    private static final ResourceLocation TEX = PhantomRenderer.tex("script"), EYES = PhantomRenderer.tex("script_eyes");

    public ScriptRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ScriptModel(ctx.bakeLayer(ScriptModel.LAYER)), 0f);
        addLayer(new GlowLayer<>(this, e -> EYES));
    }

    @Override
    protected void scale(PhantomEntity e, PoseStack ps, float partial) {
        if (e.getKind() == PhantomEntity.KIND_SHADE) ps.scale(0.8f, 0.85f, 0.8f);
        if (e.isSmall()) ps.scale(0.55f, 0.55f, 0.55f);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomEntity e) {
        return TEX;
    }
}
