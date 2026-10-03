package com.khmh.livingvillages.stock;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.village.Village;
import com.khmh.livingvillages.village.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The village's real store: every chest and barrel in its warehouses (before the first warehouse is built, the
 * chests in any of its buildings). Goods that arrived while nobody was around wait in the village's buffer and
 * are put into the chests once the warehouse is loaded again.
 */
public final class Stockpile implements Stock {
    private final ServerLevel level;
    private final Village village;
    private final List<BlockPos> containers;

    private Stockpile(ServerLevel level, Village village, List<BlockPos> containers) {
        this.level = level;
        this.village = village;
        this.containers = containers;
    }

    public static Stockpile of(ServerLevel level, Village village) {
        return new Stockpile(level, village, containers(level, village));
    }

    /** Chests and barrels that make up the store, nearest to the bell first. */
    /** Where a village's chests are, looked up at most once every two seconds: the search goes block by block. */
    private static final java.util.Map<java.util.UUID, java.util.Map.Entry<Long, List<BlockPos>>> FOUND =
            new java.util.HashMap<>();

    public static List<BlockPos> containers(ServerLevel level, Village village) {
        long now = level.getGameTime();
        var known = FOUND.get(village.id());
        if (known != null && now - known.getKey() < 40 && now >= known.getKey()) {
            return known.getValue();
        }
        List<BlockPos> out = search(level, village);
        out = List.copyOf(out);
        FOUND.put(village.id(), java.util.Map.entry(now, out));
        return out;
    }

    /** Chests somebody could not get at (walled in, no way round): left alone by everyone for a while. */
    private static final java.util.Map<Long, Long> UNREACHABLE = new java.util.HashMap<>();

    public static void unreachable(ServerLevel level, BlockPos pos) {
        UNREACHABLE.put(pos.asLong(), level.getGameTime() + 6000);
    }

    public static boolean isUnreachable(ServerLevel level, BlockPos pos) {
        Long until = UNREACHABLE.get(pos.asLong());
        if (until != null && until <= level.getGameTime()) {
            UNREACHABLE.remove(pos.asLong());
            return false;
        }
        return until != null;
    }

    /** A chest was just put in or taken away: look again next time. */
    public static void forget(Village village) {
        FOUND.remove(village.id());
    }

    private static List<BlockPos> search(ServerLevel level, Village village) {
        List<Building> stores = new ArrayList<>();
        for (Building b : village.buildings()) {
            if (b.isComplete() && "warehouse".equals(b.type() == null ? null : b.type().group())) {
                stores.add(b);
            }
        }
        if (stores.isEmpty()) {
            for (Building b : village.buildings()) {
                if (b.isComplete()) {
                    stores.add(b);
                }
            }
        }
        List<BlockPos> out = new ArrayList<>();
        for (Building b : stores) {
            BoundingBox box = b.box();
            if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                continue;
            }
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
                    out.add(p.immutable());
                }
            }
        }
        // Chests stood by the bell (an old vanilla village with no house of ours to keep them in).
        BlockPos bell = village.center();
        if (level.hasChunksAt(bell.getX() - 5, bell.getZ() - 5, bell.getX() + 5, bell.getZ() + 5)) {
            for (BlockPos p : BlockPos.betweenClosed(bell.offset(-5, -2, -5), bell.offset(5, 2, 5))) {
                if (level.getBlockEntity(p) instanceof ChestBlockEntity && !out.contains(p)) {
                    out.add(p.immutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(village.center())));
        return out;
    }

    /** Chests and barrels of the village's other buildings that hold something: work for the carriers. */
    public static List<BlockPos> outlying(ServerLevel level, Village village) {
        List<BlockPos> store = containers(level, village);
        List<BlockPos> out = new ArrayList<>();
        boolean hasWarehouse = village.buildings().stream().anyMatch(b -> b.isComplete() && b.type() != null
                && "warehouse".equals(b.type().group()));
        if (!hasWarehouse) {
            return out;
        }
        for (Building b : village.buildings()) {
            if (!b.isComplete() || b.type() != null && "warehouse".equals(b.type().group())) {
                continue;
            }
            BoundingBox box = b.box();
            if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                continue;
            }
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (!store.contains(p) && level.getBlockEntity(p) instanceof Container c && !c.isEmpty()
                        && (c instanceof ChestBlockEntity || c instanceof BarrelBlockEntity)) {
                    out.add(p.immutable());
                }
            }
        }
        return out;
    }

    public List<BlockPos> positions() {
        return containers;
    }

    private Container container(BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container c ? c : null;
    }

    @Override
    public int count(Item item) {
        int n = village.storage().count(item);
        for (BlockPos p : containers) {
            Container c = container(p);
            if (c != null) {
                n += c.countItem(item);
            }
        }
        return n;
    }

    @Override
    public boolean take(Item item, int amount) {
        if (count(item) < amount) {
            return false;
        }
        VillageStorage buffer = village.storage();
        int left = amount;
        int fromBuffer = Math.min(left, buffer.count(item));
        if (fromBuffer > 0) {
            buffer.take(item, fromBuffer);
            left -= fromBuffer;
        }
        for (BlockPos p : containers) {
            Container c = container(p);
            if (c == null) {
                continue;
            }
            for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
                ItemStack s = c.getItem(i);
                if (s.is(item)) {
                    int n = Math.min(left, s.getCount());
                    s.shrink(n);
                    left -= n;
                }
            }
            c.setChanged();
            if (left == 0) {
                break;
            }
        }
        return true;
    }

    /** Puts goods into the chests; whatever does not fit waits in the buffer. */
    @Override
    public void add(Item item, int amount) {
        int left = amount;
        while (left > 0) {
            ItemStack stack = new ItemStack(item, Math.min(left, item.getMaxStackSize()));
            int before = stack.getCount();
            ItemStack rest = insert(stack);
            int placed = before - rest.getCount();
            left -= placed;
            if (!rest.isEmpty()) {
                village.storage().add(item, left);
                return;
            }
        }
    }

    /** Puts a stack into the chests; returns what did not fit. */
    public ItemStack insert(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (BlockPos p : containers) {
            Container c = container(p);
            if (c == null) {
                continue;
            }
            rest = insertInto(c, rest);
            if (rest.isEmpty()) {
                break;
            }
        }
        return rest;
    }

    /** Merges into matching stacks first, then empty slots. */
    public static ItemStack insertInto(Container c, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
                ItemStack s = c.getItem(i);
                if (pass == 0 && !s.isEmpty() && ItemStack.isSameItemSameTags(s, rest)) {
                    int room = Math.min(c.getMaxStackSize(), s.getMaxStackSize()) - s.getCount();
                    int n = Math.min(room, rest.getCount());
                    if (n > 0) {
                        s.grow(n);
                        rest.shrink(n);
                    }
                } else if (pass == 1 && s.isEmpty() && c.canPlaceItem(i, rest)) {
                    c.setItem(i, rest.copy());
                    rest = ItemStack.EMPTY;
                }
            }
        }
        c.setChanged();
        return rest;
    }

    @Override
    public Map<Item, Integer> totals() {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (BlockPos p : containers) {
            Container c = container(p);
            if (c == null) {
                continue;
            }
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) {
                    out.merge(s.getItem(), s.getCount(), Integer::sum);
                }
            }
        }
        village.storage().totals().forEach((item, n) -> out.merge(item, n, Integer::sum));
        return out;
    }

    /** Share of chest slots in use, 0..1 (1 when there are no chests at all). */
    public double fill() {
        int used = 0, total = 0;
        for (BlockPos p : containers) {
            Container c = container(p);
            if (c == null) {
                continue;
            }
            total += c.getContainerSize();
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (!c.getItem(i).isEmpty()) {
                    used++;
                }
            }
        }
        return total == 0 ? 1.0 : (double) used / total;
    }

    /** Moves the buffer (goods that arrived while unloaded) into the chests, as far as they fit. */
    public void flushBuffer() {
        VillageStorage buffer = village.storage();
        for (Map.Entry<Item, Integer> e : buffer.totals().entrySet()) {
            Item item = e.getKey();
            int left = e.getValue();
            while (left > 0) {
                ItemStack stack = new ItemStack(item, Math.min(left, item.getMaxStackSize()));
                ItemStack rest = insert(stack);
                int placed = stack.getCount() - rest.getCount();
                if (placed <= 0) {
                    break;
                }
                buffer.take(item, placed);
                left -= placed;
            }
        }
    }
}
