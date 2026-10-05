package com.echohorror.registry;

import com.echohorror.EchoHorror;
import com.echohorror.loot.LoreNoteModifier;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModLoot {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, EchoHorror.MODID);

    static {
        MODIFIERS.register("lore_notes", LoreNoteModifier.CODEC);
    }

    private ModLoot() {}
}
