package com.eventmods.splitpad;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(SplitPad.MODID)
public class SplitPad {
    public static final String MODID = "splitpad";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SplitPad() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SplitPadConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new ClientHandler());
    }
}
