package com.khmh.livingvillages.building;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.UUID;

/** One building of a village: where it stands and how far its construction got. */
public class Building {
    public enum Phase {
        PREPARE, BUILD, DONE
    }

    private final UUID id;
    private final String typeId;
    private final BlockPos origin;
    private final Rotation rotation;
    private final int groundY;
    private final BoundingBox box;
    private final BlockPos entrance;
    private Phase phase;
    private int progress;
    private long lastStep;
    @Nullable
    private UUID workerId;

    public Building(UUID id, String typeId, BlockPos origin, Rotation rotation, int groundY, BoundingBox box,
                    BlockPos entrance, Phase phase, int progress, long lastStep) {
        this.id = id;
        this.typeId = typeId;
        this.origin = origin;
        this.rotation = rotation;
        this.groundY = groundY;
        this.box = box;
        this.entrance = entrance;
        this.phase = phase;
        this.progress = progress;
        this.lastStep = lastStep;
    }

    public UUID id() {
        return id;
    }

    public String typeId() {
        return typeId;
    }

    public BuildingType type() {
        return BuildingTypes.get(typeId);
    }

    public BlockPos origin() {
        return origin;
    }

    public Rotation rotation() {
        return rotation;
    }

    public int groundY() {
        return groundY;
    }

    public BoundingBox box() {
        return box;
    }

    public BlockPos entrance() {
        return entrance;
    }

    public Phase phase() {
        return phase;
    }

    public boolean isComplete() {
        return phase == Phase.DONE;
    }

    public int progress() {
        return progress;
    }

    public long lastStep() {
        return lastStep;
    }

    @Nullable
    public UUID workerId() {
        return workerId;
    }

    public void setWorkerId(@Nullable UUID workerId) {
        this.workerId = workerId;
    }

    void setLastStep(long lastStep) {
        this.lastStep = lastStep;
    }

    void advance() {
        progress++;
    }

    void nextPhase() {
        phase = phase == Phase.PREPARE ? Phase.BUILD : Phase.DONE;
        progress = 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("type", typeId);
        t.putLong("origin", origin.asLong());
        t.putString("rotation", rotation.name());
        t.putInt("groundY", groundY);
        t.putIntArray("box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        t.putLong("entrance", entrance.asLong());
        t.putString("phase", phase.name());
        t.putInt("progress", progress);
        t.putLong("lastStep", lastStep);
        if (workerId != null) {
            t.putUUID("worker", workerId);
        }
        return t;
    }

    public static Building load(CompoundTag t) {
        int[] b = t.getIntArray("box");
        Building building = new Building(t.getUUID("id"), t.getString("type"), BlockPos.of(t.getLong("origin")),
                Rotation.valueOf(t.getString("rotation")), t.getInt("groundY"),
                new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), BlockPos.of(t.getLong("entrance")),
                Phase.valueOf(t.getString("phase")), t.getInt("progress"), t.getLong("lastStep"));
        if (t.hasUUID("worker")) {
            building.workerId = t.getUUID("worker");
        }
        return building;
    }
}
