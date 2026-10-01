package com.khmh.livingvillages.block;

import com.khmh.livingvillages.LivingVillages;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Job blocks: the workplace of each village job, placed inside its building. */
public final class LVBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, LivingVillages.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LivingVillages.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LivingVillages.MODID);

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD);
    }

    /** Builder: workbench with a blueprint on it. */
    public static final RegistryObject<Block> BUILDERS_TABLE = block("builders_table",
            () -> new JobBlock(wood().noOcclusion(), Block.box(0, 0, 0, 16, 16, 16)));
    /** Lumberjack: tree stump with an axe in it. */
    public static final RegistryObject<Block> CHOPPING_BLOCK = block("chopping_block",
            () -> new JobBlock(wood().noOcclusion(), Block.box(2, 0, 2, 14, 10, 14)));
    /** Miner: stone counter with a pickaxe. */
    public static final RegistryObject<Block> MINERS_BENCH = block("miners_bench",
            () -> new JobBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion(), Block.box(0, 0, 0, 16, 14, 16)));
    /** Guard: rack with a sword and a bow. */
    public static final RegistryObject<Block> WEAPON_RACK = block("weapon_rack",
            () -> new JobBlock(wood().noOcclusion(), Map.of(
                    Direction.NORTH, Block.box(0, 0, 12, 16, 16, 16),
                    Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 4),
                    Direction.EAST, Block.box(0, 0, 0, 4, 16, 16),
                    Direction.WEST, Block.box(12, 0, 0, 16, 16, 16))));

    public static final List<RegistryObject<Block>> ALL =
            List.of(BUILDERS_TABLE, CHOPPING_BLOCK, MINERS_BENCH, WEAPON_RACK);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.livingvillages"))
            .icon(() -> new ItemStack(BUILDERS_TABLE.get()))
            .displayItems((params, output) -> ALL.forEach(b -> output.accept(b.get())))
            .build());

    private LVBlocks() {
    }

    private static RegistryObject<Block> block(String name, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(name, factory);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
