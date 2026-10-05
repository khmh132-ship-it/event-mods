package dev.khmh.trialcomplex.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.khmh.trialcomplex.game.Complex;
import dev.khmh.trialcomplex.game.DevTools;
import dev.khmh.trialcomplex.game.ProgressData;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.nio.file.Path;

/**
 * /puzzle — управление прохождением. Основные команды доступны всем (в открытом мире без читов тоже).
 */
public final class PuzzleCommand {
    private PuzzleCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("puzzle")
                .executes(PuzzleCommand::help)
                .then(Commands.literal("help").executes(PuzzleCommand::help))
                .then(Commands.literal("hint").executes(c -> run(c, cx -> cx.hint(c.getSource().getPlayer()))))
                .then(Commands.literal("repeat").executes(c -> run(c, cx -> {
                    String last = cx.voice().lastSaid();
                    if (!last.isEmpty()) cx.voice().interrupt(last);
                })))
                .then(Commands.literal("back").executes(c -> run(c, cx -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    cx.toCheckpoint(p);
                })))
                .then(Commands.literal("reset").executes(c -> run(c, cx -> {
                    Room r = cx.current();
                    if (r != null) cx.resetRoom(r, true);
                })))
                .then(Commands.literal("skip").executes(c -> run(c, cx -> {
                    Room r = cx.current();
                    if (r != null) cx.skip(r);
                })))
                .then(Commands.literal("start").executes(c -> run(c, cx -> {
                    if (!cx.data().started) cx.startGame();
                })))
                .then(Commands.literal("goto").then(Commands.argument("n", IntegerArgumentType.integer(0))
                        .executes(c -> run(c, cx -> cx.gotoStage(IntegerArgumentType.getInteger(c, "n"))))))
                .then(Commands.literal("status").executes(PuzzleCommand::status))
                .then(Commands.literal("dev").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("free").executes(c -> run(c, cx -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            boolean on = cx.toggleBypass(p);
                            p.setGameMode(on ? GameType.CREATIVE : GameType.ADVENTURE);
                            c.getSource().sendSuccess(() -> Component.literal("Свободный режим: " + on), false);
                        })))
                        .then(Commands.literal("peek").executes(c -> run(c, cx -> {
                            Room r = cx.current();
                            String msg = "stage=" + cx.data().stage + " room=" + (r == null ? "-" : r.id + " origin=" + r.origin().getX() + "," + r.origin().getY() + "," + r.origin().getZ()
                                    + " spawn=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", r.spawn().x, r.spawn().y, r.spawn().z) + " size=" + r.sx + "," + r.sy + "," + r.sz + " active=" + r.isActive() + " " + r.debug());
                            c.getSource().sendSuccess(() -> Component.literal("PEEK " + msg), false);
                        })))
                        .then(Commands.literal("solve").executes(c -> run(c, cx -> {
                            Room r = cx.current();
                            String res = r == null ? "-" : r.devSolve();
                            c.getSource().sendSuccess(() -> Component.literal("PEEK SOLVE " + res), false);
                        })))
                        .then(Commands.literal("buildall").executes(c -> run(c, DevTools::buildAll)))
                        .then(Commands.literal("export").then(Commands.argument("room", StringArgumentType.word())
                                .executes(c -> run(c, cx -> {
                                    Room r = cx.room(StringArgumentType.getString(c, "room"));
                                    if (r != null) DevTools.export(cx, r, Path.of("exports"));
                                })))))
        );
    }

    private interface Action {
        void run(Complex cx) throws Exception;
    }

    private static int run(CommandContext<CommandSourceStack> c, Action a) {
        Complex cx = Complex.get();
        if (cx == null) return 0;
        try {
            a.run(cx);
            return 1;
        } catch (Exception e) {
            c.getSource().sendFailure(Component.literal("Ошибка: " + e.getMessage()));
            return 0;
        }
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        String[] lines = {
                "§6Испытательный комплекс — команды:",
                "§e/puzzle hint§7 — подсказка (их 3 на испытание, Голос запомнит)",
                "§e/puzzle repeat§7 — повторить последнюю реплику Голоса",
                "§e/puzzle back§7 — вернуться к чекпоинту (если застрял)",
                "§e/puzzle reset§7 — перезапустить текущее испытание",
                "§e/puzzle skip§7 — пропустить испытание (позор)",
                "§e/puzzle goto <n>§7 — перейти к испытанию n",
                "§e/puzzle status§7 — статистика",
                "§e/puzzle start§7 — начать без второго игрока",
        };
        for (String l : lines) c.getSource().sendSuccess(() -> Component.literal(l), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        Complex cx = Complex.get();
        if (cx == null) return 0;
        ProgressData d = cx.data();
        Room r = cx.current();
        int total = (int) cx.rooms().stream().filter(Room::countsAsTrial).count();
        long mins = d.started ? (cx.level().getGameTime() - d.startedAt) / 20 / 60 : 0;
        String where = r == null ? "всё пройдено" : r.countsAsTrial() ? d.stage + "/" + total + " — " + r.title : r.title;
        String[] lines = {
                "§6Испытание: §f" + where,
                "§6В комплексе: §f" + mins + " мин",
                "§6Подсказок: §f" + d.sum(d.hints) + "§6, пропусков: §f" + d.sum(d.skips) + "§6, ошибок: §f" + d.sum(d.fails),
                "§6Падений: §fКхмх — " + d.falls.getInt("khmh") + ", Итачи — " + d.falls.getInt("itachi"),
        };
        for (String l : lines) c.getSource().sendSuccess(() -> Component.literal(l).withStyle(ChatFormatting.RESET), false);
        return 1;
    }
}
