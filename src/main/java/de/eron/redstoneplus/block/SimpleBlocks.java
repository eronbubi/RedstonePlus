package de.eron.redstoneplus.block;

import de.eron.redstoneplus.entity.FrozenTntEntity;
import de.eron.redstoneplus.entity.NukeTntEntity;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;

/** Small blocks that need little code each. */
public final class SimpleBlocks {
    private SimpleBlocks() {
    }

    /** Constant signal source on all sides (Block of Uranium, Reinforced Redstone Block). */
    public static class SignalSource extends Block {
        public SignalSource(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean isSignalSource(BlockState state) {
            return true;
        }

        @Override
        protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return 15;
        }
    }

    /** Signal source with an adjustable strength 1-15 (right click, sneak to go down). */
    public static class VariableSource extends Block {
        public static final IntegerProperty POWER = BlockStateProperties.POWER;

        public VariableSource(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 15));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(POWER);
        }

        @Override
        protected boolean isSignalSource(BlockState state) {
            return true;
        }

        @Override
        protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return state.getValue(POWER);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide()) {
                int power = state.getValue(POWER);
                power = player.isShiftKeyDown() ? (power <= 1 ? 15 : power - 1) : (power >= 15 ? 1 : power + 1);
                level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
                player.displayClientMessage(Component.translatable("message.redstoneplus.power", power), true);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Lamp without the vanilla turn-off delay; the inverted version is lit while NOT powered. */
    public static class Lamp extends Block {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;
        private final boolean inverted;

        public Lamp(Properties properties, boolean inverted) {
            super(properties);
            this.inverted = inverted;
            this.registerDefaultState(this.stateDefinition.any().setValue(LIT, inverted));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
            return this.defaultBlockState().setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()) != this.inverted);
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
            if (!level.isClientSide()) {
                boolean lit = level.hasNeighborSignal(pos) != this.inverted;
                if (lit != state.getValue(LIT)) {
                    level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Solid block that becomes passable (and see-through) while powered. */
    public static class Phantom extends Block {
        public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

        public Phantom(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(POWERED);
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(POWERED) ? Shapes.empty() : Shapes.block();
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
            if (!level.isClientSide()) {
                boolean powered = level.hasNeighborSignal(pos);
                if (powered != state.getValue(POWERED)) {
                    level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Shows the incoming signal strength as a number. */
    public static class SignalDisplay extends Block {
        public static final IntegerProperty POWER = BlockStateProperties.POWER;

        public SignalDisplay(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(POWER);
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
            if (!level.isClientSide()) {
                int power = level.getBestNeighborSignal(pos);
                if (power != state.getValue(POWER)) {
                    level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_CLIENTS);
                }
            }
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
            this.neighborChanged(state, level, pos, this, pos, false);
        }
    }

    /** Frozen TNT that passes through becomes normal primed TNT. Everything else just walks through. */
    public static class ActivatorNet extends Block {
        public ActivatorNet(Properties properties) {
            super(properties);
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            if (!level.isClientSide() && entity instanceof FrozenTntEntity frozen) {
                frozen.activate();
            }
        }
    }

    /** Pressure-plate style mine: explodes when a living entity steps on it or it gets powered. */
    public static class Landmine extends Block {
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 1, 15);

        public Landmine(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            if (!level.isClientSide() && entity instanceof LivingEntity living && !living.isSpectator()
                    && !(living instanceof Player player && player.isShiftKeyDown())) {
                detonate(level, pos);
            }
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
            if (!level.isClientSide() && level.hasNeighborSignal(pos)) {
                detonate(level, pos);
            }
        }

        private static void detonate(Level level, BlockPos pos) {
            level.removeBlock(pos, false);
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 3.5F, Level.ExplosionInteraction.TNT);
        }
    }

    /** TNT-like block that primes a huge Uranium Nuke. Lit by redstone, flint and steel, fire charges or explosions. */
    public static class UraniumNuke extends Block {
        public UraniumNuke(Properties properties) {
            super(properties);
        }

        public static void prime(Level level, BlockPos pos, int fuse) {
            if (level instanceof ServerLevel serverLevel) {
                NukeTntEntity nuke = new NukeTntEntity(ModRegistry.NUKE_TNT.get(), serverLevel);
                nuke.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                nuke.setFuse(fuse);
                serverLevel.addFreshEntity(nuke);
                serverLevel.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 2.0F, 0.5F);
                serverLevel.playSound(null, pos, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.BLOCKS, 2.0F, 0.5F);
                serverLevel.gameEvent(null, GameEvent.PRIME_FUSE, pos);
            }
        }

        private void primeAndRemove(Level level, BlockPos pos) {
            level.removeBlock(pos, false);
            prime(level, pos, NukeTntEntity.FUSE);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
            if (!oldState.is(this) && !level.isClientSide() && level.hasNeighborSignal(pos)) {
                this.primeAndRemove(level, pos);
            }
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
            if (!level.isClientSide() && level.hasNeighborSignal(pos)) {
                this.primeAndRemove(level, pos);
            }
        }

        @Override
        public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
            prime(level, pos, NukeTntEntity.FUSE / 4 + level.getRandom().nextInt(20));
        }

        @Override
        public boolean dropFromExplosion(Explosion explosion) {
            return false;
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
                return super.useItemOn(stack, state, level, pos, player, hand, hit);
            }
            if (!level.isClientSide()) {
                this.primeAndRemove(level, pos);
                if (stack.is(Items.FLINT_AND_STEEL)) {
                    stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                } else {
                    stack.consume(1, player);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
    }

    /** Moves entities standing on it in its direction, stops while powered. */
    public static class Conveyor extends Block {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

        public Conveyor(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, POWERED);
        }

        @Override
        @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
            return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection())
                    .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
            if (!level.isClientSide()) {
                boolean powered = level.hasNeighborSignal(pos);
                if (powered != state.getValue(POWERED)) {
                    level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
                }
            }
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return NewMachines.CONVEYOR_SHAPE;
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (state.getValue(POWERED) || entity.isShiftKeyDown()) {
                return;
            }
            Direction dir = state.getValue(FACING);
            Vec3 motion = entity.getDeltaMovement();
            double speed = 0.2;
            double x = dir.getStepX() != 0 ? dir.getStepX() * speed : motion.x * 0.5;
            double z = dir.getStepZ() != 0 ? dir.getStepZ() * speed : motion.z * 0.5;
            entity.setDeltaMovement(x, motion.y, z);
        }
    }
}
