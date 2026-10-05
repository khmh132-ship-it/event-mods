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
                new R00Wake(),
                new R01Sync(),
                new R02Candles(),
                new R03Describe(),
                new R04Exit(),
                new R99DemoEnd()
        );
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
    static void buildConnector(ServerLevel lvl, Room a, Room b, int nextIndex) {
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
        c.sign(len / 2, 3, 1, Direction.SOUTH, net.minecraft.world.item.DyeColor.LIGHT_BLUE,
                "ИСПЫТАНИЕ " + nextIndex, "", b.title.toUpperCase(), "");
    }
}
