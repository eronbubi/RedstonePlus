package de.eron.redstoneplus.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Wireless redstone on 16 channels, across any distance and dimension (both ends must be loaded).
 * Transmitters refresh their channel every other tick while powered; receivers output 15 while their
 * channel was refreshed recently. The Redstone Remote sends a one second pulse.
 */
public final class Wireless {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 15);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final Map<MinecraftServer, long[]> ACTIVE_UNTIL = new WeakHashMap<>();

    private Wireless() {
    }

    private static long[] channels(MinecraftServer server) {
        return ACTIVE_UNTIL.computeIfAbsent(server, s -> new long[16]);
    }

    public static void activate(MinecraftServer server, int channel, int ticks) {
        long[] until = channels(server);
        until[channel] = Math.max(until[channel], server.getTickCount() + ticks);
    }

    public static boolean isActive(MinecraftServer server, int channel) {
        return channels(server)[channel] >= server.getTickCount();
    }

    static InteractionResult cycleChannel(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            int channel = (state.getValue(CHANNEL) + (player.isShiftKeyDown() ? 15 : 1)) % 16;
            level.setBlock(pos, state.setValue(CHANNEL, channel), Block.UPDATE_ALL);
            player.displayClientMessage(Component.translatable("message.redstoneplus.channel", channel + 1), true);
        }
        return InteractionResult.SUCCESS;
    }

    public static class Transmitter extends Block {
        public Transmitter(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(CHANNEL, 0).setValue(POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(CHANNEL, POWERED);
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
            if (level.isClientSide()) {
                return;
            }
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
                if (powered) {
                    level.scheduleTick(pos, this, 1);
                }
            }
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
            if (!oldState.is(this)) {
                this.neighborChanged(state, level, pos, this, pos, false);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(POWERED)) {
                activate(level.getServer(), state.getValue(CHANNEL), 3);
                level.scheduleTick(pos, this, 2);
            }
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            return cycleChannel(state, level, pos, player);
        }
    }

    public static class Receiver extends Block {
        public Receiver(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(CHANNEL, 0).setValue(POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(CHANNEL, POWERED);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
            if (!level.isClientSide() && !oldState.is(this)) {
                level.scheduleTick(pos, this, 1);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            level.scheduleTick(pos, this, 2);
            boolean active = isActive(level.getServer(), state.getValue(CHANNEL));
            if (active != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, active), Block.UPDATE_ALL);
                level.updateNeighborsAt(pos, this);
            }
        }

        @Override
        protected boolean isSignalSource(BlockState state) {
            return true;
        }

        @Override
        protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return state.getValue(POWERED) ? 15 : 0;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            return cycleChannel(state, level, pos, player);
        }
    }
}
