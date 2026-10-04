package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The six mechanical creatures of the realm. They share {@link Construct}: they ignore light when spawning,
 * never fight each other, are immune to poison and can be short-circuited (stunned) with a Pulse Injector.
 * Each has its own ability that matches its biome.
 */
public final class Constructs {
    private Constructs() {
    }

    public abstract static class Construct extends Monster implements RealmAnimated {
        private int stunTicks;
        private int abilityStart = -10000;

        protected Construct(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.xpReward = 15;
        }

        protected static AttributeSupplier.Builder base(double health, double speed, double damage, double armor) {
            return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.MOVEMENT_SPEED, speed)
                    .add(Attributes.ATTACK_DAMAGE, damage).add(Attributes.ARMOR, armor).add(Attributes.FOLLOW_RANGE, 32.0);
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
            this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
            this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
            this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Construct.class));
            this.addTargetGoals();
            this.addJobs();
        }

        /** The work this construct does on the machines around it when nothing threatens it (see RealmJobs). */
        protected void addJobs() {
        }

        @Override
        public float getWalkTargetValue(net.minecraft.core.BlockPos pos, net.minecraft.world.level.LevelReader level) {
            return 0.0F; // a machine: daylight or dark is all the same to it, so it spawns and roams in both
        }

        protected void addTargetGoals() {
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }

        /** Short circuit: frozen in place, no abilities. */
        public void stun(int ticks) {
            this.stunTicks = Math.max(this.stunTicks, ticks);
            this.getNavigation().stop();
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
            this.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 4, false, false));
            this.playSound(SoundEvents.REDSTONE_TORCH_BURNOUT, 1.2F, 0.6F);
        }

        public boolean isStunned() {
            return this.stunTicks > 0;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide() && this.isAlive()) {
                this.clientFx();
            }
            if (this.level() instanceof ServerLevel server) {
                if (this.stunTicks > 0) {
                    this.stunTicks--;
                    if (this.tickCount % 4 == 0) {
                        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + this.getBbHeight() * 0.7, this.getZ(),
                                3, this.getBbWidth() * 0.4, 0.3, this.getBbWidth() * 0.4, 0.1);
                    }
                } else {
                    this.ability(server, this.getTarget());
                }
            }
        }

        /** Server side, every tick while not stunned. */
        protected void ability(ServerLevel level, @Nullable LivingEntity target) {
        }

        /** Client side, every tick: ambient particles on the body. */
        protected void clientFx() {
        }

        /** This creature's own sounds. */
        protected abstract RealmSounds.Set sounds();

        /** Starts the "ability" animation on every client and plays the ability sound. */
        protected void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(this.sounds().ability().get(), 2.0F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        @Override
        public void handleEntityEvent(byte id) {
            if (id == ABILITY_EVENT) {
                this.abilityStart = this.tickCount;
            } else {
                super.handleEntityEvent(id);
            }
        }

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        @Override
        protected SoundEvent getAmbientSound() {
            return this.sounds().ambient().get();
        }

        @Override
        protected void playStepSound(BlockPos pos, BlockState state) {
            this.playSound(this.sounds().step().get(), 0.7F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        @Override
        public boolean isAlliedTo(Entity other) {
            return other instanceof Construct || super.isAlliedTo(other);
        }

        @Override
        public boolean canBeAffected(MobEffectInstance effect) {
            return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
        }

        @Override
        public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            tag.putInt("Stunned", this.stunTicks);
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            this.stunTicks = tag.getInt("Stunned");
        }

        protected boolean canSee(LivingEntity target, double range) {
            return this.distanceToSqr(target) <= range * range && this.hasLineOfSight(target);
        }

        /** Everything alive around this construct except other constructs. */
        protected Iterable<LivingEntity> around(double radius) {
            return this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius),
                    e -> e != this && e.isAlive() && !(e instanceof Construct) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource source) {
            return this.sounds().hurt().get();
        }

        @Override
        protected SoundEvent getDeathSound() {
            return this.sounds().death().get();
        }
    }

    // =====================================================================================================
    /** Piston Karst. Walking stone engine: its punches launch, and it slams the ground when you get close. */
    public static class KarstColossus extends Construct {
        @Override
        protected void addJobs() {
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 14, 60, s -> s.is(Realm.CRUSHER.get()), RealmJobs::dust, RealmJobs::cycleTraps));
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.KARST_COLOSSUS;
        }

        @Override
        protected void clientFx() {
            if (RealmFx.chance(this, 12)) {
                RealmFx.emit(this, RealmFx.STEAM.get(), this.random.nextBoolean() ? 0.35 : -0.35, 2.6, -0.55, 0, 0.06, 0);
            }
            if (RealmFx.chance(this, 30)) {
                RealmFx.emit(this, RealmFx.SPARK.get(), 0, 1.7, 0.6, (this.random.nextDouble() - 0.5) * 0.1, 0.1, 0.05);
            }
        }

        private int slamCooldown = 60;

        public KarstColossus(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.xpReward = 30;
        }

        public static AttributeSupplier.Builder attributes() {
            return base(100.0, 0.22, 14.0, 12.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.STEP_HEIGHT, 1.0);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit) {
                Vec3 away = target.position().subtract(this.position()).multiply(1, 0, 1).normalize();
                target.setDeltaMovement(away.x * 1.2, 0.7, away.z * 1.2);
                target.hurtMarked = true;
                this.playSound(SoundEvents.PISTON_EXTEND, 1.5F, 0.5F);
            }
            return hit;
        }

        @Override
        protected void ability(ServerLevel level, @Nullable LivingEntity target) {
            if (this.slamCooldown > 0) {
                this.slamCooldown--;
                return;
            }
            if (target != null && this.onGround() && this.distanceToSqr(target) < 5.0 * 5.0) {
                this.slamCooldown = 120;
                for (LivingEntity victim : this.around(5.0)) {
                    victim.hurt(this.damageSources().mobAttack(this), 8.0F);
                    Vec3 away = victim.position().subtract(this.position()).multiply(1, 0, 1).normalize();
                    victim.setDeltaMovement(away.x * 1.3, 0.55, away.z * 1.3);
                    victim.hurtMarked = true;
                }
                BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Realm.KARST_LIMESTONE.get().defaultBlockState());
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    level.sendParticles(dust, this.getX() + Math.cos(a) * 3, this.getY() + 0.1, this.getZ() + Math.sin(a) * 3, 3, 0.2, 0.1, 0.2, 0.1);
                }
                this.playAbility();
                this.playSound(SoundEvents.PISTON_EXTEND, 2.0F, 0.4F);
                RealmFx.ring(level, this.position().add(0, 0.15, 0), 2.0, RealmFx.SPARK.get(), 24);
                RealmFx.ring(level, this.position().add(0, 0.15, 0), 4.0, RealmFx.STEAM.get(), 20);
                RealmFx.burst(this, RealmFx.STEAM.get(), 0.35, 2.7, -0.55, 12, 0.15, 0.05);
                RealmFx.burst(this, RealmFx.STEAM.get(), -0.35, 2.7, -0.55, 12, 0.15, 0.05);
                // the shock runs through the ground into every realm trap around
                RealmMechanics.pulseTraps(level, this.blockPosition(), 8);
            }
        }

    }

    // =====================================================================================================
    /** Switchyard Flats. Rusty rail centipede: twice as fast on rails, and it charges in straight lines. */
    public static class SwitchbackCrawler extends Construct {
        @Override
        protected void addJobs() {
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 20, 30, 8, RealmJobs::rail, RealmJobs::sparks, RealmJobs::screech).standOn());
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.SWITCHBACK_CRAWLER;
        }

        @Override
        protected void clientFx() {
            boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 0.01;
            BlockState at = this.level().getBlockState(this.blockPosition());
            if (moving && at.getBlock() instanceof BaseRailBlock && RealmFx.chance(this, 2)) {
                RealmFx.emit(this, RealmFx.SPARK.get(), this.random.nextBoolean() ? 0.5 : -0.5, 0.1, -0.2, 0, 0.15, 0);
            }
            if (RealmFx.chance(this, 20)) {
                RealmFx.emit(this, RealmFx.EMBER.get(), 0, 0.95, 0.9, 0, 0.02, 0);
            }
        }

        private static final ResourceLocation RAIL_BOOST = Realm.id("rail_boost");
        private int dashCooldown = 40;
        private int dashTicks;
        private Vec3 dashDir = Vec3.ZERO;

        public SwitchbackCrawler(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return base(45.0, 0.3, 7.0, 4.0).add(Attributes.STEP_HEIGHT, 1.0);
        }

        @Override
        protected void ability(ServerLevel level, @Nullable LivingEntity target) {
            // it couples to minecarts it touches and drags them along
            Vec3 motion = this.getDeltaMovement();
            if (motion.horizontalDistanceSqr() > 0.004) {
                for (net.minecraft.world.entity.vehicle.AbstractMinecart cart : level.getEntitiesOfClass(
                        net.minecraft.world.entity.vehicle.AbstractMinecart.class, this.getBoundingBox().inflate(0.6))) {
                    cart.setDeltaMovement(motion.x * 1.6, cart.getDeltaMovement().y, motion.z * 1.6);
                }
            }
            AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
            boolean onRail = level.getBlockState(this.blockPosition()).getBlock() instanceof BaseRailBlock
                    || level.getBlockState(this.blockPosition().below()).getBlock() instanceof BaseRailBlock;
            if (speed != null) {
                if (onRail && !speed.hasModifier(RAIL_BOOST)) {
                    speed.addTransientModifier(new AttributeModifier(RAIL_BOOST, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                } else if (!onRail && speed.hasModifier(RAIL_BOOST)) {
                    speed.removeModifier(RAIL_BOOST);
                }
            }
            if (this.dashTicks > 0) {
                this.dashTicks--;
                this.getNavigation().stop();
                this.setDeltaMovement(this.dashDir.x, this.getDeltaMovement().y, this.dashDir.z);
                this.setYRot((float) (Mth.atan2(this.dashDir.z, this.dashDir.x) * Mth.RAD_TO_DEG) - 90.0F);
                this.yBodyRot = this.getYRot();
                for (LivingEntity victim : this.around(0.4)) {
                    victim.hurt(this.damageSources().mobAttack(this), 8.0F);
                    victim.setDeltaMovement(this.dashDir.x * 1.4, 0.5, this.dashDir.z * 1.4);
                    victim.hurtMarked = true;
                    this.dashTicks = 0;
                }
                level.sendParticles(RealmFx.SPARK.get(), this.getX(), this.getY() + 0.2, this.getZ(), 3, 0.3, 0.1, 0.3, 0.15);
                level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.2, this.getZ(), 2, 0.3, 0.1, 0.3, 0.05);
                return;
            }
            if (this.dashCooldown > 0) {
                this.dashCooldown--;
                return;
            }
            if (target != null && this.onGround() && this.distanceToSqr(target) > 5.0 * 5.0 && this.canSee(target, 16.0)) {
                this.dashDir = target.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(0.85);
                this.dashTicks = 16;
                this.dashCooldown = 100;
                this.playAbility();
            }
        }


    }

    // =====================================================================================================
    /**
     * Resonance Hollows. Blind stilt-walker with a bell for a head: it hunts by the sound of footsteps
     * (sneak and it cannot find you), tolls to blind and slow everything near it, and follows Decoy Beacons.
     */
    public static class BellStalker extends Construct {
        @Override
        protected void addJobs() {
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 18, 40, s -> s.is(net.minecraft.world.level.block.Blocks.BELL), RealmJobs::sparks, RealmJobs::ring));
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.BELL_STALKER;
        }

        @Override
        protected void clientFx() {
            if (RealmFx.chance(this, 6)) {
                double side = (this.random.nextDouble() - 0.5) * 0.8;
                RealmFx.emit(this, ParticleTypes.GLOW, side, 1.6 + this.random.nextDouble() * 0.3, 0.75, 0, -0.02, 0);
            }
            if (RealmFx.chance(this, 40)) {
                RealmFx.emit(this, RealmFx.RESONANCE.get(), 0, 2.2, 0.75, 0, 0, 0);
            }
        }

        private int tollCooldown = 60;

        public BellStalker(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.xpReward = 25;
        }

        public static AttributeSupplier.Builder attributes() {
            return base(70.0, 0.26, 9.0, 6.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.STEP_HEIGHT, 1.5);
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(1, new SeekDecoyGoal(this));
        }

        @Override
        protected void addTargetGoals() {
            this.targetSelector.addGoal(2, new HearFootstepsGoal(this));
        }

        /** Called by a Lockdown Gate that went off nearby. */
        public void alert(Player culprit) {
            if (!this.isStunned() && RealmBlocks.DecoyBeaconEntity.nearest(this.level(), this.position(), 24.0) == null) {
                this.setTarget(culprit);
            }
        }

        @Override
        protected void ability(ServerLevel level, @Nullable LivingEntity target) {
            if (this.tollCooldown > 0) {
                this.tollCooldown--;
                return;
            }
            if (target != null && this.distanceToSqr(target) < 10.0 * 10.0) {
                this.tollCooldown = 140;
                this.playAbility();
                this.playSound(SoundEvents.BELL_RESONATE, 2.0F, 0.7F);
                for (LivingEntity victim : this.around(10.0)) {
                    if (victim.isSteppingCarefully()) {
                        continue;
                    }
                    victim.hurt(this.damageSources().mobAttack(this), 4.0F);
                    victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0), this);
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                }
                level.sendParticles(ParticleTypes.SONIC_BOOM, this.getX(), this.getY() + 2.8, this.getZ(), 1, 0, 0, 0, 0);
                for (int r = 1; r <= 3; r++) {
                    RealmFx.ring(level, RealmFx.on(this, 0, 2.3, 0.75), r * 1.6, RealmFx.RESONANCE.get(), 10 + r * 6);
                }
                // the toll is a vibration: sculk sensors that hear it (and the lockdown gates behind them) react
                level.gameEvent(this, net.minecraft.world.level.gameevent.GameEvent.INSTRUMENT_PLAY, this.position().add(0, 2.5, 0));
                level.sendParticles(ParticleTypes.NOTE, this.getX(), this.getY() + 3.2, this.getZ(), 8, 1.5, 0.5, 1.5, 1.0);
            }
        }




    }

    /** Targets the nearest player that is walking without sneaking; lets go of sneaking players further than 6 blocks. */
    static class HearFootstepsGoal extends Goal {
        private final BellStalker stalker;
        private final Map<UUID, Vec3> lastSeen = new HashMap<>();

        HearFootstepsGoal(BellStalker stalker) {
            this.stalker = stalker;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.stalker.tickCount % 10 != 0) {
                return false;
            }
            LivingEntity current = this.stalker.getTarget();
            if (current instanceof Player p && p.isSteppingCarefully() && this.stalker.distanceToSqr(p) > 36.0) {
                this.stalker.setTarget(null);
            }
            if (this.stalker.getTarget() != null) {
                return false;
            }
            Player heard = null;
            double best = 20.0 * 20.0;
            for (Player player : this.stalker.level().players()) {
                if (player.isSpectator() || player.isCreative()) {
                    continue;
                }
                Vec3 before = this.lastSeen.put(player.getUUID(), player.position());
                double d = this.stalker.distanceToSqr(player);
                boolean moving = before != null && before.distanceToSqr(player.position()) > 0.04;
                if (moving && !player.isSteppingCarefully() && d < best) {
                    best = d;
                    heard = player;
                }
            }
            if (heard != null && RealmBlocks.DecoyBeaconEntity.nearest(this.stalker.level(), this.stalker.position(), 24.0) == null) {
                this.stalker.setTarget(heard);
                this.stalker.playSound(SoundEvents.BELL_RESONATE, 1.5F, 1.2F);
            }
            return false;
        }
    }

    /** Walks to a Decoy Beacon and loses interest in everything else while one is close. */
    static class SeekDecoyGoal extends Goal {
        private final BellStalker stalker;
        @Nullable
        private BlockPos decoy;

        SeekDecoyGoal(BellStalker stalker) {
            this.stalker = stalker;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.stalker.tickCount % 20 != 0) {
                return false;
            }
            this.decoy = RealmBlocks.DecoyBeaconEntity.nearest(this.stalker.level(), this.stalker.position(), 24.0);
            return this.decoy != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.decoy != null && this.stalker.level().getBlockState(this.decoy).is(Realm.DECOY_BEACON.get());
        }

        @Override
        public void tick() {
            this.stalker.setTarget(null);
            if (this.decoy == null) {
                return;
            }
            this.stalker.getLookControl().setLookAt(Vec3.atCenterOf(this.decoy));
            if (this.stalker.distanceToSqr(Vec3.atCenterOf(this.decoy)) > 9.0 && this.stalker.getNavigation().isDone()) {
                this.stalker.getNavigation().moveTo(this.decoy.getX() + 0.5, this.decoy.getY(), this.decoy.getZ() + 0.5, 1.0);
            }
        }
    }

    // =====================================================================================================
    /** Sluice Gardens. Chained copper gator: at home in water, and its bite drags you towards it. */
    public static class SluiceChainjaw extends Construct {
        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.SLUICE_CHAINJAW;
        }

        @Override
        protected void clientFx() {
            if (this.isInWater() && RealmFx.chance(this, 3)) {
                RealmFx.emit(this, ParticleTypes.BUBBLE, this.random.nextDouble() - 0.5, 0.5, (this.random.nextDouble() - 0.5) * 1.5, 0, 0.1, 0);
            } else if (!this.isInWater() && RealmFx.chance(this, 25)) {
                RealmFx.emit(this, ParticleTypes.FALLING_WATER, (this.random.nextDouble() - 0.5) * 0.8, 0.6, 0, 0, 0, 0);
            }
        }

        public SluiceChainjaw(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.setPathfindingMalus(PathType.WATER, 0.0F);
        }

        public static AttributeSupplier.Builder attributes() {
            return base(55.0, 0.24, 9.0, 6.0).add(Attributes.STEP_HEIGHT, 1.0);
        }

        @Override
        protected PathNavigation createNavigation(Level level) {
            return new AmphibiousPathNavigation(this, level);
        }

        @Override
        public boolean isPushedByFluid() {
            return false;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 16, 60, s -> s.is(Realm.FLOODGATE.get()), RealmJobs::sparks, RealmJobs::cycleTraps));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
            this.goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.RandomStrollGoal(this, 0.8));
            this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Construct.class));
            this.addTargetGoals();
        }

        @Override
        public void baseTick() {
            int air = this.getAirSupply();
            super.baseTick();
            this.setAirSupply(air); // a machine does not drown
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof LivingEntity living) {
                Vec3 pull = this.position().subtract(target.position()).multiply(1, 0, 1).normalize();
                target.setDeltaMovement(pull.x * 0.7, 0.15, pull.z * 0.7);
                target.hurtMarked = true;
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this);
                this.playAbility();
            }
            return hit;
        }

        @Override
        protected void ability(ServerLevel level, @Nullable LivingEntity target) {
            if (this.isInWater()) {
                // whirlpool: everything swimming within 8 blocks is dragged round and towards it
                if (this.tickCount % 5 == 0) {
                    for (LivingEntity swimmer : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(8.0),
                            e -> e != this && e.isInWater() && !(e instanceof Construct))) {
                        Vec3 pull = this.position().subtract(swimmer.position()).normalize();
                        Vec3 swirl = new Vec3(-pull.z, 0, pull.x).scale(0.08);
                        swimmer.setDeltaMovement(swimmer.getDeltaMovement().add(pull.scale(0.06)).add(swirl));
                        swimmer.hurtMarked = true;
                    }
                    RealmFx.ring(level, this.position().add(0, 0.6, 0), 2.5 + this.random.nextDouble() * 2, ParticleTypes.BUBBLE, 12);
                }
                if (target != null) {
                    Vec3 to = target.position().subtract(this.position()).normalize().scale(0.06);
                    this.setDeltaMovement(this.getDeltaMovement().add(to));
                }
                if (this.tickCount % 8 == 0) {
                    level.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY() + 0.5, this.getZ(), 3, 0.5, 0.2, 0.5, 0.05);
                }
            }
        }



    }

    // =====================================================================================================
    /** Kiln Barrens. Walking furnace: burning punches and a volley of fire charges, unless a Signal Jammer is near. */
    public static class KilnBrute extends Construct {
        @Override
        protected void addJobs() {
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 14, 60, RealmJobs::fire, RealmJobs::sparks, RealmJobs::stoke));
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.KILN_BRUTE;
        }

        @Override
        protected void clientFx() {
            if (RealmFx.chance(this, 4)) {
                RealmFx.emit(this, ParticleTypes.LARGE_SMOKE, 0.28, 2.95, -0.28, 0, 0.08, 0);
            }
            if (RealmFx.chance(this, 5)) {
                RealmFx.emit(this, RealmFx.EMBER.get(), (this.random.nextDouble() - 0.5) * 0.9, 1.4 + this.random.nextDouble() * 0.8, 0.55, 0, 0.03, 0.02);
            }
            if (RealmFx.chance(this, 15)) {
                RealmFx.emit(this, ParticleTypes.FLAME, 0.28, 2.95, -0.28, 0, 0.04, 0);
            }
        }

        private int volleyCooldown = 60;
        private int shotsLeft;

        public KilnBrute(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.xpReward = 30;
            this.setPathfindingMalus(PathType.LAVA, 8.0F);
            this.setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
            this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        }

        public static AttributeSupplier.Builder attributes() {
            return base(90.0, 0.23, 12.0, 8.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.STEP_HEIGHT, 1.0);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit) {
                target.igniteForSeconds(5);
            }
            return hit;
        }

        @Override
        protected void ability(ServerLevel level, @Nullable LivingEntity target) {
            // a walking furnace: ore and food dropped near it comes out smelted
            if (this.tickCount % 40 == 0) {
                RealmMechanics.smeltNearby(level, this.position().add(0, 0.5, 0), 3.0, 4);
            }
            if (this.shotsLeft > 0) {
                if (this.tickCount % 6 == 0 && target != null) {
                    this.shotsLeft--;
                    Vec3 from = new Vec3(this.getX(), this.getY(0.7), this.getZ());
                    Vec3 aim = target.getEyePosition().subtract(from).normalize();
                    RandomNudge nudge = new RandomNudge(this);
                    SmallFireball fireball = new SmallFireball(level, this, new Vec3(aim.x + nudge.x, aim.y + nudge.y, aim.z + nudge.z));
                    fireball.setPos(from.x + aim.x, from.y, from.z + aim.z);
                    level.addFreshEntity(fireball);
                    this.playSound(SoundEvents.BLAZE_SHOOT, 1.2F, 0.7F);
                }
                return;
            }
            if (this.volleyCooldown > 0) {
                this.volleyCooldown--;
                return;
            }
            if (target != null && this.distanceToSqr(target) > 4.0 * 4.0 && this.canSee(target, 24.0)) {
                this.volleyCooldown = 80;
                if (RealmItems.SignalJammer.isJammed(level, this.position())) {
                    this.playSound(SoundEvents.REDSTONE_TORCH_BURNOUT, 1.0F, 0.8F);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 1.8, this.getZ(), 12, 0.5, 0.4, 0.5, 0.1);
                    return;
                }
                this.shotsLeft = 3;
                this.playAbility();
            }
        }



        @Override
        public boolean isSensitiveToWater() {
            return true;
        }
    }

    private record RandomNudge(double x, double y, double z) {
        RandomNudge(LivingEntity e) {
            this(e.getRandom().triangle(0, 0.06), e.getRandom().triangle(0, 0.04), e.getRandom().triangle(0, 0.06));
        }
    }

    // =====================================================================================================
    /** Tripwire Briar. Spider with a wire spool: climbs walls and lashes wire that roots you. Cutters hurt it badly. */
    public static class SpoolWeaver extends Construct {
        @Override
        protected void addJobs() {
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 14, 60, s -> s.is(de.eron.redstoneplus.registry.ModRegistry.INSTANT_LAMP.get()), RealmJobs::sparks, RealmJobs::web));
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.SPOOL_WEAVER;
        }

        private int spoolRegrow;

        /** Shears or Insulated Cutters unwind wire from its spool, like shearing a sheep. */
        @Override
        protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
            var stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof net.minecraft.world.item.ShearsItem) {
                if (this.spoolRegrow > 0) {
                    return net.minecraft.world.InteractionResult.FAIL;
                }
                if (!this.level().isClientSide()) {
                    this.spoolRegrow = 2400;
                    this.spawnAtLocation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STRING, 3 + this.random.nextInt(4)));
                    if (this.random.nextBoolean()) {
                        this.spawnAtLocation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIPWIRE_HOOK));
                    }
                    this.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 0.8F);
                    stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                }
                return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide());
            }
            return super.mobInteract(player, hand);
        }

        @Override
        protected void clientFx() {
            if (RealmFx.chance(this, 18)) {
                RealmFx.emit(this, RealmFx.SPARK.get(), (this.random.nextDouble() - 0.5) * 0.7, 0.9, -0.45, 0, 0.1, 0);
            }
        }

        private int lashCooldown = 40;

        public SpoolWeaver(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return base(38.0, 0.3, 6.0, 3.0);
        }

        @Override
        protected PathNavigation createNavigation(Level level) {
            return new WallClimberNavigation(this, level);
        }

        @Override
        public boolean onClimbable() {
            return this.horizontalCollision || super.onClimbable();
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (source.getEntity() instanceof LivingEntity attacker && attacker.getMainHandItem().is(Realm.INSULATED_CUTTERS.get())) {
                amount *= 2.5F;
                this.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 0.6F);
            }
            return super.hurt(source, amount);
        }

        @Override
        protected void ability(ServerLevel level, @Nullable LivingEntity target) {
            if (this.spoolRegrow > 0) {
                this.spoolRegrow--;
            }
            if (this.lashCooldown > 0) {
                this.lashCooldown--;
                return;
            }
            if (target != null && this.distanceToSqr(target) > 3.0 * 3.0 && this.canSee(target, 12.0)) {
                this.lashCooldown = 70;
                Vec3 from = this.getEyePosition();
                Vec3 to = target.getEyePosition().subtract(0, 0.4, 0);
                DustParticleOptions wire = new DustParticleOptions(new Vector3f(0.85F, 0.85F, 0.8F), 0.7F);
                int steps = (int) (from.distanceTo(to) * 3);
                for (int i = 0; i <= steps; i++) {
                    Vec3 p = from.lerp(to, i / (double) Math.max(1, steps));
                    level.sendParticles(wire, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                Vec3 pull = from.subtract(to).multiply(1, 0, 1).normalize();
                target.setDeltaMovement(pull.x * 0.6, 0.1, pull.z * 0.6);
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3), this);
                this.playAbility();
            }
        }




    }

    /** Box that fits the entity's feet, used by traps and features that need it. */
    static AABB feet(Entity e) {
        return e.getBoundingBox().setMaxY(e.getY() + 0.2);
    }
}
