package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * The miner climbs down the mine shaft and digs real branch tunnels from the bottom chamber: a main tunnel with
 * side branches every few blocks, two blocks high. Everything dug drops what an iron pickaxe would get; ores
 * are smelted when the load is handed in. Tunnels stop short of water and lava and never cut into anything that
 * is not natural ground. Progress is stored with the mine, so the tunnels continue where they left off.
 */
public class MinerWorkGoal extends Goal {
    private static final int LOAD = 64;
    private static final int MAIN = 48;
    private static final int BRANCH_EVERY = 6;
    private static final int BRANCH_LEN = 8;
    private static final int ROUNDS = 4;
    private static final ItemStack PICK = new ItemStack(Items.IRON_PICKAXE);

    private enum State { TO_MINE, DIG, RETURN }

    private record Cell(BlockPos pos, BlockPos from, int segment) {
    }

    private final VillageWorker worker;
    private State state = State.TO_MINE;
    private int timer;
    private int stuck;
    private List<Cell> plan;
    private int planRound = -1;
    private BlockPos digging;
    private int digTicks;

    public MinerWorkGoal(VillageWorker worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return worker.job() == WorkerJob.MINER && worker.workplace().map(Building::isComplete).orElse(false);
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
        resetDig();
    }

    @Override
    public void tick() {
        Village village = worker.village().orElse(null);
        Building mine = worker.workplace().orElse(null);
        if (village == null || mine == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        timer++;
        switch (state) {
            case TO_MINE -> {
                BlockPos door = VillageWorker.standAt(level, mine.entrance());
                if (worker.carried() >= LOAD) {
                    go(State.RETURN);
                } else if (horizontalDistSqr(door) <= 9 || timer > 400) {
                    Cell c = current(level, mine);
                    if (c == null) {
                        return; // mine exhausted
                    }
                    BlockPos in = level.getBlockState(c.from()).isAir() ? c.from() : chamber(level, mine);
                    worker.teleportTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5); // down the ladder
                    go(State.DIG);
                } else if (timer % 40 == 1) {
                    worker.getNavigation().moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 0.6);
                }
            }
            case DIG -> dig(level, village, mine);
            case RETURN -> {
                village.reportWorking(mine.id(), level.getGameTime());
                BlockPos drop = VillageWorker.standAt(level, depositPoint(village, mine));
                if (horizontalDistSqr(drop) <= 9 || timer > 600) {
                    handIn(village);
                    go(State.TO_MINE);
                } else if (timer % 40 == 1) {
                    worker.getNavigation().moveTo(drop.getX() + 0.5, drop.getY(), drop.getZ() + 0.5, 0.6);
                }
            }
        }
    }

    private void dig(ServerLevel level, Village village, Building mine) {
        village.reportWorking(mine.id(), level.getGameTime());
        Cell c = current(level, mine);
        if (c == null || worker.carried() >= LOAD) {
            leaveMine(level, mine);
            go(State.RETURN);
            return;
        }
        if (!level.hasChunkAt(c.pos())) {
            return;
        }
        double dx = worker.getX() - (c.pos().getX() + 0.5), dz = worker.getZ() - (c.pos().getZ() + 0.5);
        if (dx * dx + dz * dz > 2.9 * 2.9 || Math.abs(worker.getY() - c.pos().getY()) > 1.5) {
            resetDig();
            if (++stuck > 120) {
                BlockPos to = level.getBlockState(c.from()).isAir() ? c.from() : chamber(level, mine);
                worker.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
                stuck = 0;
            } else if (stuck % 20 == 1) {
                worker.getNavigation().moveTo(c.from().getX() + 0.5, c.from().getY(), c.from().getZ() + 0.5, 0.6);
            }
            return;
        }
        stuck = 0;
        worker.getNavigation().stop();
        BlockPos block = !level.getBlockState(c.pos().above()).isAir() ? c.pos().above() : c.pos();
        BlockState state = level.getBlockState(block);
        worker.getLookControl().setLookAt(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        if (state.isAir()) {
            finishCell(level, village, mine, c);
            return;
        }
        if (!diggable(level, block, state)) {
            skipSegment(mine, c);
            village.markDirty();
            return;
        }
        if (!block.equals(digging)) {
            resetDig();
            digging = block;
        }
        int need = Math.max(4, Math.min(60, (int) (state.getDestroySpeed(level, block) * 12)));
        digTicks++;
        level.destroyBlockProgress(worker.getId(), block, Math.min(9, digTicks * 10 / need));
        if (digTicks % 5 == 0) {
            worker.swing(InteractionHand.MAIN_HAND);
        }
        if (digTicks >= need) {
            for (ItemStack drop : Block.getDrops(state, level, block, level.getBlockEntity(block), worker, PICK)) {
                worker.carry(drop.getItem(), drop.getCount());
            }
            level.destroyBlock(block, false, worker);
            resetDig();
        }
    }

    private void finishCell(ServerLevel level, Village village, Building mine, Cell c) {
        int index = mine.data().getInt("index");
        if (index % 8 == 0) {
            torch(level, c);
        }
        mine.data().putInt("index", index + 1);
        village.markDirty();
    }

    /** A torch on the tunnel floor: unlike one on the wall it cannot lose its support to a later branch. */
    private void torch(ServerLevel level, Cell c) {
        BlockPos at = c.pos();
        if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isSolidRender(level, at.below())) {
            level.setBlock(at, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private void skipSegment(Building mine, Cell blocked) {
        CompoundTag data = mine.data();
        int index = data.getInt("index");
        if (blocked.segment() == 0) {
            index = plan.size(); // main tunnel blocked: this direction is done
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
                || state.is(Blocks.MOSSY_COBBLESTONE))) {
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
    private Cell current(ServerLevel level, Building mine) {
        CompoundTag data = mine.data();
        while (true) {
            int round = data.getInt("round");
            if (round >= ROUNDS) {
                return null;
            }
            if (plan == null || planRound != round) {
                plan = buildPlan(level, mine, round);
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

    private List<Cell> buildPlan(ServerLevel level, Building mine, int round) {
        BlockPos chamber = chamber(level, mine);
        Direction dir = awayFromVillage(mine, chamber);
        for (int i = 0; i < round; i++) {
            dir = dir.getClockWise();
        }
        List<Cell> cells = new ArrayList<>();
        int segment = 0;
        BlockPos prev = chamber;
        for (int m = 0; m < MAIN; m++) {
            BlockPos p = chamber.relative(dir, m + 1);
            cells.add(new Cell(p, prev, 0));
            prev = p;
            if (m >= 4 && (m - 4) % BRANCH_EVERY == 0) {
                for (Direction side : new Direction[]{dir.getCounterClockWise(), dir.getClockWise()}) {
                    segment++;
                    BlockPos from = p;
                    for (int k = 1; k <= BRANCH_LEN; k++) {
                        BlockPos q = p.relative(side, k);
                        cells.add(new Cell(q, from, segment));
                        from = q;
                    }
                }
            }
        }
        return cells;
    }

    private static Direction awayFromVillage(Building mine, BlockPos chamber) {
        BoundingBox box = mine.box();
        BlockPos center = new BlockPos((box.minX() + box.maxX()) / 2, chamber.getY(), (box.minZ() + box.maxZ()) / 2);
        BlockPos door = mine.entrance();
        int dx = center.getX() - door.getX(), dz = center.getZ() - door.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
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
                        if (best == null || p.distSqr(new BlockPos(cx, y, cz)) < best.distSqr(new BlockPos(cx, y, cz))) {
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

    private void leaveMine(ServerLevel level, Building mine) {
        BlockPos out = VillageWorker.standAt(level, mine.entrance());
        worker.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5); // up the ladder
        resetDig();
    }

    private BlockPos depositPoint(Village village, Building mine) {
        return village.buildings().stream()
                .filter(b -> b.isComplete() && b.typeId().equals("warehouse"))
                .map(Building::entrance)
                .min(Comparator.comparingDouble(e -> e.distSqr(worker.blockPosition())))
                .orElse(mine.entrance());
    }

    /** Smelts the ores at the mine's furnace, hands the load in and grinds a little cobblestone into sand. */
    private void handIn(Village village) {
        worker.convertCarried(Items.RAW_IRON, Items.IRON_INGOT);
        worker.convertCarried(Items.RAW_COPPER, Items.COPPER_INGOT);
        worker.convertCarried(Items.RAW_GOLD, Items.GOLD_INGOT);
        worker.deposit(village);
        if (village.storage().count(Items.SAND) < 32) {
            int sand = Math.min(4, village.storage().count(Items.COBBLESTONE) / 4);
            if (sand > 0) {
                village.storage().take(Items.COBBLESTONE, sand * 4);
                village.storage().add(Items.SAND, sand);
            }
        }
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
