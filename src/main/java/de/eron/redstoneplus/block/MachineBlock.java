package de.eron.redstoneplus.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

/** A {@link PoweredBlock} with a front face that can point in all six directions, placed like a dispenser. */
public abstract class MachineBlock extends PoweredBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    protected MachineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public static Direction facing(BlockState state) {
        return state.getValue(FACING);
    }

    /** Centre of the block face the machine points at, slightly outside the block. */
    public static Vec3 frontPoint(BlockPos pos, Direction facing, double distance) {
        return Vec3.atCenterOf(pos).add(facing.getStepX() * distance, facing.getStepY() * distance, facing.getStepZ() * distance);
    }
}
