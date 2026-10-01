package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.SiteFinder;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The miner digs real tunnels with real drops. From a mine he starts at the bottom of the shaft (climbing down
 * the ladder); a village without a mine gets a quarry instead: he digs his own stairway down near the edge of
 * the village and tunnels from its foot. Tunnels: a main corridor with side branches every few blocks, floor
 * torches, never next to water or lava, never through anything that is not natural ground. Dirt is left
 * behind; stone, ores, coal, sand and the like are carried to the warehouse. Progress is stored with the mine
 * (or the village, for the quarry), so the tunnels continue where they left off.
 */
public class MinerWorkGoal extends Goal {
    private static final int LOAD = 64;
    private static final int STAIRS = 10;
    private static final int MAIN = 48;
    private static final int BRANCH_EVERY = 6;
    private static final int BRANCH_LEN = 8;
    private static final int ROUNDS = 4;
    private static final List<Item> RAW_ORES = List.of(Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD);

    private enum State { TO_MINE, DIG, SMELT, RETURN }

    private record Cell(BlockPos pos, BlockPos from, int segment, int height) {
    }

    private final VillageWorker worker;
    private State state = State.TO_MINE;
    private int timer;
    private int stuck;
    private List<Cell> plan;
    private int planRound = -1;
    private BlockPos digging;
    private int digTicks;
    private final Tooling tooling;

    public MinerWorkGoal(VillageWorker worker) {
        this.worker = worker;
        this.tooling = new Tooling(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Works in his mine, or in a quarry of his own until the village has built one.
        return worker.job() == WorkerJob.MINER && worker.workplace().map(Building::isComplete).orElse(true)
                && worker.village().isPresent();
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
        resetDig();
    }

    @Nullable
    private Building mine() {
        return worker.workplace().orElse(null);
    }

    /** Where the tunnel progress is kept. */
    private CompoundTag data(Village village) {
        Building mine = mine();
        if (mine != null) {
            return mine.data();
        }
        if (!village.data().contains("quarry")) {
            village.data().put("quarry", new CompoundTag());
        }
        return village.data().getCompound("quarry");
    }

    @Override
    public void tick() {
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        timer++;
        worker.setStatus(state.name().toLowerCase() + " t" + timer);
        Building mine = mine();
        if (mine != null) {
            village.reportWorking(mine.id(), level.getGameTime());
        }
        switch (state) {
            case TO_MINE -> {
                if (worker.carried() >= LOAD || worker.inventoryFull()) {
                    go(State.SMELT);
                    return;
                }
                if (tooling.tick(level, village) == Tooling.Status.BUSY) {
                    return; // off to the warehouse for a pickaxe
                }
                Cell c = current(level, village);
                if (c == null) {
                    return; // exhausted
                }
                if (mine == null) {
                    go(State.DIG); // the quarry is walked into
                    return;
                }
                BlockPos door = VillageWorker.standAt(level, mine.entrance());
                if (horizontalDistSqr(door) <= 9 || timer > 400) {
                    BlockPos in = level.getBlockState(c.from()).isAir() ? c.from() : start(level, village);
                    worker.teleportTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5); // down the ladder
                    go(State.DIG);
                } else if (timer % 40 == 1) {
                    worker.getNavigation().moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 0.6);
                }
            }
            case DIG -> dig(level, village);
            case SMELT -> smelt(level, village);
            case RETURN -> {
                if (worker.unloadTick()) {
                    go(State.TO_MINE);
                }
            }
        }
    }

    private void dig(ServerLevel level, Village village) {
        Cell c = current(level, village);
        if (c == null || worker.carried() >= LOAD || worker.inventoryFull()) {
            if (mine() != null) {
                BlockPos out = VillageWorker.standAt(level, mine().entrance());
                worker.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5); // up the ladder
            }
            resetDig();
            go(State.SMELT);
            return;
        }
        if (!level.hasChunkAt(c.pos())) {
            return;
        }
        if (tooling.tick(level, village) == Tooling.Status.BUSY) {
            resetDig();
            return; // gone to the warehouse for a pickaxe
        }
        double dx = worker.getX() - (c.pos().getX() + 0.5), dz = worker.getZ() - (c.pos().getZ() + 0.5);
        if (dx * dx + dz * dz > 2.9 * 2.9 || Math.abs(worker.getY() - c.pos().getY()) > 2.5) {
            resetDig();
            if (++stuck > 160) {
                BlockPos to = level.getBlockState(c.from()).isAir() ? c.from() : start(level, village);
                worker.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
                stuck = 0;
            } else if (stuck % 20 == 1) {
                worker.getNavigation().moveTo(c.from().getX() + 0.5, c.from().getY(), c.from().getZ() + 0.5, 0.6);
            }
            return;
        }
        stuck = 0;
        worker.getNavigation().stop();
        BlockPos block = null;
        for (int h = c.height() - 1; h >= 0; h--) {
            if (!level.getBlockState(c.pos().above(h)).isAir()) {
                block = c.pos().above(h); // top down, so nothing falls on his head
                break;
            }
        }
        if (block == null) {
            finishCell(level, village, c);
            return;
        }
        BlockState state = level.getBlockState(block);
        worker.getLookControl().setLookAt(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        if (state.is(Blocks.TORCH) || state.is(BlockTags.REPLACEABLE) && state.getFluidState().isEmpty()) {
            level.destroyBlock(block, false, worker); // grass, snow, a stray torch: just clear it
            return;
        }
        if (!diggable(level, block, state)) {
            skipSegment(level, village, c);
            village.markDirty();
            return;
        }
        if (!tooling.canHarvest(state)) {
            resetDig();
            return; // no pickaxe for stone: wait for one (it has been asked for)
        }
        if (!block.equals(digging)) {
            resetDig();
            digging = block;
        }
        int need = tooling.breakTicks(level, block, state);
        digTicks++;
        level.destroyBlockProgress(worker.getId(), block, Math.min(9, digTicks * 10 / need));
        if (digTicks % 5 == 0) {
            worker.swing(InteractionHand.MAIN_HAND);
        }
        if (digTicks >= need) {
            boolean worthCarrying = !state.is(BlockTags.DIRT);
            if (worthCarrying) {
                for (ItemStack drop : Block.getDrops(state, level, block, level.getBlockEntity(block), worker,
                        worker.getMainHandItem())) {
                    worker.carry(drop.getItem(), drop.getCount());
                }
            }
            level.destroyBlock(block, false, worker);
            tooling.used();
            resetDig();
        }
    }

    private void finishCell(ServerLevel level, Village village, Cell c) {
        CompoundTag data = data(village);
        int index = data.getInt("index");
        if (index % 8 == 0 && c.height() == 2) {
            BlockPos at = c.pos();
            if (level.getBlockState(at.below()).isSolidRender(level, at.below())) {
                level.setBlock(at, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL); // floor torch
            }
        }
        data.putInt("index", index + 1);
        village.markDirty();
    }

    private void skipSegment(ServerLevel level, Village village, Cell blocked) {
        CompoundTag data = data(village);
        int index = data.getInt("index");
        if (blocked.segment() <= 0) {
            index = plan.size(); // the stairway or main tunnel is blocked: this direction is done
        } else {
            while (index < plan.size() && plan.get(index).segment() == blocked.segment()) {
                index++;
            }
        }
        data.putInt("index", index);
    }

    /** Only natural ground is dug, and never next to water or lava. */
    private static boolean diggable(ServerLevel level, BlockPos pos, BlockState state) {
        if (pos.getY() <= level.getMinBuildHeight() + 5 || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        if (!(state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.ORES) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.CALCITE) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.SNOW_BLOCK))) {
            return false;
        }
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(pos.relative(d)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The cell to dig next, or null once every round is done. */
    @Nullable
    private Cell current(ServerLevel level, Village village) {
        CompoundTag data = data(village);
        while (true) {
            int round = data.getInt("round");
            if (round >= ROUNDS) {
                return null;
            }
            if (plan == null || planRound != round) {
                BlockPos start = start(level, village);
                if (start == null) {
                    return null;
                }
                plan = buildPlan(start, direction(level, village), round, mine() == null);
                planRound = round;
            }
            int index = data.getInt("index");
            if (index < plan.size()) {
                return plan.get(index);
            }
            data.putInt("round", round + 1);
            data.putInt("index", 0);
        }
    }

    /**
     * Round 0 of a quarry begins with a stairway down (3 high so he can walk it); later rounds tunnel from its
     * foot in the other directions. A mine's rounds all start at the bottom of its shaft.
     */
    private static List<Cell> buildPlan(BlockPos start, Direction dir, int round, boolean quarry) {
        List<Cell> cells = new ArrayList<>();
        BlockPos base = start;
        if (quarry) {
            base = start.relative(dir, STAIRS).below(STAIRS);
            if (round == 0) {
                BlockPos prev = start;
                for (int i = 1; i <= STAIRS; i++) {
                    BlockPos p = start.relative(dir, i).below(i);
                    cells.add(new Cell(p, prev, -1, 3));
                    prev = p;
                }
            }
        }
        for (int i = 0; i < round; i++) {
            dir = dir.getClockWise();
        }
        int segment = 0;
        BlockPos prev = base;
        for (int m = 0; m < MAIN; m++) {
            BlockPos p = base.relative(dir, m + 1);
            cells.add(new Cell(p, prev, 0, 2));
            prev = p;
            if (m >= 4 && (m - 4) % BRANCH_EVERY == 0) {
                for (Direction side : new Direction[]{dir.getCounterClockWise(), dir.getClockWise()}) {
                    segment++;
                    BlockPos from = p;
                    for (int k = 1; k <= BRANCH_LEN; k++) {
                        BlockPos q = p.relative(side, k);
                        cells.add(new Cell(q, from, segment, 2));
                        from = q;
                    }
                }
            }
        }
        return cells;
    }

    @Nullable
    private BlockPos start(ServerLevel level, Village village) {
        Building mine = mine();
        if (mine != null) {
            return chamber(level, mine);
        }
        CompoundTag q = data(village);
        if (!q.contains("x")) {
            BlockPos spot = quarrySpot(level, village);
            if (spot == null) {
                return null;
            }
            q.putInt("x", spot.getX());
            q.putInt("y", spot.getY());
            q.putInt("z", spot.getZ());
            village.markDirty();
        }
        return new BlockPos(q.getInt("x"), q.getInt("y"), q.getInt("z"));
    }

    private Direction direction(ServerLevel level, Village village) {
        Building mine = mine();
        if (mine != null) {
            BoundingBox box = mine.box();
            int dx = (box.minX() + box.maxX()) / 2 - mine.entrance().getX();
            int dz = (box.minZ() + box.maxZ()) / 2 - mine.entrance().getZ();
            if (Math.abs(dx) >= Math.abs(dz)) {
                return dx >= 0 ? Direction.EAST : Direction.WEST;
            }
            return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
        }
        BlockPos s = start(level, village);
        int dx = s.getX() - village.center().getX(), dz = s.getZ() - village.center().getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST; // away from the bell
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Natural ground towards the edge of the village, clear of buildings, for the top of the stairway. */
    @Nullable
    private static BlockPos quarrySpot(ServerLevel level, Village village) {
        BlockPos c = village.center();
        int r = Math.max(16, village.radius() - STAIRS - 8);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int side = -8; side <= 8; side += 4) {
                BlockPos col = c.relative(d, r).relative(d.getClockWise(), side);
                if (!level.hasChunkAt(col)) {
                    continue;
                }
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
                BlockPos ground = top.below();
                boolean clear = village.buildings().stream().noneMatch(b -> b.box().inflatedBy(STAIRS + 2)
                        .isInside(ground));
                if (clear && SiteFinder.isNatural(level.getBlockState(ground))) {
                    return top;
                }
            }
        }
        return null;
    }

    /** Lowest spot inside the mine where one can stand: the bottom of the shaft. */
    private static BlockPos chamber(ServerLevel level, Building mine) {
        BoundingBox box = mine.box();
        int cx = (box.minX() + box.maxX()) / 2, cz = (box.minZ() + box.maxZ()) / 2;
        for (int y = box.minY() + 1; y < mine.groundY(); y++) {
            BlockPos best = null;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                            && level.getBlockState(p.below()).isSolidRender(level, p.below())) {
                        BlockPos mid = new BlockPos(cx, y, cz);
                        if (best == null || p.distSqr(mid) < best.distSqr(mid)) {
                            best = p;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return new BlockPos(cx, mine.groundY() - 4, cz); // no chamber: start digging from under the mine
    }

    /**
     * On the way up the miner takes his raw ore to a furnace (the mine's own, or the village's), collects what
     * finished smelting since last time, puts the new ore in and feeds it with coal from his load.
     */
    private void smelt(ServerLevel level, Village village) {
        Item ore = RAW_ORES.stream().filter(o -> worker.getInventory().countItem(o) > 0).findFirst().orElse(null);
        BlockPos at = ore == null ? null : furnace(level, village);
        if (at == null) {
            go(State.RETURN);
            return;
        }
        if (worker.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) > 6.25) {
            if (timer > 400) {
                go(State.RETURN);
            } else if (timer % 20 == 1) {
                worker.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.6);
            }
            return;
        }
        worker.getNavigation().stop();
        if (level.getBlockEntity(at) instanceof AbstractFurnaceBlockEntity furnace) {
            SimpleContainer inv = worker.getInventory();
            ItemStack out = furnace.getItem(2);
            if (!out.isEmpty()) {
                worker.carry(out.getItem(), out.getCount());
                furnace.setItem(2, ItemStack.EMPTY);
            }
            ItemStack in = furnace.getItem(0);
            if (in.isEmpty() || in.is(ore)) {
                int n = Math.min(inv.countItem(ore), 64 - in.getCount());
                inv.removeItemType(ore, n);
                furnace.setItem(0, new ItemStack(ore, in.getCount() + n));
                ItemStack fuel = furnace.getItem(1);
                int coal = Math.min(inv.countItem(Items.COAL), (n + 7) / 8);
                if (coal > 0 && (fuel.isEmpty() || fuel.is(Items.COAL)) && fuel.getCount() + coal <= 64) {
                    inv.removeItemType(Items.COAL, coal);
                    furnace.setItem(1, new ItemStack(Items.COAL, fuel.getCount() + coal));
                }
            }
            furnace.setChanged();
        }
        go(State.RETURN);
    }

    @Nullable
    private BlockPos furnace(ServerLevel level, Village village) {
        Building mine = mine();
        if (mine != null) {
            BoundingBox b = mine.box();
            BlockPos own = BlockPos.betweenClosedStream(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())
                    .filter(p -> level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity)
                    .map(BlockPos::immutable).findFirst().orElse(null);
            if (own != null) {
                return own;
            }
        }
        return Stations.find(level, village, com.khmh.livingvillages.economy.CraftPlanner.Station.FURNACE,
                worker.blockPosition());
    }

    private void resetDig() {
        if (digging != null) {
            worker.level().destroyBlockProgress(worker.getId(), digging, -1);
        }
        digging = null;
        digTicks = 0;
    }

    private void go(State next) {
        state = next;
        timer = 0;
        stuck = 0;
    }

    private double horizontalDistSqr(BlockPos p) {
        double dx = worker.getX() - (p.getX() + 0.5), dz = worker.getZ() - (p.getZ() + 0.5);
        return dx * dx + dz * dz;
    }
}
