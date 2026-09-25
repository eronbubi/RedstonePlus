package de.eron.redstoneplus.block.piston;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** The head and the arm segments of an extended Super Piston. They never exist as items. */
public final class SuperPistonParts {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty STICKY = BooleanProperty.create("sticky");

    private static final Map<Direction, VoxelShape> HEAD = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> ARM = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) {
            VoxelShape rod = switch (d.getAxis()) {
                case X -> Block.box(0, 6, 6, 16, 10, 10);
                case Y -> Block.box(6, 0, 6, 10, 16, 10);
                case Z -> Block.box(6, 6, 0, 10, 10, 16);
            };
            VoxelShape plate = switch (d) {
                case DOWN -> Block.box(0, 0, 0, 16, 4, 16);
                case UP -> Block.box(0, 12, 0, 16, 16, 16);
                case NORTH -> Block.box(0, 0, 0, 16, 16, 4);
                case SOUTH -> Block.box(0, 0, 12, 16, 16, 16);
                case WEST -> Block.box(0, 0, 0, 4, 16, 16);
                case EAST -> Block.box(12, 0, 0, 16, 16, 16);
            };
            HEAD.put(d, Shapes.or(plate, rod));
            ARM.put(d, rod);
        }
    }

    private SuperPistonParts() {
    }

    /** Breaking any part of the arm breaks the piston it belongs to, like the vanilla piston head does. */
    static void breakBase(Level level, BlockPos pos, Direction facing, Player player) {
        for (int i = 1; i <= SuperPistonBlock.MAX_RANGE + 1; i++) {
            BlockPos back = pos.relative(facing.getOpposite(), i);
            BlockState state = level.getBlockState(back);
            if (state.getBlock() instanceof Arm) {
                continue;
            }
            if (state.getBlock() instanceof SuperPistonBlock && state.getValue(SuperPistonBlock.FACING) == facing) {
                level.destroyBlock(back, !player.isCreative());
            }
            return;
        }
    }

    public static class Head extends Block {
        public Head(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP).setValue(STICKY, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, STICKY);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return HEAD.get(state.getValue(FACING));
        }

        @Override
        public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (!level.isClientSide()) {
                breakBase(level, pos, state.getValue(FACING), player);
            }
            return super.playerWillDestroy(level, pos, state, player);
        }

        @Override
        public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
            return ItemStack.EMPTY;
        }
    }

    public static class Arm extends Block {
        public Arm(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return ARM.get(state.getValue(FACING));
        }

        @Override
        public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (!level.isClientSide()) {
                breakBase(level, pos, state.getValue(FACING), player);
            }
            return super.playerWillDestroy(level, pos, state, player);
        }

        @Override
        public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
            return ItemStack.EMPTY;
        }
    }
}
