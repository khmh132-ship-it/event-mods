package com.echohorror.item;

import com.echohorror.story.StoryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

public class JournalItem extends Item {
    public JournalItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            StoryManager.sendJournal(sp, true);
            level.playSound(null, player.blockPosition(), com.echohorror.registry.ModSounds.get("item.page"), SoundSource.PLAYERS, 0.6f, 1f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Общий журнал отряда: цели и найденные записи.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Страницы пахнут сыростью.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
