package com.echohorror.client;

import com.echohorror.EchoHorror;
import com.echohorror.client.render.*;
import com.echohorror.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EchoHorror.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.PHANTOM.get(), PhantomRenderer::new);
        e.registerEntityRenderer(ModEntities.MIMIC.get(), MimicRenderer::new);
        e.registerEntityRenderer(ModEntities.CRAWLER.get(), CrawlerRenderer::new);
        e.registerEntityRenderer(ModEntities.SILENT.get(), SilentRenderer::new);
        e.registerEntityRenderer(ModEntities.ECHO_BOSS.get(), BossRenderer::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(ScriptModel.LAYER, ScriptModel::create);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("horror", HorrorOverlay::render);
    }
}
