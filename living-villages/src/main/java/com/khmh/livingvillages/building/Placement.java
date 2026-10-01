package com.khmh.livingvillages.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Local template coordinates to world coordinates for a given origin and rotation. */
public final class Placement {
    private Placement() {
    }

    public static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE).setRotationPivot(BlockPos.ZERO);
    }

    public static BlockPos toWorld(BlockPos origin, Rotation rotation, BlockPos local) {
        return StructureTemplate.calculateRelativePosition(settings(rotation), local).offset(origin);
    }

    public static BoundingBox box(BlockPos origin, Rotation rotation, Vec3i size) {
        BlockPos a = toWorld(origin, rotation, BlockPos.ZERO);
        BlockPos b = toWorld(origin, rotation, new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
        return BoundingBox.fromCorners(a, b);
    }

    /** Template y of the building origin when the ground surface is at {@code groundY}. */
    public static int originY(BuildingType type, int groundY) {
        return type.vanilla() ? groundY + 1 : groundY - type.groundLayer();
    }
}
