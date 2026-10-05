package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * The work realm creatures do on the machines and ruins around them when nothing is threatening them. Each creature
 * seeks one kind of block, walks there, works it for a while with its ability animation, and leaves something
 * changed behind: lamps relit, bells rung, walls repaired, campfires stoked, traps cycled.
 */
final class RealmJobs {
    private RealmJobs() {
    }

    /** What a creature does to the block once it has worked on it long enough. */
    interface Work {
        void done(ServerLevel level, Mob mob, BlockPos pos, BlockState state);
    }

    /** Called every 20 ticks while working: sound and particles of the work. */
    interface Busy {
        void tick(ServerLevel level, Mob mob, BlockPos pos);
    }

    static final class TendGoal extends Goal {
        private final PathfinderMob mob;
        private final Predicate<BlockState> wants;
        private final Work work;
        private final Busy busy;
        private final int radius;
        private final int workTicks;
        private final int minDistance;
        private BlockPos target;
        private int timer;
        private int walking;
        private int nextSearch;
        private boolean standOn;
        private java.util.function.BiPredicate<net.minecraft.world.level.Level, BlockPos> where = (level, pos) -> true;

        TendGoal(PathfinderMob mob, int radius, int workTicks, Predicate<BlockState> wants, Busy busy, Work work) {
            this(mob, radius, workTicks, 0, wants, busy, work);
        }

        /** {@code minDistance}: only blocks at least this far away (for creatures that patrol along something). */
        TendGoal(PathfinderMob mob, int radius, int workTicks, int minDistance, Predicate<BlockState> wants, Busy busy, Work work) {
            this.mob = mob;
            this.radius = radius;
            this.workTicks = workTicks;
            this.minDistance = minDistance;
            this.wants = wants;
            this.busy = busy;
            this.work = work;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        /** Only blocks whose surroundings also pass this check. */
        TendGoal where(java.util.function.BiPredicate<net.minecraft.world.level.Level, BlockPos> where) {
            this.where = where;
            return this;
        }

        /** Walk onto the block itself (rails) instead of up to it. */
        TendGoal standOn() {
            this.standOn = true;
            return this;
        }

        /** Only blocks open to the air above, the ones a creature can stand on or at. */
        TendGoal exposed() {
            return this.where((level, pos) -> level.getBlockState(pos.above()).isAir());
        }

        private boolean calm() {
            return this.mob.getTarget() == null && this.mob.getLastHurtByMob() == null && !this.mob.isPassenger();
        }

        @Override
        public boolean canUse() {
            if (!this.calm() || this.mob.tickCount < this.nextSearch) {
                return false;
            }
            this.nextSearch = this.mob.tickCount + 120 + this.mob.getRandom().nextInt(160);
            this.target = this.find();
            return this.target != null;
        }

        private BlockPos find() {
            BlockPos origin = this.mob.blockPosition();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dy = -4; dy <= 5; dy++) {
                for (int dx = -this.radius; dx <= this.radius; dx++) {
                    for (int dz = -this.radius; dz <= this.radius; dz++) {
                        p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        double d = dx * dx + dy * dy + dz * dz;
                        if (d >= bestDistance || d < this.minDistance * this.minDistance) {
                            continue;
                        }
                        if (this.wants.test(this.mob.level().getBlockState(p)) && this.where.test(this.mob.level(), p)) {
                            best = p.immutable();
                            bestDistance = d;
                        }
                    }
                }
            }
            return best;
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null && this.calm() && this.walking < 240
                    && this.wants.test(this.mob.level().getBlockState(this.target));
        }

        @Override
        public void start() {
            this.timer = 0;
            this.walking = 0;
            this.moveTo();
        }

        private void moveTo() {
            this.mob.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY() + (this.standOn ? 0 : 1), this.target.getZ() + 0.5, 1.0);
        }

        @Override
        public void stop() {
            this.target = null;
            this.mob.getNavigation().stop();
            Crews.stoppedWorking(this.mob);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.target == null) {
                return; // the job just finished; the goal stops on the next check
            }
            Vec3 center = Vec3.atCenterOf(this.target);
            this.mob.getLookControl().setLookAt(center.x, center.y, center.z);
            double reach = this.standOn ? 0.9 : 1.6 + this.mob.getBbWidth();
            if (this.mob.position().distanceToSqr(center) > (this.standOn ? 0.36 : reach * reach + 1.0)) {
                this.walking++;
                if (this.standOn && this.mob.position().distanceToSqr(center) < 6.0) {
                    // paths end a block short: step the last bit straight onto the block
                    this.mob.getMoveControl().setWantedPosition(center.x, this.target.getY(), center.z, 1.0);
                    return;
                }
                if (this.mob.getNavigation().isDone() && this.walking % 20 == 0) {
                    this.moveTo();
                }
                return;
            }
            this.mob.getNavigation().stop();
            if (!(this.mob.level() instanceof ServerLevel level)) {
                return;
            }
            if (this.mob.tickCount % 20 == 0) {
                level.broadcastEntityEvent(this.mob, RealmAnimated.ABILITY_EVENT);
                this.busy.tick(level, this.mob, this.target);
            }
            // a crew working together gets it done sooner (see Crews)
            this.timer += Crews.working(this.mob, this.target);
            if (this.timer >= this.workTicks) {
                this.work.done(level, this.mob, this.target, level.getBlockState(this.target));
                this.target = null;
                Crews.stoppedWorking(this.mob);
            }
        }
    }

    // ========================================================================================== the jobs

    static boolean deadLamp(BlockState s) {
        return s.is(ModRegistry.INSTANT_LAMP.get()) && !s.getValue(SimpleBlocks.Lamp.LIT) || s.is(Blocks.REDSTONE_LAMP) && !s.getValue(BlockStateProperties.LIT);
    }

    static boolean litLamp(BlockState s) {
        return s.is(ModRegistry.INSTANT_LAMP.get()) && s.getValue(SimpleBlocks.Lamp.LIT) || s.is(Blocks.REDSTONE_LAMP) && s.getValue(BlockStateProperties.LIT)
                || s.is(Realm.REDSTONE_CLUSTER.get()) || s.is(Blocks.LANTERN);
    }

    /** Relights a dead lamp: an arc of sparks into it and the lamp comes on. */
    static void relight(ServerLevel level, Mob mob, BlockPos pos, BlockState state) {
        if (!deadLamp(state)) {
            return;
        }
        level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 2);
        RealmFx.line(level, mob.position().add(0, mob.getBbHeight() * 0.6, 0), Vec3.atCenterOf(pos), RealmFx.SPARK.get(), 0.25);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.15);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.8F);
    }

    static void sparks(ServerLevel level, Mob mob, BlockPos pos) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.1);
    }

    static void dust(ServerLevel level, Mob mob, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0.1);
        level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.8F, 0.8F);
    }

    /** A rubble block of a ruin: rough realmstone lying against old bricks. */
    static boolean rubble(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Realm.REALMSTONE.get()) || !level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).is(Realm.REALMSTONE_BRICKS.get())) {
                return true;
            }
        }
        return false;
    }

    /** Presses rubble back into bricks, and the bricks around it too. */
    static void repair(ServerLevel level, Mob mob, BlockPos pos, BlockState state) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getBlockState(p).is(Realm.REALMSTONE.get()) && level.getRandom().nextInt(3) > 0) {
                level.setBlock(p, Realm.REALMSTONE_BRICKS.get().defaultBlockState(), 3);
            }
        }
        level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 8, 0.5, 0.2, 0.5, 0.02);
    }

    static void ring(ServerLevel level, Mob mob, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BellBlock bell) {
            bell.attemptToRing(mob, level, pos, mob.getDirection().getOpposite());
        }
    }

    static void stoke(ServerLevel level, Mob mob, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CampfireBlock && !state.getValue(CampfireBlock.LIT)) {
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, true), 3);
        }
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.8F, 1.0F);
        RealmMechanics.smeltNearby(level, Vec3.atCenterOf(pos), 3.0, 1);
    }

    static boolean fire(BlockState s) {
        return s.getBlock() instanceof CampfireBlock || s.is(Blocks.MAGMA_BLOCK);
    }

    static void cycleTraps(ServerLevel level, Mob mob, BlockPos pos, BlockState state) {
        RealmMechanics.pulseTraps(level, pos, 2);
    }

    /** Draws power out of a vein: the creature heals and glows with it. */
    static void drink(ServerLevel level, Mob mob, BlockPos pos, BlockState state) {
        mob.heal(4.0F);
        RealmFx.line(level, Vec3.atCenterOf(pos), mob.position().add(0, mob.getBbHeight() * 0.5, 0), RealmFx.SPARK.get(), 0.3);
    }

    static void web(ServerLevel level, Mob mob, BlockPos pos, BlockState state) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = pos.relative(d);
            if (level.getBlockState(p).isAir() && level.getRandom().nextInt(3) == 0) {
                level.setBlock(p, Blocks.COBWEB.defaultBlockState(), 3);
                break;
            }
        }
    }

    static boolean rail(BlockState s) {
        return s.is(BlockTags.RAILS);
    }

    static void screech(ServerLevel level, Mob mob, BlockPos pos, BlockState state) {
        level.playSound(null, pos, SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, 0.6F, 1.6F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 12, 0.4, 0.1, 0.4, 0.2);
    }

    /** Relay Striders climb onto lightning rods and charge the air above them. */
    static void charge(ServerLevel level, Mob mob, BlockPos pos, BlockState state) {
        Vec3 top = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        RealmFx.line(level, mob.position().add(0, mob.getBbHeight() * 0.8, 0), top, RealmFx.SPARK.get(), 0.2);
        RealmFx.line(level, top, top.add(0, 6, 0), RealmFx.SPARK.get(), 0.3);
        level.playSound(null, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.NEUTRAL, 0.4F, 1.6F);
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(8), e -> e instanceof RealmFauna.LampMoth)) {
            near.heal(2.0F);
        }
    }

    /** Living Capacitors cannot walk up to things; they arc power into dead lamps within reach. */
    static void arcIntoLamps(ServerLevel level, Mob mob, int radius) {
        BlockPos origin = mob.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 4, radius))) {
            BlockState s = level.getBlockState(p);
            if (deadLamp(s)) {
                relight(level, mob, p.immutable(), s);
                level.broadcastEntityEvent(mob, RealmAnimated.ABILITY_EVENT);
                return;
            }
        }
    }
}
