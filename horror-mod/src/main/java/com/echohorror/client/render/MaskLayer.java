package com.echohorror.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/** Paints something over the entity's own skin (a face that is no longer a face). Lit like the body. */
public class MaskLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final Function<T, ResourceLocation> texture;

    public MaskLayer(RenderLayerParent<T, M> parent, Function<T, ResourceLocation> texture) {
        super(parent);
        this.texture = texture;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limbSwing, float limbAmount, float partial,
                       float age, float headYaw, float headPitch) {
        ResourceLocation tex = texture.apply(e);
        if (tex == null || e.isInvisible()) return;
        getParentModel().renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(tex)), light,
                LivingEntityRenderer.getOverlayCoords(e, 0f), 1f, 1f, 1f, 1f);
    }
}
