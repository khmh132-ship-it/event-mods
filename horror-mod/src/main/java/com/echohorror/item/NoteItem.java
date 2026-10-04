package com.echohorror.item;

import com.echohorror.registry.ModItems;
import com.echohorror.story.Notes;
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

public class NoteItem extends Item {
    public NoteItem(Properties props) {
        super(props);
    }

    public static ItemStack create(String id) {
        ItemStack stack = new ItemStack(ModItems.NOTE.get());
        stack.getOrCreateTag().putString("note", id);
        return stack;
    }

    public static String id(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getString("note") : "";
    }

    @Override
    public Component getName(ItemStack stack) {
        Notes.Note n = Notes.get(id(stack));
        return n == null ? Component.literal("Пустой лист") : Component.literal(n.title());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            StoryManager.readNote(sp, id(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Notes.Note n = Notes.get(id(stack));
        if (n != null) tooltip.add(Component.literal(n.place()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("ПКМ — прочитать").withStyle(ChatFormatting.GRAY));
    }
}
