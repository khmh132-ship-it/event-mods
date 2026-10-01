package com.khmh.livingvillages.building;

import com.khmh.livingvillages.config.LVConfig;
import com.khmh.livingvillages.village.Village;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Looks for a spot for a new building: rings around the bell from the inside out, on natural, fairly flat
 * ground that is loaded, inside the village and clear of other buildings, with the entrance turned towards the
 * bell.
 */
public final class SiteFinder {
    private static final int GAP = 2;
    private static final int PLAZA = 5;

    public record Site(BlockPos origin, Rotation rotation, int groundY, BoundingBox box, BlockPos entrance) {
    }

    private SiteFinder() {
    }

    public static Optional<Site> find(ServerLevel level, Village village, BuildingType type, TemplateData data) {
        BlockPos center = village.center();
        Vec3i size = data.size();
        int half = Math.max(size.getX(), size.getZ()) / 2;
        for (int r = PLAZA + half; r <= village.radius() - half; r += 3) {
            for (BlockPos c : ring(center, r)) {
                Optional<Site> site = tryAt(level, village, type, data, c);
                if (site.isPresent()) {
                    return site;
                }
            }
        }
        return Optional.empty();
    }

    /** Points on the square ring of half-size r around the center, spaced 3 apart, nearest first. */
    private static List<BlockPos> ring(BlockPos center, int r) {
        List<BlockPos> out = new ArrayList<>();
        for (int d = -r; d <= r; d += 3) {
            out.add(center.offset(d, 0, -r));
            out.add(center.offset(d, 0, r));
            out.add(center.offset(-r, 0, d));
            out.add(center.offset(r, 0, d));
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return out;
    }

    private static Optional<Site> tryAt(ServerLevel level, Village village, BuildingType type, TemplateData data,
                                        BlockPos target) {
        BlockPos center = village.center();
        List<Rotation> rotations = new ArrayList<>(List.of(Rotation.values()));
        // Entrance pointing at the bell first.
        rotations.sort(Comparator.comparingDouble(rot -> entranceAt(data, rot, target).distSqr(center)));
        for (Rotation rot : rotations) {
            BoundingBox zeroBox = Placement.box(BlockPos.ZERO, rot, data.size());
            int ox = target.getX() - (zeroBox.minX() + zeroBox.maxX()) / 2;
            int oz = target.getZ() - (zeroBox.minZ() + zeroBox.maxZ()) / 2;
            BoundingBox flat = Placement.box(new BlockPos(ox, 0, oz), rot, data.size());
            if (!insideVillage(village, flat) || overlaps(village, flat)) {
                continue;
            }
            if (!level.hasChunksAt(flat.minX() - 1, flat.minZ() - 1, flat.maxX() + 1, flat.maxZ() + 1)) {
                continue;
            }
            Integer ground = groundLevel(level, flat);
            if (ground == null) {
                continue;
            }
            BlockPos origin = new BlockPos(ox, Placement.originY(type, ground), oz);
            BoundingBox box = Placement.box(origin, rot, data.size());
            BlockPos entrance = Placement.toWorld(origin, rot, data.entrance());
            return Optional.of(new Site(origin, rot, ground, box, entrance));
        }
        return Optional.empty();
    }

    private static BlockPos entranceAt(TemplateData data, Rotation rot, BlockPos target) {
        BoundingBox zeroBox = Placement.box(BlockPos.ZERO, rot, data.size());
        BlockPos origin = new BlockPos(target.getX() - (zeroBox.minX() + zeroBox.maxX()) / 2, 0,
                target.getZ() - (zeroBox.minZ() + zeroBox.maxZ()) / 2);
        BlockPos e = Placement.toWorld(origin, rot, data.entrance());
        return new BlockPos(e.getX(), target.getY(), e.getZ());
    }

    private static boolean insideVillage(Village village, BoundingBox box) {
        BlockPos c = village.center();
        long r2 = (long) village.radius() * village.radius();
        for (int x : new int[]{box.minX(), box.maxX()}) {
            for (int z : new int[]{box.minZ(), box.maxZ()}) {
                long dx = x - c.getX(), dz = z - c.getZ();
                if (dx * dx + dz * dz > r2) {
                    return false;
                }
            }
        }
        // Keep the plaza around the bell free.
        return !(box.minX() <= c.getX() + PLAZA && box.maxX() >= c.getX() - PLAZA
                && box.minZ() <= c.getZ() + PLAZA && box.maxZ() >= c.getZ() - PLAZA);
    }

    private static boolean overlaps(Village village, BoundingBox box) {
        for (Building b : village.buildings()) {
            BoundingBox o = b.box();
            if (box.minX() <= o.maxX() + GAP && box.maxX() >= o.minX() - GAP
                    && box.minZ() <= o.maxZ() + GAP && box.maxZ() >= o.minZ() - GAP) {
                return true;
            }
            BlockPos e = b.entrance(); // keep doors reachable
            if (e.getX() >= box.minX() - 1 && e.getX() <= box.maxX() + 1
                    && e.getZ() >= box.minZ() - 1 && e.getZ() <= box.maxZ() + 1) {
                return true;
            }
        }
        return false;
    }

    /** Most common ground height under the footprint, or null if the ground is unsuitable. */
    private static Integer groundLevel(ServerLevel level, BoundingBox box) {
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        int maxSlope = LVConfig.MAX_SLOPE.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
            for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                int top = groundUnderTrees(level, pos.set(x, 0, z));
                if (!isNatural(level.getBlockState(pos.set(x, top, z)))) {
                    return null;
                }
                min = Math.min(min, top);
                max = Math.max(max, top);
                if (max - min > maxSlope) {
                    return null;
                }
                counts.addTo(top, 1);
            }
        }
        int best = min, bestCount = -1;
        for (Int2IntOpenHashMap.Entry e : counts.int2IntEntrySet()) {
            if (e.getIntValue() > bestCount) {
                best = e.getIntKey();
                bestCount = e.getIntValue();
            }
        }
        return best;
    }

    /** Height of the ground in a column, looking through tree trunks (they get felled while levelling). */
    public static int groundUnderTrees(ServerLevel level, BlockPos column) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.getX(), 0, column.getZ());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
        while (y > level.getMinBuildHeight()) {
            BlockState s = level.getBlockState(pos.setY(y));
            if (!s.getFluidState().isEmpty()
                    || !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.REPLACEABLE)) {
                break;
            }
            y--;
        }
        return y;
    }

    /** Untouched ground: no paths, farmland, buildings, water or trees. */
    public static boolean isNatural(BlockState s) {
        return s.getFluidState().isEmpty() && (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.CLAY) || s.is(BlockTags.TERRACOTTA));
    }
}
