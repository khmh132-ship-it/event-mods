package com.echohorror.item;

import com.echohorror.entity.CrawlerEntity;
import com.echohorror.entity.MimicEntity;
import com.echohorror.entity.PhantomEntity;
import com.echohorror.entity.SilentEntity;
import com.echohorror.horror.Sanity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ручной колокольчик. A clean tone the Echo cannot copy: visions dissolve, the blind ones reel, Немая freezes,
 * and a Двойник cannot keep its borrowed face.
 */
public class HandbellItem extends Item {
    public HandbellItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            sl.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0f, 1.6f);
            sl.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 1.4f);
            sl.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 2.2, player.getZ(), 6, 0.6, 0.3, 0.6, 1);
            AABB box = player.getBoundingBox().inflate(16);
            for (PhantomEntity e : sl.getEntitiesOfClass(PhantomEntity.class, box.inflate(16))) e.dissolve();
            for (CrawlerEntity c : sl.getEntitiesOfClass(CrawlerEntity.class, box)) {
                c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 3));
                c.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                c.setTarget(null);
            }
            for (SilentEntity s : sl.getEntitiesOfClass(SilentEntity.class, box)) s.freeze(120);
            int unmasked = 0;
            for (MimicEntity m : sl.getEntitiesOfClass(MimicEntity.class, box)) {
                if (!m.isRevealed()) {
                    m.reveal();
                    unmasked++;
                }
            }
            for (Player o : sl.players()) {
                if (o instanceof ServerPlayer os && os.distanceTo(player) < 16) Sanity.add(os, 6f);
            }
            if (unmasked > 0) {
                sp.displayClientMessage(Component.literal("Звон сорвал с кого-то чужое лицо.").withStyle(ChatFormatting.DARK_RED), true);
            }
            player.getCooldowns().addCooldown(this, 900);
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Чистый звон, который Эхо не умеет повторить.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Развеивает видения, оглушает Ползунов, замораживает Немую,").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal("срывает маску с Двойника. Перезарядка 45 с.").withStyle(ChatFormatting.DARK_AQUA));
    }
}
