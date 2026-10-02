package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.stock.Stockpile;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A worker's trip to the warehouse: walk to the nearest chest or barrel with room, open it, put the load in,
 * close it, and go on to the next one if it filled up. With no chest anywhere (a brand-new village) the load
 * is left at the bell, which counts as the village store until the first warehouse is built.
 */
class Unloading {
    private static final int OPEN_TICKS = 10;
    private static final int GIVE_UP_TICKS = 300;

    private final VillageWorker worker;
    private final Set<BlockPos> skip = new HashSet<>();
    private BlockPos target;
    private int timer;
    private int opened = -1;
    /** Items the worker keeps for his own work (a farmer's seeds), item -> how many. */
    private java.util.Map<net.minecraft.world.item.Item, Integer> keep = java.util.Map.of();

    Unloading(VillageWorker worker) {
        this.worker = worker;
    }

    void reset() {
        if (target != null && opened >= 0 && worker.level() instanceof ServerLevel level) {
            ContainerLid.close(level, target);
        }
        target = null;
        timer = 0;
        opened = -1;
        skip.clear();
    }

    void keep(java.util.Map<net.minecraft.world.item.Item, Integer> keep) {
        this.keep = keep;
    }

    private int keptCount() {
        int n = 0;
        for (var e : keep.entrySet()) {
            n += Math.min(e.getValue(), worker.getInventory().countItem(e.getKey()));
        }
        return n;
    }

    boolean tick() {
        if (worker.carried() - keptCount() <= 0) {
            reset();
            return true;
        }
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            return true;
        }
        if (target == null) {
            target = pick(level, village);
            timer = 0;
            opened = -1;
        }
        timer++;
        boolean bell = target.equals(village.center());
        // The bell stands on whatever ground there is: after a while anywhere near it will do.
        // (and if the bell cannot be got at at all, what he carries counts as delivered to the village store)
        double reach = bell ? (timer > GIVE_UP_TICKS * 4 ? Double.MAX_VALUE : timer > GIVE_UP_TICKS ? 144 : 16) : 6.25;
        if (worker.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > reach) {
            if (!bell && timer > GIVE_UP_TICKS) {
                skip.add(target);
                target = null;
            } else if (timer % 20 == 1) {
                worker.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6);
            }
            return false;
        }
        if (!target.equals(village.center()) && !Reach.sees(worker, target)) {
            if (timer >= GIVE_UP_TICKS) {
                skip.add(target); // walled in: some other chest
                target = null;
            } else if (timer % 20 == 1) { // a wall in between: round to the chest's open side
                worker.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6);
            }
            return false;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (target.equals(pendingChest)) {
            pendingChest = null;
            if (!placeChest(level, village, target)) {
                skip.add(target);
                target = null;
            }
            return false;
        }
        if (target.equals(village.center())) {
            putInBuffer(village);
            reset();
            return true;
        }
        if (opened < 0) {
            ContainerLid.open(level, target);
            opened = timer;
            return false;
        }
        if (timer - opened < OPEN_TICKS) {
            return false;
        }
        boolean emptied = putInto(level, target);
        ContainerLid.close(level, target);
        if (!emptied) {
            skip.add(target); // full: on to the next chest
        }
        target = null;
        return emptied && worker.carried() == 0;
    }

    private BlockPos pick(ServerLevel level, Village village) {
        List<BlockPos> chests = Stockpile.containers(level, village);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : chests) {
            if (skip.contains(p) || !hasRoom(level, p)) {
                continue;
            }
            double d = p.distSqr(worker.blockPosition());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best == null && village.countGroup("warehouse") == 0 && (chests.isEmpty() || village.stockFill() >= 0.7)
                && woodForChest(village) != null) {
            // Nowhere to put things yet: knock a chest together and stand it in a house, like a player would.
            pendingChest = chestSpot(level, village);
            if (pendingChest != null) {
                return pendingChest;
            }
        }
        return best != null ? best : village.center();
    }

    private BlockPos pendingChest;

    /** Two logs (carried, or lying at the bell) or eight planks make a chest; null if there is no wood. */
    private net.minecraft.world.item.Item woodForChest(Village village) {
        SimpleContainer inv = worker.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(net.minecraft.tags.ItemTags.LOGS) && s.getCount() >= 2
                    || s.is(net.minecraft.tags.ItemTags.PLANKS) && s.getCount() >= 8) {
                return s.getItem();
            }
        }
        return village.storage().count(net.minecraft.world.item.Items.OAK_LOG) >= 2
                ? net.minecraft.world.item.Items.OAK_LOG : null;
    }

    /** A free corner on the floor of a finished house (or any building), out of the doorway. */
    private BlockPos chestSpot(ServerLevel level, Village village) {
        BlockPos best = null;
        for (com.khmh.livingvillages.building.Building b : village.buildings()) {
            if (!b.isComplete()) {
                continue;
            }
            var box = b.box();
            for (BlockPos p : BlockPos.betweenClosed(box.minX() + 1, box.minY(), box.minZ() + 1, box.maxX() - 1,
                    box.maxY() - 1, box.maxZ() - 1)) {
                if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()
                        || !level.getBlockState(p.below()).isSolidRender(level, p.below())
                        || p.closerThan(b.entrance(), 2.5) || !underRoof(level, p) || skip.contains(p)) {
                    continue;
                }
                int walls = 0;
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos n = p.relative(d);
                    if (level.getBlockState(n).isSolidRender(level, n)) {
                        walls++;
                    } else if (level.getBlockState(n).getBlock() instanceof net.minecraft.world.level.block.BedBlock
                            || level.getBlockState(n).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                        walls = -9; // not against a bed or a door: leave the room usable
                    } else if (!level.getBlockState(n).isAir()) {
                        walls++; // glass, a fence, a pane: still a wall to stand against
                    }
                }
                if (walls >= 2 && (best == null || p.distSqr(worker.blockPosition()) < best.distSqr(worker.blockPosition()))) {
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    private static boolean underRoof(ServerLevel level, BlockPos p) {
        for (int h = 2; h <= 6; h++) {
            if (!level.getBlockState(p.above(h)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Puts the chest down (paying for it with wood); false if the spot is taken or the wood is gone. */
    private boolean placeChest(ServerLevel level, Village village, BlockPos at) {
        net.minecraft.world.item.Item wood = woodForChest(village);
        if (wood == null || !level.getBlockState(at).isAir()) {
            return false;
        }
        int need = new ItemStack(wood).is(net.minecraft.tags.ItemTags.PLANKS) ? 8 : 2;
        if (worker.getInventory().countItem(wood) >= need) {
            worker.getInventory().removeItemType(wood, need);
        } else {
            village.storage().take(wood, need);
        }
        level.setBlock(at, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        com.khmh.livingvillages.LivingVillages.LOGGER.info("Village {}: a chest was put in at {}",
                village.id().toString().substring(0, 8), at);
        return true;
    }

    private static boolean hasRoom(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container c)) {
            return false;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private boolean putInto(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container c)) {
            return false;
        }
        SimpleContainer inv = worker.getInventory();
        java.util.Map<net.minecraft.world.item.Item, Integer> keepLeft = new java.util.HashMap<>(keep);
        boolean all = true;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            int kept = Math.min(s.getCount(), keepLeft.getOrDefault(s.getItem(), 0));
            if (kept > 0) {
                keepLeft.put(s.getItem(), keepLeft.get(s.getItem()) - kept);
                if (kept == s.getCount()) {
                    continue;
                }
            }
            ItemStack give = s.copyWithCount(s.getCount() - kept);
            ItemStack rest = Stockpile.insertInto(c, give);
            s.setCount(kept + rest.getCount());
            all &= rest.isEmpty();
        }
        inv.setChanged();
        return all;
    }

    private void putInBuffer(Village village) {
        SimpleContainer inv = worker.getInventory();
        java.util.Map<net.minecraft.world.item.Item, Integer> keepLeft = new java.util.HashMap<>(keep);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            int kept = Math.min(s.getCount(), keepLeft.getOrDefault(s.getItem(), 0));
            if (s.isEmpty() || kept == s.getCount()) {
                keepLeft.computeIfPresent(s.getItem(), (k, v) -> v - kept);
                continue;
            }
            keepLeft.computeIfPresent(s.getItem(), (k, v) -> v - kept);
            village.storage().add(s.getItem(), s.getCount() - kept);
            s.setCount(kept);
        }
        inv.setChanged();
    }
}
