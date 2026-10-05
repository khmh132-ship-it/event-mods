package com.echohorror.registry;

import com.echohorror.EchoHorror;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EchoHorror.MODID);
    private static final Map<String, RegistryObject<SoundEvent>> BY_NAME = new HashMap<>();

    static {
        String[] names = {
            "ambient.depths",
            "ambient.drone",
            "entity.boss.death",
            "entity.boss.hurt",
            "entity.boss.idle",
            "entity.boss.roar",
            "entity.boss.stun",
            "entity.boss.voice",
            "entity.crawler.click",
            "entity.crawler.death",
            "entity.crawler.hurt",
            "entity.mimic.idle",
            "entity.mimic.reveal",
            "entity.silent.attack",
            "entity.silent.move",
            "entity.watcher.idle",
            "item.flashlight",
            "item.page",
            "item.pills",
            "music.ending",
            "music.finale",
            "radio.b_attention",
            "radio.b_counting",
            "radio.b_deeper",
            "radio.b_end",
            "radio.b_heard",
            "radio.b_lis",
            "radio.b_more",
            "radio.b_prologue",
            "radio.intro",
            "radio.num0",
            "radio.num1",
            "radio.num10",
            "radio.num11",
            "radio.num12",
            "radio.num2",
            "radio.num3",
            "radio.num4",
            "radio.num5",
            "radio.num6",
            "radio.num7",
            "radio.num8",
            "radio.num9",
            "radio.tune",
            "scare.breath",
            "scare.glitch",
            "scare.heartbeat",
            "scare.heartbeat_fast",
            "scare.hum",
            "scare.knock",
            "scare.knock_frantic",
            "scare.music_box",
            "scare.scrape",
            "scare.scream_far",
            "scare.scream_near",
            "scare.static",
            "scare.stinger",
            "scare.tinnitus",
            "scare.vanish",
            "story.bell",
            "story.bell_far",
            "story.chapter",
            "story.door_open",
            "story.power",
            "tape.tape1",
            "tape.tape2",
            "tape.tape3",
            "tape.tape4",
            "tape.tape5",
            "tape.tape6",
            "tape.tape7", "tape.tape8",
            "voice.call",
            "story.camp_horn", "voice.camp_lineup", "voice.camp_children", "item.music_box_play",
            "story.siren", "voice.bunker_alarm",
            "voice.mimic1", "voice.mimic2", "voice.mimic3", "voice.mimic4", "voice.mimic5",
            "dream.d1", "dream.d2", "dream.d3", "dream.d4", "dream.d5", "dream.d6", "dream.d7", "dream.d8", "dream.d9", "voice.kuzmich1", "voice.kuzmich2", "voice.kuzmich3", "voice.lis_call", "voice.lis_why",
            "voice.child",
            "voice.help",
            "voice.laugh",
            "voice.open",
            "whisper"
        };
        for (String n : names) {
            BY_NAME.put(n, SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EchoHorror.MODID, n))));
        }
    }

    private ModSounds() {}

    /** Returns the registered sound event for a short name such as "scare.knock". */
    public static SoundEvent get(String name) {
        RegistryObject<SoundEvent> ro = BY_NAME.get(name);
        if (ro == null) throw new IllegalArgumentException("Unknown echohorror sound: " + name);
        return ro.get();
    }

    public static boolean exists(String name) {
        return BY_NAME.containsKey(name);
    }
}
