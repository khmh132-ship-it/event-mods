package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/** What the cartographer has charted around the village: trees, sand, clay, sugar cane. */
final class Scouting {
    private static final int MAX_PER_KIND = 64;

    private Scouting() {
    }

    private static ListTag list(Village v, String kind) {
        CompoundTag map = v.data().getCompound("map");
        if (!v.data().contains("map")) {
            v.data().put("map", map);
        }
        if (!map.contains(kind)) {
            map.put(kind, new ListTag());
        }
        return map.getList(kind, Tag.TAG_LONG);
    }

    static void mark(Village v, String kind, BlockPos pos) {
        ListTag list = list(v, kind);
        LongTag tag = LongTag.valueOf(pos.asLong());
        for (Tag t : list) {
            if (BlockPos.of(((LongTag) t).getAsLong()).distSqr(pos) < 36) {
                return; // already charted nearby
            }
        }
        list.add(tag);
        if (list.size() > MAX_PER_KIND) {
            list.remove(0);
        }
        v.markDirty();
    }

    /** Nearest charted spot of a kind still matching {@code still}; stale entries are dropped on the way. */
    @Nullable
    static BlockPos nearest(Village v, String kind, BlockPos from, ServerLevel level, Predicate<BlockPos> still) {
        ListTag list = list(v, kind);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = list.size() - 1; i >= 0; i--) {
            BlockPos p = BlockPos.of(((LongTag) list.get(i)).getAsLong());
            if (!level.hasChunkAt(p)) {
                continue;
            }
            if (!still.test(p)) {
                list.remove(i);
                continue;
            }
            double d = p.distSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
