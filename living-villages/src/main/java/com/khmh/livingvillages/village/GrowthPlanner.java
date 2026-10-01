package com.khmh.livingvillages.village;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.BuildingType;
import com.khmh.livingvillages.building.BuildingTypes;
import com.khmh.livingvillages.building.MaterialCost;
import com.khmh.livingvillages.building.SiteFinder;
import com.khmh.livingvillages.building.TemplateData;
import com.khmh.livingvillages.config.LVConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** Decides what a village builds next and where. One building at a time. */
public final class GrowthPlanner {
    private record Rule(String group, int minLevel, IntUnaryOperator maxAtLevel, Predicate<Village> when) {
        Rule(String group, int minLevel, int max) {
            this(group, minLevel, level -> max, v -> true);
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("lumberjack", 1, 1),
            new Rule("house", 1, l -> 99, v -> v.beds() < v.population() + 2),
            new Rule("warehouse", 1, 1),
            new Rule("farm", 1, l -> l, v -> true),
            new Rule("builder", 1, 1),
            new Rule("quest_board", 1, 1),
            new Rule("mine", 2, 1),
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

    private GrowthPlanner() {
    }

    public static void tick(ServerLevel level, Village v, long now) {
        if (!LVConfig.AUTO_GROWTH.get() || !v.autoGrowth()) {
            return;
        }
        tickIgnoringConfig(level, v, now);
    }

    private static void tickIgnoringConfig(ServerLevel level, Village v, long now) {
        if (now - v.lastPlan() < LVConfig.PLAN_INTERVAL.get()) {
            return;
        }
        v.setLastPlan(now);
        if (v.buildings().stream().anyMatch(b -> !b.isComplete())) {
            return;
        }
        String waiting = null;
        List<String> report = new ArrayList<>();
        for (Rule rule : RULES) {
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
            if (!MaterialCost.canAfford(v.storage(), data.cost())) {
                if (waiting == null) {
                    waiting = type.id();
                }
                report.add(type.id() + ": needs " + MaterialCost.describeMissing(v.storage(), data.cost()));
                continue; // build something cheaper meanwhile
            }
            Optional<SiteFinder.Site> site = SiteFinder.find(level, v, type, data);
            if (site.isEmpty()) {
                report.add(type.id() + ": no free site");
                continue;
            }
            start(v, type, data, site.get(), now);
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

    public static Building start(Village v, BuildingType type, TemplateData data, SiteFinder.Site site, long now) {
        MaterialCost.pay(v.storage(), data.cost());
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
