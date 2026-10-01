package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.economy.CraftPlanner;
import com.khmh.livingvillages.economy.Request;
import com.khmh.livingvillages.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The apprentice makes what the village asks for, with real recipes: he picks the oldest open request, works
 * out the crafts, fetches the raw materials from the warehouse, crafts at a crafting table (small recipes in
 * his hands), smelts in a real furnace with real fuel, and takes the result back to the warehouse. A village
 * without a table or furnace gets one set up by the bell first.
 */
public class ApprenticeWorkGoal extends Goal {
    private static final int CRAFT_TICKS = 20;
    private static final int SMELT_TIMEOUT = 260;

    private enum State { PICK, FETCH, WORK, DELIVER }

    private final VillageWorker worker;
    private final Fetching fetching;
    private State state = State.PICK;
    private Request request;
    private List<CraftPlanner.Step> steps = List.of();
    private int stepIndex;
    private int timer;
    private int cooldown;
    private boolean furnaceLoaded;
    /** A crafting table or furnace to set up before the actual order. */
    private Item setUp;

    public ApprenticeWorkGoal(VillageWorker worker) {
        this.worker = worker;
        this.fetching = new Fetching(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (worker.job() != WorkerJob.APPRENTICE || --cooldown > 0) {
            return false;
        }
        cooldown = 20;
        return worker.village().map(v -> v.requests().stream().anyMatch(this::workable)).orElse(false)
                || worker.carried() > 0;
    }

    @Override
    public boolean canContinueToUse() {
        return worker.job() == WorkerJob.APPRENTICE && worker.village().isPresent();
    }

    @Override
    public void start() {
        go(worker.carried() > 0 ? State.DELIVER : State.PICK);
    }

    @Override
    public void stop() {
        if (request != null && worker.getUUID().equals(request.claimedBy())) {
            request.claim(null);
        }
        request = null;
        fetching.reset();
        worker.resetUnloading();
        worker.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean workable(Request r) {
        return r.remaining() > 0 && (r.claimedBy() == null || r.claimedBy().equals(worker.getUUID()))
                && (r.unobtainableSince() < 0 || worker.level().getGameTime() - r.unobtainableSince() > 1200);
    }

    @Override
    public void tick() {
        Village village = worker.village().orElse(null);
        if (village == null || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        timer++;
        switch (state) {
            case PICK -> pick(level, village);
            case FETCH -> {
                if (fetching.tick()) {
                    go(State.WORK);
                    stepIndex = 0;
                }
            }
            case WORK -> work(level, village);
            case DELIVER -> {
                if (worker.unloadTick()) {
                    request = null;
                    go(State.PICK);
                }
            }
        }
    }

    private void pick(ServerLevel level, Village village) {
        Map<Item, Integer> stock = new HashMap<>(village.stock(level).totals());
        SimpleContainer inv = worker.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                stock.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        // Nothing to work at? Set up a crafting table (and later a furnace) by the bell first.
        if (Stations.find(level, village, CraftPlanner.Station.TABLE, worker.blockPosition()) == null) {
            if (begin(level, stock, Items.CRAFTING_TABLE, 1, null)) {
                return;
            }
        }
        List<Request> open = village.requests().stream().filter(this::workable)
                .sorted(Comparator.comparingLong(Request::created)).toList();
        for (Request r : open) {
            if (!CraftPlanner.canMake(level, r.item())) {
                r.setUnobtainable(level.getGameTime()); // raw material: gatherers have to bring it
                continue;
            }
            var plan = CraftPlanner.plan(level, stock, r.item(), r.remaining());
            if (plan.isEmpty()) {
                r.setUnobtainable(level.getGameTime());
                continue;
            }
            boolean needsFurnace = plan.get().steps().stream().anyMatch(s -> s.station() == CraftPlanner.Station.FURNACE);
            if (needsFurnace && Stations.find(level, village, CraftPlanner.Station.FURNACE, worker.blockPosition()) == null
                    && begin(level, stock, Items.FURNACE, 1, null)) {
                return;
            }
            r.setUnobtainable(-1);
            r.claim(worker.getUUID());
            if (begin(level, stock, r.item(), r.remaining(), r)) {
                return;
            }
        }
        if (worker.carried() > 0) {
            go(State.DELIVER);
        }
    }

    /** Plans and starts making {@code count} of {@code item}; false if it cannot be done right now. */
    private boolean begin(ServerLevel level, Map<Item, Integer> stock, Item item, int count, Request forRequest) {
        var plan = CraftPlanner.plan(level, stock, item, count);
        if (plan.isEmpty()) {
            return false;
        }
        request = forRequest;
        setUp = forRequest == null ? item : null;
        steps = plan.get().steps();
        Map<Item, Integer> fetch = new HashMap<>();
        plan.get().fetch().forEach((k, n) -> {
            int missing = n - worker.getInventory().countItem(k);
            if (missing > 0) {
                fetch.put(k, missing);
            }
        });
        fetching.start(fetch);
        go(State.FETCH);
        return true;
    }

    private void work(ServerLevel level, Village village) {
        if (stepIndex >= steps.size()) {
            finishOrder(level, village);
            return;
        }
        CraftPlanner.Step step = steps.get(stepIndex);
        if (step.station() == CraftPlanner.Station.HAND) {
            if (timer >= CRAFT_TICKS) {
                craft(step);
            }
            return;
        }
        BlockPos at = Stations.find(level, village, step.station(), worker.blockPosition());
        if (at == null) {
            abort();
            return;
        }
        if (worker.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) > 6.25) {
            if (timer % 20 == 1) {
                worker.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.6);
            }
            if (timer > 600) {
                abort();
            }
            return;
        }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        if (step.station() == CraftPlanner.Station.TABLE) {
            if (timer % 10 == 0) {
                worker.swing(InteractionHand.MAIN_HAND);
            }
            if (timer >= CRAFT_TICKS + Math.min(80, step.times() * 5)) {
                craft(step);
            }
        } else {
            smelt(level, at, step);
        }
    }

    private void craft(CraftPlanner.Step step) {
        SimpleContainer inv = worker.getInventory();
        for (Map.Entry<Item, Integer> e : step.inputs().entrySet()) {
            if (inv.countItem(e.getKey()) < e.getValue()) {
                abort(); // someone took the materials
                return;
            }
        }
        step.inputs().forEach(inv::removeItemType);
        worker.carry(step.output(), step.times() * step.outputCount());
        worker.level().playSound(null, worker.blockPosition(), SoundEvents.VILLAGER_WORK_FLETCHER, SoundSource.NEUTRAL,
                0.6F, 1.0F);
        stepIndex++;
        timer = 0;
    }

    /** Loads the furnace with the input and fuel, waits by it, and takes out the result. */
    private void smelt(ServerLevel level, BlockPos at, CraftPlanner.Step step) {
        if (!(level.getBlockEntity(at) instanceof AbstractFurnaceBlockEntity furnace)) {
            abort();
            return;
        }
        SimpleContainer inv = worker.getInventory();
        Item input = step.inputs().keySet().iterator().next();
        if (!furnaceLoaded) {
            // Whatever is finished in there belongs to the village; take it along.
            ItemStack old = furnace.getItem(2);
            if (!old.isEmpty()) {
                worker.carry(old.getItem(), old.getCount());
                furnace.setItem(2, ItemStack.EMPTY);
            }
            ItemStack slotIn = furnace.getItem(0);
            if (!slotIn.isEmpty() && !slotIn.is(input)) {
                return; // busy with somebody else's batch
            }
            int n = Math.min(step.times(), inv.countItem(input));
            if (n < step.times() || inv.countItem(step.fuel()) < step.fuelCount()) {
                abort();
                return;
            }
            inv.removeItemType(input, n);
            furnace.setItem(0, new ItemStack(input, slotIn.getCount() + n));
            ItemStack fuelSlot = furnace.getItem(1);
            if (fuelSlot.isEmpty() || fuelSlot.is(step.fuel())) {
                inv.removeItemType(step.fuel(), step.fuelCount());
                furnace.setItem(1, new ItemStack(step.fuel(), fuelSlot.getCount() + step.fuelCount()));
            }
            furnace.setChanged();
            furnaceLoaded = true;
            timer = 0;
            return;
        }
        ItemStack out = furnace.getItem(2);
        boolean done = out.getCount() >= step.times() * step.outputCount() || furnace.getItem(0).isEmpty() && timer > 20;
        if (done || timer > SMELT_TIMEOUT * Math.max(1, step.times())) {
            if (!out.isEmpty()) {
                worker.carry(out.getItem(), out.getCount());
                furnace.setItem(2, ItemStack.EMPTY);
                furnace.setChanged();
            }
            furnaceLoaded = false;
            stepIndex++;
            timer = 0;
        }
    }

    private void finishOrder(ServerLevel level, Village village) {
        if (setUp != null) {
            Item made = setUp;
            setUp = null;
            BlockPos spot = Stations.spotNearBell(level, village);
            if (spot != null && worker.getInventory().countItem(made) > 0) {
                worker.getInventory().removeItemType(made, 1);
                Block block = made == Items.FURNACE ? Blocks.FURNACE : Blocks.CRAFTING_TABLE;
                level.setBlock(spot, block.defaultBlockState(), Block.UPDATE_ALL);
                level.playSound(null, spot, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            go(State.PICK);
            return;
        }
        if (request != null) {
            int made = worker.getInventory().countItem(request.item());
            request.fulfil(made);
            request.claim(null);
        }
        go(State.DELIVER);
    }

    private void abort() {
        if (request != null) {
            request.claim(null);
        }
        request = null;
        setUp = null;
        furnaceLoaded = false;
        go(worker.carried() > 0 ? State.DELIVER : State.PICK);
    }

    private void go(State next) {
        state = next;
        timer = 0;
    }
}
