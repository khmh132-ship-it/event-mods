package com.khmh.livingvillages.command;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.building.BuildingType;
import com.khmh.livingvillages.building.BuildingTypes;
import com.khmh.livingvillages.building.Construction;
import com.khmh.livingvillages.building.MaterialCost;
import com.khmh.livingvillages.building.SiteFinder;
import com.khmh.livingvillages.building.TemplateData;
import com.khmh.livingvillages.entity.VillageWorker;
import com.khmh.livingvillages.stock.Stockpile;
import com.khmh.livingvillages.village.Workers;
import com.khmh.livingvillages.village.GrowthPlanner;
import com.khmh.livingvillages.village.Production;
import com.khmh.livingvillages.village.Village;
import com.khmh.livingvillages.village.VillageEvents;
import com.khmh.livingvillages.village.VillageManager;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Debug and admin commands, all under {@code /village} (op level 2). */
public final class VillageCommand {
    private static final SimpleCommandExceptionType NOT_IN_VILLAGE =
            new SimpleCommandExceptionType(Component.literal("You are not inside a village"));
    private static final SimpleCommandExceptionType NO_BELL =
            new SimpleCommandExceptionType(Component.literal("No bell within 8 blocks"));
    private static final DynamicCommandExceptionType UNKNOWN_TYPE =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown building type: " + id));

    private VillageCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("village")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(VillageCommand::list))
                .then(Commands.literal("info").executes(VillageCommand::info))
                .then(Commands.literal("create").executes(VillageCommand::create))
                .then(Commands.literal("count").then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 96))
                        .executes(ctx -> {
                            // Debug: what lies within a radius (all heights below the surface), block -> count.
                            ServerLevel level = ctx.getSource().getLevel();
                            int r = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius");
                            BlockPos c = BlockPos.containing(ctx.getSource().getPosition());
                            java.util.Map<String, Integer> n = new java.util.TreeMap<>();
                            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
                                var st = level.getBlockState(p);
                                if (st.is(Blocks.OAK_FENCE) || st.is(Blocks.OAK_PLANKS) || st.is(Blocks.WALL_TORCH)
                                        || st.is(Blocks.TORCH) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.IRON_ORE)
                                        || st.is(Blocks.COAL_ORE) || st.isAir()) {
                                    n.merge(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(st.getBlock()).getPath(), 1, Integer::sum);
                                }
                            }
                            say(ctx, n.toString());
                            return 1;
                        })))
                .then(Commands.literal("found").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            BlockPos.containing(ctx.getSource().getPosition()));
                    Village v = com.khmh.livingvillages.village.Founding.found(level, VillageManager.get(level), at);
                    say(ctx, "Camp founded: " + shortId(v));
                    return 1;
                }))
                .then(Commands.literal("discover").executes(VillageCommand::discover))
                .then(Commands.literal("types").executes(VillageCommand::types))
                .then(Commands.literal("buildings").executes(VillageCommand::buildings))
                .then(Commands.literal("requests").executes(VillageCommand::requests))
                .then(Commands.literal("workers").executes(VillageCommand::workers))
                .then(Commands.literal("request")
                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 4096))
                                        .executes(VillageCommand::request))))
                .then(Commands.literal("build")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        BuildingTypes.all().stream().map(BuildingType::id), b))
                                .executes(ctx -> build(ctx, false))
                                .then(Commands.argument("free", BoolArgumentType.bool())
                                        .executes(ctx -> build(ctx, BoolArgumentType.getBool(ctx, "free"))))))
                .then(Commands.literal("finish").executes(VillageCommand::finish))
                .then(Commands.literal("plan").executes(VillageCommand::plan))
                .then(Commands.literal("produce")
                        .then(Commands.argument("cycles", IntegerArgumentType.integer(1, 10000))
                                .executes(VillageCommand::produce)))
                .then(Commands.literal("growth")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(VillageCommand::growth)))
                .then(Commands.literal("storage")
                        .then(Commands.literal("add")
                                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> changeStorage(ctx, true)))))
                        .then(Commands.literal("take")
                                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> changeStorage(ctx, false)))))));
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    private static String shortId(Village v) {
        return v.id().toString().substring(0, 8);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        VillageManager manager = VillageManager.get(ctx.getSource().getLevel());
        if (manager.all().isEmpty()) {
            say(ctx, "No villages in this dimension");
            return 0;
        }
        for (Village v : manager.all()) {
            BlockPos c = v.center();
            say(ctx, String.format("%s  bell %d %d %d  level %d  pop %d  buildings %d", shortId(v),
                    c.getX(), c.getY(), c.getZ(), v.level(), v.population(), v.buildings().size()));
        }
        return manager.all().size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Village v = here(ctx);
        BlockPos c = v.center();
        CommandSourceStack src = ctx.getSource();
        say(ctx, String.format("Village %s at %d %d %d, radius %d, level %d, population %d, beds %d",
                shortId(v), c.getX(), c.getY(), c.getZ(), v.radius(), v.level(), v.population(), v.beds()));
        say(ctx, "Professions: " + v.professions());
        long done = v.buildings().stream().filter(Building::isComplete).count();
        say(ctx, String.format("Buildings: %d finished, %d under construction%s", done,
                v.buildings().size() - done, v.waitingFor() == null ? "" : ", saving up for " + v.waitingFor()));
        if (src.getEntity() != null) {
            say(ctx, "Your reputation: " + v.reputation(src.getEntity().getUUID()));
        }
        Stockpile stock = v.stock(ctx.getSource().getLevel());
        say(ctx, String.format("Stock: %d chests/barrels, %.0f%% full, %d item kinds waiting outside",
                stock.positions().size(), stock.fill() * 100, v.storage().totals().size()));
        Map<Item, Integer> totals = stock.totals();
        if (totals.isEmpty()) {
            say(ctx, "Storage is empty");
        }
        totals.forEach((item, n) -> say(ctx, "  " + BuiltInRegistries.ITEM.getKey(item) + " x" + n));
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos from = BlockPos.containing(ctx.getSource().getPosition());
        BlockPos bell = null;
        for (BlockPos p : BlockPos.withinManhattan(from, 8, 8, 8)) {
            if (level.getBlockState(p).is(Blocks.BELL)) {
                bell = p.immutable();
                break;
            }
        }
        if (bell == null) {
            throw NO_BELL.create();
        }
        VillageManager manager = VillageManager.get(level);
        Optional<Village> existing = manager.byBell(bell);
        BlockPos found = bell;
        Village v = existing.orElseGet(() -> manager.create(found));
        say(ctx, (existing.isPresent() ? "Village already exists: " : "Village founded: ") + shortId(v));
        return 1;
    }

    private static int discover(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int n = VillageEvents.discoverAround(level, VillageManager.get(level),
                BlockPos.containing(ctx.getSource().getPosition()), 64);
        say(ctx, "Discovered " + n + " villages");
        return n;
    }

    private static int types(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        for (BuildingType t : BuildingTypes.all()) {
            String cost = TemplateData.get(level, t).map(d -> MaterialCost.describe(d.cost())
                    + "  size " + d.size().toShortString()).orElse("MISSING TEMPLATE");
            say(ctx, String.format("%s [%s, L%d]: %s", t.id(), t.group(), t.minLevel(), cost));
        }
        return BuildingTypes.all().size();
    }

    private static int buildings(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Village v = here(ctx);
        for (Building b : v.buildings()) {
            BlockPos o = b.origin();
            say(ctx, String.format("%s at %d %d %d %s: %s %d", b.typeId(), o.getX(), o.getY(), o.getZ(),
                    b.rotation(), b.phase(), b.progress()));
        }
        return v.buildings().size();
    }

    private static int workers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Village v = here(ctx);
        List<VillageWorker> list = Workers.of(ctx.getSource().getLevel(), v);
        for (VillageWorker w : list) {
            StringBuilder inv = new StringBuilder();
            for (int i = 0; i < w.getInventory().getContainerSize(); i++) {
                var s = w.getInventory().getItem(i);
                if (!s.isEmpty()) {
                    inv.append(' ').append(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()).append('x').append(s.getCount());
                }
            }
            say(ctx, String.format("%s @%s food %d, tool %s: %s |%s", w.job().name().toLowerCase(),
                    w.blockPosition().toShortString(), w.food(),
                    w.getMainHandItem().isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(w.getMainHandItem().getItem()).getPath(),
                    w.status(), inv));
        }
        return list.size();
    }

    private static int request(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Village v = here(ctx);
        Item item = ItemArgument.getItem(ctx, "item").getItem();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        v.request(item, count, "player", ctx.getSource().getLevel().getGameTime());
        say(ctx, "Ordered " + count + " " + BuiltInRegistries.ITEM.getKey(item).getPath());
        return 1;
    }

    private static int requests(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Village v = here(ctx);
        long now = ctx.getSource().getLevel().getGameTime();
        if (v.requests().isEmpty()) {
            say(ctx, "No open requests");
        }
        v.requests().forEach(r -> say(ctx, String.format("%s x%d for %s%s%s",
                BuiltInRegistries.ITEM.getKey(r.item()).getPath(), r.remaining(), r.requester(),
                r.claimedBy() != null ? ", being made" : "",
                r.unobtainableSince() >= 0 ? ", cannot be made (" + (now - r.unobtainableSince()) / 20 + "s)" : "")));
        return v.requests().size();
    }

    private static int build(CommandContext<CommandSourceStack> ctx, boolean free) throws CommandSyntaxException {
        Village v = here(ctx);
        String id = StringArgumentType.getString(ctx, "type");
        BuildingType type = BuildingTypes.get(id);
        if (type == null) {
            throw UNKNOWN_TYPE.create(id);
        }
        ServerLevel level = ctx.getSource().getLevel();
        TemplateData data = TemplateData.get(level, type).orElse(null);
        if (data == null) {
            ctx.getSource().sendFailure(Component.literal("Template missing: " + type.template()));
            return 0;
        }
        Optional<SiteFinder.Site> site = SiteFinder.find(level, v, type, data);
        if (site.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No free site for " + id));
            return 0;
        }
        if (free) {
            v.addBuilding(type, site.get(), level.getGameTime()).data().putBoolean("free", true);
        } else {
            GrowthPlanner.start(level, v, type, data, site.get(), level.getGameTime());
        }
        BlockPos o = site.get().origin();
        say(ctx, String.format("Started %s at %d %d %d facing %s", id, o.getX(), o.getY(), o.getZ(), site.get().rotation()));
        return 1;
    }

    private static int finish(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Village v = here(ctx);
        int n = 0;
        for (Building b : v.buildings()) {
            if (!b.isComplete()) {
                Construction.finish(ctx.getSource().getLevel(), v, b);
                n++;
            }
        }
        say(ctx, "Finished " + n + " buildings");
        return n;
    }

    private static int plan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Village v = here(ctx);
        int before = v.buildings().size();
        GrowthPlanner.planNow(ctx.getSource().getLevel(), v);
        if (v.buildings().stream().anyMatch(b -> !b.isComplete()) && v.buildings().size() == before) {
            say(ctx, "Busy: a building is under construction");
        }
        v.lastPlanReport().forEach(line -> say(ctx, "  " + line));
        return v.buildings().size() - before;
    }

    private static int produce(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Village v = here(ctx);
        int cycles = IntegerArgumentType.getInteger(ctx, "cycles");
        Production.runCycles(v, cycles, ctx.getSource().getLevel().getGameTime(), false);
        say(ctx, "Ran " + cycles + " production cycles");
        return cycles;
    }

    private static int growth(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Village v = here(ctx);
        v.setAutoGrowth(BoolArgumentType.getBool(ctx, "enabled"));
        say(ctx, "Auto growth " + (v.autoGrowth() ? "on" : "off"));
        return 1;
    }

    private static int changeStorage(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        Village v = here(ctx);
        Stockpile stock = v.stock(ctx.getSource().getLevel());
        Item item = ItemArgument.getItem(ctx, "item").getItem();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (add) {
            stock.add(item, count);
        } else if (!stock.take(item, count)) {
            ctx.getSource().sendFailure(Component.literal("Not enough " + id + " (have " + stock.count(item) + ")"));
            return 0;
        }
        say(ctx, id + " now " + stock.count(item));
        return 1;
    }

    private static Village here(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        return VillageManager.get(src.getLevel()).at(BlockPos.containing(src.getPosition()))
                .orElseThrow(NOT_IN_VILLAGE::create);
    }
}
