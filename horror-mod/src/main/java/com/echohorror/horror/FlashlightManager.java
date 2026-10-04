package com.echohorror.horror;

import com.echohorror.item.FlashlightItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Real dynamic light for the flashlight: an invisible light block follows the beam. */
public final class FlashlightManager {
    private record Light(ResourceKey<Level> dim, BlockPos pos) {}

    private static final Map<UUID, Light> LIGHTS = new HashMap<>();

    private FlashlightManager() {}

    public static void tick(MinecraftServer server, long tick) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ItemStack fl = p.getMainHandItem().getItem() instanceof FlashlightItem ? p.getMainHandItem()
                    : p.getOffhandItem().getItem() instanceof FlashlightItem ? p.getOffhandItem() : ItemStack.EMPTY;
            boolean on = !fl.isEmpty() && FlashlightItem.isOn(fl) && !FlashlightItem.isEmpty(fl) && p.isAlive() && !p.isSpectator();
            if (!on) {
                remove(server, p.getUUID());
                continue;
            }
            if (tick % 10 == 0 && !p.getAbilities().instabuild) {
                boolean danger = Scares.horrorNearby(p, 14);
                fl.setDamageValue(Math.min(fl.getMaxDamage() - 1, fl.getDamageValue() + (danger ? 2 : 1)));
                if (FlashlightItem.isEmpty(fl)) {
                    FlashlightItem.setOn(fl, false);
                    p.displayClientMessage(Component.literal("Фонарь погас. Батарея села.").withStyle(ChatFormatting.RED), true);
                    remove(server, p.getUUID());
                    continue;
                }
            }
            // flicker when something is near
            if (Scares.horrorNearby(p, 14) && p.getRandom().nextFloat() < 0.35f) {
                remove(server, p.getUUID());
                continue;
            }
            update(p);
        }
    }

    private static void update(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(26));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        BlockPos target = hit.getType() == HitResult.Type.MISS ? BlockPos.containing(end) : hit.getBlockPos().relative(hit.getDirection());
        Vec3 back = p.getLookAngle().scale(-1);
        BlockPos spot = null;
        for (int i = 0; i < 4; i++) {
            BlockPos c = BlockPos.containing(Vec3.atCenterOf(target).add(back.scale(i)));
            if (level.getBlockState(c).isAir() || level.getBlockState(c).is(Blocks.LIGHT)) {
                spot = c;
                break;
            }
        }
        Light old = LIGHTS.get(p.getUUID());
        if (spot == null) {
            remove(p.server, p.getUUID());
            return;
        }
        if (old != null && old.dim == level.dimension() && old.pos.equals(spot)) return;
        remove(p.server, p.getUUID());
        if (level.getBlockState(spot).isAir()) {
            level.setBlock(spot, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 14), 3);
            LIGHTS.put(p.getUUID(), new Light(level.dimension(), spot.immutable()));
        }
    }

    public static void remove(MinecraftServer server, UUID id) {
        Light l = LIGHTS.remove(id);
        if (l == null) return;
        ServerLevel level = server.getLevel(l.dim);
        if (level != null && level.isLoaded(l.pos) && level.getBlockState(l.pos).is(Blocks.LIGHT)) {
            level.setBlock(l.pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    public static void clearAll(MinecraftServer server) {
        for (UUID id : LIGHTS.keySet().toArray(new UUID[0])) remove(server, id);
    }
}
