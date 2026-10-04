package com.khmh.livingvillages.village;

import com.khmh.livingvillages.building.CostKey;
import java.util.Map;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.BuildingType;
import com.khmh.livingvillages.building.BuildingTypes;
import com.khmh.livingvillages.building.MaterialCost;
import com.khmh.livingvillages.building.SiteFinder;
import com.khmh.livingvillages.building.TemplateData;
import com.khmh.livingvillages.config.LVConfig;
import com.khmh.livingvillages.stock.Stockpile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** Decides what a village builds next and where. One building at a time. */
public final class GrowthPlanner {
    /** The first rules: a village saves up for these rather than putting up something cheaper meanwhile. */
    private static final int ESSENTIALS = 5;

    private record Rule(String group, int minLevel, IntUnaryOperator maxAtLevel, Predicate<Village> when) {
        Rule(String group, int minLevel, int max) {
            this(group, minLevel, level -> max, v -> true);
        }
    }

    private static final List<Rule> RULES = List.of(
            // A camp's first worries: food, somewhere to keep things, wood, beds; then stone.
            new Rule("farm", 1, 1),
            // Beds before anything big: children are the only way a camp gets more hands.
            new Rule("house", 1, l -> 99, v -> v.beds() < v.population() + 2),
            // Mouths to feed: a field for every two of them (rounded up) and one over, whatever the village's level
            // (one field feeds a person and a half, and no food means no children: no level-up either).
            new Rule("farm", 1, l -> 99, v -> v.countGroup("farm") < (v.population() + 1) / 2 + 1),
            new Rule("warehouse", 1, l -> 99, v -> v.countGroup("warehouse") == 0 && v.population() >= 5
                    || v.stockFill() >= 0.8),
            new Rule("lumberjack", 1, 1),
            new Rule("mine", 1, 1),
            new Rule("farm", 1, l -> l, v -> true),
            new Rule("builder", 1, 1),
            new Rule("apprentice", 1, 1),
            new Rule("quest_board", 1, 1),
            new Rule("sawmill", 2, 1),
            new Rule("pen", 2, l -> l - 1, v -> true),
            new Rule("watchtower", 2, l -> l == 2 ? 1 : 3, v -> true),
            new Rule("workshop", 2, l -> l == 2 ? 4 : 8, v -> true),
            new Rule("town_hall", 3, 1),
            new Rule("tavern", 3, 1),
            new Rule("barracks", 3, 1),
            new Rule("golem_pad", 3, 1),
            new Rule("stable", 3, 1),
            new Rule("house", 1, l -> 99, v -> v.beds() < v.population() + 4 + 2 * v.level()));

    // All the wood and stone in hand before the first block goes down: no house standing half-built for half an hour.
    private static final double START_SHARE = 1.0;

    private GrowthPlanner() {
    }

    public static void tick(ServerLevel level, Village v, long now) {
        if (!LVConfig.AUTO_GROWTH.get() || !v.autoGrowth()) {
            return;
        }
        tickIgnoringConfig(level, v, now);
    }

    private static void tickIgnoringConfig(ServerLevel level, Village v, long now) {
        // Every few seconds at most: a builder done with one house should not stand about half a minute for the next.
        if (now - v.lastPlan() < Math.min(100, LVConfig.PLAN_INTERVAL.get())) {
            return;
        }
        v.setLastPlan(now);
        if (v.buildings().stream().anyMatch(b -> !b.isComplete())) {
            return;
        }
        String waiting = null;
        List<String> report = new ArrayList<>();
        for (int i = 0; i < RULES.size(); i++) {
            Rule rule = RULES.get(i);
            if (waiting != null && i >= ESSENTIALS) {
                break; // the basics (fields, a store, wood, beds) are saved up for before anything else
            }
            if (v.level() < rule.minLevel() || !rule.when().test(v)
                    || v.countGroup(rule.group()) >= rule.maxAtLevel().applyAsInt(v.level())) {
                continue;
            }
            BuildingType type = pick(v, rule.group(), level.getRandom());
            if (type == null) {
                continue;
            }
            TemplateData data = TemplateData.get(level, type).orElse(null);
            if (data == null) {
                report.add(type.id() + ": template missing");
                continue;
            }
            Stockpile stock = v.stock(level);
            // All the bulk is saved up before work starts; what it is made into (stairs, doors) is made as it goes up.
            // Only the bulk (wood, stone) is saved up for; glass, iron and the like are asked for while it goes
            // up, and a builder makes do without them if nobody can get any.
            Map<CostKey, Integer> bulk = new java.util.HashMap<>(data.cost());
            bulk.keySet().removeIf(k -> k instanceof CostKey.Of);
            // A field is cheap and food comes first: half its wood in hand is enough to start it.
            double share = "farm".equals(rule.group()) ? 0.5 : START_SHARE;
            if (!MaterialCost.canAfford(stock, bulk, share)) {
                if (waiting == null) {
                    waiting = type.id();
                }
                report.add(type.id() + ": needs " + MaterialCost.describeMissing(stock, bulk));
                continue; // build something cheaper meanwhile
            }
            Optional<SiteFinder.Site> site = SiteFinder.find(level, v, type, data);
            if (site.isEmpty()) {
                report.add(type.id() + ": no free site");
                continue;
            }
            start(level, v, type, data, site.get(), now);
            report.add(type.id() + ": started");
            v.setWaitingFor(null);
            v.setLastPlanReport(report);
            return;
        }
        v.setWaitingFor(waiting);
        v.setLastPlanReport(report);
    }

    /** Runs the planner right away, ignoring the plan interval and the global switch (debug). */
    public static void planNow(ServerLevel level, Village v) {
        v.setLastPlan(Long.MIN_VALUE / 2);
        boolean auto = v.autoGrowth();
        v.setAutoGrowth(true);
        tickIgnoringConfig(level, v, level.getGameTime());
        v.setAutoGrowth(auto);
    }

    /** Starts a building; materials are taken block by block as it goes up, not in advance. */
    public static Building start(ServerLevel level, Village v, BuildingType type, TemplateData data,
                                 SiteFinder.Site site, long now) {
        Building b = v.addBuilding(type, site, now);
        LivingVillages.LOGGER.info("Village {} starts {} at {}", v.id().toString().substring(0, 8), type.id(),
                site.origin());
        return b;
    }

    /** A type from the group allowed at the village's level, preferring ones the village does not have yet. */
    private static BuildingType pick(Village v, String group, RandomSource random) {
        List<BuildingType> options = BuildingTypes.inGroup(group, v.level());
        if (options.isEmpty()) {
            return null;
        }
        List<BuildingType> fresh = new ArrayList<>();
        for (BuildingType t : options) {
            if (v.countType(t.id()) == 0) {
                fresh.add(t);
            }
        }
        if (group.equals("workshop")) {
            options = fresh; // one of each workshop at most
        } else if (!fresh.isEmpty()) {
            options = fresh;
        }
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }
}
