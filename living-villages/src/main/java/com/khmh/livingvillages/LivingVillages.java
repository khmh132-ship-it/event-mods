package com.khmh.livingvillages;

import com.khmh.livingvillages.command.VillageCommand;
import com.khmh.livingvillages.config.LVConfig;
import com.khmh.livingvillages.village.VillageEvents;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(LivingVillages.MODID)
public class LivingVillages {
    public static final String MODID = "livingvillages";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LivingVillages() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, LVConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(VillageEvents.class);
        MinecraftForge.EVENT_BUS.addListener(VillageCommand::register);
    }
}
