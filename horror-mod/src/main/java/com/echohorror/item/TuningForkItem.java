package com.echohorror.item;

import com.echohorror.entity.MimicEntity;
import com.echohorror.horror.Sanity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Камертон. Rings true against a true voice — and trembles near something wearing a borrowed one.
 * The lower your sanity, the less you can trust it.
 */
public class TuningForkItem extends Item {
    public static final double RANGE = 24;

    public TuningForkItem(Properties props) {
        super(props);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer sp) || sp.tickCount % 20 != 7) return;
        if (!selected && sp.getOffhandItem() != stack) return;
        double nearest = Double.MAX_VALUE;
        for (MimicEntity m : level.getEntitiesOfClass(MimicEntity.class, sp.getBoundingBox().inflate(RANGE))) {
            if (m.isRevealed() || !m.isAlive()) continue;
            nearest = Math.min(nearest, m.distanceTo(sp));
        }
        boolean lie = Sanity.get(sp) < 25 && sp.getRandom().nextFloat() < 0.12f; // a tired mind hears what it fears
        if (nearest > RANGE && !lie) return;
        double d = nearest > RANGE ? 6 + sp.getRandom().nextInt(14) : nearest;
        float strength = (float) (1 - d / RANGE);
        sp.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.25f + 0.5f * strength, 0.5f + strength);
        String bars = "≋".repeat(1 + (int) (strength * 4));
        sp.displayClientMessage(Component.literal("Камертон дрожит " + bars + "  — кто-то рядом говорит чужим голосом")
                .withStyle(strength > 0.6f ? ChatFormatting.RED : ChatFormatting.GOLD), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Держите в руке: дрожит, если в 24 блоках").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal("кто-то носит чужое лицо.").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal("Усталому уму не верьте: камертон тоже может ошибаться.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
