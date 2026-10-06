package dev.khmh.trialcomplex.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Стиль зоны: из чего пол, стены, потолок, отделка, светильники. */
public record Theme(Palette floor, Palette wall, Palette ceil, BlockState trim, BlockState pillar, BlockState accent,
                    BlockState lamp, BlockState lampFrame, boolean floorGrid) {

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    public static final Theme LAB = new Theme(
            Palette.of(Blocks.POLISHED_DIORITE, 6, Blocks.CALCITE, 2, Blocks.SMOOTH_QUARTZ, 1),
            Palette.of(Blocks.WHITE_CONCRETE, 8, Blocks.SMOOTH_QUARTZ, 3, Blocks.CALCITE, 1),
            Palette.of(Blocks.SMOOTH_QUARTZ, 5, Blocks.WHITE_CONCRETE, 2),
            s(Blocks.LIGHT_GRAY_CONCRETE), Builder.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y), s(Blocks.CYAN_TERRACOTTA),
            s(Blocks.SEA_LANTERN), s(Blocks.LIGHT_GRAY_CONCRETE), true);

    public static final Theme VAULT = new Theme(
            Palette.of(Blocks.POLISHED_BLACKSTONE_BRICKS, 5, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, 1, Blocks.POLISHED_BLACKSTONE, 2),
            Palette.of(Blocks.POLISHED_BLACKSTONE, 5, Blocks.BLACKSTONE, 1, Blocks.POLISHED_BLACKSTONE_BRICKS, 3),
            Palette.of(Blocks.POLISHED_BLACKSTONE),
            s(Blocks.GILDED_BLACKSTONE), s(Blocks.CHISELED_POLISHED_BLACKSTONE), s(Blocks.GOLD_BLOCK),
            s(Blocks.SHROOMLIGHT), s(Blocks.POLISHED_BLACKSTONE_BRICKS), false);

    public static final Theme HALL = new Theme(
            Palette.of(Blocks.DARK_OAK_PLANKS, 5, Blocks.SPRUCE_PLANKS, 2),
            Palette.of(Blocks.STRIPPED_DARK_OAK_WOOD, 3, Blocks.DARK_OAK_PLANKS, 2, Blocks.SPRUCE_PLANKS, 1),
            Palette.of(Blocks.DARK_OAK_PLANKS),
            s(Blocks.SPRUCE_PLANKS), Builder.axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y), s(Blocks.RED_WOOL),
            s(Blocks.OCHRE_FROGLIGHT), s(Blocks.SPRUCE_PLANKS), false);

    public static final Theme MINE = new Theme(
            Palette.of(Blocks.GRAY_CONCRETE, 4, Blocks.POLISHED_ANDESITE, 3, Blocks.ANDESITE, 1),
            Palette.of(Blocks.LIGHT_GRAY_CONCRETE, 4, Blocks.STONE_BRICKS, 2, Blocks.POLISHED_ANDESITE, 2, Blocks.CRACKED_STONE_BRICKS, 1),
            Palette.of(Blocks.GRAY_CONCRETE, 3, Blocks.POLISHED_ANDESITE, 1),
            s(Blocks.YELLOW_CONCRETE), Builder.axis(Blocks.POLISHED_BASALT, Direction.Axis.Y), s(Blocks.BLACK_CONCRETE),
            s(Blocks.SEA_LANTERN), s(Blocks.IRON_BLOCK), false);

    public static final Theme LIGHT = new Theme(
            Palette.of(Blocks.BLACK_CONCRETE, 5, Blocks.GRAY_CONCRETE, 1),
            Palette.of(Blocks.GRAY_CONCRETE, 4, Blocks.BLACK_CONCRETE, 1, Blocks.POLISHED_DEEPSLATE, 2),
            Palette.of(Blocks.BLACK_CONCRETE),
            s(Blocks.POLISHED_DEEPSLATE), Builder.axis(Blocks.POLISHED_BASALT, Direction.Axis.Y), s(Blocks.RED_CONCRETE),
            s(Blocks.PEARLESCENT_FROGLIGHT), s(Blocks.POLISHED_DEEPSLATE), true);

    public static final Theme ILLUSION = new Theme(
            Palette.of(Blocks.PURPUR_BLOCK, 4, Blocks.PURPUR_PILLAR, 1, Blocks.END_STONE_BRICKS, 2),
            Palette.of(Blocks.END_STONE_BRICKS, 5, Blocks.PURPUR_BLOCK, 2, Blocks.END_STONE, 1),
            Palette.of(Blocks.PURPUR_BLOCK),
            s(Blocks.PURPUR_PILLAR), Builder.axis(Blocks.PURPUR_PILLAR, Direction.Axis.Y), s(Blocks.AMETHYST_BLOCK),
            s(Blocks.PEARLESCENT_FROGLIGHT), s(Blocks.PURPUR_BLOCK), false);

    public static final Theme MECH = new Theme(
            Palette.of(Blocks.WAXED_CUT_COPPER, 4, Blocks.WAXED_EXPOSED_CUT_COPPER, 2, Blocks.WAXED_WEATHERED_CUT_COPPER, 1),
            Palette.of(Blocks.BRICKS, 4, Blocks.MUD_BRICKS, 2, Blocks.PACKED_MUD, 1),
            Palette.of(Blocks.SPRUCE_PLANKS, 3, Blocks.STRIPPED_SPRUCE_WOOD, 1),
            s(Blocks.WAXED_CUT_COPPER), s(Blocks.WAXED_COPPER_BLOCK), s(Blocks.ORANGE_TERRACOTTA),
            s(Blocks.OCHRE_FROGLIGHT), s(Blocks.WAXED_CUT_COPPER), false);

    public static final Theme LOGIC = new Theme(
            Palette.of(Blocks.MOSSY_STONE_BRICKS, 2, Blocks.STONE_BRICKS, 5, Blocks.CRACKED_STONE_BRICKS, 1),
            Palette.of(Blocks.STONE_BRICKS, 5, Blocks.MOSSY_STONE_BRICKS, 2, Blocks.CRACKED_STONE_BRICKS, 1),
            Palette.of(Blocks.STONE_BRICKS),
            s(Blocks.CHISELED_STONE_BRICKS), Builder.axis(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y), s(Blocks.GREEN_TERRACOTTA),
            s(Blocks.GLOWSTONE), s(Blocks.STONE_BRICKS), false);

    public static final Theme CORE = new Theme(
            Palette.of(Blocks.NETHER_BRICKS, 4, Blocks.RED_NETHER_BRICKS, 2, Blocks.CRACKED_NETHER_BRICKS, 1),
            Palette.of(Blocks.BLACKSTONE, 3, Blocks.NETHER_BRICKS, 3, Blocks.RED_NETHER_BRICKS, 1),
            Palette.of(Blocks.NETHER_BRICKS),
            s(Blocks.CRIMSON_PLANKS), s(Blocks.CRYING_OBSIDIAN), s(Blocks.RED_CONCRETE),
            s(Blocks.SHROOMLIGHT), s(Blocks.NETHER_BRICKS), false);

    /** Детали отделки для стиля: «экран» в стене (две клетки), блок трубы под потолком, бордюр пола, встроенная лампа. */
    private BlockState[] details() {
        if (this == LAB) return new BlockState[]{s(Blocks.BLACK_STAINED_GLASS), s(Blocks.CYAN_STAINED_GLASS), Builder.axis(Blocks.POLISHED_BASALT, Direction.Axis.X), s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.SEA_LANTERN)};
        if (this == VAULT) return new BlockState[]{s(Blocks.GOLD_BLOCK), s(Blocks.CHISELED_POLISHED_BLACKSTONE), s(Blocks.GILDED_BLACKSTONE), s(Blocks.GILDED_BLACKSTONE), s(Blocks.SHROOMLIGHT)};
        if (this == HALL) return new BlockState[]{s(Blocks.BOOKSHELF), s(Blocks.BOOKSHELF), Builder.axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X), s(Blocks.SPRUCE_PLANKS), s(Blocks.OCHRE_FROGLIGHT)};
        if (this == MINE) return new BlockState[]{s(Blocks.YELLOW_CONCRETE), s(Blocks.BLACK_CONCRETE), Builder.axis(Blocks.POLISHED_BASALT, Direction.Axis.X), s(Blocks.YELLOW_CONCRETE), s(Blocks.SEA_LANTERN)};
        if (this == LIGHT) return new BlockState[]{s(Blocks.CRYING_OBSIDIAN), s(Blocks.OBSIDIAN), Builder.axis(Blocks.POLISHED_BASALT, Direction.Axis.X), s(Blocks.POLISHED_DEEPSLATE), s(Blocks.PEARLESCENT_FROGLIGHT)};
        if (this == ILLUSION) return new BlockState[]{s(Blocks.AMETHYST_BLOCK), s(Blocks.PURPUR_PILLAR), Builder.axis(Blocks.PURPUR_PILLAR, Direction.Axis.X), s(Blocks.PURPUR_BLOCK), s(Blocks.PEARLESCENT_FROGLIGHT)};
        if (this == MECH) return new BlockState[]{s(Blocks.WAXED_EXPOSED_COPPER), s(Blocks.WAXED_COPPER_BLOCK), Builder.axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X), s(Blocks.WAXED_CUT_COPPER), s(Blocks.OCHRE_FROGLIGHT)};
        if (this == LOGIC) return new BlockState[]{s(Blocks.CHISELED_STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), Builder.axis(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X), s(Blocks.CHISELED_STONE_BRICKS), s(Blocks.GLOWSTONE)};
        return new BlockState[]{s(Blocks.MAGMA_BLOCK), s(Blocks.RED_NETHER_BRICKS), Builder.axis(Blocks.CRIMSON_STEM, Direction.Axis.X), s(Blocks.RED_NETHER_BRICKS), s(Blocks.SHROOMLIGHT)};
    }

    private static BlockState rotX(BlockState st, Direction.Axis a) {
        return st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)
                ? st.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, a) : st;
    }

    /** Короб комнаты с отделкой. */
    public void shell(Builder b, int sx, int sy, int sz) {
        b.shell(sx, sy, sz, floor, wall, ceil);
        BlockState[] d = details();
        // трубы по кромке потолка
        for (int x = 1; x < sx - 1; x++) { b.set(x, sy - 1, 1, rotX(d[2], Direction.Axis.X)); b.set(x, sy - 1, sz - 2, rotX(d[2], Direction.Axis.X)); }
        for (int z = 1; z < sz - 1; z++) { b.set(1, sy - 1, z, rotX(d[2], Direction.Axis.Z)); b.set(sx - 2, sy - 1, z, rotX(d[2], Direction.Axis.Z)); }
        // бордюр пола
        if (!floorGrid) {
            for (int x = 1; x < sx - 1; x++) { b.set(x, 0, 1, d[3]); b.set(x, 0, sz - 2, d[3]); }
            for (int z = 1; z < sz - 1; z++) { b.set(1, 0, z, d[3]); b.set(sx - 2, 0, z, d[3]); }
        }
        if (floorGrid)
            for (int x = 0; x < sx; x++)
                for (int z = 0; z < sz; z++)
                    if (x % 4 == 0 || z % 4 == 0) b.set(x, 0, z, trim);
        // плинтус и карниз
        for (int x = 0; x < sx; x++) {
            b.set(x, 1, 0, trim); b.set(x, 1, sz - 1, trim);
            b.set(x, sy - 2, 0, trim); b.set(x, sy - 2, sz - 1, trim);
        }
        for (int z = 0; z < sz; z++) {
            b.set(0, 1, z, trim); b.set(sx - 1, 1, z, trim);
            b.set(0, sy - 2, z, trim); b.set(sx - 1, sy - 2, z, trim);
        }
        // цветная полоса
        if (sy >= 7) {
            for (int x = 1; x < sx - 1; x++) { b.set(x, 3, 0, accent); b.set(x, 3, sz - 1, accent); }
            for (int z = 1; z < sz - 1; z++) { b.set(0, 3, z, accent); b.set(sx - 1, 3, z, accent); }
        }
        // пилястры
        for (int x = 4; x < sx - 1; x += 4)
            for (int y = 2; y < sy - 2; y++) { b.set(x, y, 0, pillar); b.set(x, y, sz - 1, pillar); }
        for (int z = 4; z < sz - 1; z += 4)
            for (int y = 2; y < sy - 2; y++) { b.set(0, y, z, pillar); b.set(sx - 1, y, z, pillar); }
        // решётки вентиляции
        if (sy >= 7)
            for (int x = 2; x < sx - 2; x += 8) { b.set(x, sy - 3, 0, Blocks.IRON_BARS); b.set(x, sy - 3, sz - 1, Blocks.IRON_BARS); }
        // «экраны»/вставки в стенах между пилястрами и встроенные лампы
        if (sy >= 8) {
            for (int x = 6; x < sx - 3; x += 8) {
                for (int z : new int[]{0, sz - 1}) { b.set(x, 4, z, d[0]); b.set(x + 1, 4, z, d[1]); b.set(x, 5, z, d[1]); b.set(x + 1, 5, z, d[0]); }
            }
            for (int z = 6; z < sz - 3; z += 8) {
                for (int x : new int[]{0, sx - 1}) { b.set(x, 4, z, d[0]); b.set(x, 4, z + 1, d[1]); b.set(x, 5, z, d[1]); b.set(x, 5, z + 1, d[0]); }
            }
            for (int x = 2; x < sx - 2; x += 4) { b.set(x, sy - 3, 0, d[4]); b.set(x, sy - 3, sz - 1, d[4]); }
            for (int z = 2; z < sz - 2; z += 4) { b.set(0, sy - 3, z, d[4]); b.set(sx - 1, sy - 3, z, d[4]); }
        }
        lamps(b, sx, sy, sz);
    }

    /** Светильники 2×2 в рамках по сетке 4×4. */
    public void lamps(Builder b, int sx, int sy, int sz) {
        for (int x = 2; x < sx - 2; x += 4)
            for (int z = 2; z < sz - 2; z += 4) {
                for (int dx = -1; dx <= 2; dx++)
                    for (int dz = -1; dz <= 2; dz++)
                        if (x + dx > 0 && x + dx < sx - 1 && z + dz > 0 && z + dz < sz - 1) b.set(x + dx, sy - 1, z + dz, lampFrame);
                for (int dx = 0; dx <= 1; dx++)
                    for (int dz = 0; dz <= 1; dz++)
                        if (x + dx < sx - 1 && z + dz < sz - 1) b.set(x + dx, sy - 1, z + dz, lamp);
            }
    }
}
