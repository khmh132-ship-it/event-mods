package com.echohorror.block;

import com.echohorror.registry.ModItems;
import com.echohorror.story.StoryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ShardLockBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty SHARDS = IntegerProperty.create("shards", 0, 3);

    public ShardLockBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(SHARDS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, SHARDS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        int n = state.getValue(SHARDS);
        ItemStack held = player.getItemInHand(hand);
        if (n >= 3) return InteractionResult.PASS;
        if (!held.is(ModItems.ECHO_SHARD.get())) {
            if (hand == InteractionHand.MAIN_HAND) {
                player.displayClientMessage(Component.literal("Печать. Три пустых гнезда (" + n + "/3). Изнутри доносится гул.")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            }
            return InteractionResult.PASS;
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        level.setBlock(pos, state.setValue(SHARDS, n + 1), 3);
        level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.2f, 0.6f);
        if (player instanceof ServerPlayer sp) StoryManager.onShardInserted(sp, pos, n + 1);
        return InteractionResult.CONSUME;
    }
}
