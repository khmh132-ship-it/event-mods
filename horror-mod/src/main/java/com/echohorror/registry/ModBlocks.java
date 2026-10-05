package com.echohorror.registry;

import com.echohorror.EchoHorror;
import com.echohorror.block.PowerSwitchBlock;
import com.echohorror.block.RadioConsoleBlock;
import com.echohorror.block.ShardLockBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EchoHorror.MODID);

    private static BlockBehaviour.Properties story() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F)
                .sound(SoundType.METAL).noLootTable().requiresCorrectToolForDrops();
    }

    public static final RegistryObject<Block> RADIO_CONSOLE = BLOCKS.register("radio_console",
            () -> new RadioConsoleBlock(story().lightLevel(s -> 3)));
    public static final RegistryObject<Block> SHARD_LOCK = BLOCKS.register("shard_lock",
            () -> new ShardLockBlock(story().lightLevel(s -> s.getValue(ShardLockBlock.SHARDS) * 3)));
    public static final RegistryObject<Block> POWER_SWITCH = BLOCKS.register("power_switch",
            () -> new PowerSwitchBlock(story().lightLevel(s -> s.getValue(PowerSwitchBlock.ON) ? 7 : 0)));

    public static final RegistryObject<Block> GREAT_BELL_ROPE = BLOCKS.register("great_bell_rope",
            () -> new com.echohorror.block.BellRopeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(-1.0F, 3600000.0F).sound(SoundType.WOOL).noLootTable().noOcclusion().lightLevel(s -> 6)));

    private ModBlocks() {}
}
