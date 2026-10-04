package com.echohorror.item;

import com.echohorror.registry.ModItems;
import com.echohorror.story.StoryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TapeItem extends Item {
    public TapeItem(Properties props) {
        super(props);
    }

    public static ItemStack create(String id) {
        ItemStack stack = new ItemStack(ModItems.TAPE.get());
        stack.getOrCreateTag().putString("tape", id);
        return stack;
    }

    public static String id(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getString("tape") : "";
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(switch (id(stack)) {
            case "tape1" -> "Магнитофонная плёнка №1";
            case "tape2" -> "Магнитофонная плёнка №7";
            case "tape3" -> "Магнитофонная плёнка №12";
            case "tape4" -> "Плёнка без номера";
            default -> "Размагниченная плёнка";
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            if (StoryManager.playTape(sp, id(stack))) {
                player.getCooldowns().addCooldown(this, 700);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Подпись на коробке: «А. С. Воронов. Не для протокола»").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("ПКМ — прослушать (слышно всем рядом)").withStyle(ChatFormatting.GRAY));
    }
}
