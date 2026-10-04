package com.echohorror.item;

import com.echohorror.registry.ModItems;
import com.echohorror.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Flashlight: lights up the spot it points at (server places invisible light blocks). Durability = battery. */
public class FlashlightItem extends Item {
    public static final int CAPACITY = 1200; // 1 point per 10 ticks => 10 minutes of light

    public FlashlightItem(Properties props) {
        super(props);
    }

    public static boolean isOn(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("on");
    }

    public static void setOn(ItemStack stack, boolean on) {
        stack.getOrCreateTag().putBoolean("on", on);
    }

    public static boolean isEmpty(ItemStack stack) {
        return stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                int slot = findBattery(player);
                if (slot >= 0 && stack.getDamageValue() > 0) {
                    player.getInventory().getItem(slot).shrink(1);
                    stack.setDamageValue(0);
                    player.displayClientMessage(Component.literal("Батарея заменена.").withStyle(ChatFormatting.GRAY), true);
                } else if (slot < 0) {
                    player.displayClientMessage(Component.literal("Нет батареек.").withStyle(ChatFormatting.RED), true);
                }
            } else if (isEmpty(stack)) {
                setOn(stack, false);
                player.displayClientMessage(Component.literal("Батарея села. (Shift+ПКМ — заменить)").withStyle(ChatFormatting.RED), true);
            } else {
                setOn(stack, !isOn(stack));
            }
            level.playSound(null, player.blockPosition(), ModSounds.get("item.flashlight"), SoundSource.PLAYERS, 0.7f, isOn(stack) ? 1.2f : 0.9f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static int findBattery(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.BATTERY.get())) return i;
        }
        return -1;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isOn(stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int pct = Math.round(100f * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
        tooltip.add(Component.literal("Заряд: " + pct + "%").withStyle(pct > 20 ? ChatFormatting.GRAY : ChatFormatting.RED));
        tooltip.add(Component.literal("ПКМ — вкл/выкл, Shift+ПКМ — заменить батарейку").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Мигает, когда рядом что-то есть.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
