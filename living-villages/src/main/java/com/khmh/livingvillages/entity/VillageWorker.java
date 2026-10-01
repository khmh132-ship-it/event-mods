package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.village.Village;
import com.khmh.livingvillages.village.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import java.util.Optional;
import java.util.UUID;

/**
 * A villager the village hired for a job. Looks like a villager, but runs its own goal-based AI instead of the
 * vanilla villager brain. Belongs to one village and one workplace building.
 */
public class VillageWorker extends PathfinderMob implements InventoryCarrier {
    private static final EntityDataAccessor<Integer> DATA_JOB =
            SynchedEntityData.defineId(VillageWorker.class, EntityDataSerializers.INT);

    @Nullable
    private UUID villageId;
    @Nullable
    private UUID workplaceId;
    private final SimpleContainer inventory = new SimpleContainer(18);
    private final Unloading unloading = new Unloading(this);
    private int orphanTicks;

    public VillageWorker(EntityType<? extends VillageWorker> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(true);
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
        goalSelector.addGoal(1, new CollectItemsGoal(this));
        goalSelector.addGoal(2, new BuilderWorkGoal(this));
        goalSelector.addGoal(2, new LumberjackWorkGoal(this));
        goalSelector.addGoal(2, new MinerWorkGoal(this));
        goalSelector.addGoal(2, new ApprenticeWorkGoal(this));
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

    /** Gives the worker a job. A builder may have no workplace yet: he serves the whole village. */
    public void assign(WorkerJob job, Village village, @Nullable Building workplace) {
        entityData.set(DATA_JOB, job.ordinal());
        villageId = village.id();
        setWorkplace(village, workplace);
    }

    public void setWorkplace(Village village, @Nullable Building workplace) {
        workplaceId = workplace == null ? null : workplace.id();
        if (workplace != null) {
            restrictTo(workplace.entrance(), 24);
        } else {
            restrictTo(village.center(), village.radius());
        }
    }

    public boolean hasWorkplace() {
        return workplaceId != null;
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

    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    /** Total number of items in the inventory. */
    public int carried() {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            n += inventory.getItem(i).getCount();
        }
        return n;
    }

    public boolean inventoryFull() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /** Puts gathered items into the inventory; what does not fit falls to the ground. */
    void carry(Item item, int amount) {
        int left = amount;
        while (left > 0) {
            ItemStack stack = new ItemStack(item, Math.min(left, item.getMaxStackSize()));
            left -= stack.getCount();
            ItemStack rest = inventory.addItem(stack);
            if (!rest.isEmpty()) {
                spawnAtLocation(rest);
            }
        }
    }

    void convertCarried(Item from, Item to) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(from)) {
                inventory.setItem(i, new ItemStack(to, s.getCount()));
            }
        }
    }

    /** Walks to the warehouse and puts everything into the chests; true once done. */
    boolean unloadTick() {
        return unloading.tick();
    }

    void resetUnloading() {
        unloading.reset();
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return inventory.canAddItem(stack);
    }

    @Override
    protected void pickUpItem(ItemEntity item) {
        InventoryCarrier.pickUpItem(this, this, item);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        for (ItemStack s : inventory.removeAllItems()) {
            spawnAtLocation(s);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (job() == WorkerJob.BUILDER && tickCount % 20 == 0) {
            village().ifPresent(v -> v.noteBuilder(level().getGameTime()));
        }
        if (tickCount % 100 != 0) {
            return;
        }
        // A worker whose village or workplace is gone goes back to being an ordinary villager.
        boolean lost = village().isEmpty() || !job().villageWide() && workplace().isEmpty();
        if (lost) {
            orphanTicks += 100;
            if (orphanTicks >= 600) {
                retire();
            }
        } else {
            orphanTicks = 0;
            if (!hasRestriction()) {
                village().ifPresent(v -> setWorkplace(v, workplace().orElse(null)));
            }
        }
    }

    /** Turns back into a plain villager. */
    public void retire() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        leaveJob();
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
        if (!level().isClientSide) {
            com.khmh.livingvillages.LivingVillages.LOGGER.info("Village worker ({}) died at {}: {}",
                    job().name().toLowerCase(), blockPosition(), source.getMsgId());
        }
        leaveJob();
    }

    /** Frees the workplace and the village-wide job slot so somebody else can be hired. */
    private void leaveJob() {
        workplace().ifPresent(b -> {
            if (getUUID().equals(b.workerId())) {
                b.setWorkerId(null);
            }
        });
        village().ifPresent(v -> {
            String key = "worker_" + job().name().toLowerCase();
            if (v.data().hasUUID(key) && v.data().getUUID(key).equals(getUUID())) {
                v.data().remove(key);
            }
            v.markDirty();
        });
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            String what = village().map(v -> v.buildings().stream()
                    .filter(x -> !x.isComplete()).findFirst()
                    .map(x -> "working on " + x.typeId()).orElse("waiting for work"))
                    .orElse("without a village");
            if (carried() > 0) {
                what += ", carrying " + carried() + " items";
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
        writeInventoryToTag(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_JOB, tag.getInt("Job"));
        villageId = tag.hasUUID("Village") ? tag.getUUID("Village") : null;
        workplaceId = tag.hasUUID("Workplace") ? tag.getUUID("Workplace") : null;
        readInventoryFromTag(tag);
    }

    /** Standing spot at the surface next to a position. */
    static BlockPos standAt(Level level, BlockPos pos) {
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                pos);
    }
}
