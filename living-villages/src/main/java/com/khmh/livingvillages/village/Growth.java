package com.khmh.livingvillages.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The game only lets plants grow within a hundred-odd blocks of a player. A village kept loaded while nobody is
 * about would have fields that never ripen and saplings that never come up, so its own fields and the saplings
 * its lumberjack planted get the same random ticks the game gives them when a player is near (and only then not:
 * no double growth).
 */
public final class Growth {
    private static final int EVERY = 20;
    private static final int MAX_SAPLINGS = 128;

    private Growth() {
    }

    /** The lumberjack put a sapling in here: it grows with the village. */
    public static void noteSapling(Village v, BlockPos pos) {
        var list = v.data().getList("saplings", Tag.TAG_LONG);
        if (list.size() < MAX_SAPLINGS) {
            list.add(LongTag.valueOf(pos.asLong()));
            v.data().put("saplings", list);
        }
    }

    static void tick(ServerLevel level, Village v, long now) {
        if (now % EVERY != 0 || playerNear(level, v.center())) {
            return;
        }
        int speed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (speed <= 0) {
            return;
        }
        // A block gets a random tick with odds speed/4096 per game tick; this runs every EVERY ticks.
        float chance = Math.min(1.0F, EVERY * speed / 4096.0F);
        var random = level.getRandom();
        for (com.khmh.livingvillages.building.Building b : v.buildings()) {
            var type = com.khmh.livingvillages.building.BuildingTypes.get(b.typeId());
            if (type == null || !"farm".equals(type.group()) || !b.isComplete()) {
                continue;
            }
            var box = b.box();
            if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                continue;
            }
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                BlockState s = level.getBlockState(p);
                if ((s.getBlock() instanceof CropBlock || s.getBlock() instanceof StemBlock
                        || s.getBlock() instanceof SweetBerryBushBlock) && random.nextFloat() < chance) {
                    s.randomTick(level, p.immutable(), random);
                }
            }
        }
        var list = v.data().getList("saplings", Tag.TAG_LONG);
        for (int i = list.size() - 1; i >= 0; i--) {
            BlockPos p = BlockPos.of(((LongTag) list.get(i)).getAsLong());
            if (!level.hasChunkAt(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (!s.is(BlockTags.SAPLINGS)) {
                list.remove(i); // grown into a tree, or gone
            } else if (random.nextFloat() < chance && s.getBlock() instanceof BonemealableBlock) {
                s.randomTick(level, p, random);
            }
        }
        v.data().put("saplings", list);
    }

    /** Within the distance at which the game itself grows plants for a player. */
    private static boolean playerNear(ServerLevel level, BlockPos pos) {
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - pos.getX(), dz = p.getZ() - pos.getZ();
            if (dx * dx + dz * dz < 144 * 144 && !p.isSpectator()) {
                return true;
            }
        }
        return false;
    }
}
