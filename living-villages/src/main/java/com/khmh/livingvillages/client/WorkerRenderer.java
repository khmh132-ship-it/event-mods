package com.khmh.livingvillages.client;

import com.khmh.livingvillages.entity.VillageWorker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws workers as plains villagers wearing the clothes of a matching vanilla profession. */
public class WorkerRenderer extends MobRenderer<VillageWorker, VillagerModel<VillageWorker>> {
    private static final ResourceLocation SKIN = new ResourceLocation("textures/entity/villager/villager.png");

    public WorkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerModel<>(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        addLayer(new Clothes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(VillageWorker worker) {
        return SKIN;
    }

    @Override
    protected void scale(VillageWorker worker, PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F); // same as villagers
    }

    private static class Clothes extends RenderLayer<VillageWorker, VillagerModel<VillageWorker>> {
        private static final ResourceLocation TYPE = new ResourceLocation("textures/entity/villager/type/plains.png");

        Clothes(RenderLayerParent<VillageWorker, VillagerModel<VillageWorker>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, VillageWorker worker,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (worker.isInvisible()) {
                return;
            }
            renderColoredCutoutModel(getParentModel(), TYPE, pose, buffers, light, worker, 1, 1, 1);
            ResourceLocation outfit = new ResourceLocation("textures/entity/villager/profession/"
                    + worker.job().outfit() + ".png");
            renderColoredCutoutModel(getParentModel(), outfit, pose, buffers, light, worker, 1, 1, 1);
        }
    }
}
