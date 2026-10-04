package com.echohorror.item;

import com.echohorror.story.StoryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LocatorItem extends Item {
    public LocatorItem(Properties props) {
        super(props);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer sp)) return;
        boolean held = selected || sp.getOffhandItem() == stack;
        if (held && sp.tickCount % 10 == 0) StoryManager.locatorTick(sp);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Радиопеленгатор. Держите в руке, чтобы найти источник сигнала.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Иногда ловит то, чего ловить не должен.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
