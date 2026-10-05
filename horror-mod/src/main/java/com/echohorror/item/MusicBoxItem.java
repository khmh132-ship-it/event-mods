package com.echohorror.item;

import com.echohorror.horror.MusicBoxAura;
import com.echohorror.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/**
 * Музыкальная шкатулка Маши. A mechanical tune the Echo can't repeat — for as long as it plays, nothing out there
 * can come near, and the people around it can breathe.
 */
public class MusicBoxItem extends Item {
    public static final int PLAY_TICKS = 380;

    public MusicBoxItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            sl.playSound(null, sp, ModSounds.get("item.music_box_play"), SoundSource.PLAYERS, 1.2f, 1.0f);
            MusicBoxAura.start(sp, PLAY_TICKS);
            sp.displayClientMessage(Component.literal("Шкатулка играет. Вокруг становится тихо.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
            com.echohorror.story.Achievements.award(sp, "lullaby");
            player.getCooldowns().addCooldown(this, 20 * 150);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Колыбельная, которую Эхо не умеет повторить.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Пока играет (~19 с): твари не подходят, видения молчат,").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal("рассудок всех рядом восстанавливается. Перезарядка 2,5 мин.").withStyle(ChatFormatting.DARK_AQUA));
    }
}
