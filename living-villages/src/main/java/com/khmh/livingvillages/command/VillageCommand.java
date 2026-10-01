package com.khmh.livingvillages.command;

import com.khmh.livingvillages.village.Village;
import com.khmh.livingvillages.village.VillageManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Debug commands: {@code /village list|info|storage add|storage take}. */
public final class VillageCommand {
    private static final SimpleCommandExceptionType NOT_IN_VILLAGE =
            new SimpleCommandExceptionType(Component.literal("You are not inside a village"));

    private VillageCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("village")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(VillageCommand::list))
                .then(Commands.literal("info").executes(VillageCommand::info))
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

    private static int list(CommandContext<CommandSourceStack> ctx) {
        VillageManager manager = VillageManager.get(ctx.getSource().getLevel());
        if (manager.all().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No villages in this dimension"), false);
            return 0;
        }
        for (Village v : manager.all()) {
            BlockPos c = v.center();
            ctx.getSource().sendSuccess(() -> Component.literal(String.format("%s  bell %d %d %d  level %d  pop %d",
                    v.id().toString().substring(0, 8), c.getX(), c.getY(), c.getZ(), v.level(), v.population())), false);
        }
        return manager.all().size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Village v = here(ctx);
        BlockPos c = v.center();
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal(String.format("Village %s at %d %d %d, radius %d, level %d, population %d",
                v.id().toString().substring(0, 8), c.getX(), c.getY(), c.getZ(), v.radius(), v.level(), v.population())), false);
        if (src.getEntity() != null) {
            int rep = v.reputation(src.getEntity().getUUID());
            src.sendSuccess(() -> Component.literal("Your reputation: " + rep), false);
        }
        if (v.storage().view().isEmpty()) {
            src.sendSuccess(() -> Component.literal("Storage is empty"), false);
        }
        for (Object2IntMap.Entry<Item> e : v.storage().view().object2IntEntrySet()) {
            String id = BuiltInRegistries.ITEM.getKey(e.getKey()).toString();
            int count = e.getIntValue();
            src.sendSuccess(() -> Component.literal("  " + id + " x" + count), false);
        }
        return 1;
    }

    private static int changeStorage(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        Village v = here(ctx);
        Item item = ItemArgument.getItem(ctx, "item").getItem();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (add) {
            v.storage().add(item, count);
        } else if (!v.storage().take(item, count)) {
            ctx.getSource().sendFailure(Component.literal("Not enough " + id + " (have " + v.storage().count(item) + ")"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(id + " now " + v.storage().count(item)), false);
        return 1;
    }

    private static Village here(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        return VillageManager.get(src.getLevel()).at(BlockPos.containing(src.getPosition()))
                .orElseThrow(NOT_IN_VILLAGE::create);
    }
}
