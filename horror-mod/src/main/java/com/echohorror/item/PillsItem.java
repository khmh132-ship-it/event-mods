package com.echohorror.item;

import com.echohorror.horror.Sanity;
import com.echohorror.registry.ModSounds;
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

public class PillsItem extends Item {
    public PillsItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            Sanity.add(sp, 35f);
            level.playSound(null, player.blockPosition(), ModSounds.get("item.pills"), SoundSource.PLAYERS, 0.8f, 1f);
            sp.displayClientMessage(Component.literal("Руки перестают дрожать.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.getCooldowns().addCooldown(this, 200);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Успокоительное. Восстанавливает рассудок.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("«Принимать при слуховых галлюцинациях»").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
