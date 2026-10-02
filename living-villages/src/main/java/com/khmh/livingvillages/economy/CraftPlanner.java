package com.khmh.livingvillages.economy;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Works out how to get N of an item with the game's real recipes: what to take from the warehouse and which
 * crafts or smelts to do, in order. "20 oak stairs" becomes "take 8 oak logs; 2x logs to planks (by hand);
 * 4x planks to stairs (at a crafting table)". Smelting also needs fuel, which is planned the same way.
 */
public final class CraftPlanner {
    public enum Station { HAND, TABLE, FURNACE }

    /** One craft (or smelt) done {@code times} times. */
    public record Step(Station station, int times, Map<Item, Integer> inputs, Item output, int outputCount,
                       Item fuel, int fuelCount) {
    }

    public record Plan(Map<Item, Integer> fetch, List<Step> steps) {
        public boolean isEmpty() {
            return steps.isEmpty();
        }
    }

    private record Candidate(Station station, int outCount, List<Ingredient> ingredients) {
    }

    private static final int MAX_DEPTH = 4;
    private static final List<Item> FUELS = List.of(Items.COAL, Items.CHARCOAL);
    private static Map<Item, List<Candidate>> recipes;

    private CraftPlanner() {
    }

    public static void clearCache() {
        recipes = null;
    }

    public static boolean canMake(ServerLevel level, Item item) {
        return !recipesFor(level, item).isEmpty();
    }

    public static Optional<Plan> plan(ServerLevel level, Map<Item, Integer> stock, Item want, int count) {
        State s = new State(new HashMap<>(stock));
        return s.obtain(level, want, count, 0, new HashSet<>()) ? Optional.of(new Plan(s.fetch, s.steps)) : Optional.empty();
    }

    private static final class State {
        Map<Item, Integer> avail;
        Map<Item, Integer> fetch = new LinkedHashMap<>();
        List<Step> steps = new ArrayList<>();

        State(Map<Item, Integer> avail) {
            this.avail = avail;
        }

        State copy() {
            State c = new State(new HashMap<>(avail));
            c.fetch = new LinkedHashMap<>(fetch);
            c.steps = new ArrayList<>(steps);
            return c;
        }

        void adopt(State o) {
            avail = o.avail;
            fetch = o.fetch;
            steps = o.steps;
        }

        boolean obtain(ServerLevel level, Item item, int count, int depth, Set<Item> visiting) {
            int have = avail.getOrDefault(item, 0);
            int take = Math.min(have, count);
            if (take > 0) {
                avail.put(item, have - take);
                fetch.merge(item, take, Integer::sum);
            }
            int need = count - take;
            if (need == 0) {
                return true;
            }
            if (depth >= MAX_DEPTH || !visiting.add(item)) {
                return false;
            }
            try {
                for (Candidate c : recipesFor(level, item)) {
                    State trial = copy();
                    if (trial.craft(level, c, item, need, depth, visiting)) {
                        adopt(trial);
                        return true;
                    }
                }
                return false;
            } finally {
                visiting.remove(item);
            }
        }

        private boolean craft(ServerLevel level, Candidate c, Item output, int need, int depth, Set<Item> visiting) {
            int times = (need + c.outCount() - 1) / c.outCount();
            Map<Item, Integer> inputs = new LinkedHashMap<>();
            for (Ingredient ing : c.ingredients()) {
                // Every option of the ingredient in turn (coal, else charcoal burnt from logs...).
                boolean got = false;
                for (Item option : options(level, ing, visiting)) {
                    State trial = copy();
                    if (trial.obtain(level, option, times, depth + 1, visiting)) {
                        adopt(trial);
                        inputs.merge(option, times, Integer::sum);
                        got = true;
                        break;
                    }
                }
                if (!got) {
                    return false;
                }
            }
            Item fuel = Items.AIR;
            int fuelCount = 0;
            if (c.station() == Station.FURNACE) {
                fuel = chooseFuel();
                fuelCount = fuel == Items.COAL || fuel == Items.CHARCOAL ? (times + 7) / 8 : (times * 2 + 2) / 3;
                if (!obtain(level, fuel, fuelCount, depth + 1, visiting)) {
                    return false;
                }
            }
            steps.add(new Step(c.station(), times, inputs, output, c.outCount(), fuel, fuelCount));
            int surplus = times * c.outCount() - need;
            if (surplus > 0) {
                avail.merge(output, surplus, Integer::sum);
            }
            return true;
        }

        /** An ingredient's options: those we have, most first, then those we could make. */
        private List<Item> options(ServerLevel level, Ingredient ing, Set<Item> visiting) {
            List<Item> have = new ArrayList<>();
            List<Item> makeable = new ArrayList<>();
            for (ItemStack s : ing.getItems()) {
                Item item = s.getItem();
                if (have.contains(item) || makeable.contains(item)) {
                    continue;
                }
                if (avail.getOrDefault(item, 0) > 0) {
                    have.add(item);
                } else if (!visiting.contains(item) && !recipesFor(level, item).isEmpty()) {
                    makeable.add(item);
                }
            }
            have.sort(Comparator.comparingInt((Item i) -> avail.getOrDefault(i, 0)).reversed());
            have.addAll(makeable.subList(0, Math.min(4, makeable.size())));
            return have;
        }

        /** Coal or charcoal if there is any, otherwise wood (planks burn for 1.5 items each). */
        private Item chooseFuel() {
            for (Item f : FUELS) {
                if (avail.getOrDefault(f, 0) > 0) {
                    return f;
                }
            }
            Item bestWood = Items.OAK_PLANKS;
            int bestHave = -1;
            for (Map.Entry<Item, Integer> e : avail.entrySet()) {
                @SuppressWarnings("deprecation")
                boolean planks = e.getKey().builtInRegistryHolder().is(net.minecraft.tags.ItemTags.PLANKS);
                if (planks && e.getValue() > bestHave) {
                    bestHave = e.getValue();
                    bestWood = e.getKey();
                }
            }
            return bestWood;
        }
    }

    private static List<Candidate> recipesFor(ServerLevel level, Item item) {
        if (recipes == null) {
            recipes = index(level);
        }
        return recipes.getOrDefault(item, List.of());
    }

    private static Map<Item, List<Candidate>> index(ServerLevel level) {
        RegistryAccess access = level.registryAccess();
        Map<Item, List<Candidate>> out = new HashMap<>();
        for (CraftingRecipe r : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            add(out, r, access, r.canCraftInDimensions(2, 2) ? Station.HAND : Station.TABLE);
        }
        for (SmeltingRecipe r : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            add(out, r, access, Station.FURNACE);
        }
        villagerRecipes(out);
        // Simple recipes first: by hand, then table, then furnace; fewer ingredients first.
        out.values().forEach(list -> list.sort(Comparator.comparingInt((Candidate c) -> c.station().ordinal())
                .thenComparingInt(c -> c.ingredients().size())));
        return out;
    }

    /**
     * Crafts villagers know that players do not, where the vanilla way is impractical for a village: shepherds
     * spin wool into string, leatherworkers tan rotten flesh, masons crush stone into gravel and sand, fletchers
     * knap flint. Done at a crafting table until those professions have their own workstations.
     */
    private static void villagerRecipes(Map<Item, List<Candidate>> out) {
        villager(out, Items.STRING, 4, Ingredient.of(net.minecraft.tags.ItemTags.WOOL));
        villager(out, Items.LEATHER, 1, Ingredient.of(Items.ROTTEN_FLESH), Ingredient.of(Items.ROTTEN_FLESH),
                Ingredient.of(Items.ROTTEN_FLESH), Ingredient.of(Items.ROTTEN_FLESH));
        villager(out, Items.GRAVEL, 1, Ingredient.of(Items.COBBLESTONE), Ingredient.of(Items.COBBLESTONE));
        villager(out, Items.SAND, 1, Ingredient.of(Items.GRAVEL));
        villager(out, Items.FLINT, 1, Ingredient.of(Items.GRAVEL), Ingredient.of(Items.GRAVEL));
        // A bed without wool: a straw or hide mattress on a plank frame.
        Ingredient planks = Ingredient.of(net.minecraft.tags.ItemTags.PLANKS);
        villager(out, Items.WHITE_BED, 1, Ingredient.of(Items.WHEAT), Ingredient.of(Items.WHEAT),
                Ingredient.of(Items.WHEAT), planks, planks, planks);
        villager(out, Items.WHITE_BED, 1, Ingredient.of(Items.LEATHER), Ingredient.of(Items.LEATHER),
                Ingredient.of(Items.LEATHER), planks, planks, planks);
    }

    private static void villager(Map<Item, List<Candidate>> out, Item result, int count, Ingredient... ingredients) {
        out.computeIfAbsent(result, k -> new ArrayList<>()).add(new Candidate(Station.TABLE, count, List.of(ingredients)));
    }

    private static void add(Map<Item, List<Candidate>> out, Recipe<?> r, RegistryAccess access, Station station) {
        if (r.isSpecial()) {
            return;
        }
        ItemStack result = r.getResultItem(access);
        // Tools come with a Damage:0 tag; anything carrying other data (enchanted books, potions...) is skipped.
        boolean plainTag = !result.hasTag() || result.getTag().size() == 1 && result.getTag().contains("Damage");
        if (result.isEmpty() || !plainTag) {
            return;
        }
        List<Ingredient> ings = new ArrayList<>();
        for (Ingredient ing : r.getIngredients()) {
            if (!ing.isEmpty()) {
                if (ing.test(result)) {
                    return; // uses its own output (repairs, dyeing back and forth)
                }
                ings.add(ing);
            }
        }
        if (ings.isEmpty()) {
            return;
        }
        out.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(new Candidate(station, result.getCount(), ings));
    }
}
