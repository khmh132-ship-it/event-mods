package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.village.Village;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Finds a natural tree near the hut, walks there, fells it log by log, replants a sapling and carries the logs
 * to the warehouse (or the hut if there is none). While a lumberjack works, the hut's abstract production pauses.
 */
public class LumberjackWorkGoal extends Goal {
    private static final int SEARCH_RADIUS = 24;
    private static final int LOAD = 16;
    private static final int MAX_TREE = 48;

    private enum State { SEEK, WALK, CHOP, RETURN }

    private final VillageWorker worker;
    private final LongOpenHashSet unreachable = new LongOpenHashSet();
    private State state = State.SEEK;
    private BlockPos tree;
    private List<BlockPos> logs = new ArrayList<>();
    private int timer;
    private int cooldown;
    private String lastLog = "minecraft:oak_log";
    private final Tooling tooling;
    private int chopTicks;

    public LumberjackWorkGoal(VillageWorker worker) {
        this.worker = worker;
        this.tooling = new Tooling(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Works from his hut, or from the bell until the village has built him one.
        return worker.job() == WorkerJob.LUMBERJACK && worker.workplace().map(Building::isComplete).orElse(true);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        worker.getNavigation().stop();
        worker.resetUnloading();
        tooling.reset();
    }

    @Override
    public void tick() {
        Village village = worker.village().orElse(null);
        if (village == null) {
            return;
        }
        Building hut = worker.workplace().orElse(null);
        BlockPos home = hut != null ? hut.entrance() : village.center();
        Level level = worker.level();
        long now = level.getGameTime();
        timer++;
        worker.setStatus(state.name().toLowerCase() + " t" + timer);
        switch (state) {
            case SEEK -> {
                if (tooling.tick((net.minecraft.server.level.ServerLevel) level, village) == Tooling.Status.BUSY) {
                    return; // off to the warehouse for an axe
                }
                if (worker.carried() >= LOAD) {
                    go(State.RETURN);
                } else if (--cooldown <= 0) {
                    tree = findTree(level, village, home);
                    if (tree == null && level instanceof net.minecraft.server.level.ServerLevel sl) {
                        // Nothing near the hut: woods the cartographer has charted.
                        tree = Scouting.nearest(village, "trees", home, sl,
                                p -> sl.getBlockState(p).is(BlockTags.LOGS) && !unreachable.contains(p.asLong()));
                    }
                    if (tree == null) {
                        cooldown = 200;
                        if (worker.carried() > 0) {
                            go(State.RETURN);
                        }
                    } else {
                        go(State.WALK);
                    }
                }
            }
            case WALK -> {
                if (timer > 400) {
                    unreachable.add(tree.asLong());
                    go(State.SEEK);
                } else if (horizontalDistSqr(tree) <= 9 && Math.abs(worker.getY() - tree.getY()) < 3) {
                    logs = collectTree(level, village, tree);
                    worker.getNavigation().stop();
                    go(State.CHOP);
                } else if (timer % 40 == 1) {
                    worker.getNavigation().moveTo(tree.getX() + 0.5, tree.getY(), tree.getZ() + 0.5, 0.6);
                }
            }
            case CHOP -> {
                if (!logs.isEmpty()) {
                    BlockPos at = logs.get(logs.size() - 1);
                    worker.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
                    // Leaves between him and the log come off first, as they would for a player.
                    BlockPos leaf = leafInTheWay(level, at);
                    if (leaf != null) {
                        BlockState ls = level.getBlockState(leaf);
                        int needLeaf = tooling.breakTicks((net.minecraft.server.level.ServerLevel) level, leaf, ls);
                        level.destroyBlockProgress(worker.getId(), leaf, Math.min(9, chopTicks * 10 / Math.max(1, needLeaf)));
                        if (chopTicks % 6 == 0) {
                            worker.swing(InteractionHand.MAIN_HAND);
                        }
                        if (++chopTicks >= needLeaf) {
                            chopTicks = 0;
                            level.destroyBlockProgress(worker.getId(), leaf, -1);
                            level.destroyBlock(leaf, true, worker); // saplings and apples fall where they can be picked up
                        }
                        return;
                    }
                } else {
                    worker.getLookControl().setLookAt(tree.getX() + 0.5, tree.getY() + 1.5, tree.getZ() + 0.5);
                }
                if (hut != null) {
                    village.reportWorking(hut.id(), now);
                }
                if (logs.isEmpty()) {
                    replant(level, tree, lastLog);
                    go(worker.carried() >= LOAD ? State.RETURN : State.SEEK);
                    return;
                }
                BlockPos log = logs.get(logs.size() - 1);
                BlockState state = level.getBlockState(log);
                if (state.is(BlockTags.LOGS)) {
                    int need = tooling.breakTicks((net.minecraft.server.level.ServerLevel) level, log, state);
                    level.destroyBlockProgress(worker.getId(), log, Math.min(9, chopTicks * 10 / need));
                    if (chopTicks % 6 == 0) {
                        worker.swing(InteractionHand.MAIN_HAND);
                    }
                    if (++chopTicks < need) {
                        return;
                    }
                    chopTicks = 0;
                    level.destroyBlockProgress(worker.getId(), log, -1);
                    tooling.used();
                }
                logs.remove(logs.size() - 1);
                if (state.is(BlockTags.LOGS)) {
                    Item item = state.getBlock().asItem();
                    lastLog = BuiltInRegistries.ITEM.getKey(item).toString();
                    level.destroyBlock(log, false, worker);
                    worker.carry(item, 1);
                    worker.swing(InteractionHand.MAIN_HAND);
                }
            }
            case RETURN -> {
                if (hut != null) {
                    village.reportWorking(hut.id(), now);
                }
                if (worker.unloadTick()) {
                    go(State.SEEK);
                }
            }
        }
    }

    private void go(State next) {
        state = next;
        timer = 0;
    }

    /** The first leaf block on the line from his eyes to the log, if any. */
    private BlockPos leafInTheWay(Level level, BlockPos log) {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(worker.getEyePosition(), log.getCenter(),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, worker));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && !hit.getBlockPos().equals(log)
                && level.getBlockState(hit.getBlockPos()).is(BlockTags.LEAVES)) {
            return hit.getBlockPos();
        }
        return null;
    }

    private double horizontalDistSqr(BlockPos p) {
        double dx = worker.getX() - (p.getX() + 0.5), dz = worker.getZ() - (p.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    /** Nearest trunk base of a natural tree (logs on dirt with leaves on top) outside all village buildings. */
    private BlockPos findTree(Level level, Village village, BlockPos home) {
        // Near the hut first, then further and further out, as a player would go looking for woods.
        for (int r = SEARCH_RADIUS; r <= SEARCH_RADIUS * 4; r += SEARCH_RADIUS) {
            BlockPos found = findTree(level, village, home, r);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private BlockPos findTree(Level level, Village village, BlockPos home, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = home.getX() + dx, z = home.getZ() + dz;
                if (!level.hasChunkAt(pos.set(x, 0, z))) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (!level.getBlockState(pos.set(x, top, z)).is(BlockTags.LOGS)) {
                    continue;
                }
                int y = top;
                while (level.getBlockState(pos.set(x, y - 1, z)).is(BlockTags.LOGS)) {
                    y--;
                }
                BlockPos base = new BlockPos(x, y, z);
                if (!level.getBlockState(base.below()).is(BlockTags.DIRT) || unreachable.contains(base.asLong())
                        || inBuilding(village, base) || !hasLeaves(level, new BlockPos(x, top, z))) {
                    continue;
                }
                double d = base.distSqr(worker.blockPosition());
                if (d < bestDist) {
                    bestDist = d;
                    best = base;
                }
            }
        }
        return best;
    }

    private static boolean hasLeaves(Level level, BlockPos top) {
        for (BlockPos p : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2))) {
            if (level.getBlockState(p).is(BlockTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inBuilding(Village village, BlockPos pos) {
        for (Building b : village.buildings()) {
            BoundingBox box = b.box();
            if (box.inflatedBy(1).isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    /** All logs of the tree, lowest first, so they are chopped from the top down. */
    private static List<BlockPos> collectTree(Level level, Village village, BlockPos base) {
        List<BlockPos> out = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(base);
        seen.add(base.asLong());
        while (!queue.isEmpty() && out.size() < MAX_TREE) {
            BlockPos p = queue.poll();
            out.add(p);
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, 0, -1), p.offset(1, 1, 1))) {
                if (Math.abs(n.getX() - base.getX()) > 4 || Math.abs(n.getZ() - base.getZ()) > 4
                        || !seen.add(n.asLong())) {
                    continue;
                }
                if (level.getBlockState(n).is(BlockTags.LOGS) && !inBuilding(village, n)) {
                    queue.add(n.immutable());
                }
            }
        }
        out.sort(Comparator.comparingInt(BlockPos::getY));
        return out;
    }

    /** Plants a sapling of the felled species (oak if there is none) where the trunk stood. */
    private static void replant(Level level, BlockPos base, String logItem) {
        if (!level.getBlockState(base).isAir() || !level.getBlockState(base.below()).is(BlockTags.DIRT)) {
            return;
        }
        Block sapling = Blocks.OAK_SAPLING;
        ResourceLocation log = ResourceLocation.tryParse(logItem);
        if (log != null && log.getPath().endsWith("_log")) {
            ResourceLocation id = new ResourceLocation(log.getNamespace(),
                    log.getPath().substring(0, log.getPath().length() - 4) + "_sapling");
            Block candidate = BuiltInRegistries.BLOCK.get(id);
            if (candidate.defaultBlockState().is(BlockTags.SAPLINGS)) {
                sapling = candidate;
            }
        }
        level.setBlock(base, sapling.defaultBlockState(), Block.UPDATE_ALL);
    }
}
