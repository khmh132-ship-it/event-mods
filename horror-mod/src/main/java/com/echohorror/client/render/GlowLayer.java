package com.echohorror.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/** Glowing eyes visible in total darkness. */
public class GlowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final Function<T, ResourceLocation> texture;

    public GlowLayer(RenderLayerParent<T, M> parent, Function<T, ResourceLocation> texture) {
        super(parent);
        this.texture = texture;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limbSwing, float limbAmount, float partial,
                       float age, float headYaw, float headPitch) {
        ResourceLocation tex = texture.apply(e);
        if (tex == null || e.isInvisible()) return;
        getParentModel().renderToBuffer(ps, buf.getBuffer(RenderType.eyes(tex)), 15728640, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
    }
}
