package com.khmh.livingvillages;

import com.khmh.livingvillages.command.VillageCommand;
import com.khmh.livingvillages.village.VillageEvents;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(LivingVillages.MODID)
public class LivingVillages {
    public static final String MODID = "livingvillages";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LivingVillages() {
        MinecraftForge.EVENT_BUS.register(VillageEvents.class);
        MinecraftForge.EVENT_BUS.addListener(VillageCommand::register);
    }
}
