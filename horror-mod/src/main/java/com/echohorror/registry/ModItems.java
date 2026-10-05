package com.echohorror.registry;

import com.echohorror.EchoHorror;
import com.echohorror.item.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EchoHorror.MODID);

    public static final RegistryObject<Item> JOURNAL = ITEMS.register("journal",
            () -> new JournalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LOCATOR = ITEMS.register("locator",
            () -> new LocatorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FLASHLIGHT = ITEMS.register("flashlight",
            () -> new FlashlightItem(new Item.Properties().stacksTo(1).durability(FlashlightItem.CAPACITY)));
    public static final RegistryObject<Item> BATTERY = ITEMS.register("battery",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> NOTE = ITEMS.register("note",
            () -> new NoteItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TAPE = ITEMS.register("tape",
            () -> new TapeItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PILLS = ITEMS.register("pills",
            () -> new PillsItem(new Item.Properties().stacksTo(8)));
    public static final RegistryObject<Item> BELL_CLAPPER = ITEMS.register("bell_clapper",
            () -> new LoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), true,
                    "Тяжёлый. Тёплый, будто его только что держали в руке.",
                    "Повесьте его обратно в колокол Тихого Лога."));
    public static final RegistryObject<Item> ECHO_SHARD = ITEMS.register("echo_shard",
            () -> new LoreItem(new Item.Properties().stacksTo(3).rarity(Rarity.EPIC).fireResistant(), true,
                    "Осколок старого колокола. Тихо гудит.",
                    "Если прижать к уху — слышно, как кто-то зовёт тебя по имени."));
    public static final RegistryObject<Item> SILENCE = ITEMS.register("silence",
            () -> new SilenceItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> ECHO_HEART = ITEMS.register("echo_heart",
            () -> new EchoHeartItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> GREAT_BELL_ROPE = ITEMS.register("great_bell_rope",
            () -> new BlockItem(ModBlocks.GREAT_BELL_ROPE.get(), new Item.Properties()));

    public static final RegistryObject<Item> RADIO_CONSOLE = ITEMS.register("radio_console",
            () -> new BlockItem(ModBlocks.RADIO_CONSOLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SHARD_LOCK = ITEMS.register("shard_lock",
            () -> new BlockItem(ModBlocks.SHARD_LOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> POWER_SWITCH = ITEMS.register("power_switch",
            () -> new BlockItem(ModBlocks.POWER_SWITCH.get(), new Item.Properties()));

    public static final RegistryObject<Item> CRAWLER_EGG = ITEMS.register("crawler_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CRAWLER, 0xA8AAA0, 0x461E1A, new Item.Properties()));
    public static final RegistryObject<Item> MIMIC_EGG = ITEMS.register("mimic_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.MIMIC, 0x009696, 0x780F0F, new Item.Properties()));
    public static final RegistryObject<Item> SILENT_EGG = ITEMS.register("silent_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SILENT, 0x969691, 0x0A090A, new Item.Properties()));
    public static final RegistryObject<Item> BOSS_EGG = ITEMS.register("echo_boss_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.ECHO_BOSS, 0x0A080C, 0xE6E1D7, new Item.Properties()));

    private ModItems() {}
}
