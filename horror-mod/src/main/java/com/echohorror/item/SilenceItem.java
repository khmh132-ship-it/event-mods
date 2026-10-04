package com.echohorror.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Trophy after the finale: while carried, sanity never drops below a floor. */
public class SilenceItem extends Item {
    public SilenceItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("То, что осталось от Отголоска.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Если поднести к уху — ничего не слышно. Совсем ничего.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("Пока при вас — рассудок защищён.").withStyle(ChatFormatting.DARK_AQUA));
    }
}
