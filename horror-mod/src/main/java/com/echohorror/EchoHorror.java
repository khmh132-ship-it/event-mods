package com.echohorror;

import com.echohorror.network.Net;
import com.echohorror.registry.ModBlocks;
import com.echohorror.registry.ModEntities;
import com.echohorror.registry.ModItems;
import com.echohorror.registry.ModSounds;
import com.echohorror.registry.ModTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ЭХО: Объект «Колокол» — сюжетный хоррор-мод.
 */
@Mod(EchoHorror.MODID)
public class EchoHorror {
    public static final String MODID = "echohorror";
    public static final Logger LOG = LoggerFactory.getLogger("EchoHorror");

    public EchoHorror() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.SOUNDS.register(bus);
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModTabs.TABS.register(bus);
        com.echohorror.registry.ModLoot.MODIFIERS.register(bus);
        bus.addListener(ModEntities::onAttributes);
        bus.addListener(this::setup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }
}
