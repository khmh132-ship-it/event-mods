package com.echohorror.registry;

import com.echohorror.EchoHorror;
import com.echohorror.item.NoteItem;
import com.echohorror.item.TapeItem;
import com.echohorror.story.Notes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EchoHorror.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.literal("ЭХО: Объект «Колокол»"))
            .icon(() -> new ItemStack(ModItems.LOCATOR.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.JOURNAL.get());
                out.accept(ModItems.LOCATOR.get());
                out.accept(ModItems.FLASHLIGHT.get());
                out.accept(ModItems.BATTERY.get());
                out.accept(ModItems.PILLS.get());
                out.accept(ModItems.HANDBELL.get());
                out.accept(ModItems.MUSIC_BOX.get());
                out.accept(ModItems.BELL_CLAPPER.get());
                out.accept(ModItems.ECHO_SHARD.get());
                out.accept(ModItems.SILENCE.get());
                out.accept(ModItems.ECHO_HEART.get());
                out.accept(ModItems.GREAT_BELL_ROPE.get());
                for (int i = 1; i <= 7; i++) out.accept(TapeItem.create("tape" + i));
                for (Notes.Note n : Notes.all().values()) {
                    if (!n.id().startsWith("tape")) out.accept(NoteItem.create(n.id()));
                }
                out.accept(ModItems.RADIO_CONSOLE.get());
                out.accept(ModItems.SHARD_LOCK.get());
                out.accept(ModItems.POWER_SWITCH.get());
                out.accept(ModItems.CRAWLER_EGG.get());
                out.accept(ModItems.MIMIC_EGG.get());
                out.accept(ModItems.SILENT_EGG.get());
                out.accept(ModItems.BOSS_EGG.get());
            })
            .build());

    private ModTabs() {}
}
