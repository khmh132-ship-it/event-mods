package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.SiteFinder;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The miner works a real mine, the way a player would. He walks down the stairs of the mine to the chamber at
 * the bottom and from there drives 3x3 corridors into the rock, abandoned-mineshaft style: every few blocks a
 * support (two fence posts on each side, a plank beam across the top, a torch on either face of the beam's
 * middle). Corridors have side branches; at the end of each level's first corridor a three-wide staircase goes
 * down to the next level, and so on down to y=0.
 * <p>
 * Everything he passes is looked at: ore showing in a wall, floor or ceiling is followed and the whole vein is
 * dug out; breaking into a cave or an old mineshaft he lights it up and leaves its timbers alone. Lava or water
 * ahead, or a monster in sight, and he gives that corridor up, goes back and carries on somewhere else. Holes
 * in the floor are filled with cobblestone. Full, he walks back to the chests in the chamber and unloads,
 * keeping his timber and torches. A village without a mine yet gets a quarry: a stairway down near its edge.
 */
public class MinerWorkGoal extends Goal {
    private static final int LOAD = 256;
    private static final int MAIN = 32;
    private static final int BRANCH = 16;
    private static final int LEVEL_DROP = 16;
    private static final int QUARRY_DROP = 14;
    private static final int FLOOR_Y = 0;
    private static final double REACH = 5.5;
    private static final Map<Item, Integer> KEEP = Map.of(Items.OAK_FENCE, 16, Items.OAK_PLANKS, 16,
            Items.TORCH, 32, Items.STICK, 8, Items.COAL, 8, Items.COBBLESTONE, 32);

    private static final int CORRIDOR = 0, STAIRS = 1;
    private static final int OPEN = 0, DONE = 1, ABANDONED = 2;

    private enum State { SUPPLY, DESCEND, WORK, FLEE, UNLOAD }

    private final VillageWorker worker;
    private final Tooling tooling;
    private final Fetching fetching;
    private State state = State.SUPPLY;
    private int timer;
    private int stuck;
    private BlockPos digging;
    private int digTicks;
    private long lastRequest = -10000;
    private boolean supplied;
    /** Ore blocks found next to the corridor, dug before going on. */
    private final ArrayDeque<BlockPos> vein = new ArrayDeque<>();
    /** Floor spots in a cave or old mineshaft just broken into, to put torches on. */
    private final ArrayDeque<BlockPos> lights = new ArrayDeque<>();
    private BlockPos walkTarget;

    public MinerWorkGoal(VillageWorker worker) {
        this.worker = worker;
        this.tooling = new Tooling(worker);
        this.fetching = new Fetching(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
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
    public void start() {
        go(supplied ? State.DESCEND : State.SUPPLY);
    }

    @Override
    public void stop() {
        worker.getNavigation().stop();
        worker.resetUnloading();
        tooling.reset();
        fetching.reset();
        resetDig();
        vein.clear();
        lights.clear();
    }

    @Nullable
    private Building mine() {
        return worker.workplace().orElse(null);
    }

    /** Where the mine's layout and progress are kept. */
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

    // ---------------------------------------------------------------- the layout

    /** One straight piece of the mine: a corridor, or a staircase going down one block per block forward. */
    private record Segment(BlockPos start, Direction dir, int len, int kind, int parent, int depth) {
        CompoundTag save(int done, int st) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", start.getX());
            t.putInt("y", start.getY());
            t.putInt("z", start.getZ());
            t.putInt("d", dir.get2DDataValue());
            t.putInt("len", len);
            t.putInt("kind", kind);
            t.putInt("parent", parent);
            t.putInt("depth", depth);
            t.putInt("done", done);
            t.putInt("state", st);
            return t;
        }

        static Segment load(CompoundTag t) {
            return new Segment(new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")),
                    Direction.from2DDataValue(t.getInt("d")), t.getInt("len"), t.getInt("kind"), t.getInt("parent"),
                    t.getInt("depth"));
        }

        int height() {
            return kind == STAIRS ? 4 : 3;
        }

        /** Floor centre of slice k. */
        BlockPos slice(int k) {
            BlockPos p = start.relative(dir, k);
            return kind == STAIRS ? p.below(k + 1) : p;
        }

        /** Where he stands to dig slice k: the slice before it (or the spot this segment leaves from). */
        BlockPos stand(int k) {
            if (k > 0) {
                return slice(k - 1);
            }
            return start.relative(dir.getOpposite());
        }
    }

    private static ListTag segments(CompoundTag data) {
        if (!data.contains("segs", Tag.TAG_LIST)) {
            data.put("segs", new ListTag());
        }
        return data.getList("segs", Tag.TAG_COMPOUND);
    }

    private static int add(ListTag segs, Segment s) {
        segs.add(s.save(0, OPEN));
        return segs.size() - 1;
    }

    /** A corridor with branches off both sides every eight blocks. */
    private static void corridor(ListTag segs, BlockPos start, Direction dir, int len, int parent, int depth,
                                 boolean branches) {
        int main = add(segs, new Segment(start, dir, len, CORRIDOR, parent, depth));
        if (!branches) {
            return;
        }
        for (int k = 4; k < len - 2; k += 8) {
            BlockPos at = start.relative(dir, k);
            for (Direction side : new Direction[]{dir.getCounterClockWise(), dir.getClockWise()}) {
                add(segs, new Segment(at.relative(side, 2), side, BRANCH, CORRIDOR, main, depth));
            }
        }
    }

    /** A level: corridors forward and to both sides from a hub, then the staircase on to the next one. */
    private static void level(ListTag segs, BlockPos hub, Direction forward, int parent, int depth,
                              List<Direction> exits) {
        int first = segs.size();
        for (Direction d : exits) {
            BlockPos from = d == forward ? hub.relative(d) : hub.relative(d, 2);
            corridor(segs, from, d, MAIN, parent, depth, true);
        }
        if (segs.size() > first) {
            Segment main = Segment.load(segs.getCompound(first));
            int drop = Math.min(LEVEL_DROP, main.start().getY() - FLOOR_Y);
            if (drop > 3) {
                add(segs, new Segment(main.start().relative(main.dir(), main.len()), main.dir(), drop, STAIRS, first,
                        depth + 1));
            }
        }
    }

    private void layOut(ServerLevel level, Village village, CompoundTag data) {
        ListTag segs = segments(data);
        Building mine = mine();
        if (mine != null) {
            BlockPos hub = chamber(level, mine);
            Direction toStairs = towards(hub, mine.entrance());
            List<Direction> exits = new ArrayList<>();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (d != toStairs) {
                    BlockPos face = wallFace(level, hub, d);
                    if (face != null) {
                        exits.add(d);
                        corridor(segs, face, d, MAIN, -1, 0, true);
                    }
                }
            }
            if (exits.isEmpty()) { // a closed chamber: break out through the floor
                add(segs, new Segment(hub, toStairs.getOpposite(), Math.min(LEVEL_DROP, hub.getY() - FLOOR_Y),
                        STAIRS, -1, 1));
            } else {
                Segment main = Segment.load(segs.getCompound(0));
                int drop = Math.min(LEVEL_DROP, main.start().getY() - FLOOR_Y);
                if (drop > 3) {
                    add(segs, new Segment(main.start().relative(main.dir(), main.len()), main.dir(), drop, STAIRS, 0,
                            1));
                }
            }
        } else {
            BlockPos top = quarrySpot(level, village);
            if (top == null) {
                return;
            }
            Direction out = towards(village.center(), top);
            add(segs, new Segment(top.relative(out), out, QUARRY_DROP, STAIRS, -1, 0));
        }
        data.putBoolean("laid", true);
        village.markDirty();
    }

    /** When a staircase is finished, the next level opens at its foot. */
    private static void onFinished(ListTag segs, int index) {
        Segment s = Segment.load(segs.getCompound(index));
        if (s.kind() != STAIRS) {
            return;
        }
        BlockPos foot = s.slice(s.len() - 1);
        Direction f = s.dir();
        level(segs, foot, f, index, s.depth(), List.of(f, f.getCounterClockWise(), f.getClockWise()));
    }

    /** First open segment, or -1 when the whole mine is worked out. */
    private static int current(ListTag segs) {
        for (int i = 0; i < segs.size(); i++) {
            if (segs.getCompound(i).getInt("state") == OPEN) {
                CompoundTag t = segs.getCompound(i);
                int parent = t.getInt("parent");
                // A branch of an abandoned corridor cannot be reached.
                if (parent >= 0 && segs.getCompound(parent).getInt("state") == ABANDONED) {
                    t.putInt("state", ABANDONED);
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- the day's work

    @Override
    public void tick() {
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        timer++;
        Building mine = mine();
        if (mine != null) {
            village.reportWorking(mine.id(), level.getGameTime());
        }
        if (state != State.FLEE && state != State.UNLOAD && timer % 10 == 0 && danger(level) != null) {
            CompoundTag data = data(village);
            int cur = current(segments(data));
            if (cur >= 0 && state == State.WORK) {
                segments(data).getCompound(cur).putInt("state", ABANDONED);
                village.markDirty();
            }
            resetDig();
            vein.clear();
            go(State.FLEE);
        }
        switch (state) {
            case SUPPLY -> supply(level, village);
            case DESCEND -> descend(level, village);
            case WORK -> work(level, village);
            case FLEE -> {
                worker.setStatus("running from danger");
                BlockPos safe = hub(level, village);
                if (safe == null || walk(level, village, safe, 2.0) || timer > 600) {
                    go(State.WORK); // carries on somewhere else
                }
            }
            case UNLOAD -> {
                worker.setStatus("unloading");
                BlockPos safe = hub(level, village);
                if (safe != null && timer < 1200 && !walkedOut && !walk(level, village, safe, 2.0)) {
                    return;
                }
                walkedOut = true;
                if (worker.unloadTick(KEEP)) {
                    walkedOut = false;
                    go(supplied ? State.WORK : State.SUPPLY); // more timber and torches while up here
                }
            }
        }
    }

    private boolean walkedOut;

    /** Timber and torches for the supports, from the stores; asked for if there are none. */
    private void supply(ServerLevel level, Village village) {
        worker.setStatus("getting timber and torches");
        if (fetching.active()) {
            if (fetching.tick() || timer > 1200) {
                fetching.reset();
                supplied = true;
                go(State.DESCEND);
            }
            return;
        }
        var inv = worker.getInventory();
        int fences = inv.countItem(Items.OAK_FENCE), planks = inv.countItem(Items.OAK_PLANKS);
        int torches = inv.countItem(Items.TORCH);
        if (fences >= 8 && planks >= 6 && torches >= 8) {
            supplied = true;
            go(State.DESCEND);
            return;
        }
        var stock = village.stock(level);
        long now = level.getGameTime();
        if (now - lastRequest > 2400) {
            lastRequest = now;
            if (stock.count(Items.OAK_FENCE) < 16) {
                village.request(Items.OAK_FENCE, 16, "mine:" + worker.getUUID(), now);
            }
            if (stock.count(Items.TORCH) < 16 && stock.count(Items.COAL) == 0) {
                village.request(Items.TORCH, 16, "mine:" + worker.getUUID(), now);
            }
        }
        fetching.start(Map.of(Items.OAK_FENCE, Math.max(0, 16 - fences), Items.OAK_PLANKS, Math.max(0, 16 - planks),
                Items.TORCH, Math.max(0, 32 - torches), Items.COAL, Math.max(0, 4 - inv.countItem(Items.COAL))));
        if (!fetching.active()) {
            supplied = true;
            go(State.DESCEND);
        }
    }

    /** Down the mine's stairs to the chamber (or to the top of the quarry). */
    private void descend(ServerLevel level, Village village) {
        worker.setStatus("going down the mine");
        if (tooling.tick(level, village) == Tooling.Status.BUSY) {
            return;
        }
        BlockPos hub = hub(level, village);
        if (hub == null) {
            return;
        }
        if (walk(level, village, hub, 2.5)) {
            go(State.WORK);
        }
    }

    @Nullable
    private BlockPos hub(ServerLevel level, Village village) {
        Building mine = mine();
        if (mine != null) {
            return chamber(level, mine);
        }
        ListTag segs = segments(data(village));
        if (segs.isEmpty()) {
            BlockPos top = quarrySpot(level, village);
            return top;
        }
        return Segment.load(segs.getCompound(0)).stand(0);
    }

    private void work(ServerLevel level, Village village) {
        var inv = worker.getInventory();
        boolean bare = inv.countItem(Items.OAK_FENCE) < 4 && inv.countItem(Items.TORCH) == 0
                && inv.countItem(Items.COAL) == 0;
        if (worker.carried() >= LOAD || worker.inventoryFull() || bare && worker.carried() >= 64) {
            if (bare) {
                supplied = false;
            }
            resetDig();
            go(State.UNLOAD);
            return;
        }
        if (tooling.tick(level, village) == Tooling.Status.BUSY) {
            resetDig();
            return;
        }
        makeTorches();
        CompoundTag data = data(village);
        if (!data.getBoolean("laid")) {
            layOut(level, village, data);
            return;
        }
        if (!lights.isEmpty()) {
            light(level, village);
            return;
        }
        if (!vein.isEmpty()) {
            digVein(level, village);
            return;
        }
        ListTag segs = segments(data);
        int index = current(segs);
        if (index < 0) {
            worker.setStatus("the mine is worked out");
            return;
        }
        CompoundTag t = segs.getCompound(index);
        Segment seg = Segment.load(t);
        int k = t.getInt("done");
        if (k >= seg.len()) {
            t.putInt("state", DONE);
            onFinished(segs, index);
            village.markDirty();
            return;
        }
        BlockPos stand = seg.stand(k);
        worker.setStatus((seg.kind() == STAIRS ? "digging stairs down" : "digging a corridor") + " at "
                + seg.slice(k).toShortString());
        if (!level.hasChunkAt(seg.slice(k))) {
            return;
        }
        if (!at(stand, 1.6)) {
            resetDig();
            if (walk(level, village, stand, 1.2)) {
                return;
            }
            if (++stuck > 600) { // cannot get there: give this piece up
                t.putInt("state", ABANDONED);
                village.markDirty();
                stuck = 0;
            }
            return;
        }
        stuck = 0;
        worker.getNavigation().stop();
        Result r = digSlice(level, village, seg, k);
        if (r == Result.BLOCKED) {
            t.putInt("state", ABANDONED);
            village.markDirty();
            go(State.FLEE); // lava or water ahead: back off and carry on elsewhere
        } else if (r == Result.CLEAR) {
            finishSlice(level, village, seg, k);
            t.putInt("done", k + 1);
            village.markDirty();
        }
    }

    private enum Result { WORKING, CLEAR, BLOCKED }

    private List<BlockPos> cells(Segment seg, int k) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = seg.slice(k);
        Direction side = seg.dir().getClockWise();
        for (int h = seg.height() - 1; h >= 0; h--) { // top down, so nothing falls on his head
            for (int w : new int[]{0, -1, 1}) {
                out.add(c.relative(side, w).above(h));
            }
        }
        return out;
    }

    private Result digSlice(ServerLevel level, Village village, Segment seg, int k) {
        List<BlockPos> cells = cells(seg, k);
        Set<BlockPos> inside = new HashSet<>(cells);
        for (BlockPos p : cells) {
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!inside.contains(n) && !level.getFluidState(n).isEmpty()) {
                    return Result.BLOCKED;
                }
            }
            if (!level.getFluidState(p).isEmpty()) {
                return Result.BLOCKED;
            }
        }
        for (BlockPos p : cells) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || isOurs(s)) {
                continue;
            }
            if (s.is(BlockTags.REPLACEABLE) && s.getFluidState().isEmpty()) {
                level.destroyBlock(p, false, worker);
                return Result.WORKING;
            }
            if (!natural(level, p, s)) {
                // Someone else's timbers, rails, a chest: an old mineshaft or a building. Leave it be.
                lightAround(level, p, inside);
                return Result.BLOCKED;
            }
            breakBlock(level, p, s);
            return Result.WORKING;
        }
        return Result.CLEAR;
    }

    /** Our own supports and torches, met again where a branch starts. */
    private static boolean isOurs(BlockState s) {
        return s.is(Blocks.OAK_FENCE) || s.is(Blocks.OAK_PLANKS) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH);
    }

    /** Swings at a block until it breaks; true once broken. */
    private boolean breakBlock(ServerLevel level, BlockPos block, BlockState state) {
        worker.getLookControl().setLookAt(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        if (!tooling.canHarvest(state)) {
            resetDig();
            return false; // no pickaxe good enough: waits for one (it has been asked for)
        }
        if (!block.equals(digging)) {
            resetDig();
            digging = block;
        }
        int need = tooling.breakTicks(level, block, state);
        digTicks++;
        level.destroyBlockProgress(worker.getId(), block, Math.min(9, digTicks * 10 / Math.max(1, need)));
        if (digTicks % 5 == 0) {
            worker.swing(InteractionHand.MAIN_HAND);
        }
        if (digTicks < need) {
            return false;
        }
        if (!state.is(BlockTags.DIRT)) {
            for (ItemStack drop : Block.getDrops(state, level, block, level.getBlockEntity(block), worker,
                    worker.getMainHandItem())) {
                worker.carry(drop.getItem(), drop.getCount());
            }
        }
        level.destroyBlock(block, false, worker);
        tooling.used();
        resetDig();
        return true;
    }

    /** After a slice is open: fill the floor, look for ore and voids, put up a support or a torch. */
    private void finishSlice(ServerLevel level, Village village, Segment seg, int k) {
        List<BlockPos> cells = cells(seg, k);
        Set<BlockPos> inside = new HashSet<>(cells);
        BlockPos c = seg.slice(k);
        Direction side = seg.dir().getClockWise();
        // Ore in the walls, floor and ceiling: the whole vein.
        for (BlockPos p : cells) {
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!inside.contains(n) && level.getBlockState(n).is(Tags.Blocks.ORES) && !vein.contains(n)) {
                    followVein(level, n, c);
                }
            }
        }
        // Breaking into open space: a cave or an old mineshaft gets lit up.
        for (BlockPos p : cells) {
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!inside.contains(n) && d != seg.dir().getOpposite() && level.getBlockState(n).isAir()) {
                    lightAround(level, n, inside);
                    break;
                }
            }
        }
        // No holes underfoot.
        for (int w = -1; w <= 1; w++) {
            BlockPos floor = c.relative(side, w).below();
            if (level.getBlockState(floor).isAir() || level.getBlockState(floor).canBeReplaced()) {
                Item fill = worker.getInventory().countItem(Items.COBBLESTONE) > 0 ? Items.COBBLESTONE
                        : worker.getInventory().countItem(Items.COBBLED_DEEPSLATE) > 0 ? Items.COBBLED_DEEPSLATE : null;
                if (fill != null) {
                    worker.getInventory().removeItemType(fill, 1);
                    level.setBlock(floor, Block.byItem(fill).defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (seg.kind() == CORRIDOR && k % 4 == 2) {
            support(level, c, seg.dir());
        } else if (seg.kind() == CORRIDOR && k % 4 == 3) {
            // The far face of the last beam: only now is there room for its torch.
            BlockPos beam = seg.slice(k - 1).above(2);
            if (level.getBlockState(beam).is(Blocks.OAK_PLANKS)) {
                placeTorch(level, beam.relative(seg.dir()), Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, seg.dir()));
            }
        } else if (seg.kind() == STAIRS && k % 4 == 3) {
            BlockPos at = c.relative(side).above();
            if (level.getBlockState(c.relative(side, 2).above()).isSolidRender(level, c.relative(side, 2).above())) {
                placeTorch(level, at, Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, side.getOpposite()));
            }
        }
    }

    /** Two fence posts each side, a plank beam over the top, a torch on both faces of the beam's middle. */
    private void support(ServerLevel level, BlockPos c, Direction dir) {
        var inv = worker.getInventory();
        Direction side = dir.getClockWise();
        if (inv.countItem(Items.OAK_FENCE) >= 4 && inv.countItem(Items.OAK_PLANKS) >= 3) {
            for (int w : new int[]{-1, 1}) {
                for (int h = 0; h < 2; h++) {
                    place(level, c.relative(side, w).above(h), Blocks.OAK_FENCE.defaultBlockState());
                }
            }
            for (int w = -1; w <= 1; w++) {
                place(level, c.relative(side, w).above(2), Blocks.OAK_PLANKS.defaultBlockState());
            }
            inv.removeItemType(Items.OAK_FENCE, 4);
            inv.removeItemType(Items.OAK_PLANKS, 3);
            BlockPos beam = c.above(2);
            for (Direction face : new Direction[]{dir, dir.getOpposite()}) {
                placeTorch(level, beam.relative(face), Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, face));
            }
        } else {
            // No timber: at least a torch on the floor by the wall.
            BlockPos at = c.relative(side);
            if (level.getBlockState(at.below()).isSolidRender(level, at.below())) {
                placeTorch(level, at, Blocks.TORCH.defaultBlockState());
            }
        }
        if (inv.countItem(Items.OAK_FENCE) < 4 || inv.countItem(Items.OAK_PLANKS) < 3) {
            supplied = false; // fetch more on the next trip up
        }
    }

    private void place(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            worker.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void placeTorch(ServerLevel level, BlockPos pos, BlockState torch) {
        if (worker.getInventory().countItem(Items.TORCH) > 0 && level.getBlockState(pos).isAir()
                && torch.canSurvive(level, pos)) {
            level.setBlock(pos, torch, Block.UPDATE_ALL);
            worker.getInventory().removeItemType(Items.TORCH, 1);
            worker.swing(InteractionHand.MAIN_HAND);
        }
    }

    /** Coal and a stick make four torches, in hand, like a player; a stick from planks if need be. */
    private void makeTorches() {
        var inv = worker.getInventory();
        if (inv.countItem(Items.TORCH) >= 4 || inv.countItem(Items.COAL) == 0) {
            return;
        }
        if (inv.countItem(Items.STICK) == 0 && inv.countItem(Items.OAK_PLANKS) >= 5) {
            inv.removeItemType(Items.OAK_PLANKS, 2);
            worker.carry(Items.STICK, 4);
        }
        if (inv.countItem(Items.STICK) > 0) {
            inv.removeItemType(Items.COAL, 1);
            inv.removeItemType(Items.STICK, 1);
            worker.carry(Items.TORCH, 4);
        }
    }

    /** Collects the connected ore around {@code first}, within reach of the corridor. */
    private void followVein(ServerLevel level, BlockPos first, BlockPos from) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        open.add(first);
        seen.add(first);
        Block kind = level.getBlockState(first).getBlock();
        while (!open.isEmpty() && vein.size() < 48) {
            BlockPos p = open.poll();
            vein.add(p);
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                BlockPos q = n.immutable();
                if (seen.add(q) && level.getBlockState(q).is(kind) && q.closerThan(from, 6)) {
                    open.add(q);
                }
            }
        }
    }

    private void digVein(ServerLevel level, Village village) {
        BlockPos next = vein.peek();
        BlockState s = level.getBlockState(next);
        worker.setStatus("digging out a vein of " + s.getBlock().getName().getString());
        if (!s.is(Tags.Blocks.ORES)) {
            vein.poll();
            return;
        }
        // Dug in order, each one opens the way to the next; stone in between is taken too.
        boolean exposed = false;
        for (Direction d : Direction.values()) {
            if (level.getBlockState(next.relative(d)).isAir()) {
                exposed = true;
                break;
            }
        }
        if (!exposed || worker.getEyePosition().distanceTo(next.getCenter()) > REACH) {
            vein.poll();
            vein.add(next); // later, from closer
            if (++stuck > vein.size() * 2 + 4) {
                vein.clear();
                stuck = 0;
            }
            return;
        }
        if (breakBlock(level, next, s)) {
            vein.poll();
            stuck = 0;
            // The hole it leaves in the floor is filled so nobody steps into it.
            if (next.getY() < worker.getBlockY() && worker.getInventory().countItem(Items.COBBLESTONE) > 0) {
                level.setBlock(next, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
                worker.getInventory().removeItemType(Items.COBBLESTONE, 1);
            }
        }
    }

    /** Looks over the open space behind {@code opening}; a sizeable one gets torches on its dark floor. */
    private void lightAround(ServerLevel level, BlockPos opening, Set<BlockPos> tunnel) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>(tunnel); // not back into our own tunnel
        open.add(opening);
        seen.add(opening);
        List<BlockPos> floor = new ArrayList<>();
        while (!open.isEmpty() && seen.size() < 600) {
            BlockPos p = open.poll();
            if (level.getBlockState(p.below()).isSolidRender(level, p.below())) {
                floor.add(p);
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.closerThan(opening, 14) && seen.add(n) && level.getBlockState(n).isAir()) {
                    open.add(n);
                }
            }
        }
        if (seen.size() - tunnel.size() < 40) {
            return; // just a pocket
        }
        List<BlockPos> chosen = new ArrayList<>();
        floor.sort(Comparator.comparingDouble(p -> p.distSqr(opening)));
        for (BlockPos p : floor) {
            if (chosen.size() >= 10) {
                break;
            }
            if (level.getBrightness(LightLayer.BLOCK, p) < 8 && chosen.stream().noneMatch(q -> q.closerThan(p, 6))
                    && lights.stream().noneMatch(q -> q.closerThan(p, 6))) {
                chosen.add(p);
            }
        }
        lights.addAll(chosen);
    }

    private void light(ServerLevel level, Village village) {
        BlockPos spot = lights.peek();
        worker.setStatus("lighting up a cave");
        if (worker.getInventory().countItem(Items.TORCH) == 0) {
            lights.clear();
            supplied = false;
            return;
        }
        if (worker.getEyePosition().distanceTo(spot.getCenter()) <= 4.5) {
            worker.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            placeTorch(level, spot, Blocks.TORCH.defaultBlockState());
            lights.poll();
            walkTarget = null;
            return;
        }
        if (walk(level, village, spot, 3.5) || ++stuck > 300) {
            if (stuck > 300) {
                lights.poll(); // cannot get there
            }
            stuck = 0;
        }
    }

    // ---------------------------------------------------------------- danger and moving about

    /** A monster he can see close by. */
    @Nullable
    private Mob danger(ServerLevel level) {
        if (worker.getY() > level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worker.getBlockX(),
                worker.getBlockZ()) - 2) {
            return null; // up top the guards deal with them
        }
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(worker.blockPosition()).inflate(10),
                m -> m instanceof Enemy && m.isAlive() && worker.hasLineOfSight(m));
        return mobs.isEmpty() ? null : mobs.get(0);
    }

    /** Walks towards {@code target}; true once within {@code reach}. */
    private boolean walk(ServerLevel level, Village village, BlockPos target, double reach) {
        if (at(target, reach)) {
            worker.getNavigation().stop();
            walkTarget = null;
            return true;
        }
        if (!target.equals(walkTarget) || worker.getNavigation().isDone() || timer % 40 == 0) {
            walkTarget = target;
            BlockPos via = waypoint(level, village, target);
            worker.getNavigation().moveTo(via.getX() + 0.5, via.getY(), via.getZ() + 0.5, 0.6);
        }
        if (timer % 20 == 0) {
            if (worker.getNavigation().isDone() && ++stuck > 30) {
                // Hopelessly stuck (a gravel fall, a dead end): scramble over to the spot.
                worker.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
                stuck = 0;
            }
        }
        return false;
    }

    /** Long ways through the tunnels go from one corridor mouth to the next. */
    private BlockPos waypoint(ServerLevel level, Village village, BlockPos target) {
        if (worker.blockPosition().closerThan(target, 40)) {
            return target;
        }
        ListTag segs = segments(data(village));
        BlockPos best = target;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < segs.size(); i++) {
            Segment s = Segment.load(segs.getCompound(i));
            if (segs.getCompound(i).getInt("done") == 0) {
                continue;
            }
            for (BlockPos p : new BlockPos[]{s.stand(0), s.slice(Math.max(0, segs.getCompound(i).getInt("done") - 1))}) {
                if (worker.blockPosition().closerThan(p, 40)) {
                    double d = p.distSqr(target);
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        if (best == target) {
            BlockPos hub = hub(level, village);
            return hub != null && worker.blockPosition().closerThan(hub, 40) ? hub : target;
        }
        return best;
    }

    private boolean at(BlockPos p, double reach) {
        double dx = worker.getX() - (p.getX() + 0.5), dz = worker.getZ() - (p.getZ() + 0.5);
        return dx * dx + dz * dz <= reach * reach && Math.abs(worker.getY() - p.getY()) < 1.5;
    }

    // ---------------------------------------------------------------- the place

    /** Only natural ground is dug; nothing at the bottom of the world, nothing unbreakable. */
    private static boolean natural(ServerLevel level, BlockPos pos, BlockState state) {
        if (pos.getY() <= level.getMinBuildHeight() + 5 || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.ORES) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.CALCITE) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.TUFF)
                || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.GLOW_LICHEN) || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.COBBLED_DEEPSLATE);
    }

    private static Direction towards(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Walking from the chamber's middle in {@code d}: the first slice of natural rock, three wide and three high,
     * where a corridor can start; null if the chamber's wall that way is chests, a bench or the stairs.
     */
    @Nullable
    private static BlockPos wallFace(ServerLevel level, BlockPos hub, Direction d) {
        Direction side = d.getClockWise();
        for (int k = 1; k <= 8; k++) {
            BlockPos p = hub.relative(d, k);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) {
                continue;
            }
            for (int w = -1; w <= 1; w++) {
                for (int h = 0; h < 3; h++) {
                    BlockPos q = p.relative(side, w).above(h);
                    BlockState qs = level.getBlockState(q);
                    if (!qs.isAir() && !natural(level, q, qs) && !qs.is(Blocks.WALL_TORCH)) {
                        return null;
                    }
                }
            }
            return p;
        }
        return null;
    }

    /** Natural ground towards the edge of the village, clear of buildings, for the top of the stairway. */
    @Nullable
    private BlockPos quarrySpot(ServerLevel level, Village village) {
        CompoundTag q = data(village);
        if (q.contains("x")) {
            return new BlockPos(q.getInt("x"), q.getInt("y"), q.getInt("z"));
        }
        BlockPos c = village.center();
        int r = Math.max(16, village.radius() - QUARRY_DROP - 8);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int side = -8; side <= 8; side += 4) {
                BlockPos col = c.relative(d, r).relative(d.getClockWise(), side);
                if (!level.hasChunkAt(col)) {
                    continue;
                }
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
                BlockPos ground = top.below();
                boolean clear = village.buildings().stream().noneMatch(b -> b.box().inflatedBy(QUARRY_DROP + 2)
                        .isInside(ground));
                if (clear && SiteFinder.isNatural(level.getBlockState(ground))) {
                    q.putInt("x", top.getX());
                    q.putInt("y", top.getY());
                    q.putInt("z", top.getZ());
                    village.markDirty();
                    return top;
                }
            }
        }
        return null;
    }

    /** Lowest spot inside the mine where one can stand: the chamber at the bottom of the stairs. */
    private static BlockPos chamber(ServerLevel level, Building mine) {
        BoundingBox box = mine.box();
        for (int y = box.minY(); y < mine.groundY(); y++) {
            List<BlockPos> floor = new ArrayList<>();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                            && level.getBlockState(p.below()).isSolidRender(level, p.below())) {
                        floor.add(p);
                    }
                }
            }
            if (!floor.isEmpty()) {
                // The middle of the chamber's open floor.
                double mx = floor.stream().mapToInt(BlockPos::getX).average().orElse(0);
                double mz = floor.stream().mapToInt(BlockPos::getZ).average().orElse(0);
                BlockPos mid = BlockPos.containing(mx, y, mz);
                return floor.stream().min(Comparator.comparingDouble(p -> p.distSqr(mid))).orElse(floor.get(0));
            }
        }
        return new BlockPos((box.minX() + box.maxX()) / 2, mine.groundY() - 4, (box.minZ() + box.maxZ()) / 2);
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
        walkTarget = null;
    }
}
