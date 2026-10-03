package com.khmh.livingvillages.village;

import com.khmh.livingvillages.LivingVillages;
import com.khmh.livingvillages.building.BuildingType;
import com.khmh.livingvillages.building.BuildingTypes;
import com.khmh.livingvillages.building.Construction;
import com.khmh.livingvillages.building.SiteFinder;
import com.khmh.livingvillages.building.TemplateData;
import com.khmh.livingvillages.config.LVConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Villages are not generated with the world; they are founded. Somewhere in open country near a player a camp
 * appears: a bell, one or two small houses and two villagers with nothing at all. Everything else (wood, fields,
 * chests, a warehouse, more people) they have to make for themselves.
 */
public final class Founding {
    public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME,
            new ResourceLocation(LivingVillages.MODID, "founding"));

    private Founding() {
    }

    /** Chunks where vanilla would have started a village, waiting for their surroundings to load. */
    private static final java.util.Set<Long> PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * A newly generated chunk that vanilla would have made a village start: a camp goes there instead, once the
     * land around it has loaded. Same places and spacing as vanilla villages, in the same kinds of country.
     */
    public static void onNewChunk(ServerLevel level, net.minecraft.world.level.ChunkPos chunk) {
        if (!LVConfig.NATURAL_FOUNDING.get() || level.dimension() != Level.OVERWORLD) {
            return;
        }
        var sets = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var villages = sets.getHolder(net.minecraft.world.level.levelgen.structure.BuiltinStructureSets.VILLAGES);
        if (villages.isEmpty()) {
            return;
        }
        var placement = villages.get().value().placement();
        if (placement.isStructureChunk(level.getChunkSource().getGeneratorState(), chunk.x, chunk.z)) {
            PENDING.add(chunk.toLong());
            LivingVillages.LOGGER.debug("Village start chunk {} noted", chunk);
        }
    }

    static void tick(ServerLevel level, VillageManager manager) {
        if (PENDING.isEmpty() || level.dimension() != Level.OVERWORLD) {
            return;
        }
        for (Long key : java.util.List.copyOf(PENDING)) {
            net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(key);
            BlockPos centre = c.getMiddleBlockPosition(64);
            if (!level.hasChunksAt(centre.offset(-40, 0, -40), centre.offset(40, 0, 40))) {
                continue; // wait for the surroundings
            }
            PENDING.remove(key);
            LivingVillages.LOGGER.debug("Village start chunk {} loaded; biome {}", c,
                    level.getBiome(centre).unwrapKey().map(k -> k.location().toString()).orElse("?"));
            if (nearest(manager, centre) < LVConfig.FOUNDING_SPACING.get() / 2.0) {
                continue;
            }
            // The spot itself or close by, on open ground of a village biome.
            for (int r = 0; r <= 24; r += 8) {
                BlockPos spot = null;
                for (int a = 0; a < 8 && spot == null; a++) {
                    double ang = a * Math.PI / 4;
                    spot = suitable(level, centre.offset((int) (Math.cos(ang) * r), 0, (int) (Math.sin(ang) * r)));
                    if (r == 0) {
                        break;
                    }
                }
                if (spot != null) {
                    found(level, manager, spot);
                    break;
                }
            }
        }
    }

    /** Debug: a camp somewhere 48-128 blocks from {@code around}, the way natural ones used to be found. */
    @Nullable
    public static Village tryAround(ServerLevel level, VillageManager manager, BlockPos around) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 48 + random.nextInt(80);
            BlockPos spot = suitable(level, around.offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist)));
            if (spot != null && nearest(manager, spot) >= LVConfig.FOUNDING_SPACING.get()) {
                return found(level, manager, spot);
            }
        }
        return null;
    }

    private static double nearest(VillageManager manager, BlockPos pos) {
        return manager.all().stream().mapToDouble(v -> Math.sqrt(v.center().distSqr(pos)))
                .min().orElse(Double.MAX_VALUE);
    }

    /** Dry, fairly flat natural ground in a founding biome with the area around it loaded. */
    @Nullable
    private static BlockPos suitable(ServerLevel level, BlockPos col) {
        if (!level.hasChunksAt(col.offset(-32, 0, -32), col.offset(32, 0, 32))) {
            return null;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
        if (!level.getBiome(top).is(BIOMES)) {
            return null;
        }
        BlockState ground = level.getBlockState(top.below());
        if (!(ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.SNOW_BLOCK))
                || !level.getFluidState(top).isEmpty()) {
            return null;
        }
        int min = top.getY(), max = top.getY();
        for (int dx = -6; dx <= 6; dx += 3) {
            for (int dz = -6; dz <= 6; dz += 3) {
                BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.offset(dx, 0, dz));
                if (!level.getFluidState(p.below()).isEmpty()) {
                    return null;
                }
                min = Math.min(min, p.getY());
                max = Math.max(max, p.getY());
            }
        }
        if (max - min > 4) {
            return null;
        }
        // Wood within reach: a camp on a bare meadow or a mountainside has nothing to build with and starves.
        int trees = 0;
        for (int dx = -32; dx <= 32 && trees < 4; dx += 4) {
            for (int dz = -32; dz <= 32 && trees < 4; dz += 4) {
                BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, top.offset(dx, 0, dz)).below();
                BlockState s = level.getBlockState(p);
                if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
                    trees++;
                }
            }
        }
        return trees >= 4 ? top : null;
    }

    /** Puts down the camp at the given surface spot: bell, houses, two villagers, empty stores. */
    public static Village found(ServerLevel level, VillageManager manager, BlockPos top) {
        BlockPos bell = top;
        level.setBlock(bell.below(), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR),
                Block.UPDATE_ALL);
        Village v = new Village(java.util.UUID.randomUUID(), bell);
        manager.add(v);
        manager.setDirty();
        RandomSource random = level.getRandom();
        // Two small houses: a bed each for the founders and, more often than not, one over for the first child
        // (with a single bed a camp waited hours on its next house before anyone could be born).
        int houses = 2;
        for (int i = 0; i < houses; i++) {
            BuildingType type = BuildingTypes.get("small_house_" + (1 + random.nextInt(8)));
            if (type == null) {
                type = BuildingTypes.get("small_house_1");
            }
            Optional<TemplateData> data = TemplateData.get(level, type);
            if (data.isEmpty()) {
                continue;
            }
            Optional<SiteFinder.Site> site = SiteFinder.find(level, v, type, data.get());
            if (site.isPresent()) {
                var b = v.addBuilding(type, site.get(), level.getGameTime());
                b.data().putBoolean("free", true);
                Construction.finish(level, v, b);
            }
        }
        // The quest board stands by the bell from the first day.
        BuildingType board = BuildingTypes.get("quest_board");
        if (board != null) {
            TemplateData.get(level, board).flatMap(d -> SiteFinder.find(level, v, board, d)).ifPresent(site -> {
                var b = v.addBuilding(board, site, level.getGameTime());
                b.data().putBoolean("free", true);
                Construction.finish(level, v, b);
            });
        }
        for (int i = 0; i < 2; i++) {
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager != null) {
                BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bell.offset(i * 2 - 1, 0, 2));
                villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
                villager.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
                villager.setAge(0);
                level.addFreshEntity(villager);
            }
        }
        LivingVillages.LOGGER.info("A new village camp at {} with {} houses", bell, houses);
        return v;
    }
}
