package dev.khmh.trialcomplex;

import com.mojang.logging.LogUtils;
import dev.khmh.trialcomplex.game.Complex;
import dev.khmh.trialcomplex.net.Net;
import dev.khmh.trialcomplex.voice.VoiceLines;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TrialComplex.MODID)
public class TrialComplex {
    public static final String MODID = "trialcomplex";
    public static final Logger LOG = LogUtils.getLogger();

    public TrialComplex() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(Complex.Events.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Net.register();
            VoiceLines.load();
        });
    }
}
