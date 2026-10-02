package com.khmh.livingvillages.village;

import com.khmh.livingvillages.building.Building;
import com.khmh.livingvillages.economy.Request;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The quest board by the bell: its signs show what the village is short of right now, and a player who brings
 * some of it (right-clicking a sign with the item in hand) hands it over into the village store and is paid in
 * emeralds from it, one for every eight items, as far as the village has emeralds. Right-clicking with an empty
 * hand (or anything not asked for) lists every open request in chat.
 */
public final class QuestBoard {
    private QuestBoard() {
    }

    /** Open requests, oldest first, that a player could help with (not ones being made right now). */
    private static List<Request> wanted(Village v) {
        List<Request> out = new ArrayList<>();
        for (Request r : v.requests()) {
            if (r.remaining() > 0) {
                out.add(r);
            }
        }
        out.sort(Comparator.comparingLong(Request::created));
        return out;
    }

    private static List<BlockPos> signs(ServerLevel level, Village v) {
        List<BlockPos> out = new ArrayList<>();
        for (Building b : v.buildings()) {
            if (!b.isComplete() || !"quest_board".equals(b.typeId())) {
                continue;
            }
            BoundingBox box = b.box();
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (level.getBlockEntity(p) instanceof SignBlockEntity) {
                    out.add(p.immutable());
                }
            }
        }
        out.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()).thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return out;
    }

    /** Writes the current needs onto the board's signs (called every few seconds while loaded). */
    static void refresh(ServerLevel level, Village v) {
        List<BlockPos> signs = signs(level, v);
        if (signs.isEmpty()) {
            return;
        }
        List<Request> list = wanted(v);
        for (int i = 0; i < signs.size(); i++) {
            if (!(level.getBlockEntity(signs.get(i)) instanceof SignBlockEntity sign)) {
                continue;
            }
            SignText text = new SignText();
            if (i == 0 && list.isEmpty()) {
                text = text.setMessage(0, Component.literal("Village needs").withStyle(ChatFormatting.BOLD))
                        .setMessage(2, Component.literal("nothing now"));
            } else {
                for (int line = 0; line < 2; line++) {
                    int k = i * 2 + line;
                    if (k < list.size()) {
                        Request r = list.get(k);
                        text = text.setMessage(line * 2, Component.literal(r.remaining() + " x"))
                                .setMessage(line * 2 + 1, r.item().getDescription().copy());
                    }
                }
            }
            sign.setText(text, true);
            sign.setChanged();
            level.sendBlockUpdated(signs.get(i), sign.getBlockState(), sign.getBlockState(), 3);
        }
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof SignBlock)) {
            return;
        }
        Optional<Village> village = VillageManager.get(level).at(pos);
        if (village.isEmpty() || village.get().buildings().stream().noneMatch(b -> "quest_board".equals(b.typeId())
                && b.box().isInside(pos))) {
            return;
        }
        Village v = village.get();
        event.setCanceled(true); // no sign editing on the board
        ItemStack hand = player.getMainHandItem();
        Request match = hand.isEmpty() ? null : wanted(v).stream().filter(r -> r.item() == hand.getItem())
                .findFirst().orElse(null);
        if (match == null) {
            List<Request> list = wanted(v);
            player.sendSystemMessage(Component.literal(list.isEmpty() ? "The village needs nothing right now."
                    : "The village needs:").withStyle(ChatFormatting.GOLD));
            for (Request r : list) {
                player.sendSystemMessage(Component.literal("  " + r.remaining() + " x ").append(r.item().getDescription()));
            }
            return;
        }
        int n = Math.min(hand.getCount(), match.remaining());
        hand.shrink(n);
        v.storage().add(match.item(), n);
        match.fulfil(n);
        int pay = Math.min(Math.max(1, n / 8), v.stock(level).count(Items.EMERALD));
        if (pay > 0) {
            v.stock(level).take(Items.EMERALD, pay);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, pay));
        }
        player.sendSystemMessage(Component.literal("Handed over " + n + " ").append(match.item().getDescription())
                .append(pay > 0 ? Component.literal(", paid " + pay + " emerald" + (pay > 1 ? "s" : "")) : Component.literal(" (the village has no emeralds to pay with)"))
                .withStyle(ChatFormatting.GREEN));
        v.markDirty();
        refresh(level, v);
    }
}
