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
    /** 0..20 like a player's hunger bar. */
    private int food = 20;
    @Nullable
    private BlockPos bed;
    private String status = "idle";
    private int orphanTicks;

    public VillageWorker(EntityType<? extends VillageWorker> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(true);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
        // Up a block like a player on stairs, no hopping; and around water rather than through it.
        setMaxUpStep(1.0F);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER, 8.0F);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER_BORDER, 2.0F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_JOB, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new SleepGoal(this));
        goalSelector.addGoal(1, new EatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(1, new CollectItemsGoal(this));
        goalSelector.addGoal(2, new BuilderWorkGoal(this));
        goalSelector.addGoal(2, new LumberjackWorkGoal(this));
        goalSelector.addGoal(2, new MinerWorkGoal(this));
        goalSelector.addGoal(2, new CrafterWorkGoal(this));
        goalSelector.addGoal(2, new FarmerWorkGoal(this));
        goalSelector.addGoal(2, new AnimalWorkGoal(this));
        goalSelector.addGoal(2, new FishermanGoal(this));
        goalSelector.addGoal(2, new ClericGoal(this));
        goalSelector.addGoal(3, new SugarCaneGoal(this));
        goalSelector.addGoal(2, new CartographerGoal(this));
        goalSelector.addGoal(2, new StorekeeperGoal(this));
        goalSelector.addGoal(2, new CarrierGoal(this));
        // Everyone but a guard runs from what would kill him, as plain villagers do; back to work once it is gone.
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(this,
                net.minecraft.world.entity.monster.Monster.class, 8.0F, 0.6, 0.75,
                m -> job() != WorkerJob.GUARD && !(m instanceof net.minecraft.world.entity.monster.EnderMan)
                        && !(m instanceof net.minecraft.world.entity.monster.ZombifiedPiglin)
                        && hasLineOfSight(m)));
        goalSelector.addGoal(1, new CourtGoal(this));
        goalSelector.addGoal(1, new GuardGoals.Melee(this));
        goalSelector.addGoal(2, new GuardGoals.Patrol(this));
        targetSelector.addGoal(1, new GuardGoals.Retaliate(this));
        targetSelector.addGoal(2, new GuardGoals.Hunt(this));
        // Fallen into a pit or the quarry: getting out comes before the work up top (which he cannot reach anyway).
        goalSelector.addGoal(1, new ComeUpGoal(this));
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

    /** What he is doing right now, for /village workers. */
    public String status() {
        // Goals set their status every tick they run; one not refreshed for a while is stale.
        return tickCount - statusSetAt > 100 ? "idle" : status;
    }

    private int statusSetAt;

    public void setStatus(String status) {
        this.status = status;
        this.statusSetAt = tickCount;
    }

    public int food() {
        return food;
    }

    public boolean isHungry() {
        return food <= 10;
    }

    public boolean isStarving() {
        return food <= 2;
    }

    void feed(int nutrition) {
        food = Math.min(20, food + nutrition);
    }

    /** Work goes slower on an empty stomach. */
    public double workSpeed() {
        return food <= 4 ? 0.5 : 1.0;
    }

    @Nullable
    public BlockPos bed() {
        return bed;
    }

    void setBed(@Nullable BlockPos bed) {
        this.bed = bed;
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
        unloading.keep(java.util.Map.of()); // a former job's keepings do not stick
        return unloading.tick();
    }

    /** Like {@link #unloadTick()}, but the worker keeps some items for his own work. */
    boolean unloadTick(java.util.Map<Item, Integer> keep) {
        unloading.keep(keep);
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

    private final Unstuck unstuck = new Unstuck(this);

    /** A jump straight up, for putting a block underfoot. */
    void hop() {
        getNavigation().stop();
        jumpFromGround();
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new WorkerNavigation(this, level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel sl && getNavigation() instanceof WorkerNavigation nav) {
            unstuck.tick(sl, nav.wanted, nav.wantedAt);
        }
        if (tickCount % 4800 == 0 && food > 0 && !isSleeping()) {
            food--; // about a loaf a day: wheat takes days to ripen, and the fields have to feed children too
        }
        if (tickCount % 40 == 0 && level() instanceof ServerLevel sl) {
            village().ifPresent(v -> com.khmh.livingvillages.village.VillageLoader.follow(sl, this, v));
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

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl && reason.shouldDestroy()) {
            com.khmh.livingvillages.village.VillageLoader.release(sl, this);
        }
        super.remove(reason);
    }

    private java.util.UUID mate;
    private long mateUntil;

    /** Paired off by the village for a child: walks up to {@code other} till {@code until} (null to stop). */
    public void courting(@Nullable net.minecraft.world.entity.Entity other, long until) {
        mate = other == null ? null : other.getUUID();
        mateUntil = until;
    }

    @Nullable
    java.util.UUID mate() {
        if (mate != null && level().getGameTime() > mateUntil) {
            mate = null;
        }
        return mate;
    }

    /** Down off a roof or a ledge four blocks high rather than stuck up there (a bump, half a heart at most). */
    @Override
    public int getMaxFallDistance() {
        return Math.max(super.getMaxFallDistance(), 4);
    }

    /** Where he is trying to get to and whether there is a way, for /village workers. */
    public String navInfo() {
        if (getNavigation() instanceof WorkerNavigation nav && nav.wanted != null) {
            return " -> " + BlockPos.containing(nav.wanted).toShortString() + (nav.noWay() ? " (no way)" : "");
        }
        return "";
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
            Beds.release(v, this);
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
        tag.putInt("Food", food);
        if (bed != null) {
            tag.putLong("Bed", bed.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_JOB, tag.getInt("Job"));
        villageId = tag.hasUUID("Village") ? tag.getUUID("Village") : null;
        workplaceId = tag.hasUUID("Workplace") ? tag.getUUID("Workplace") : null;
        readInventoryFromTag(tag);
        food = tag.contains("Food") ? tag.getInt("Food") : 20;
        bed = tag.contains("Bed") ? BlockPos.of(tag.getLong("Bed")) : null;
    }

    /** Standing spot at the surface next to a position. */
    static BlockPos standAt(Level level, BlockPos pos) {
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                pos);
    }
}
