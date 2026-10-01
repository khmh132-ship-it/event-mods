package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.village.Village;
import com.khmh.livingvillages.village.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A villager the village hired for a job. Looks like a villager, but runs its own goal-based AI instead of the
 * vanilla villager brain. Belongs to one village and one workplace building.
 */
public class VillageWorker extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_JOB =
            SynchedEntityData.defineId(VillageWorker.class, EntityDataSerializers.INT);

    @Nullable
    private UUID villageId;
    @Nullable
    private UUID workplaceId;
    private final Map<String, Integer> carried = new LinkedHashMap<>();
    private int orphanTicks;

    public VillageWorker(EntityType<? extends VillageWorker> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_JOB, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new BuilderWorkGoal(this));
        goalSelector.addGoal(2, new LumberjackWorkGoal(this));
        goalSelector.addGoal(2, new MinerWorkGoal(this));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Nullable
    public UUID villageId() {
        return villageId;
    }

    public WorkerJob job() {
        return WorkerJob.byId(entityData.get(DATA_JOB));
    }

    public void assign(WorkerJob job, Village village, Building workplace) {
        entityData.set(DATA_JOB, job.ordinal());
        villageId = village.id();
        workplaceId = workplace.id();
        restrictTo(workplace.entrance(), 24);
    }

    public Optional<Village> village() {
        if (villageId == null || !(level() instanceof ServerLevel server)) {
            return Optional.empty();
        }
        return VillageManager.get(server).byId(villageId);
    }

    public Optional<Building> workplace() {
        return village().flatMap(v -> v.buildings().stream().filter(b -> b.id().equals(workplaceId)).findFirst());
    }

    /** Total number of items carried. */
    public int carried() {
        return carried.values().stream().mapToInt(Integer::intValue).sum();
    }

    void carry(Item item, int amount) {
        if (amount > 0) {
            carried.merge(BuiltInRegistries.ITEM.getKey(item).toString(), amount, Integer::sum);
        }
    }

    void convertCarried(Item from, Item to) {
        Integer n = carried.remove(BuiltInRegistries.ITEM.getKey(from).toString());
        if (n != null) {
            carry(to, n);
        }
    }

    /** Hands everything carried over to the village stockpile (as much as fits). */
    void deposit(Village village) {
        carried.forEach((id, n) -> {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                Item item = BuiltInRegistries.ITEM.get(key);
                int room = Math.max(0, village.storageCap() - village.storage().count(item));
                village.storage().add(item, Math.min(room, n));
            }
        });
        carried.clear();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 100 != 0) {
            return;
        }
        // A worker whose village or workplace is gone goes back to being an ordinary villager.
        if (workplace().isEmpty()) {
            orphanTicks += 100;
            if (orphanTicks >= 600) {
                retire();
            }
        } else {
            orphanTicks = 0;
            workplace().ifPresent(w -> {
                if (!hasRestriction()) {
                    restrictTo(w.entrance(), 24);
                }
            });
        }
    }

    /** Turns back into a plain villager. */
    public void retire() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Villager villager = EntityType.VILLAGER.create(server);
        if (villager != null) {
            villager.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
            villager.setCustomName(getCustomName());
            server.addFreshEntity(villager);
        }
        discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        workplace().ifPresent(b -> {
            if (getUUID().equals(b.workerId())) {
                b.setWorkerId(null);
                village().ifPresent(Village::markDirty);
            }
        });
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            String what = workplace().map(b -> village().map(v -> v.buildings().stream()
                    .filter(x -> !x.isComplete()).findFirst()
                    .map(x -> "working on " + x.typeId()).orElse("waiting for work")).orElse(""))
                    .orElse("without a village");
            if (job() != WorkerJob.BUILDER) {
                what = carried.isEmpty() ? "working" : "carrying " + carried;
            }
            player.displayClientMessage(Component.translatable("entity.livingvillages.worker.job." +
                    job().name().toLowerCase()).append(": " + what), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Job", entityData.get(DATA_JOB));
        if (villageId != null) {
            tag.putUUID("Village", villageId);
        }
        if (workplaceId != null) {
            tag.putUUID("Workplace", workplaceId);
        }
        CompoundTag load = new CompoundTag();
        carried.forEach(load::putInt);
        tag.put("Carried", load);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_JOB, tag.getInt("Job"));
        villageId = tag.hasUUID("Village") ? tag.getUUID("Village") : null;
        workplaceId = tag.hasUUID("Workplace") ? tag.getUUID("Workplace") : null;
        carried.clear();
        CompoundTag load = tag.getCompound("Carried");
        for (String k : load.getAllKeys()) {
            carried.put(k, load.getInt(k));
        }
    }

    /** Standing spot at the surface next to a position. */
    static BlockPos standAt(Level level, BlockPos pos) {
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                pos);
    }
}
