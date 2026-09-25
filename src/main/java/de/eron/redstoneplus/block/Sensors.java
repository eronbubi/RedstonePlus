package de.eron.redstoneplus.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

/** Blocks that measure something in the world and output it as a redstone signal on all sides. */
public final class Sensors {
    private Sensors() {
    }

    public abstract static class Sensor extends Block {
        public static final IntegerProperty POWER = BlockStateProperties.POWER;
        public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

        protected Sensor(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0).setValue(POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(POWER, POWERED);
        }

        protected abstract int measure(BlockState state, ServerLevel level, BlockPos pos);

        protected int interval() {
            return 10;
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
            if (!level.isClientSide() && !oldState.is(this)) {
                level.scheduleTick(pos, this, 1);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            level.scheduleTick(pos, this, this.interval());
            int power = Math.max(0, Math.min(15, this.measure(state, level, pos)));
            if (power != state.getValue(POWER)) {
                level.setBlock(pos, state.setValue(POWER, power).setValue(POWERED, power > 0), Block.UPDATE_ALL);
                level.updateNeighborsAt(pos, this);
            }
        }

        @Override
        protected boolean isSignalSource(BlockState state) {
            return true;
        }

        @Override
        protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return state.getValue(POWER);
        }
    }

    public static class PlayerDetector extends Sensor {
        public PlayerDetector(Properties properties) {
            super(properties);
        }

        @Override
        protected int measure(BlockState state, ServerLevel level, BlockPos pos) {
            Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8.0, p -> !p.isSpectator());
            return nearest == null ? 0 : 15;
        }
    }

    public static class MobDetector extends Sensor {
        public MobDetector(Properties properties) {
            super(properties);
        }

        @Override
        protected int measure(BlockState state, ServerLevel level, BlockPos pos) {
            int count = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(8.0), e -> e instanceof Enemy && e.isAlive()).size();
            return count == 0 ? 0 : Math.min(15, 7 + count);
        }
    }

    /** 0 = clear, 7 = rain, 15 = thunderstorm. */
    public static class WeatherSensor extends Sensor {
        public WeatherSensor(Properties properties) {
            super(properties);
        }

        @Override
        protected int measure(BlockState state, ServerLevel level, BlockPos pos) {
            return level.isThundering() ? 15 : level.isRaining() ? 7 : 0;
        }
    }

    public static class NightSensor extends Sensor {
        public NightSensor(Properties properties) {
            super(properties);
        }

        @Override
        protected int measure(BlockState state, ServerLevel level, BlockPos pos) {
            return level.isNight() ? 15 : 0;
        }
    }

    public static class ItemDetector extends Sensor {
        public ItemDetector(Properties properties) {
            super(properties);
        }

        @Override
        protected int measure(BlockState state, ServerLevel level, BlockPos pos) {
            return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5.0)).stream().mapToInt(e -> e.getItem().getCount()).sum();
        }
    }

    public static class LightSensor extends Sensor {
        public LightSensor(Properties properties) {
            super(properties);
        }

        @Override
        protected int measure(BlockState state, ServerLevel level, BlockPos pos) {
            return level.getMaxLocalRawBrightness(pos.above());
        }
    }

    public static class EntityCounter extends Sensor {
        public EntityCounter(Properties properties) {
            super(properties);
        }

        @Override
        protected int measure(BlockState state, ServerLevel level, BlockPos pos) {
            return level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(8.0), e -> e.isAlive() && !e.isSpectator()).size();
        }
    }

    /** Outputs 15 while the block in front is not air. */
    public static class BlockDetector extends LaserSensor {
        public BlockDetector(Properties properties) {
            super(properties);
        }

        @Override
        protected int measure(BlockState state, ServerLevel level, BlockPos pos) {
            return level.getBlockState(pos.relative(state.getValue(FACING))).isAir() ? 0 : 15;
        }
    }

    /** Invisible beam up to 32 blocks in front; outputs 15 while any entity is in the beam. */
    public static class LaserSensor extends Sensor {
        public static final DirectionProperty FACING = BlockStateProperties.FACING;

        public LaserSensor(Properties properties) {
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
        protected int interval() {
            return 2;
        }

        @Override
        protected int measure(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = state.getValue(FACING);
            Vec3 start = MachineBlock.frontPoint(pos, facing, 0.51);
            Vec3 end = start.add(facing.getStepX() * 32.0, facing.getStepY() * 32.0, facing.getStepZ() * 32.0);
            BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null));
            Vec3 stop = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
            AABB beam = new AABB(start, stop).inflate(0.05);
            boolean blocked = !level.getEntities((Entity) null, beam,
                    e -> !e.isSpectator() && (e instanceof LivingEntity || e instanceof ItemEntity) && e.getBoundingBox().clip(start, stop).isPresent()).isEmpty();
            return blocked ? 15 : 0;
        }
    }
}
