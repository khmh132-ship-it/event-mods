package com.echohorror.story;

import com.echohorror.EchoHorror;
import com.echohorror.block.ShardLockBlock;
import com.echohorror.entity.SilentEntity;
import com.echohorror.item.NoteItem;
import com.echohorror.item.TapeItem;
import com.echohorror.registry.ModBlocks;
import com.echohorror.registry.ModEntities;
import com.echohorror.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural story locations, built block by block at runtime.
 * All coordinates inside a builder are relative to its origin; y = 0 is the first air layer above the floor.
 */
public final class Structures {
    private Structures() {}

    // =====================================================================================================
    // Builder
    // =====================================================================================================
    static final class B {
        final ServerLevel level;
        final BlockPos o;
        final RandomSource r;
        final List<BlockPos> connect = new ArrayList<>();

        B(ServerLevel level, BlockPos origin) {
            this.level = level;
            this.o = origin;
            this.r = level.random;
        }

        BlockPos at(int x, int y, int z) {
            return o.offset(x, y, z);
        }

        boolean valid(BlockPos p) {
            return p.getY() > level.getMinBuildHeight() + 1 && p.getY() < level.getMaxBuildHeight() - 1;
        }

        void set(int x, int y, int z, BlockState s) {
            BlockPos p = at(x, y, z);
            if (!valid(p)) return;
            if (level.getBlockState(p).is(Blocks.BEDROCK)) return;
            level.setBlock(p, s, 2);
        }

        void set(int x, int y, int z, Block b) {
            set(x, y, z, b.defaultBlockState());
        }

        /** Set a block that should connect to neighbours (panes, bars, fences, walls, stairs). */
        void setC(int x, int y, int z, BlockState s) {
            set(x, y, z, s);
            connect.add(at(x, y, z));
        }

        void setC(int x, int y, int z, Block b) {
            setC(x, y, z, b.defaultBlockState());
        }

        BlockState get(int x, int y, int z) {
            return level.getBlockState(at(x, y, z));
        }

        boolean air(int x, int y, int z) {
            return get(x, y, z).isAir();
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
            fill(x1, y1, z1, x2, y2, z2, b.defaultBlockState());
        }

        /** Sets air and plugs any neighbouring fluid so caves can't flood the structure. */
        void carve(int x, int y, int z) {
            set(x, y, z, Blocks.AIR.defaultBlockState());
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos n = at(x, y, z).relative(dir);
                if (valid(n) && !level.getFluidState(n).isEmpty()) level.setBlock(n, Blocks.TUFF.defaultBlockState(), 2);
            }
        }

        void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
            fill(x1, y1, z1, x2, y2, z2, Blocks.AIR.defaultBlockState());
        }

        /** Random pick from a palette. */
        BlockState pick(Block... blocks) {
            return blocks[r.nextInt(blocks.length)].defaultBlockState();
        }

        void fillMix(int x1, int y1, int z1, int x2, int y2, int z2, Block... palette) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, pick(palette));
        }

        /** Hollow box: walls + floor + ceiling from the palette, interior air. */
        void shell(int x1, int y1, int z1, int x2, int y2, int z2, Block[] walls, Block[] floor, Block[] ceil) {
            for (int x = x1; x <= x2; x++)
                for (int y = y1; y <= y2; y++)
                    for (int z = z1; z <= z2; z++) {
                        boolean edgeX = x == x1 || x == x2, edgeZ = z == z1 || z == z2;
                        if (y == y1) set(x, y, z, pick(floor));
                        else if (y == y2) set(x, y, z, pick(ceil));
                        else if (edgeX || edgeZ) set(x, y, z, pick(walls));
                        else set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
        }

        /** Randomly knocks out non-air blocks. */
        void decay(int x1, int y1, int z1, int x2, int y2, int z2, float chance) {
            for (int x = x1; x <= x2; x++)
                for (int y = y1; y <= y2; y++)
                    for (int z = z1; z <= z2; z++)
                        if (r.nextFloat() < chance && !air(x, y, z)) set(x, y, z, Blocks.AIR.defaultBlockState());
        }

        void cobwebs(int x1, int y1, int z1, int x2, int y2, int z2, float chance) {
            for (int x = x1; x <= x2; x++)
                for (int y = y1; y <= y2; y++)
                    for (int z = z1; z <= z2; z++)
                        if (r.nextFloat() < chance && air(x, y, z)) set(x, y, z, Blocks.COBWEB);
        }

        /** Levels the ground: solid fill under y=-1, top layer from palette, air above up to clearHeight. */
        void prepare(int x1, int z1, int x2, int z2, int clearHeight, Block... top) {
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    for (int y = 0; y <= clearHeight; y++) set(x, y, z, Blocks.AIR.defaultBlockState());
                    set(x, -1, z, pick(top));
                    for (int y = -2; y >= -14; y--) {
                        BlockState s = get(x, y, z);
                        if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) break;
                        set(x, y, z, y > -4 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
                    }
                }
        }

        void items(BlockPos p, ItemStack... stacks) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof Container c) {
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < c.getContainerSize(); i++) slots.add(i);
                for (ItemStack s : stacks) {
                    if (slots.isEmpty()) break;
                    int idx = slots.remove(r.nextInt(slots.size()));
                    c.setItem(idx, s);
                }
                be.setChanged();
            }
        }

        BlockPos chest(int x, int y, int z, Direction facing, ItemStack... stacks) {
            set(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
            items(at(x, y, z), stacks);
            return at(x, y, z);
        }

        BlockPos barrel(int x, int y, int z, ItemStack... stacks) {
            set(x, y, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            items(at(x, y, z), stacks);
            return at(x, y, z);
        }

        /** Wall sign hanging on the block behind it (opposite of facing). */
        void sign(int x, int y, int z, Direction facing, String... lines) {
            set(x, y, z, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing));
            text(at(x, y, z), lines);
        }

        void standingSign(int x, int y, int z, int rotation, String... lines) {
            set(x, y, z, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation & 15));
            text(at(x, y, z), lines);
        }

        void text(BlockPos p, String... lines) {
            if (level.getBlockEntity(p) instanceof SignBlockEntity sbe) {
                SignText t = new SignText();
                for (int i = 0; i < Math.min(4, lines.length); i++) t = t.setMessage(i, Component.literal(lines[i]));
                t = t.setColor(net.minecraft.world.item.DyeColor.RED);
                sbe.setText(t, true);
                sbe.setWaxed(true);
            }
        }

        void door(int x, int y, int z, Block door, Direction facing, boolean open) {
            BlockState s = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.OPEN, open)
                    .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
            set(x, y, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(x, y + 1, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }

        void bed(int x, int y, int z, Direction facing, Block bed) {
            BlockState s = bed.defaultBlockState().setValue(BedBlock.FACING, facing);
            set(x, y, z, s.setValue(BedBlock.PART, BedPart.FOOT));
            set(x + facing.getStepX(), y, z + facing.getStepZ(), s.setValue(BedBlock.PART, BedPart.HEAD));
        }

        void ladder(int x, int y1, int y2, int z, Direction facing) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                set(x, y, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
        }

        BlockState stairs(Block b, Direction facing) {
            return b.defaultBlockState().setValue(StairBlock.FACING, facing);
        }

        BlockState stairsTop(Block b, Direction facing) {
            return stairs(b, facing).setValue(StairBlock.HALF, Half.TOP);
        }

        void candles(int x, int y, int z) {
            set(x, y, z, Blocks.BLACK_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + r.nextInt(4)));
        }

        /** Re-evaluates connection shapes for panes/bars/fences/walls/stairs. */
        void finish() {
            for (BlockPos p : connect) {
                BlockState s = level.getBlockState(p);
                BlockState u = Block.updateFromNeighbourShapes(s, level, p);
                if (u != s) level.setBlock(p, u, 2);
            }
            connect.clear();
        }

        StoryData.Box box(int x1, int y1, int z1, int x2, int y2, int z2) {
            BlockPos a = at(x1, y1, z1), b = at(x2, y2, z2);
            return new StoryData.Box(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        }
    }

    // =====================================================================================================
    // Site search
    // =====================================================================================================
    /** Finds a reasonably flat, dry site roughly {@code dist} blocks away from {@code from}. Returns air-level pos. */
    public static BlockPos findSite(ServerLevel level, BlockPos from, int dist, int radius) {
        return findSite(level, from, dist, radius, Double.NaN);
    }

    /** {@code preferred} is an angle in radians (NaN = random); candidates fan out around it. */
    public static BlockPos findSite(ServerLevel level, BlockPos from, int dist, int radius, double preferred) {
        return findSite(level, from, dist, radius, preferred, List.of());
    }

    /** Same, but stays at least 45 blocks away from everything in {@code avoid}. */
    public static BlockPos findSite(ServerLevel level, BlockPos from, int dist, int radius, double preferred, List<BlockPos> avoid) {
        RandomSource r = level.random;
        double base = Double.isNaN(preferred) ? r.nextDouble() * Math.PI * 2 : preferred;
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        double[] distances = {1.0, 1.35, 0.75, 1.7, 2.2};
        net.minecraft.world.level.chunk.ChunkGenerator gen = level.getChunkSource().getGenerator();
        net.minecraft.world.level.levelgen.RandomState rs = level.getChunkSource().randomState();
        search:
        for (double dm : distances) {
            for (int i = 0; i < 16; i++) {
                int k = (i + 1) / 2 * (i % 2 == 0 ? 1 : -1);
                double ang = base + k * (Math.PI * 2 / 16);
                int cx = from.getX() + (int) (Math.cos(ang) * dist * dm);
                int cz = from.getZ() + (int) (Math.sin(ang) * dist * dm);
                int[][] samples = {{0, 0}, {radius, radius}, {-radius, radius}, {radius, -radius}, {-radius, -radius},
                        {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}};
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, water = 0;
                List<Integer> hs = new ArrayList<>();
                for (int[] smp : samples) {
                    int x = cx + smp[0], z = cz + smp[1];
                    int h, floor;
                    if (level.hasChunk(x >> 4, z >> 4)) { // already generated: real heightmaps
                        h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                        BlockPos top = new BlockPos(x, h - 1, z);
                        if (!level.getFluidState(top).isEmpty()) floor = Math.min(floor, h - 2);
                    } else { // not generated: ask the noise, no chunk generation (keeps the server responsive)
                        floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                        h = Math.max(floor, level.getSeaLevel());
                    }
                    if (floor < level.getSeaLevel() || floor < h) water++;
                    hs.add(floor);
                    min = Math.min(min, floor);
                    max = Math.max(max, floor);
                }
                hs.sort(Integer::compare);
                int score = (max - min) + water * 25 + (int) (Math.abs(dm - 1.0) * 6);
                for (BlockPos a : avoid) {
                    if (a != null && Math.hypot(a.getX() - cx, a.getZ() - cz) < 45) score += 150;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = new BlockPos(cx, hs.get(hs.size() / 2), cz);
                }
                if (water == 0 && max - min <= 5) break search;
            }
        }
        if (best == null) best = from.offset(dist, 0, 0);
        EchoHorror.LOG.info("Site search from {} -> {} (score {})", from, best, bestScore);
        int y = Mth.clamp(best.getY(), level.getSeaLevel() - 2, level.getMaxBuildHeight() - 40);
        return new BlockPos(best.getX(), y, best.getZ());
    }

    private static ItemStack note(String id) {
        return NoteItem.create(id);
    }

    private static ItemStack it(net.minecraft.world.item.Item item, int n) {
        return new ItemStack(item, n);
    }

    // =====================================================================================================
    // Chapter 1-2: Relay station R-7
    // =====================================================================================================
    public static void buildRadio(ServerLevel level, BlockPos site, StoryData d) {
        EchoHorror.LOG.info("Building relay station at {}", site);
        B b = new B(level, site);
        b.prepare(-11, -11, 11, 11, 26, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.PODZOL);

        // fence
        for (int i = -10; i <= 10; i++) {
            int[][] pts = {{i, -10}, {i, 10}, {-10, i}, {10, i}};
            for (int[] p : pts) {
                if (p[1] == -10 && Math.abs(p[0]) <= 1) continue; // gate
                if (b.r.nextFloat() < 0.18f) continue;
                boolean post = (p[0] + p[1]) % 5 == 0 || Math.abs(p[0]) == 10 && Math.abs(p[1]) == 10;
                if (post) {
                    b.setC(p[0], 0, p[1], Blocks.MOSSY_COBBLESTONE_WALL);
                    b.setC(p[0], 1, p[1], Blocks.COBBLESTONE_WALL);
                } else {
                    b.setC(p[0], 0, p[1], Blocks.IRON_BARS);
                    if (b.r.nextFloat() < 0.7f) b.setC(p[0], 1, p[1], Blocks.IRON_BARS);
                }
            }
        }
        b.sign(2, 1, -11, Direction.NORTH, "РЕТРАНСЛЯТОР", "Р-7", "ВХОД", "ВОСПРЕЩЁН");
        b.set(2, 0, -11, Blocks.MOSSY_COBBLESTONE_WALL);
        b.set(2, 1, -10, Blocks.MOSSY_COBBLESTONE);

        // main building
        Block[] walls = {Blocks.GRAY_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.CRACKED_STONE_BRICKS, Blocks.ANDESITE, Blocks.STONE_BRICKS};
        b.shell(-5, -1, 1, 5, 5, 9, walls, new Block[]{Blocks.POLISHED_ANDESITE, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS},
                new Block[]{Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE, Blocks.STONE});
        b.decay(-5, 5, 1, 5, 5, 9, 0.08f);
        b.door(0, 0, 1, Blocks.SPRUCE_DOOR, Direction.NORTH, false);
        // windows: some glass, some boarded, some broken
        int[][] windows = {{-5, 4}, {-5, 7}, {5, 4}, {5, 7}, {-3, 1}, {3, 1}, {-2, 9}, {2, 9}};
        for (int[] w : windows) {
            float f = b.r.nextFloat();
            if (f < 0.45f) b.set(w[0], 2, w[1], Blocks.OAK_PLANKS);
            else if (f < 0.75f) b.setC(w[0], 2, w[1], Blocks.GLASS_PANE);
            else b.set(w[0], 2, w[1], Blocks.AIR);
        }
        // desk + console
        b.fill(-2, 0, 8, 2, 0, 8, Blocks.SPRUCE_PLANKS);
        b.set(0, 1, 8, ModBlocks.RADIO_CONSOLE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        b.set(-2, 1, 8, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        b.set(2, 1, 8, Blocks.DAYLIGHT_DETECTOR);
        b.setC(0, 0, 6, b.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        d.put("console", b.at(0, 1, 8));
        d.put("radio", b.at(0, 0, 5));
        d.put("radio_door", b.at(0, 0, 0));
        b.chest(-4, 0, 8, Direction.EAST, note("lis1"), note("lis2"), it(ModItems.BATTERY.get(), 2), it(Items.BREAD, 3));
        b.barrel(4, 0, 8, note("lis3"), it(ModItems.PILLS.get(), 1), it(Items.CANDLE, 2));
        b.chest(4, 0, 3, Direction.WEST, note("lis4"), it(ModItems.BATTERY.get(), 1), TapeItem.create("tape6"));
        // makeshift bed, stains, trail
        b.set(-4, 0, 3, Blocks.WHITE_CARPET);
        b.set(-4, 0, 4, Blocks.WHITE_CARPET);
        b.set(-3, 0, 4, Blocks.RED_CARPET);
        for (int z = 2; z <= 7; z++) if (b.r.nextFloat() < 0.75f) b.set(b.r.nextInt(2) - (z % 2), 0, z, Blocks.REDSTONE_WIRE);
        b.sign(-4, 2, 5, Direction.EAST, "ЭТО", "НЕ", "ОНА");
        b.sign(3, 2, 8, Direction.NORTH, "ОНО", "СЧИТАЕТ");
        b.sign(1, 2, 2, Direction.SOUTH, "НЕ", "ОТКРЫВАЙ");
        b.sign(-1, 3, 2, Direction.SOUTH, "три раза", "как всегда", "стучит Таня");
        b.sign(0, 3, 0, Direction.NORTH, "Серёжа", "открой", "мне холодно");
        b.cobwebs(-4, 3, 2, 4, 4, 8, 0.12f);

        // antenna mast
        b.fill(6, -1, -6, 8, 0, -4, Blocks.STONE_BRICKS);
        for (int y = 1; y <= 22; y++) {
            b.setC(7, y, -5, Blocks.IRON_BARS);
            if (y % 4 == 0) {
                b.setC(6, y, -5, Blocks.IRON_BARS);
                b.setC(8, y, -5, Blocks.IRON_BARS);
                b.setC(7, y, -6, Blocks.IRON_BARS);
                b.setC(7, y, -4, Blocks.IRON_BARS);
            }
        }
        b.set(7, 23, -5, Blocks.LIGHTNING_ROD);

        // generator shed
        b.shell(-9, -1, -8, -6, 3, -5, new Block[]{Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE}, new Block[]{Blocks.COBBLESTONE},
                new Block[]{Blocks.OAK_SLAB});
        b.clear(-6, 0, -7, -6, 1, -7);
        b.set(-8, 0, -7, Blocks.FURNACE);
        b.set(-7, 0, -6, Blocks.CAULDRON);
        b.chest(-8, 0, -6, Direction.EAST, note("station_order"), it(ModItems.BATTERY.get(), 3), it(ModItems.PILLS.get(), 1));

        // graves of the crew (one is empty and freshly dug)
        String[] names = {"Глушко", "Рябов", "Лисицын"};
        for (int i = 0; i < 3; i++) {
            int z = 3 + i * 2;
            b.set(-9, 0, z, Blocks.MOSSY_STONE_BRICKS);
            b.standingSign(-9, 1, z, 12, names[i], i == 2 ? "1951 — 1986" : "1950 — 1986");
            if (i == 2) {
                b.clear(-8, -2, z, -7, -1, z);
                b.set(-8, -3, z, Blocks.DIRT);
                b.set(-7, -3, z, Blocks.DIRT);
                b.set(-6, 0, z, Blocks.COARSE_DIRT);
            } else {
                b.set(-8, -1, z, Blocks.COARSE_DIRT);
                b.set(-7, -1, z, Blocks.COARSE_DIRT);
            }
        }
        b.finish();
        d.region("station", b.box(-11, -3, -11, 11, 25, 11));
        d.put("station_center", site);
    }

    // =====================================================================================================
    // Chapter 3: the village Tikhiy Log
    // =====================================================================================================
    private static void house(B b, int x1, int z1, boolean doorSouth, boolean burned) {
        int x2 = x1 + 6, z2 = z1 + 6;
        b.prepare(x1 - 1, z1 - 1, x2 + 1, z2 + 1, 10, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.DIRT_PATH);
        Block[] wall = burned ? new Block[]{Blocks.BLACKSTONE, Blocks.COAL_BLOCK, Blocks.BLACK_CONCRETE, Blocks.STRIPPED_DARK_OAK_LOG}
                : new Block[]{Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.DARK_OAK_PLANKS};
        b.fillMix(x1, -1, z1, x2, -1, z2, burned ? new Block[]{Blocks.NETHERRACK, Blocks.COARSE_DIRT, Blocks.BLACKSTONE}
                : new Block[]{Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.COARSE_DIRT});
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = 0; y <= 3; y++) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                    if (corner) b.set(x, y, z, burned ? Blocks.BASALT : Blocks.DARK_OAK_LOG);
                    else if (edge) b.set(x, y, z, b.pick(wall));
                }
        // gable roof along x
        for (int k = 0; k <= 3; k++) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                if (burned && b.r.nextFloat() < 0.55f) continue;
                if (b.r.nextFloat() < 0.07f) continue;
                b.setC(x, 4 + k, z1 - 1 + k, b.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
                b.setC(x, 4 + k, z2 + 1 - k, b.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
            }
            for (int z = z1 + k; z <= z2 - k; z++) {
                b.set(x1, 4 + k, z, b.pick(wall));
                b.set(x2, 4 + k, z, b.pick(wall));
            }
        }
        for (int x = x1 - 1; x <= x2 + 1; x++) if (!burned || b.r.nextFloat() < 0.4f) b.set(x, 8, z1 + 3, Blocks.DARK_OAK_SLAB);
        // windows
        int mx = x1 + 3, mz = z1 + 3;
        int[][] win = {{x1, mz}, {x2, mz}, {mx, doorSouth ? z1 : z2}};
        for (int[] w : win) {
            float f = b.r.nextFloat();
            if (f < 0.5f) b.setC(w[0], 1, w[1], Blocks.GLASS_PANE);
            else if (f < 0.8f) b.set(w[0], 1, w[1], Blocks.AIR);
            else b.set(w[0], 1, w[1], Blocks.OAK_PLANKS);
        }
        int dz = doorSouth ? z2 : z1;
        b.decay(x1, 0, z1, x2, 3, z2, burned ? 0.12f : 0.03f);
        b.door(mx, 0, dz, Blocks.SPRUCE_DOOR, doorSouth ? Direction.SOUTH : Direction.NORTH, true);
        b.cobwebs(x1 + 1, 2, z1 + 1, x2 - 1, 5, z2 - 1, 0.06f);
    }

    /** Пионерлагерь «Звёздочка»: two cabins, a medical hut, the line-up square with its flagpole and a loudspeaker. */
    public static void buildPioneerCamp(ServerLevel level, BlockPos site, StoryData d) {
        EchoHorror.LOG.info("Building the pioneer camp at {}", site);
        B b = new B(level, site);
        b.prepare(-21, -19, 21, 17, 10, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT);
        // the line-up square
        for (int x = -7; x <= 7; x++)
            for (int z = -7; z <= 7; z++)
                if (x * x + z * z <= 42) b.set(x, -1, z, b.pick(Blocks.GRAVEL, Blocks.DIRT_PATH, Blocks.DIRT_PATH, Blocks.COARSE_DIRT));
        for (int z = 7; z <= 16; z++) b.set(0, -1, z, b.pick(Blocks.DIRT_PATH, Blocks.GRAVEL));
        // flagpole with a torn red flag
        b.set(0, -1, 0, Blocks.STONE_BRICKS);
        for (int y = 0; y <= 8; y++) b.setC(0, y, 0, Blocks.IRON_BARS);
        b.set(1, 8, 0, Blocks.RED_WOOL);
        b.set(2, 8, 0, Blocks.RED_WOOL);
        b.set(1, 7, 0, Blocks.RED_WOOL);
        b.set(3, 8, 0, Blocks.RED_CARPET);
        // the loudspeaker
        for (int y = 0; y <= 4; y++) b.setC(6, y, -6, Blocks.SPRUCE_FENCE);
        b.set(6, 5, -6, Blocks.HOPPER);
        b.set(6, 6, -6, Blocks.GRAY_CONCRETE);
        d.put("pioneer_speaker", b.at(6, 5, -6));
        // benches around the square
        for (int[] bc : new int[][]{{-9, -3}, {-9, 3}, {9, -3}, {9, 3}}) {
            b.set(bc[0], 0, bc[1], b.stairs(Blocks.SPRUCE_STAIRS, bc[0] < 0 ? Direction.WEST : Direction.EAST));
            b.set(bc[0], 0, bc[1] + 1, b.stairs(Blocks.SPRUCE_STAIRS, bc[0] < 0 ? Direction.WEST : Direction.EAST));
        }
        // the bugler: a plaster pioneer on a plinth, missing his head
        b.set(-5, 0, 10, Blocks.STONE_BRICKS);
        b.set(-5, 1, 10, Blocks.WHITE_CONCRETE);
        b.set(-5, 2, 10, Blocks.WHITE_CONCRETE);
        b.setC(-4, 2, 10, Blocks.WHITE_CONCRETE_POWDER);
        b.set(-5, 0, 11, Blocks.WHITE_CONCRETE_POWDER);
        // gate with the camp's name
        for (int y = 0; y <= 4; y++) {
            b.set(-3, y, 16, Blocks.STRIPPED_BIRCH_LOG);
            b.set(3, y, 16, Blocks.STRIPPED_BIRCH_LOG);
        }
        for (int x = -3; x <= 3; x++) b.set(x, 5, 16, Blocks.BIRCH_SLAB);
        b.set(0, 4, 16, Blocks.BIRCH_PLANKS);
        b.sign(0, 4, 17, Direction.SOUTH, "П/Л", "«ЗВЁЗДОЧКА»", "", "добро пожаловать");
        for (int x = -20; x <= 20; x++) {
            if (Math.abs(x) <= 3 || b.r.nextFloat() < 0.3f) continue;
            b.setC(x, 0, 16, Blocks.BIRCH_FENCE);
        }
        // two cabins
        cabin(b, -19, -6, true);
        cabin(b, 11, -6, false);
        b.chest(-15, 0, -5, Direction.SOUTH, note("camp_counselor1"), TapeItem.create("tape8"), it(ModItems.PILLS.get(), 2), it(Items.BREAD, 3));
        b.chest(15, 0, -5, Direction.SOUTH, note("camp_list"), it(ModItems.BATTERY.get(), 2), it(Items.CANDLE, 3));
        // medical hut
        Block[] white = {Blocks.WHITE_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.WHITE_TERRACOTTA, Blocks.CALCITE};
        for (int x = -5; x <= 5; x++)
            for (int z = -18; z <= -11; z++) {
                b.set(x, -1, z, Blocks.WHITE_CONCRETE);
                for (int y = 0; y <= 3; y++) {
                    boolean edge = x == -5 || x == 5 || z == -18 || z == -11;
                    b.set(x, y, z, edge ? b.pick(white) : Blocks.AIR.defaultBlockState());
                }
                b.set(x, 4, z, Blocks.SMOOTH_STONE_SLAB);
            }
        b.door(0, 0, -11, Blocks.BIRCH_DOOR, Direction.SOUTH, false);
        b.set(-1, 3, -11, Blocks.RED_WOOL); // the red cross over the door
        b.set(1, 3, -11, Blocks.RED_WOOL);
        b.set(0, 3, -11, Blocks.RED_WOOL);
        b.set(0, 4, -11, Blocks.RED_WOOL);
        for (int x : new int[]{-3, 3}) b.setC(x, 1, -11, Blocks.GLASS_PANE);
        b.setC(-5, 1, -15, Blocks.GLASS_PANE);
        b.setC(5, 1, -15, Blocks.GLASS_PANE);
        b.bed(-3, 0, -16, Direction.NORTH, Blocks.WHITE_BED);
        b.set(3, 0, -17, Blocks.BREWING_STAND);
        b.set(4, 0, -17, Blocks.CAULDRON);
        b.set(-4, 0, -12, Blocks.WHITE_CARPET);
        BlockPos box = b.chest(-2, 0, -17, Direction.EAST, it(ModItems.MUSIC_BOX.get(), 1), note("camp_masha"), note("camp_counselor2"),
                it(ModItems.PILLS.get(), 1));
        d.put("pioneer_box", box);
        b.cobwebs(-4, 2, -17, 4, 3, -12, 0.12f);
        b.finish();
        d.put("pioneer", site);
        d.region("pioneer", b.box(-22, -3, -20, 22, 12, 18));
        d.region("medpunkt", b.box(-5, -1, -18, 5, 4, -11));
        d.setDirty();
    }

    /** A camp cabin with bunk rows along its long walls. */
    private static void cabin(B b, int x1, int z1, boolean doorEast) {
        int x2 = x1 + 8, z2 = z1 + 12;
        Block[] wall = {Blocks.BIRCH_PLANKS, Blocks.BIRCH_PLANKS, Blocks.STRIPPED_BIRCH_LOG, Blocks.LIGHT_BLUE_TERRACOTTA};
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                b.set(x, -1, z, Blocks.SPRUCE_PLANKS);
                for (int y = 0; y <= 3; y++) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    b.set(x, y, z, edge ? b.pick(wall) : Blocks.AIR.defaultBlockState());
                }
                b.set(x, 4, z, b.r.nextFloat() < 0.06f ? Blocks.AIR : Blocks.DARK_OAK_SLAB);
            }
        int dx = doorEast ? x2 : x1;
        b.door(dx, 0, z1 + 6, Blocks.BIRCH_DOOR, doorEast ? Direction.EAST : Direction.WEST, true);
        for (int z = z1 + 2; z <= z2 - 2; z += 3) {
            b.setC(x1, 1, z, Blocks.GLASS_PANE);
            b.setC(x2, 1, z, Blocks.GLASS_PANE);
            b.bed(x1 + 2, 0, z, Direction.WEST, Blocks.WHITE_BED);
            b.bed(x2 - 2, 0, z, Direction.EAST, Blocks.WHITE_BED);
        }
        b.cobwebs(x1 + 1, 2, z1 + 1, x2 - 1, 3, z2 - 1, 0.07f);
    }

    /** Optional places along the way: 0 = geologists' camp, 1 = cordon post, 2 = Tanya's car. */
    public static void buildCamp(ServerLevel level, BlockPos site, StoryData d, int kind) {
        EchoHorror.LOG.info("Building side location {} at {}", kind, site);
        B b = new B(level, site);
        b.prepare(-5, -5, 5, 5, 8, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.PODZOL);
        switch (kind) {
            case 0 -> { // geologists: a collapsed tent, a dead fire, instruments
                for (int x = -3; x <= 1; x++) {
                    b.set(x, 0, -2, Blocks.GREEN_WOOL);
                    b.set(x, 1, -1, Blocks.GREEN_CARPET);
                    b.set(x, 0, 0, Blocks.GREEN_WOOL);
                }
                b.set(-1, 1, -1, Blocks.GREEN_WOOL);
                b.set(3, 0, 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
                b.set(3, 0, -2, Blocks.LECTERN);
                b.set(2, 0, 3, Blocks.TRIPWIRE_HOOK);
                b.set(-3, 0, 3, Blocks.BONE_BLOCK);
                b.set(-2, 0, 3, Blocks.SKELETON_SKULL);
                b.chest(-3, 0, -1, Direction.EAST, note("lore_geologist"), it(ModItems.BATTERY.get(), 2), it(ModItems.PILLS.get(), 1),
                        it(Items.COMPASS, 1), it(ModItems.TUNING_FORK.get(), 1));
                b.standingSign(4, 0, 3, 6, "ПАРТИЯ №4", "не отвечайте", "на позывные");
            }
            case 1 -> { // cordon post: sandbags, a barrier, a toppled tower
                for (int x = -4; x <= 4; x++) {
                    if (Math.abs(x) <= 1) continue;
                    b.set(x, 0, -3, Blocks.MUD_BRICKS);
                    if (b.r.nextBoolean()) b.set(x, 1, -3, Blocks.MUD_BRICK_SLAB);
                }
                b.setC(-1, 0, -3, Blocks.OAK_FENCE);
                b.setC(1, 0, -3, Blocks.OAK_FENCE);
                b.set(0, 1, -3, Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                for (int z = 0; z <= 4; z++) b.set(3, 0, z, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
                b.barrel(-3, 0, 1, note("lore_soldier"), it(ModItems.BATTERY.get(), 3), it(Items.ARROW, 16));
                b.barrel(-3, 0, 2, it(ModItems.PILLS.get(), 1), it(Items.BREAD, 4));
                b.standingSign(0, 0, -5, 8, "СТОЙ!", "ЗАПРЕТНАЯ ЗОНА", "ОГОНЬ БЕЗ", "ПРЕДУПРЕЖДЕНИЯ");
                b.set(1, 0, 1, Blocks.REDSTONE_WIRE);
                b.set(1, 0, 2, Blocks.REDSTONE_WIRE);
            }
            default -> { // Tanya's car: she drove all the way here
                b.fill(-2, 0, -1, 2, 0, 1, Blocks.BLACK_CONCRETE);
                b.fill(-1, 1, -1, 1, 1, 1, Blocks.BLACK_CONCRETE);
                b.set(-1, 1, 0, Blocks.AIR);
                b.set(0, 1, 0, Blocks.AIR);
                b.setC(-1, 1, -1, Blocks.GLASS_PANE);
                b.setC(1, 1, -1, Blocks.GLASS_PANE);
                b.setC(-1, 1, 1, Blocks.GLASS_PANE);
                b.set(0, 2, 0, Blocks.BLACK_CONCRETE);
                for (int[] w : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) b.set(w[0], 0, w[1], Blocks.COAL_BLOCK);
                b.set(-2, 1, 0, Blocks.REDSTONE_LAMP);
                b.chest(1, 1, 0, Direction.WEST, note("lore_tanya"), it(ModItems.BATTERY.get(), 1), it(Items.PAPER, 2));
                b.set(3, 0, 0, Blocks.RED_CARPET);
                b.set(4, 0, 1, Blocks.RED_CARPET);
                b.standingSign(-4, 0, 0, 4, "Серёжа,", "я приехала", "открой");
            }
        }
        b.finish();
        d.put("camp" + kind, site);
        d.region("camp" + kind, b.box(-6, -2, -6, 6, 8, 6));
        d.setDirty();
    }

    public static void buildVillage(ServerLevel level, BlockPos site, StoryData d) {
        EchoHorror.LOG.info("Building village Tikhiy Log at {}", site);
        B b = new B(level, site);
        // plaza + paths following the terrain
        b.prepare(-6, -6, 6, 6, 12, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.DIRT_PATH, Blocks.COBBLESTONE);
        int[][] paths = {{-17, -10}, {16, -11}, {-18, 11}, {17, 12}, {0, 22}};
        for (int[] p : paths) {
            int steps = Math.max(Math.abs(p[0]), Math.abs(p[1]));
            for (int s = 0; s <= steps; s++) {
                int x = p[0] * s / steps, z = p[1] * s / steps;
                for (int w = -1; w <= 1; w++) {
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, b.at(x + w, 0, z)).below();
                    if (level.getBlockState(top).is(Blocks.GRASS_BLOCK) || level.getBlockState(top).is(Blocks.DIRT))
                        level.setBlock(top, b.r.nextBoolean() ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), 2);
                }
            }
        }

        // the well (sealed)
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                b.set(x, 0, z, b.pick(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE));
            }
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            b.setC(c[0], 1, c[1], Blocks.SPRUCE_FENCE);
            b.setC(c[0], 2, c[1], Blocks.SPRUCE_FENCE);
        }
        b.fill(-1, 3, -1, 1, 3, 1, Blocks.SPRUCE_SLAB);
        b.set(0, 0, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
        b.clear(0, -2, 0, 0, -1, 0);
        b.set(0, -1, 0, Blocks.COBWEB);
        b.fill(0, -5, 0, 0, -3, 0, Blocks.COBBLESTONE);
        b.barrel(2, 0, -2, note("well"), note("recruit"));
        b.standingSign(-2, 0, 2, 8, "КОЛОДЕЦ", "ЗАКРЫТ", "не слушай", "что внизу");
        d.put("well", b.at(0, 0, 0));
        d.put("village", b.at(0, 0, 0));

        // house A — Masha
        house(b, -20, -17, true, false);
        b.bed(-19, 0, -16, Direction.SOUTH, Blocks.RED_BED);
        b.setC(-16, 0, -15, Blocks.SPRUCE_FENCE);
        b.set(-16, 1, -15, Blocks.SPRUCE_PRESSURE_PLATE);
        b.candles(-15, 0, -13);
        b.chest(-15, 0, -16, Direction.WEST, note("masha"), it(Items.BREAD, 2), it(ModItems.BATTERY.get(), 1), it(Items.PAPER, 3), TapeItem.create("tape5"));
        b.set(-18, 0, -12, Blocks.FLOWER_POT);
        b.sign(-19, 2, -16, Direction.SOUTH, "мама", "не моргает");

        // house B — Kuzmich, with the cellar
        house(b, 13, -18, true, false);
        b.chest(18, 0, -13, Direction.WEST, note("farmer"), it(Items.BREAD, 2), it(ModItems.BATTERY.get(), 2));
        b.set(14, 0, -17, Blocks.CRAFTING_TABLE);
        b.bed(14, 0, -15, Direction.EAST, Blocks.BROWN_BED);
        // cellar under the house: interior x 14..18, z -16..-13, y -6..-2
        b.shell(13, -7, -17, 19, -1, -12, new Block[]{Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE},
                new Block[]{Blocks.COBBLESTONE, Blocks.DIRT, Blocks.COARSE_DIRT}, new Block[]{Blocks.SPRUCE_PLANKS});
        b.set(16, -1, -16, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
        b.ladder(16, -6, -2, -16, Direction.SOUTH);
        BlockPos clapperChest = b.chest(18, -6, -13, Direction.WEST, it(ModItems.BELL_CLAPPER.get(), 1), note("priest2"), it(ModItems.PILLS.get(), 2));
        b.set(14, -6, -13, Blocks.BONE_BLOCK);
        b.set(14, -6, -14, Blocks.SKELETON_SKULL);
        for (int i = 0; i < 6; i++) b.set(14 + b.r.nextInt(5), -6, -16 + b.r.nextInt(4), Blocks.REDSTONE_WIRE);
        b.sign(18, -4, -15, Direction.WEST, "ОНО", "ЗДЕСЬ", "ЖИВЁТ");
        b.sign(14, -4, -14, Direction.EAST, "не говори", "вслух");
        b.cobwebs(14, -3, -16, 18, -2, -13, 0.25f);
        d.put("cellar", b.at(16, -5, -14));
        d.put("clapper_chest", clapperChest);
        d.put("cellar_hatch", b.at(16, -1, -16));
        d.region("cellar", b.box(14, -7, -16, 18, -2, -13));

        // house C — walls of warnings
        house(b, -21, 11, false, false);
        String[][] warnings = {{"НЕ", "ОТВЕЧАЙ"}, {"НЕ", "ОТКРЫВАЙ"}, {"ЭТО", "НЕ ОНИ"}, {"ТИХО"}, {"ОНИ", "ВЕРНУЛИСЬ"},
                {"не зови", "по имени"}, {"СВОИ", "НЕ СТУЧАТ"}, {"оно", "учится"}};
        int wi = 0;
        for (int y = 1; y <= 2; y++) {
            for (int x = -20; x <= -16; x++) {
                if (b.r.nextFloat() < 0.6f) b.sign(x, y, 12, Direction.SOUTH, warnings[wi++ % warnings.length]);
                if (b.r.nextFloat() < 0.6f) b.sign(x, y, 16, Direction.NORTH, warnings[wi++ % warnings.length]);
            }
            for (int z = 13; z <= 15; z++) {
                if (b.r.nextFloat() < 0.6f) b.sign(-20, y, z, Direction.EAST, warnings[wi++ % warnings.length]);
                if (b.r.nextFloat() < 0.6f) b.sign(-16, y, z, Direction.WEST, warnings[wi++ % warnings.length]);
            }
        }
        b.set(-18, 0, 13, Blocks.AIR);
        b.chest(-18, 0, 15, Direction.NORTH, it(ModItems.BATTERY.get(), 2), it(Items.TORCH, 6), it(ModItems.PILLS.get(), 1), TapeItem.create("tape7"));

        // house D — burned
        house(b, 14, 12, false, true);
        b.chest(17, 0, 16, Direction.NORTH, it(ModItems.BATTERY.get(), 3), it(Items.COAL, 4), it(ModItems.PILLS.get(), 1));

        // the church
        b.prepare(-7, 20, 7, 40, 18, Blocks.GRAVEL, Blocks.COBBLESTONE, Blocks.COARSE_DIRT);
        Block[] stone = {Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS};
        b.fillMix(-5, -1, 22, 5, -1, 38, Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
        for (int x = -5; x <= 5; x++)
            for (int z = 26; z <= 38; z++)
                for (int y = 0; y <= 6; y++)
                    if (x == -5 || x == 5 || z == 26 || z == 38) b.set(x, y, z, b.pick(stone));
        for (int k = 0; k <= 5; k++) {
            for (int z = 25; z <= 39; z++) {
                if (b.r.nextFloat() < 0.05f) continue;
                b.setC(-6 + k, 7 + k, z, b.stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST));
                b.setC(6 - k, 7 + k, z, b.stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST));
            }
            for (int x = -5 + k; x <= 5 - k; x++) {
                b.set(x, 7 + k, 26, b.pick(stone));
                b.set(x, 7 + k, 38, b.pick(stone));
            }
        }
        for (int z = 25; z <= 39; z++) b.set(0, 13, z, Blocks.DARK_OAK_SLAB);
        for (int z : new int[]{29, 32, 35}) {
            for (int x : new int[]{-5, 5}) {
                for (int y = 2; y <= 4; y++) {
                    float f = b.r.nextFloat();
                    b.setC(x, y, z, f < 0.4f ? Blocks.RED_STAINED_GLASS_PANE.defaultBlockState()
                            : f < 0.75f ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // pews
        for (int z = 29; z <= 34; z += 2) {
            for (int x = -3; x <= 3; x++) {
                if (x == 0 || b.r.nextFloat() < 0.15f) continue;
                b.setC(x, 0, z, b.stairs(Blocks.SPRUCE_STAIRS, b.r.nextFloat() < 0.1f ? Direction.EAST : Direction.NORTH));
            }
        }
        // altar
        b.fill(-1, 0, 36, 1, 0, 36, Blocks.STONE_BRICK_SLAB);
        b.candles(-1, 1, 36);
        b.candles(1, 1, 36);
        b.set(0, 1, 36, Blocks.SKELETON_SKULL);
        b.chest(0, 0, 37, Direction.NORTH, note("priest1"), it(ModItems.PILLS.get(), 2), it(Items.CANDLE, 4), it(ModItems.HANDBELL.get(), 1));
        b.fill(-1, 0, 34, 1, 0, 35, Blocks.RED_CARPET);
        b.sign(-2, 3, 37, Direction.NORTH, "ЗВОНИТЕ");
        b.sign(2, 3, 37, Direction.NORTH, "В ПОЛНОЧЬ");
        b.cobwebs(-4, 4, 27, 4, 6, 37, 0.06f);

        // bell tower: x -2..2, z 22..26, height 0..14
        for (int y = 0; y <= 14; y++)
            for (int x = -2; x <= 2; x++)
                for (int z = 22; z <= 26; z++) {
                    boolean edge = x == -2 || x == 2 || z == 22 || z == 26;
                    if (edge) b.set(x, y, z, b.pick(stone));
                    else b.set(x, y, z, Blocks.AIR.defaultBlockState());
                }
        b.door(0, 0, 22, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
        b.clear(-1, 0, 26, 1, 2, 26); // arch into the nave
        b.ladder(1, 0, 10, 25, Direction.WEST);
        for (int x = -1; x <= 1; x++)
            for (int z = 23; z <= 25; z++)
                if (!(x == 1 && z == 25)) b.set(x, 10, z, Blocks.SPRUCE_PLANKS);
        // belfry openings
        b.clear(0, 11, 22, 0, 12, 22);
        b.clear(0, 11, 26, 0, 12, 26);
        b.clear(-2, 11, 24, -2, 12, 24);
        b.clear(2, 11, 24, 2, 12, 24);
        b.fill(-2, 14, 22, 2, 14, 26, Blocks.STONE_BRICKS);
        for (int k = 0; k <= 2; k++)
            for (int x = -2 + k; x <= 2 - k; x++)
                for (int z = 22 + k; z <= 26 - k; z++) b.set(x, 15 + k, z, Blocks.DEEPSLATE_TILES);
        b.set(0, 18, 24, Blocks.LIGHTNING_ROD);
        b.set(0, 13, 24, Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Direction.EAST)
                .setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
        d.put("bell", b.at(0, 13, 24));
        b.sign(-1, 1, 23, Direction.SOUTH, "язык", "украли");

        // graveyard
        b.prepare(9, 24, 19, 38, 6, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.PODZOL);
        for (int gx = 10; gx <= 18; gx += 4)
            for (int gz = 25; gz <= 35; gz += 5) {
                b.set(gx, 0, gz, b.pick(Blocks.STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK_WALL, Blocks.COBBLESTONE_WALL));
                if (b.r.nextFloat() < 0.45f) { // opened from inside
                    b.clear(gx, -2, gz + 1, gx, -1, gz + 2);
                    b.set(gx + 1, 0, gz + 1, Blocks.COARSE_DIRT);
                    b.set(gx - 1, 0, gz + 2, Blocks.DIRT);
                } else {
                    b.set(gx, -1, gz + 1, Blocks.COARSE_DIRT);
                    b.set(gx, -1, gz + 2, Blocks.COARSE_DIRT);
                }
            }
        b.finish();
        d.region("village", b.box(-34, -10, -34, 34, 30, 42));
        d.region("church", b.box(-5, 0, 22, 5, 14, 38));
    }

    // =====================================================================================================
    // Chapters 4-6: the Depths, Object "Kolokol" and the Belfry — all underground below the well
    // =====================================================================================================
    public static int depthsY(ServerLevel level, BlockPos well) {
        int min = level.getMinBuildHeight();
        return Mth.clamp(well.getY() - 40, min + 30, well.getY() - 22);
    }

    private static void carveBlob(B b, int cx, int cy, int cz, int rx, int ry, int rz, Block[] floor) {
        for (int x = -rx - 1; x <= rx + 1; x++)
            for (int z = -rz - 1; z <= rz + 1; z++)
                for (int y = 0; y <= ry * 2; y++) {
                    double nx = x / (double) rx, ny = (y - ry) / (double) ry, nz = z / (double) rz;
                    double dd = nx * nx + (y < ry ? 0 : ny * ny) + nz * nz;
                    if (dd < 1.0 + b.r.nextDouble() * 0.12) b.carve(cx + x, cy + y, cz + z);
                }
        for (int x = -rx; x <= rx; x++)
            for (int z = -rz; z <= rz; z++)
                if ((x * x) / (double) (rx * rx) + (z * z) / (double) (rz * rz) < 1.0) b.set(cx + x, cy - 1, cz + z, b.pick(floor));
    }

    private static void tunnel(B b, int x1, int z1, int x2, int z2, Block[] floor) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        int wobble = 0;
        for (int s = 0; s <= steps; s++) {
            int x = x1 + (x2 - x1) * s / steps, z = z1 + (z2 - z1) * s / steps;
            if (s % 5 == 0 && s > 3 && s < steps - 3) wobble = Mth.clamp(wobble + b.r.nextInt(3) - 1, -2, 2);
            if (alongX) z += wobble;
            else x += wobble;
            int h = 3 + (b.r.nextFloat() < 0.2f ? 1 : 0);
            for (int w = -1; w <= 1; w++) {
                int tx = alongX ? x : x + w, tz = alongX ? z + w : z;
                for (int y = 0; y <= h; y++) b.carve(tx, y, tz);
                b.set(tx, -1, tz, b.pick(floor));
            }
            if (s % 7 == 3) { // mine supports
                for (int y = 0; y <= 3; y++) {
                    b.set(alongX ? x : x - 2, y, alongX ? z - 2 : z, Blocks.STRIPPED_OAK_LOG);
                    b.set(alongX ? x : x + 2, y, alongX ? z + 2 : z, Blocks.STRIPPED_OAK_LOG);
                }
                for (int w = -2; w <= 2; w++) b.set(alongX ? x : x + w, 4, alongX ? z + w : z, Blocks.OAK_PLANKS);
            }
        }
    }

    public static void buildDepths(ServerLevel level, StoryData d) {
        BlockPos well = d.get("well");
        int dy = depthsY(level, well);
        BlockPos origin = new BlockPos(well.getX(), dy, well.getZ());
        d.put("depths", origin);
        EchoHorror.LOG.info("Building the Depths at {}", origin);
        B b = new B(level, origin);
        Block[] floor = {Blocks.DEEPSLATE, Blocks.TUFF, Blocks.COBBLED_DEEPSLATE, Blocks.SCULK, Blocks.GRAVEL};

        // the shaft from the well down to the chamber
        int top = well.getY() - 1 - dy;
        for (int y = 0; y <= top; y++) {
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue;
                    if (y > 7) b.set(x, y, z, b.pick(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLED_DEEPSLATE));
                }
            b.carve(0, y, 0);
        }
        // central chamber
        carveBlob(b, 0, 0, 0, 7, 3, 7, floor);
        for (int y = 0; y <= top; y++) {
            if (y > 6) b.set(0, y, -1, Blocks.COBBLED_DEEPSLATE);
            b.set(0, y, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        b.fill(-1, 0, -1, 1, 6, -1, Blocks.COBBLED_DEEPSLATE);
        b.ladder(0, 0, top, 0, Direction.SOUTH);
        b.chest(4, 0, 3, Direction.WEST, note("miner1"), it(ModItems.BATTERY.get(), 3), it(ModItems.PILLS.get(), 1), it(Items.TORCH, 8));
        b.set(-4, 0, 3, Blocks.BONE_BLOCK);
        b.sign(3, 1, -5, Direction.SOUTH, "ЗАПАД", "ВОСТОК", "СЕВЕР", "↓ ОБЪЕКТ — ЮГ");
        b.set(3, 1, -6, Blocks.COBBLED_DEEPSLATE);
        b.set(-3, 1, -6, Blocks.COBBLED_DEEPSLATE);
        b.sign(-3, 1, -5, Direction.SOUTH, "НЕ ГОВОРИТЕ", "ОНИ СЛЫШАТ", "ГОЛОСА", "только шёпотом");

        // tunnels + shard rooms
        int[][] rooms = {{-40, 0}, {40, 0}, {0, -40}};
        String[] extraNote = {"miner2", "", "miner3"};
        List<BlockPos> chests = d.list("shard_chests");
        chests.clear();
        for (int i = 0; i < rooms.length; i++) {
            int rx = rooms[i][0], rz = rooms[i][1];
            int sx = Integer.signum(rx) * 7, sz = Integer.signum(rz) * 7;
            tunnel(b, sx, sz, rx - Integer.signum(rx) * 5, rz - Integer.signum(rz) * 5, floor);
            carveBlob(b, rx, 0, rz, 5, 3, 5, floor);
            b.set(rx, 0, rz, Blocks.POLISHED_BLACKSTONE);
            List<ItemStack> loot = new ArrayList<>();
            loot.add(it(ModItems.ECHO_SHARD.get(), 1));
            if (!extraNote[i].isEmpty()) loot.add(note(extraNote[i]));
            loot.add(it(ModItems.BATTERY.get(), 1));
            chests.add(b.chest(rx, 1, rz, Direction.SOUTH, loot.toArray(new ItemStack[0])));
            for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) b.candles(rx + c[0], 0, rz + c[1]);
            for (int k = 0; k < 5; k++) b.set(rx + b.r.nextInt(7) - 3, 0, rz + b.r.nextInt(7) - 3, Blocks.BONE_BLOCK);
            d.region("shard" + i, b.box(rx - 6, -1, rz - 6, rx + 6, 7, rz + 6));
        }
        // south tunnel to the gate
        tunnel(b, 0, 7, 0, 27, floor);
        for (int x = -3; x <= 3; x++) for (int y = 0; y <= 4; y++) for (int z = 27; z <= 33; z++) b.carve(x, y, z);
        b.fillMix(-3, -1, 27, 3, -1, 33, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES);
        b.fillMix(-4, -1, 34, 4, 5, 34, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES);
        List<BlockPos> gate = d.list("gate");
        gate.clear();
        for (int x = -1; x <= 1; x++)
            for (int y = 0; y <= 2; y++) {
                b.set(x, y, 34, ModBlocks.SEALED_DOOR.get().defaultBlockState());
                gate.add(b.at(x, y, 34));
            }
        b.set(2, 0, 33, Blocks.POLISHED_DEEPSLATE);
        b.set(2, 1, 33, ModBlocks.SHARD_LOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(ShardLockBlock.SHARDS, 0));
        d.put("lock", b.at(2, 1, 33));
        b.sign(-2, 2, 33, Direction.NORTH, "ОБЪЕКТ П/Я 7", "«КОЛОКОЛ»", "ВХОД", "ВОСПРЕЩЁН");
        b.sign(-3, 1, 33, Direction.NORTH, "вставь", "три осколка");
        d.region("depths", b.box(-48, -2, -48, 48, 9, 34));
        b.finish();
    }

    private static void lamp(B b, int x, int y, int z, List<BlockPos> lamps) {
        b.set(x, y, z, Blocks.REDSTONE_LAMP);
        b.set(x, y + 1, z, Blocks.STONE);
        lamps.add(b.at(x, y + 1, z));
    }

    public static void buildBunker(ServerLevel level, StoryData d) {
        BlockPos origin = d.get("depths");
        EchoHorror.LOG.info("Building Object Kolokol at {}", origin);
        B b = new B(level, origin);
        List<BlockPos> lamps = d.list("lamps");
        lamps.clear();
        Block[] wall = {Blocks.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.CRACKED_STONE_BRICKS, Blocks.WHITE_CONCRETE};
        Block[] floor = {Blocks.SMOOTH_STONE, Blocks.GRAY_CONCRETE, Blocks.POLISHED_ANDESITE};
        Block[] ceil = {Blocks.GRAY_CONCRETE};

        // main corridor
        b.shell(-2, -1, 35, 2, 4, 78, wall, floor, ceil);
        b.clear(-1, 0, 35, 1, 2, 35);
        for (int z = 38; z <= 76; z += 6) lamp(b, 0, 4, z, lamps);
        b.sign(-1, 2, 36, Direction.SOUTH, "ПРОТОКОЛ", "«ТИШИНА»", "соблюдать", "тишину");
        // lab (right)
        b.shell(3, -1, 40, 11, 4, 48, wall, floor, ceil);
        b.clear(2, 0, 44, 3, 1, 44);
        lamp(b, 7, 4, 44, lamps);
        b.set(5, 0, 41, Blocks.BREWING_STAND);
        b.set(6, 0, 41, Blocks.CAULDRON);
        b.set(9, 0, 41, Blocks.CRAFTING_TABLE);
        b.set(10, 0, 47, Blocks.IRON_BLOCK);
        b.fill(4, 0, 47, 8, 0, 47, Blocks.SMOOTH_STONE_SLAB);
        b.chest(10, 0, 41, Direction.WEST, TapeItem.create("tape1"), note("lab_note"), note("voronov1"), it(ModItems.PILLS.get(), 3));
        b.cobwebs(4, 2, 41, 10, 3, 47, 0.06f);
        // barracks (left)
        b.shell(-11, -1, 40, -3, 4, 48, wall, floor, ceil);
        b.clear(-3, 0, 44, -2, 1, 44);
        lamp(b, -7, 4, 44, lamps);
        b.bed(-10, 0, 41, Direction.EAST, Blocks.WHITE_BED);
        b.bed(-10, 0, 43, Direction.EAST, Blocks.WHITE_BED);
        b.bed(-10, 0, 46, Direction.EAST, Blocks.RED_BED);
        b.barrel(-4, 0, 47, note("protocol"), it(ModItems.BATTERY.get(), 3), it(Items.BREAD, 4));
        b.barrel(-4, 0, 41, it(ModItems.BATTERY.get(), 2), it(ModItems.PILLS.get(), 1), it(ModItems.HANDBELL.get(), 1));
        b.set(-6, 0, 47, Blocks.RED_CARPET);
        b.set(-7, 0, 47, Blocks.REDSTONE_WIRE);

        // cell block N (right)
        b.shell(3, -1, 54, 17, 4, 72, wall, floor, ceil);
        b.clear(2, 0, 56, 3, 1, 56);
        b.sign(1, 2, 55, Direction.WEST, "БЛОК Н", "НЕ ОТВОДИТЬ", "ВЗГЛЯД");
        lamp(b, 6, 4, 58, lamps);
        lamp(b, 6, 4, 66, lamps);
        for (int cz = 55; cz <= 67; cz += 4) {
            b.fill(10, 0, cz + 3, 16, 3, cz + 3, Blocks.LIGHT_GRAY_CONCRETE);
            for (int z = cz; z <= Math.min(cz + 2, 71); z++)
                for (int y = 0; y <= 2; y++)
                    if (b.r.nextFloat() < 0.75f) b.setC(9, y, z, Blocks.IRON_BARS);
            b.set(15, 0, cz + 1, Blocks.WHITE_CARPET);
        }
        b.fill(9, 0, 71, 16, 3, 71, Blocks.LIGHT_GRAY_CONCRETE);
        b.chest(4, 0, 57, Direction.EAST, note("subject_n3"), it(ModItems.PILLS.get(), 1));
        b.chest(4, 0, 70, Direction.EAST, TapeItem.create("tape2"), it(ModItems.BATTERY.get(), 2));
        b.set(6, 0, 71, ModBlocks.POWER_SWITCH.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        b.sign(7, 2, 71, Direction.NORTH, "РУБИЛЬНИК №1");
        d.region("cellblock", b.box(3, -1, 54, 17, 4, 72));

        // archive (left)
        b.shell(-17, -1, 54, -3, 4, 72, wall, floor, ceil);
        b.clear(-3, 0, 56, -2, 1, 56);
        lamp(b, -10, 4, 58, lamps);
        lamp(b, -10, 4, 68, lamps);
        for (int z = 58; z <= 69; z += 3)
            for (int x = -15; x <= -5; x++) {
                if (x == -10 || x == -11) continue;
                for (int y = 0; y <= 2; y++) if (b.r.nextFloat() < 0.88f) b.set(x, y, z, Blocks.BOOKSHELF);
            }
        b.chest(-15, 0, 55, Direction.EAST, note("voronov2"), it(ModItems.BATTERY.get(), 1));
        b.chest(-5, 0, 71, Direction.NORTH, TapeItem.create("tape3"), it(ModItems.PILLS.get(), 1));
        b.set(-15, 0, 71, ModBlocks.POWER_SWITCH.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        b.sign(-14, 2, 71, Direction.NORTH, "РУБИЛЬНИК №2");
        b.cobwebs(-16, 1, 55, -4, 3, 71, 0.05f);
        d.region("archive", b.box(-17, -1, 54, -3, 4, 72));

        // generator hall
        b.shell(-8, -1, 78, 8, 6, 91, wall, floor, ceil);
        b.clear(-1, 0, 78, 1, 2, 78);
        lamp(b, -4, 6, 82, lamps);
        lamp(b, 4, 6, 82, lamps);
        lamp(b, 0, 6, 87, lamps);
        for (int x : new int[]{-6, -4, 4, 6}) {
            b.set(x, 0, 84, Blocks.BLAST_FURNACE);
            b.set(x, 1, 84, Blocks.IRON_BLOCK);
            b.set(x, 2, 84, Blocks.LIGHTNING_ROD);
        }
        b.fill(-7, 4, 80, 7, 4, 80, Blocks.CUT_COPPER);
        b.set(0, 0, 89, ModBlocks.POWER_SWITCH.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        b.sign(1, 2, 90, Direction.NORTH, "РУБИЛЬНИК №3");
        b.chest(-6, 0, 89, Direction.EAST, note("voronov3"), it(ModItems.PILLS.get(), 2), it(ModItems.BATTERY.get(), 2));
        List<BlockPos> door = d.list("belfry_door");
        door.clear();
        for (int x = -1; x <= 1; x++)
            for (int y = 0; y <= 2; y++) {
                b.set(x, y, 91, ModBlocks.SEALED_DOOR.get().defaultBlockState());
                door.add(b.at(x, y, 91));
            }
        b.sign(-2, 2, 90, Direction.NORTH, "ЗВОННИЦА", "питание", "отключено");
        d.region("genroom", b.box(-8, -1, 78, 8, 6, 91));
        d.region("bunker", b.box(-18, -2, 35, 18, 7, 91));
        List<BlockPos> sw = d.list("switches");
        sw.clear();
        sw.add(b.at(6, 0, 71));
        sw.add(b.at(-15, 0, 71));
        sw.add(b.at(0, 0, 89));
        d.put("bunker", b.at(0, 0, 40));

        // subject N-3 and her copies
        int[][] silents = {{14, 57}, {14, 65}, {6, 68}};
        for (int[] s : silents) {
            SilentEntity e = new SilentEntity(ModEntities.SILENT.get(), level);
            BlockPos p = b.at(s[0], 0, s[1]);
            e.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 90f, 0f);
            e.setPersistenceRequired();
            level.addFreshEntity(e);
        }
        b.finish();
    }

    public static void buildBelfry(ServerLevel level, StoryData d) {
        BlockPos origin = d.get("depths");
        B b = new B(level, origin);
        // the arena
        BlockPos a = origin.offset(0, -20, 127);
        B ar = new B(level, a);
        int R = 16;
        for (int x = -R - 2; x <= R + 2; x++)
            for (int z = -R - 2; z <= R + 2; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > R + 1.6) continue;
                Block[] shell = {Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_BRICKS};
                if (dist > R) { // outer wall
                    for (int y = -2; y <= 12; y++) ar.set(x, y, z, ar.pick(shell));
                    continue;
                }
                int top = 10 + (int) Math.floor(Math.sqrt(Math.max(0, (R * R - dist * dist) / 6.0)));
                ar.set(x, -2, z, Blocks.DEEPSLATE_TILES);
                ar.set(x, -1, z, dist < 4 ? Blocks.SCULK.defaultBlockState()
                        : ((int) dist % 4 == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                        : ar.r.nextFloat() < 0.1f ? Blocks.SCULK.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState()));
                for (int y = 0; y <= top; y++) ar.set(x, y, z, Blocks.AIR.defaultBlockState());
                ar.set(x, top + 1, z, ar.pick(shell));
                ar.set(x, top + 2, z, ar.pick(shell));
            }
        ar.set(0, -1, 0, Blocks.SCULK_CATALYST);
        // stairs down: z 92..110, from floor -1 to -20
        for (int i = 0; i <= 18; i++) {
            int z = 92 + i;
            int fy = -2 - i;
            for (int x = -2; x <= 2; x++)
                for (int y = fy; y <= fy + 5; y++) {
                    boolean wall = x == -2 || x == 2 || y == fy + 5;
                    b.set(x, y, z, wall ? b.pick(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS) : Blocks.AIR.defaultBlockState());
                }
            for (int x = -1; x <= 1; x++) b.set(x, fy, z, b.stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH));
            b.set(-2, fy, z, Blocks.DEEPSLATE_BRICKS);
            b.set(2, fy, z, Blocks.DEEPSLATE_BRICKS);
            for (int x = -2; x <= 2; x++) b.set(x, fy - 1, z, Blocks.DEEPSLATE_BRICKS);
        }
        // pillars and bells
        List<BlockPos> bells = d.list("arena_bells");
        bells.clear();
        for (int[] c : new int[][]{{10, 10}, {-10, 10}, {10, -10}, {-10, -10}}) {
            ar.fill(c[0], 0, c[1], c[0] + Integer.signum(c[0]), 13, c[1] + Integer.signum(c[1]), Blocks.DEEPSLATE_BRICKS);
            ar.set(c[0] - Integer.signum(c[0]), 3, c[1], Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
            ar.set(c[0] - Integer.signum(c[0]), 2, c[1], Blocks.DEEPSLATE_BRICK_WALL);
            int bx = c[0] * 7 / 10, bz = c[1] * 7 / 10;
            ar.set(bx, 0, bz, Blocks.POLISHED_BLACKSTONE);
            ar.set(bx, 1, bz, Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Direction.NORTH)
                    .setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
            bells.add(ar.at(bx, 1, bz));
        }
        // dim soul light hanging from the dome so the fight is playable
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + Math.PI / 8;
            int lx = (int) Math.round(Math.cos(ang) * 12), lz = (int) Math.round(Math.sin(ang) * 12);
            double dd = Math.sqrt(lx * lx + lz * lz);
            int top = 10 + (int) Math.floor(Math.sqrt(Math.max(0, (R * R - dd * dd) / 6.0)));
            for (int y = 6; y <= top; y++) ar.set(lx, y, lz, Blocks.CHAIN);
            ar.set(lx, 5, lz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        // the great bell hanging over the throat
        for (int y = 9; y <= 14; y++) {
            int rad = y == 9 ? 4 : y <= 12 ? 3 : y == 13 ? 2 : 1;
            for (int x = -rad; x <= rad; x++)
                for (int z = -rad; z <= rad; z++) {
                    double dd = Math.sqrt(x * x + z * z);
                    if (dd <= rad + 0.3 && dd >= rad - 0.9) ar.set(x, y, z, Blocks.WAXED_OXIDIZED_CUT_COPPER);
                }
        }
        for (int y = 15; y <= 17; y++) ar.set(0, y, 0, Blocks.CHAIN);
        d.put("arena", a);
        d.region("arena", ar.box(-R - 1, -2, -R - 1, R + 1, 17, R + 1));
        d.region("stairs", b.box(-3, -23, 90, 3, 5, 112));
        d.region("gatewall", b.box(-5, -2, 32, 5, 6, 35));
        b.finish();
        ar.finish();
    }
}
