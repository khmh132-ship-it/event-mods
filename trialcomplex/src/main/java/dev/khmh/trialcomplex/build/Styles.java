package dev.khmh.trialcomplex.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Общие стили отделки зон. */
public final class Styles {
    private Styles() {}

    // ---- «Лаборатория»: белая плитка, кварц, серые полосы ----
    public static final Palette LAB_FLOOR = Palette.of(Blocks.POLISHED_DIORITE, 6, Blocks.CALCITE, 2, Blocks.SMOOTH_QUARTZ, 1);
    public static final Palette LAB_WALL = Palette.of(Blocks.WHITE_CONCRETE, 8, Blocks.SMOOTH_QUARTZ, 3, Blocks.CALCITE, 1);
    public static final Palette LAB_CEIL = Palette.of(Blocks.SMOOTH_QUARTZ, 5, Blocks.WHITE_CONCRETE, 2);
    public static final Palette LAB_TRIM = Palette.of(Blocks.LIGHT_GRAY_CONCRETE);

    /** Лабораторная коробка с отделкой: плинтус, карниз, пилястры, сетка пола, вентиляция. */
    public static void lab(Builder b, int sx, int sy, int sz, BlockState lamp) {
        lab(b, sx, sy, sz, lamp, Blocks.CYAN_TERRACOTTA);
    }

    public static void lab(Builder b, int sx, int sy, int sz, BlockState lamp, net.minecraft.world.level.block.Block accent) {
        b.shell(sx, sy, sz, LAB_FLOOR, LAB_WALL, LAB_CEIL);
        // сетка пола
        for (int x = 0; x < sx; x++)
            for (int z = 0; z < sz; z++)
                if (x % 4 == 0 || z % 4 == 0) b.set(x, 0, z, Blocks.LIGHT_GRAY_CONCRETE);
        // плинтус и карниз
        for (int x = 0; x < sx; x++) {
            b.set(x, 1, 0, LAB_TRIM); b.set(x, 1, sz - 1, LAB_TRIM);
            b.set(x, sy - 2, 0, LAB_TRIM); b.set(x, sy - 2, sz - 1, LAB_TRIM);
        }
        for (int z = 0; z < sz; z++) {
            b.set(0, 1, z, LAB_TRIM); b.set(sx - 1, 1, z, LAB_TRIM);
            b.set(0, sy - 2, z, LAB_TRIM); b.set(sx - 1, sy - 2, z, LAB_TRIM);
        }
        // пилястры
        BlockState pillar = Builder.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y);
        for (int x = 4; x < sx - 1; x += 4)
            for (int y = 2; y < sy - 2; y++) {
                b.set(x, y, 0, pillar);
                b.set(x, y, sz - 1, pillar);
            }
        for (int z = 4; z < sz - 1; z += 4)
            for (int y = 2; y < sy - 2; y++) {
                b.set(0, y, z, pillar);
                b.set(sx - 1, y, z, pillar);
            }
        // цветная полоса зоны
        if (sy >= 7) {
            int ay = 3;
            for (int x = 1; x < sx - 1; x++) {
                if (b.get(x, ay, 0).is(Blocks.WHITE_CONCRETE) || b.get(x, ay, 0).is(Blocks.SMOOTH_QUARTZ) || b.get(x, ay, 0).is(Blocks.CALCITE)) b.set(x, ay, 0, accent.defaultBlockState());
                if (b.get(x, ay, sz - 1).is(Blocks.WHITE_CONCRETE) || b.get(x, ay, sz - 1).is(Blocks.SMOOTH_QUARTZ) || b.get(x, ay, sz - 1).is(Blocks.CALCITE)) b.set(x, ay, sz - 1, accent.defaultBlockState());
            }
            for (int z = 1; z < sz - 1; z++) {
                if (!b.get(0, ay, z).is(Blocks.QUARTZ_PILLAR)) b.set(0, ay, z, accent.defaultBlockState());
                if (!b.get(sx - 1, ay, z).is(Blocks.QUARTZ_PILLAR)) b.set(sx - 1, ay, z, accent.defaultBlockState());
            }
        }
        // вентиляционные решётки под потолком
        for (int x = 2; x < sx - 2; x += 4) {
            b.set(x, sy - 3, 0, Blocks.IRON_BARS);
            b.set(x, sy - 3, sz - 1, Blocks.IRON_BARS);
        }
        // потолочные светильники в рамках
        for (int x = 2; x < sx - 2; x += 4)
            for (int z = 2; z < sz - 2; z += 4) {
                for (int dx = -1; dx <= 2; dx++)
                    for (int dz = -1; dz <= 2; dz++) b.set(x + dx, sy - 1, z + dz, Blocks.LIGHT_GRAY_CONCRETE);
                ceilingLamp(b, x, sy - 1, z, lamp);
            }
    }

    /** Рамка проёма двери 3×4 в стене x (вход/выход), с индикатором над ней. */
    public static void doorFrame(Builder b, int x, int z, int sy) {
        BlockState frame = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        for (int y = 1; y <= 5 && y < sy - 1; y++) {
            b.set(x, y, z - 2, frame);
            b.set(x, y, z + 2, frame);
        }
        if (5 < sy - 1) for (int dz = -1; dz <= 1; dz++) b.set(x, 5, z + dz, frame);
    }

    /** Потолочная панель 2×2 со стеклом-рассеивателем вокруг. */
    public static void ceilingLamp(Builder b, int x, int y, int z, BlockState lamp) {
        b.set(x, y, z, lamp);
        b.set(x + 1, y, z, lamp);
        b.set(x, y, z + 1, lamp);
        b.set(x + 1, y, z + 1, lamp);
    }

    public static BlockState hangingLantern(boolean soul) {
        return (soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    // ---- «Архив»: тёмное дерево, книги ----
    public static final Palette ARCH_FLOOR = Palette.of(Blocks.DARK_OAK_PLANKS, 5, Blocks.SPRUCE_PLANKS, 2);
    public static final Palette ARCH_WALL = Palette.of(Blocks.DEEPSLATE_BRICKS, 6, Blocks.CRACKED_DEEPSLATE_BRICKS, 1, Blocks.POLISHED_DEEPSLATE, 2);
    public static final Palette ARCH_CEIL = Palette.of(Blocks.DARK_OAK_PLANKS);

    // ---- «Тех»: бетон, сталь ----
    public static final Palette TECH_FLOOR = Palette.of(Blocks.GRAY_CONCRETE, 4, Blocks.POLISHED_ANDESITE, 3, Blocks.ANDESITE, 1);
    public static final Palette TECH_WALL = Palette.of(Blocks.LIGHT_GRAY_CONCRETE, 5, Blocks.STONE_BRICKS, 2, Blocks.POLISHED_ANDESITE, 2);
    public static final Palette TECH_CEIL = Palette.of(Blocks.GRAY_CONCRETE, 3, Blocks.POLISHED_ANDESITE, 1);
}
