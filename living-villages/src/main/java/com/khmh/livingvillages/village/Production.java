package com.khmh.livingvillages.village;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.BuildingTypes;
import com.khmh.livingvillages.config.LVConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract economy: every production cycle each villager and each finished production building adds goods to
 * the stockpile, and the village eats. Runs whether or not the village is loaded; missed cycles are made up for
 * (up to a cap) so a village keeps growing while nobody is around.
 */
public final class Production {
    /** What a villager of a given profession gathers per cycle. "none" is an unemployed villager. */
    private static final Map<String, Map<Item, Double>> BY_PROFESSION = Map.ofEntries(
            Map.entry("none", Map.of(Items.OAK_LOG, 1.0, Items.COBBLESTONE, 0.5, Items.SAND, 0.25,
                    Items.WHITE_WOOL, 0.2, Items.IRON_INGOT, 0.1)),
            Map.entry("nitwit", Map.of(Items.OAK_LOG, 0.25)),
            Map.entry("farmer", Map.of(Items.WHEAT, 2.0, Items.CARROT, 0.5, Items.POTATO, 0.5, Items.BEETROOT, 0.25)),
            Map.entry("fisherman", Map.of(Items.COD, 1.0)),
            Map.entry("shepherd", Map.of(Items.WHITE_WOOL, 1.0)),
            Map.entry("butcher", Map.of(Items.BEEF, 0.5, Items.PORKCHOP, 0.5)),
            Map.entry("leatherworker", Map.of(Items.LEATHER, 0.5)),
            Map.entry("mason", Map.of(Items.COBBLESTONE, 2.0)),
            Map.entry("toolsmith", Map.of(Items.IRON_INGOT, 0.25, Items.COAL, 0.25)),
            Map.entry("weaponsmith", Map.of(Items.IRON_INGOT, 0.25, Items.COAL, 0.25)),
            Map.entry("armorer", Map.of(Items.IRON_INGOT, 0.25, Items.COAL, 0.25)),
            Map.entry("fletcher", Map.of(Items.STICK, 1.0)),
            // The village's own workers, for the time nobody is around to watch them do it for real.
            Map.entry("lumberjack", Map.of(Items.OAK_LOG, 4.0, Items.OAK_SAPLING, 0.5, Items.APPLE, 0.2)),
            Map.entry("miner", Map.of(Items.COBBLESTONE, 6.0, Items.COAL, 0.5, Items.RAW_IRON, 0.3,
                    Items.RAW_COPPER, 0.2)),
            Map.entry("cleric", Map.of(Items.ROTTEN_FLESH, 0.1)),
            Map.entry("librarian", Map.of(Items.SUGAR_CANE, 0.5)));

    /** Professions whose output comes from the world itself while the village is loaded. */
    private static final Set<String> REAL_WHEN_LOADED = Set.of("none", "nitwit", "farmer", "lumberjack", "miner",
            "butcher", "shepherd", "fisherman", "cleric", "librarian");
    /** Building groups worked for real while loaded. */
    private static final Set<String> REAL_BUILDINGS = Set.of("lumberjack", "mine", "farm");

    private static final List<Item> FOOD = List.of(Items.BREAD, Items.WHEAT, Items.CARROT, Items.POTATO,
            Items.BEETROOT, Items.COD, Items.SALMON, Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN);
    private static final double FOOD_PER_VILLAGER = 0.25;

    private Production() {
    }

    public static void tick(Village v, long now, boolean loaded) {
        int interval = LVConfig.PRODUCTION_INTERVAL.get();
        if (v.lastProduction() <= 0) {
            v.setLastProduction(now);
            return;
        }
        long due = (now - v.lastProduction()) / interval;
        if (due <= 0) {
            return;
        }
        int cycles = (int) Math.min(due, LVConfig.MAX_CATCHUP_CYCLES.get());
        // Cycles that passed while nobody was around are simulated; only the current one is "live".
        int live = loaded ? 1 : 0;
        runCycles(v, cycles - live, now, false);
        runCycles(v, live, now, true);
        v.setLastProduction(cycles < due ? now : v.lastProduction() + due * interval);
    }

    public static void runCycles(Village v, int cycles, long now, boolean loaded) {
        for (int i = 0; i < cycles; i++) {
            cycle(v, now, loaded);
        }
    }

    /**
     * One production cycle. In an unloaded village everything is simulated. In a loaded one, work that is done
     * for real is left out: unemployed villagers just idle, farmers' harvest is collected from their inventories
     * (see {@link Gathering}), and buildings whose job has a real worker (lumberjack hut, mine) or real villagers
     * (farms) produce nothing by themselves. Professions without a real implementation yet stay simulated.
     */
    private static void cycle(Village v, long now, boolean loaded) {
        if (v.population() <= 0) {
            return; // nobody left to work
        }
        double efficiency = loaded || eat(v) ? 1.0 : 0.5;
        double mult = LVConfig.PRODUCTION_MULTIPLIER.get() * efficiency;
        Map<Item, Double> out = new HashMap<>();
        int employed = 0;
        for (Map.Entry<String, Integer> e : v.professions().entrySet()) {
            if (!e.getKey().equals("none")) {
                employed += e.getValue();
            }
            if (loaded && REAL_WHEN_LOADED.contains(e.getKey())) {
                continue;
            }
            Map<Item, Double> rates = BY_PROFESSION.get(e.getKey());
            if (rates != null) {
                rates.forEach((item, rate) -> out.merge(item, rate * e.getValue(), Double::sum));
            }
        }
        // Villagers not seen yet (e.g. counted while unloaded) gather like unemployed ones.
        int unknown = v.population() - employed - v.professions().getOrDefault("none", 0);
        if (unknown > 0 && !loaded) {
            BY_PROFESSION.get("none").forEach((item, rate) -> out.merge(item, rate * unknown, Double::sum));
        }
        for (Building b : v.buildings()) {
            if (!b.isComplete() || BuildingTypes.get(b.typeId()) == null) {
                continue;
            }
            // A worker doing the job for real replaces the building's simulated output.
            boolean worked = v.isWorkedRecently(b.id(), now, 3L * LVConfig.PRODUCTION_INTERVAL.get());
            if (worked || loaded && REAL_BUILDINGS.contains(b.type().group())) {
                continue;
            }
            b.type().produces().forEach((item, rate) -> out.merge(item, rate, Double::sum));
        }
        int cap = v.storageCap();
        out.forEach((item, amount) -> {
            int whole = v.accumulate(item, amount * mult);
            if (whole > 0) {
                int room = Math.max(0, cap - v.storage().count(item));
                v.storage().add(item, Math.min(whole, room));
            }
        });
    }

    /** Takes this cycle's food; false if the village went hungry. */
    private static boolean eat(Village v) {
        int need = v.accumulateNeed(v.population() * FOOD_PER_VILLAGER);
        for (Item food : FOOD) {
            if (need <= 0) {
                break;
            }
            int n = Math.min(need, v.storage().count(food));
            if (n > 0) {
                v.storage().take(food, n);
                need -= n;
            }
        }
        return need <= 0;
    }
}
