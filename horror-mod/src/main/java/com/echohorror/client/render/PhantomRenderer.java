package com.echohorror.client.render;

import com.echohorror.EchoHorror;
import com.echohorror.entity.PhantomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PhantomRenderer extends HumanoidMobRenderer<PhantomEntity, PlayerModel<PhantomEntity>> {
    static final ResourceLocation WATCHER = tex("watcher"), WATCHER_EYES = tex("watcher_eyes"), SHADE = tex("shade"),
            SHADE_EYES = tex("shade_eyes"), SILENT = tex("silent"), MIMIC = tex("mimic_reveal"), MIMIC_EYES = tex("mimic_eyes");

    private final PlayerModel<PhantomEntity> wide, slim;

    static ResourceLocation tex(String n) {
        return new ResourceLocation(EchoHorror.MODID, "textures/entity/" + n + ".png");
    }

    public PhantomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.0f);
        wide = model;
        slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new GlowLayer<>(this, e -> switch (e.getKind()) {
            case PhantomEntity.KIND_WATCHER -> WATCHER_EYES;
            case PhantomEntity.KIND_SHADE -> SHADE_EYES;
            case PhantomEntity.KIND_MIMIC -> MIMIC_EYES;
            default -> null;
        }));
    }

    @Override
    public void render(PhantomEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        model = e.getKind() == PhantomEntity.KIND_FAKE_PLAYER && Skins.of(e.getSkin().orElse(null)).slim() ? slim : wide;
        super.render(e, yaw, partial, ps, buf, light);
    }

    @Override
    protected void scale(PhantomEntity e, PoseStack ps, float partial) {
        switch (e.getKind()) {
            case PhantomEntity.KIND_WATCHER -> ps.scale(0.85f, 1.32f, 0.85f);
            case PhantomEntity.KIND_SHADE -> ps.scale(0.95f, 1.08f, 0.95f);
            default -> ps.scale(0.9375f, 0.9375f, 0.9375f);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomEntity e) {
        return switch (e.getKind()) {
            case PhantomEntity.KIND_WATCHER -> WATCHER;
            case PhantomEntity.KIND_SHADE -> SHADE;
            case PhantomEntity.KIND_SILENT -> SILENT;
            case PhantomEntity.KIND_MIMIC -> MIMIC;
            default -> Skins.of(e.getSkin().orElse(null)).texture();
        };
    }
}
