package dev.khmh.trialcomplex.game;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.rooms.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** Порядок испытаний и их расположение в мире: цепочка вдоль +X, соединённая шлюзами. */
public final class Layout {
    public static final int BASE_Y = 64;
    public static final int GAP = 7; // длина шлюза между комнатами

    private Layout() {}

    public static List<Room> create() {
        return List.of(
                new C00Cells(),
                new RCommand("cmd1", 1),
                new RLaser("laser1", 1),
                new RBridge("bridge1", 1),
                new RCows("cows1", 1),
                new RMines("mines1", 1),
                new RLights("lights1", 1),
                new RMaze("maze1", 1),
                new RMelody("melody1", 1),
                new RLaser("laser2", 2),
                new RRiddle("riddle1", 1),
                new RSlide("slide1", 1),
                new RCommand("cmd2", 2),
                new RDoors("doors1", 1),
                new RBridge("bridge2", 2),
                new RKnights("knights1", 1),
                new RMines("mines2", 2),
                new RPersp("persp1", 1),
                new RLights("lights2", 2),
                new RParkour("parkour1", 1),
                new RLaser("laser3", 3),
                new RCipher("cipher1", 1),
                new RMelody("melody2", 2),
                new RMaze("maze2", 2),
                new RCows("cows2", 2),
                new RDoors("doors2", 2),
                new RLaser("laser4", 4),
                new RParkour("parkour2", 2),
                new RRiddle("riddle2", 2),
                new RSlide("slide2", 2),
                new RMines("mines3", 3),
                new RKnights("knights2", 2),
                new RCommand("cmd3", 3),
                new RPersp("persp2", 2),
                new RLights("lights3", 3),
                new RCipher("cipher2", 2),
                new RMelody("melody3", 3),
                new RDoors("doors3", 3),
                new RBridge("bridge3", 3),
                new RParkour("parkour3", 3),
                new RLights("lights4", 4),
                new RRiddle("riddle3", 3),
                new RKnights("knights3", 3),
                new RMaze("maze3", 3),
                new RCows("cows3", 3),
                new RDoors("doors4", 4),
                new RLaser("laser5", 5),
                new RLaser("laser6", 6),
                new RCore()
        );
    }

    /** Пароль финала — цифры, нарисованные в шлюзах (по порядку). */
    public static final String FINAL_CODE = "4817";

    public static String finalCode() {
        return FINAL_CODE;
    }

    /** Номера шлюзов (индекс комнаты, после которой шлюз) с цифрами пароля. */
    public static int digitConnector(int idx, int total) {
        for (int k = 0; k < 4; k++) if (idx == (k + 1) * total / 5) return k;
        return -1;
    }

    static void place(Complex cx, List<Room> rooms) {
        int x = 0;
        int doorZ = 0;
        for (Room r : rooms) {
            r.place(cx, new BlockPos(x, BASE_Y, doorZ - r.entryZ));
            doorZ = r.origin.getZ() + r.exitZ;
            x += r.sx + GAP;
        }
    }

    /** Шлюз между a и b: короткий коридор 3×4 с подсветкой и табличкой следующего испытания. */
    static void buildConnector(ServerLevel lvl, Room a, Room b, int nextIndex, int total) {
        BlockPos start = a.at(a.sx, 0, a.exitZ - 2);
        Builder c = new Builder(lvl, start, 99L + nextIndex);
        int len = GAP;
        Palette wall = Palette.of(Blocks.POLISHED_DEEPSLATE, 5, Blocks.DEEPSLATE_TILES, 2, Blocks.CRACKED_DEEPSLATE_TILES, 1);
        Palette floor = Palette.of(Blocks.POLISHED_BASALT.defaultBlockState(), 1);
        c.shell(len, 6, 5, floor, wall, Palette.of(Blocks.POLISHED_DEEPSLATE));
        // торцы — это стены соседних комнат; убираем торцы шлюза
        c.clear(0, 1, 1, 0, 4, 3);
        c.clear(len - 1, 1, 1, len - 1, 4, 3);
        // пол-решётка с подсветкой
        for (int i = 0; i < len; i++) {
            c.set(i, 0, 2, i % 2 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.POLISHED_BASALT.defaultBlockState());
            c.set(i, 0, 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
            c.set(i, 0, 3, Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int i = 1; i < len - 1; i += 2) {
            c.set(i, 5, 2, Blocks.OCHRE_FROGLIGHT);
            c.set(i, 4, 1, Builder.trapdoor(Blocks.IRON_TRAPDOOR, Direction.SOUTH, true, false));
            c.set(i, 4, 3, Builder.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, true, false));
        }
        // стрелки-полоски на стенах
        for (int i = 1; i < len - 1; i++) {
            c.set(i, 2, 0, Blocks.CYAN_TERRACOTTA);
            c.set(i, 2, 4, Blocks.CYAN_TERRACOTTA);
        }
        c.sign(len / 2, 3, 3, Direction.NORTH, net.minecraft.world.item.DyeColor.LIGHT_BLUE,
                b.countsAsTrial() ? "ИСПЫТАНИЕ " + nextIndex : "", "", b.title.toUpperCase(), "");
        int dk = digitConnector(nextIndex - 1, total);
        if (dk >= 0) {
            // большая светящаяся цифра на северной стене шлюза
            c.fill(1, 1, 0, len - 2, 5, 0, Blocks.BLACK_CONCRETE);
            for (int i = 1; i < len - 1; i++) c.set(i, 4, 1, Blocks.AIR);
            dev.khmh.trialcomplex.build.PixelText.draw(lvl, c.pos(2, 5, 0), Direction.EAST, String.valueOf(FINAL_CODE.charAt(dk)),
                    Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState());
            c.sign(5, 2, 1, Direction.SOUTH, net.minecraft.world.item.DyeColor.ORANGE, "ЦИФРА " + (dk + 1) + " ИЗ 4", "ЗАПОМНИТЕ.", "ПРИГОДИТСЯ.", "");
        }
    }
}
