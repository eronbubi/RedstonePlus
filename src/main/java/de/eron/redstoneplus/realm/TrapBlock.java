package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A redstone trap of the realm: the response half of a trigger/response pair.
 * <p>
 * A rising redstone edge arms the trap. It then fires once, sets FIRING for {@link #cooldown()} ticks and arms
 * every touching trap of the same kind {@link #chainDelay()} ticks later, so rows and walls of traps go off
 * as a ripple from the block the trigger touches. DISARMED traps (made safe with the matching counter item)
 * never fire, and a Signal Jammer held nearby blocks them.
 */
public abstract class TrapBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty FIRING = BooleanProperty.create("firing");
    public static final BooleanProperty DISARMED = BooleanProperty.create("disarmed");

    protected TrapBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false)
                .setValue(FIRING, false).setValue(DISARMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, FIRING, DISARMED);
    }

    /** The trap's front points at the player who places it. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Ticks the trap stays fired before it resets and can fire again. */
    protected int cooldown() {
        return 20;
    }

    /** Ticks before a touching trap of the same kind fires. */
    protected int chainDelay() {
        return 2;
    }

    /** Last chance to refuse firing (the Lockdown Gate checks for a Decoy Beacon here). */
    protected boolean canFire(ServerLevel level, BlockPos pos) {
        return true;
    }

    protected abstract void fire(BlockState state, ServerLevel level, BlockPos pos);

    protected void reset(BlockState state, ServerLevel level, BlockPos pos) {
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            BlockState now = state.setValue(POWERED, powered);
            level.setBlock(pos, now, Block.UPDATE_CLIENTS);
            if (powered) {
                this.arm(now, level, pos, 1);
            }
        }
    }

    /** Schedules the trap to fire unless it is disarmed, fired or already waiting. */
    public void arm(BlockState state, Level level, BlockPos pos, int delay) {
        if (state.getValue(DISARMED) || state.getValue(FIRING) || level.getBlockTicks().hasScheduledTick(pos, this)) {
            return;
        }
        level.scheduleTick(pos, this, delay);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(FIRING)) {
            BlockState rested = state.setValue(FIRING, false);
            level.setBlock(pos, rested, Block.UPDATE_ALL);
            this.reset(rested, level, pos);
            return;
        }
        if (state.getValue(DISARMED)) {
            return;
        }
        if (RealmItems.SignalJammer.isJammed(level, Vec3.atCenterOf(pos))) {
            level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.6F, 1.4F);
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.01);
            return;
        }
        if (!this.canFire(level, pos)) {
            return;
        }
        BlockState fired = state.setValue(FIRING, true);
        level.setBlock(pos, fired, Block.UPDATE_ALL);
        this.fire(fired, level, pos);
        level.scheduleTick(pos, this, this.cooldown());
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            BlockState other = level.getBlockState(next);
            if (other.is(this)) {
                this.arm(other, level, next, this.chainDelay());
            }
        }
    }

    /** Makes a trap safe for good. Returns false if it already was. */
    public static boolean disarm(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof TrapBlock) || state.getValue(DISARMED)) {
            return false;
        }
        level.setBlock(pos, state.setValue(DISARMED, true).setValue(FIRING, false), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 1.0F, 0.8F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.01);
        }
        return true;
    }

    /** Arms a disarmed trap again (Repeater Wrench). */
    public static void rearm(Level level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(DISARMED, false), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    protected static Vec3 front(BlockPos pos, Direction facing, double distance) {
        return Vec3.atCenterOf(pos).add(facing.getStepX() * distance, 0, facing.getStepZ() * distance);
    }

    /** Throws an entity along {@code dir}, replacing its motion so several traps in a row do not stack up. */
    protected static void fling(Entity entity, Direction dir, double speed, double up) {
        entity.setDeltaMovement(dir.getStepX() * speed, up, dir.getStepZ() * speed);
        entity.hurtMarked = true;
    }

    // =====================================================================================================
    // Piston Karst: pressure plate -> crushing passage. Counter: Piston Brace.

    /** Rams the two blocks in front of it. Jam it for good with a Piston Brace. */
    public static class Crusher extends TrapBlock {
        public Crusher(Properties properties) {
            super(properties);
        }

        @Override
        protected void fire(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = state.getValue(FACING);
            BlockPos first = pos.relative(facing);
            AABB box = new AABB(first).minmax(new AABB(first.relative(facing)));
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> !e.isSpectator())) {
                victim.hurt(level.damageSources().cramming(), 9.0F);
                fling(victim, facing, 0.9, 0.25);
            }
            level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Realm.KARST_LIMESTONE.get().defaultBlockState()),
                    first.getX() + 0.5, first.getY() + 0.5, first.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
        }

        @Override
        protected void reset(BlockState state, ServerLevel level, BlockPos pos) {
            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.8F, 0.6F);
        }
    }

    // =====================================================================================================
    // Switchyard Flats: detector rail -> hazard diversion. Counter: Pulse Injector.

    /** Kicks everything on the rails beside it off the track, towards where it points. */
    public static class HazardSwitch extends TrapBlock {
        public HazardSwitch(Properties properties) {
            super(properties);
        }

        @Override
        protected int cooldown() {
            return 30;
        }

        @Override
        protected void fire(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = state.getValue(FACING);
            AABB box = new AABB(pos).inflate(1.5, 1.0, 1.5).move(facing.getStepX(), 0.5, facing.getStepZ());
            for (Entity entity : level.getEntities((Entity) null, box, e -> !e.isSpectator() && !(e instanceof Constructs.Construct))) {
                if (entity.isPassenger()) {
                    entity.stopRiding();
                }
                fling(entity, facing, 1.3, 0.55);
            }
            level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 1.6F);
            level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.4, 0.3, 0.4, 0.2);
        }
    }

    // =====================================================================================================
    // Resonance Hollows: footstep vibration (sculk sensor) -> gate lockdown. Counter: Decoy Beacon.

    /** A portcullis: open it can be walked through, fired it is a solid wall of bars for ten seconds. */
    public static class LockdownGate extends TrapBlock {
        private static final VoxelShape RAISED = Block.box(0, 13, 0, 16, 16, 16);

        public LockdownGate(Properties properties) {
            super(properties);
        }

        @Override
        protected int cooldown() {
            return 200;
        }

        @Override
        protected int chainDelay() {
            return 1;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(FIRING) ? Shapes.block() : RAISED;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(FIRING) ? Shapes.block() : Shapes.empty();
        }

        @Override
        protected boolean canFire(ServerLevel level, BlockPos pos) {
            if (RealmBlocks.DecoyBeaconEntity.nearest(level, Vec3.atCenterOf(pos), 16.0) != null) {
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.5F);
                return false;
            }
            return true;
        }

        @Override
        protected void fire(BlockState state, ServerLevel level, BlockPos pos) {
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.5F);
            // the lockdown calls every Bell Stalker nearby to the one who set it off
            Player culprit = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 12.0, e -> !e.isSpectator() && !(e instanceof Player pl && pl.isCreative()));
            if (culprit != null) {
                for (Constructs.BellStalker stalker : level.getEntitiesOfClass(Constructs.BellStalker.class, new AABB(pos).inflate(32.0))) {
                    stalker.alert(culprit);
                }
            }
        }

        @Override
        protected void reset(BlockState state, ServerLevel level, BlockPos pos) {
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
    }

    // =====================================================================================================
    // Sluice Gardens: bridge tripwire -> floodgate surge. Counter: Repeater Wrench.

    /** Releases a surge that sweeps everything within 6 blocks in the direction it points. */
    public static class Floodgate extends TrapBlock {
        public Floodgate(Properties properties) {
            super(properties);
        }

        @Override
        protected int cooldown() {
            return 100;
        }

        @Override
        protected void fire(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = state.getValue(FACING);
            AABB box = new AABB(pos).inflate(6.0, 2.0, 6.0).move(0, 1.0, 0);
            for (Entity entity : level.getEntities((Entity) null, box, e -> !e.isSpectator() && !(e instanceof Constructs.Construct))) {
                fling(entity, facing, 1.1, 0.35);
                entity.clearFire();
                if (entity instanceof LivingEntity living) {
                    living.hurt(level.damageSources().drown(), 2.0F);
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                }
            }
            Vec3 out = front(pos, facing, 1.0);
            level.sendParticles(ParticleTypes.SPLASH, out.x, out.y + 0.5, out.z, 60, 1.5, 0.6, 1.5, 0.3);
            level.sendParticles(ParticleTypes.FALLING_WATER, out.x, out.y + 1.0, out.z, 30, 1.2, 0.8, 1.2, 0.0);
            level.playSound(null, pos, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.BLOCKS, 2.0F, 0.7F);
        }
    }

    // =====================================================================================================
    // Kiln Barrens: pressure plate -> fire-charge volley. Counter: Signal Jammer.

    /** Spits a fire charge out of its front. */
    public static class KilnTurret extends TrapBlock {
        public KilnTurret(Properties properties) {
            super(properties);
        }

        @Override
        protected int cooldown() {
            return 30;
        }

        @Override
        protected void fire(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = state.getValue(FACING);
            Vec3 start = front(pos, facing, 0.8);
            RandomSource random = level.getRandom();
            Vec3 motion = new Vec3(facing.getStepX() + random.triangle(0, 0.08), random.triangle(0, 0.05), facing.getStepZ() + random.triangle(0, 0.08));
            SmallFireball fireball = new SmallFireball(level, start.x, start.y, start.z, motion.normalize());
            level.addFreshEntity(fireball);
            level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.0F, 0.8F + random.nextFloat() * 0.3F);
            level.sendParticles(ParticleTypes.FLAME, start.x, start.y, start.z, 8, 0.1, 0.1, 0.1, 0.02);
        }
    }

    // =====================================================================================================
    // Tripwire Briar: tripwire -> arrow volley. Counter: Insulated Cutters.

    /** Fires a fan of three arrows. */
    public static class VolleyLauncher extends TrapBlock {
        public VolleyLauncher(Properties properties) {
            super(properties);
        }

        @Override
        protected void fire(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = state.getValue(FACING);
            Vec3 start = front(pos, facing, 0.7);
            Direction side = facing.getClockWise();
            for (int i = -1; i <= 1; i++) {
                Arrow arrow = new Arrow(level, start.x, start.y, start.z, new ItemStack(Items.ARROW), null);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.shoot(facing.getStepX() + side.getStepX() * 0.15 * i, 0.05, facing.getStepZ() + side.getStepZ() * 0.15 * i, 1.7F, 3.0F);
                level.addFreshEntity(arrow);
            }
            level.playSound(null, pos, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
    }
}
