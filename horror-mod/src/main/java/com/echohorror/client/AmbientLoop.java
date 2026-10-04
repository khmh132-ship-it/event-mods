package com.echohorror.client;

import com.echohorror.EchoHorror;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A looping background drone that fades in and out instead of cutting. */
public class AmbientLoop extends AbstractTickableSoundInstance {
    private final String name;
    private final float target;
    private boolean fadingOut;

    public AmbientLoop(String name, float target) {
        super(SoundEvent.createVariableRangeEvent(new ResourceLocation(EchoHorror.MODID, name)), SoundSource.AMBIENT, RandomSource.create());
        this.name = name;
        this.target = target;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    public String name() {
        return name;
    }

    public void fadeOut() {
        fadingOut = true;
    }

    public boolean isFadingOut() {
        return fadingOut;
    }

    @Override
    public void tick() {
        if (fadingOut) {
            volume -= 0.01f;
            if (volume <= 0.01f) stop();
        } else if (volume < target) {
            volume = Math.min(target, volume + 0.005f);
        }
    }
}
