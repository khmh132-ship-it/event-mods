package com.echohorror.command;

import com.echohorror.horror.HorrorDirector;
import com.echohorror.horror.Sanity;
import com.echohorror.horror.Scares;
import com.echohorror.item.NoteItem;
import com.echohorror.item.TapeItem;
import com.echohorror.story.Notes;
import com.echohorror.story.StoryData;
import com.echohorror.story.StoryManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

/** /echo — admin tools for the event host. */
public final class EchoCommand {
    private static final String[] PLACES = {"radio", "village", "bell", "cellar", "well", "depths", "lock", "bunker", "arena", "camp0", "camp1", "camp2", "pioneer"};

    private EchoCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("echo").requires(s -> s.hasPermission(2))
                .then(Commands.literal("start").executes(EchoCommand::start))
                .then(Commands.literal("status").executes(EchoCommand::status))
                .then(Commands.literal("reset").executes(c -> {
                    StoryManager.reset(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Сюжет сброшен (постройки остались в мире). При autoStartOnJoin=true он начнётся заново через 30 секунд."), true);
                    return 1;
                }))
                .then(Commands.literal("chapter").then(Commands.argument("n", IntegerArgumentType.integer(0, 7)).executes(c -> {
                    int n = IntegerArgumentType.getInteger(c, "n");
                    BlockPos near = BlockPos.containing(c.getSource().getPosition());
                    StoryManager.ensureBuilt(c.getSource().getServer(), n, near);
                    StoryData.get(c.getSource().getServer()).chapter = n - 1 < 0 ? 0 : n - 1;
                    StoryManager.setChapter(c.getSource().getServer(), n);
                    c.getSource().sendSuccess(() -> Component.literal("Глава установлена: " + n), true);
                    return 1;
                })))
                .then(Commands.literal("sanity").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0, 100)).executes(c -> {
                            Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
                            float v = FloatArgumentType.getFloat(c, "value");
                            for (ServerPlayer p : ps) Sanity.set(p, v);
                            c.getSource().sendSuccess(() -> Component.literal("Рассудок = " + v + " для " + ps.size() + " игр."), true);
                            return ps.size();
                        }))))
                .then(Commands.literal("scare").then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Scares.all().stream().map(Scares.Scare::name), b))
                                .executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "target");
                                    String name = StringArgumentType.getString(c, "name");
                                    boolean ok = HorrorDirector.force(p, name);
                                    c.getSource().sendSuccess(() -> Component.literal(name + ": " + (ok ? "выполнено" : "не сработало (нет условий)")), false);
                                    return ok ? 1 : 0;
                                }))))
                .then(Commands.literal("tp").then(Commands.argument("place", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(PLACES, b))
                        .executes(EchoCommand::tp)))
                .then(Commands.literal("kit").then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                    StoryData data = StoryData.get(c.getSource().getServer());
                    for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
                        data.kits.remove(p.getUUID());
                        StoryManager.giveKit(p);
                    }
                    return 1;
                })))
                .then(Commands.literal("siege").executes(c -> {
                    StoryManager.siege(c.getSource().getServer(), StoryData.get(c.getSource().getServer()));
                    c.getSource().sendSuccess(() -> Component.literal("Ночь Эха: из темноты идут три волны."), true);
                    return 1;
                }))
                .then(Commands.literal("scene").then(Commands.argument("which", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"lineup", "blackout", "dream", "house"}, b))
                        .executes(c -> {
                            String w = StringArgumentType.getString(c, "which");
                            boolean ok = StoryManager.scene(c.getSource().getServer(), w);
                            if (ok) c.getSource().sendSuccess(() -> Component.literal("Сцена: " + w), true);
                            else c.getSource().sendFailure(Component.literal("Нельзя запустить сейчас: " + w));
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("ending").then(Commands.argument("which", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"silence", "echo", "lullaby"}, b))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String which = StringArgumentType.getString(c, "which");
                            boolean ok = which.equals("lullaby") ? StoryManager.tryLullaby(p) : StoryManager.chooseEnding(p, which.equals("echo"));
                            if (!ok) c.getSource().sendFailure(Component.literal("Концовку можно выбрать только после победы над Отголоском."));
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("note").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Notes.all().keySet(), b))
                        .executes(c -> {
                            String id = StringArgumentType.getString(c, "id");
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ItemStack s = id.startsWith("tape") ? TapeItem.create(id) : NoteItem.create(id);
                            if (!p.getInventory().add(s)) p.drop(s, false);
                            return 1;
                        }))));
    }

    private static int start(CommandContext<CommandSourceStack> c) {
        StoryData d = StoryData.get(c.getSource().getServer());
        if (d.chapter != 0) {
            c.getSource().sendFailure(Component.literal("Сюжет уже идёт (глава " + d.chapter + "). Используйте /echo reset."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Эхо просыпается..."), true);
        StoryManager.start(c.getSource().getServer(), BlockPos.containing(c.getSource().getPosition()));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        StoryData d = StoryData.get(c.getSource().getServer());
        StringBuilder sb = new StringBuilder();
        sb.append("Глава: ").append(d.chapter).append(" — ").append(StoryManager.chapterTitle(d.chapter).replace('|', ' ')).append('\n');
        sb.append("Цель: ").append(StoryManager.objective(d)).append('\n');
        sb.append("Ночей: ").append(d.nights).append(", записей: ").append(d.notes.size()).append('\n');
        d.pos.forEach((k, v) -> sb.append(k).append(": ").append(v.toShortString()).append('\n'));
        sb.append("Флаги: ").append(String.join(", ", d.flags));
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY), false);
        ServerPlayer p = c.getSource().getPlayer();
        if (p != null) {
            c.getSource().sendSuccess(() -> Component.literal("Ваш рассудок: " + Math.round(Sanity.get(p))), false);
            List<String> in = new ArrayList<>();
            d.regions.forEach((k, box) -> {
                if (box.contains(p.position())) in.add(k);
            });
            c.getSource().sendSuccess(() -> Component.literal("Вы в зонах: " + (in.isEmpty() ? "—" : String.join(", ", in))), false);
        }
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        StoryData d = StoryData.get(c.getSource().getServer());
        String place = StringArgumentType.getString(c, "place");
        BlockPos pos = switch (place) {
            case "radio" -> d.get("radio");
            case "bell" -> d.get("bell") == null ? null : d.get("bell").below(2);
            case "lock" -> d.get("lock") == null ? null : d.get("lock").north();
            case "camp0", "camp1", "camp2" -> d.get(place) == null ? null : d.get(place).south(4);
            case "pioneer" -> d.get(place) == null ? null : d.get(place).south(10);
            default -> d.get(place);
        };
        if (pos == null) {
            c.getSource().sendFailure(Component.literal("Локация ещё не построена: " + place));
            return 0;
        }
        p.teleportTo(c.getSource().getServer().overworld(), pos.getX() + 0.5, pos.getY() + (place.equals("depths") || place.equals("arena") ? 0 : 1), pos.getZ() + 0.5, p.getYRot(), p.getXRot());
        return 1;
    }
}
