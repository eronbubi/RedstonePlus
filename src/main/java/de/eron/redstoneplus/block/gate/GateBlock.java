package de.eron.redstoneplus.block.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;

/**
 * Base for every flat, repeater-like logic component.
 * FACING is the output direction (the way the player looked when placing it); the input is behind,
 * the side inputs are left and right. All state changes go through a scheduled tick so the block
 * never mutates the world from inside a neighbour update.
 */
public abstract class GateBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Last seen value of the back input, used by edge-triggered components. */
    public static final BooleanProperty INPUT = BooleanProperty.create("input");
    public static final IntegerProperty DELAY = IntegerProperty.create("delay", 1, 20);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    protected GateBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    /** Computes the next state from the current inputs. Called on the server from a scheduled tick. */
    protected abstract BlockState update(BlockState state, ServerLevel level, BlockPos pos);

    /** Signal strength this block sends towards {@code towards} (the direction from this block to the receiver). */
    protected int outputTowards(BlockState state, Direction towards) {
        return towards == state.getValue(FACING) && state.getValue(POWERED) ? 15 : 0;
    }

    /** Delay in game ticks between an input change and the re-evaluation. */
    protected int reactionDelay(BlockState state) {
        return 2;
    }

    // ---------- input helpers ----------

    public static Direction back(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    public static Direction left(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    public static Direction right(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    /** Signal arriving from the neighbour on {@code side}. */
    public static int input(Level level, BlockPos pos, Direction side) {
        BlockPos from = pos.relative(side);
        int signal = level.getSignal(from, side);
        if (signal >= 15) {
            return signal;
        }
        BlockState state = level.getBlockState(from);
        return Math.max(signal, state.is(Blocks.REDSTONE_WIRE) ? state.getValue(RedStoneWireBlock.POWER) : 0);
    }

    public static boolean on(Level level, BlockPos pos, Direction side) {
        return input(level, pos, side) > 0;
    }

    // ---------- block behaviour ----------

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && direction.getAxis().isHorizontal();
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.outputTowards(state, direction.getOpposite());
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.getSignal(state, level, pos, direction);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, this.reactionDelay(state));
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
            this.notifyOutputs(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!movedByPiston && !state.is(newState.getBlock())) {
            this.notifyOutputs(level, pos);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState next = this.update(state, level, pos);
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            this.notifyOutputs(level, pos);
        }
    }

    /** Wakes up every horizontal neighbour and the blocks around them (needed for strong power through blocks). */
    protected void notifyOutputs(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos target = pos.relative(dir);
            level.neighborChanged(target, this, pos);
            level.updateNeighborsAtExceptFromFacing(target, this, dir.getOpposite());
        }
    }

    // ---------- configurable delay (right click, sneak to go backwards) ----------

    protected boolean hasDelay() {
        return false;
    }

    protected Component delayMessage(int value) {
        return Component.translatable("message.redstoneplus.delay_ticks", value, value * 2);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!this.hasDelay()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            int value = state.getValue(DELAY);
            value = player.isShiftKeyDown() ? (value <= 1 ? 20 : value - 1) : (value >= 20 ? 1 : value + 1);
            level.setBlock(pos, state.setValue(DELAY, value), Block.UPDATE_ALL);
            player.displayClientMessage(this.delayMessage(value), true);
        }
        return InteractionResult.SUCCESS;
    }
}
