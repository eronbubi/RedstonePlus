package de.eron.redstoneplus.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import javax.annotation.Nullable;

/**
 * A block driven by a redstone signal from any side, like a dispenser. POWERED mirrors the input.
 * Subclasses react to the rising edge ({@link #onRise}), the falling edge ({@link #onFall}) or, when
 * {@link #workInterval()} is above zero, work every N ticks while powered ({@link #work}).
 */
public abstract class PoweredBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    protected PoweredBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
    }

    protected void onFall(BlockState state, ServerLevel level, BlockPos pos) {
    }

    protected void work(BlockState state, ServerLevel level, BlockPos pos) {
    }

    /** 0 = edge triggered only, otherwise the interval in ticks for {@link #work} while powered. */
    protected int workInterval() {
        return 0;
    }

    /** Whether {@link #onFall} is needed. Otherwise only rising edges schedule a tick, so even 1 tick pulses fire. */
    protected boolean reactsToFall() {
        return false;
    }

    protected boolean readPower(Level level, BlockPos pos) {
        return level.hasNeighborSignal(pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        boolean powered = this.readPower(level, pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            if (powered || this.reactsToFall()) {
                level.scheduleTick(pos, this, 1);
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) {
            this.neighborChanged(state, level, pos, this, pos, false);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.workInterval() > 0) {
            if (state.getValue(POWERED)) {
                level.scheduleTick(pos, this, this.workInterval());
                this.work(state, level, pos);
            }
        } else if (!this.reactsToFall() || state.getValue(POWERED)) {
            this.onRise(state, level, pos);
        } else {
            this.onFall(state, level, pos);
        }
    }
}
