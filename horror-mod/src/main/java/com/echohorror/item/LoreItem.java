package com.echohorror.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Simple item with atmospheric tooltip lines. */
public class LoreItem extends Item {
    private final String[] lore;
    private final boolean foil;

    public LoreItem(Properties props, boolean foil, String... lore) {
        super(props);
        this.lore = lore;
        this.foil = foil;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (String s : lore) tooltip.add(Component.literal(s).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
