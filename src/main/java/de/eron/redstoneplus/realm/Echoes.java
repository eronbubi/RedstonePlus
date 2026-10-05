package de.eron.redstoneplus.realm;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The five Echoes of the Great Bell, and the Bell itself (see {@link RealmStory} for the story). They are bosses: a bar
 * at the top of the screen, a seat they never stray far from, and moves of their own that come faster once they are
 * badly hurt. They are outside the Concordance (fighting them breaks no rule). Each drops its core when it falls; the
 * five cores make the Heart of the Five.
 */
public final class Echoes {
    private Echoes() {
    }

    /** The tag of the creatures an Echo calls to its side: they fight for it, whatever the Concordance says. */
    public static final String CALLED = "redstoneplus.echo_called";

    // ============================================================================================ the shared body
    public abstract static class EchoBoss extends Monster implements RealmAnimated, SealedReach.Lawless {
        private final ServerBossEvent bar;
        private int abilityStart = -10000;
        @Nullable
        protected BlockPos seat;
        private int alone;
        protected int moveA = 60;
        protected int moveB = 100;
        private boolean summoned;

        protected EchoBoss(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.xpReward = 250;
            this.bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            this.bar.setDarkenScreen(true);
            this.setPersistenceRequired();
            if (this.flies()) {
                this.setNoGravity(true);
            }
        }

        /** Which Echo this is, or null for the Overtoll. */
        @Nullable
        protected abstract RealmStory.Echo echo();

        protected abstract RealmSounds.Set sounds();

        /** Floats and flies (all but the Sluicemother). */
        protected boolean flies() {
            return true;
        }

        /** How high above its target it likes to hover. */
        protected double hoverHeight() {
            return 3.0;
        }

        protected double flySpeed() {
            return 0.22;
        }

        /** At or below half health: faster, angrier. */
        protected boolean enraged() {
            return this.getHealth() <= this.getMaxHealth() * 0.5F;
        }

        protected static AttributeSupplier.Builder base(double health, double damage, double armor) {
            return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.ATTACK_DAMAGE, damage)
                    .add(Attributes.ARMOR, armor).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                    .add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.STEP_HEIGHT, 1.5);
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            if (!this.flies()) {
                this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
            }
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        }

        void setSeat(BlockPos seat) {
            this.seat = seat.immutable();
        }

        // ---- the bar
        @Override
        public void startSeenByPlayer(ServerPlayer player) {
            super.startSeenByPlayer(player);
            this.bar.addPlayer(player);
        }

        @Override
        public void stopSeenByPlayer(ServerPlayer player) {
            super.stopSeenByPlayer(player);
            this.bar.removePlayer(player);
        }

        @Override
        public void setCustomName(@Nullable Component name) {
            super.setCustomName(name);
        }

        // ---- the body of a boss
        @Override
        public boolean removeWhenFarAway(double distance) {
            return false;
        }

        @Override
        public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
            return false;
        }

        @Override
        public boolean isPushedByFluid() {
            return false;
        }

        @Override
        public boolean canChangeDimensions(Level from, Level to) {
            return false;
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (source.getEntity() instanceof EchoBoss || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
                return false;
            }
            return super.hurt(source, amount);
        }

        @Override
        public float getWalkTargetValue(BlockPos pos, net.minecraft.world.level.LevelReader level) {
            return 0.0F;
        }

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        protected void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(this.sounds().ability().get(), 4.0F, 0.9F + this.random.nextFloat() * 0.2F);
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
        protected SoundEvent getAmbientSound() {
            return this.sounds().ambient().get();
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource source) {
            return this.sounds().hurt().get();
        }

        @Override
        protected SoundEvent getDeathSound() {
            return this.sounds().death().get();
        }

        @Override
        protected float getSoundVolume() {
            return 3.0F;
        }

        @Override
        public int getAmbientSoundInterval() {
            return 140;
        }

        // ---- saving the seat
        @Override
        public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            if (this.seat != null) {
                tag.putLong("Seat", this.seat.asLong());
            }
            tag.putBoolean("Summoned", this.summoned);
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            if (tag.contains("Seat")) {
                this.seat = BlockPos.of(tag.getLong("Seat"));
            }
            this.summoned = tag.getBoolean("Summoned");
            if (this.hasCustomName()) {
                this.bar.setName(this.getDisplayName());
            }
        }

        // ---- every tick
        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide()) {
                this.clientGlow();
                return;
            }
            ServerLevel level = (ServerLevel) this.level();
            this.bar.setName(this.getDisplayName());
            this.bar.setProgress(this.getHealth() / this.getMaxHealth());
            if (this.flies()) {
                this.fly();
            }
            this.leash();
            // nobody near for half a minute: it settles back into its seat, to be woken again
            if (level.getNearestPlayer(this, 64.0) == null) {
                if (++this.alone > 600) {
                    this.discard();
                }
                return;
            }
            this.alone = 0;
            if (!this.summoned && this.enraged()) {
                this.summoned = true;
                this.summonHelpers(level);
            }
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                return;
            }
            if (--this.moveA <= 0) {
                this.moveA = this.moveA(level, target);
            }
            if (--this.moveB <= 0) {
                this.moveB = this.moveB(level, target);
            }
        }

        /** Its first move; returns the ticks until it uses it again. */
        protected abstract int moveA(ServerLevel level, LivingEntity target);

        /** Its signature move; returns the ticks until it uses it again. */
        protected abstract int moveB(ServerLevel level, LivingEntity target);

        /** At half health, once: it calls creatures of the realm to its side. */
        protected void summonHelpers(ServerLevel level) {
        }

        /** Floats towards a point above its target (or above its seat), and strikes what it reaches. */
        private void fly() {
            LivingEntity target = this.getTarget();
            Vec3 goal;
            if (target != null && target.isAlive()) {
                goal = target.position().add(0, this.hoverHeight(), 0);
                this.getLookControl().setLookAt(target, 30.0F, 30.0F);
                double reach = this.getBbWidth() * 0.9 + 2.0;
                if (this.distanceToSqr(target) < reach * reach && this.tickCount % 25 == 0) {
                    this.swing(InteractionHand.MAIN_HAND);
                    this.doHurtTarget(target);
                }
            } else if (this.seat != null) {
                goal = Vec3.atCenterOf(this.seat).add(0, 8, 0);
            } else {
                return;
            }
            Vec3 to = goal.subtract(this.position());
            double dist = to.length();
            Vec3 v = this.getDeltaMovement().scale(0.85);
            if (dist > 1.0) {
                v = v.add(to.normalize().scale(this.flySpeed() * (this.enraged() ? 1.3 : 1.0) * 0.25));
            }
            this.setDeltaMovement(v);
            if (target == null && dist > 0.5) {
                this.setYRot((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F);
                this.yBodyRot = this.getYRot();
            }
        }

        /** It does not leave its sanctum: pulled back if it goes further than 32 blocks from its seat. */
        private void leash() {
            if (this.seat == null) {
                return;
            }
            double d = this.distanceToSqr(Vec3.atCenterOf(this.seat));
            if (d > 32 * 32) {
                Vec3 back = Vec3.atCenterOf(this.seat).subtract(this.position()).normalize().scale(0.4);
                this.setDeltaMovement(this.getDeltaMovement().add(back));
                if (d > 56 * 56) {
                    this.teleportTo(this.seat.getX() + 0.5, this.seat.getY() + 6, this.seat.getZ() + 0.5);
                }
            }
        }

        protected void clientGlow() {
            if (this.random.nextInt(2) == 0) {
                int c = this.echo() == null ? 0xFFB040 : this.echo().color;
                DustParticleOptions dust = new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 1.6F);
                this.level().addParticle(dust, this.getRandomX(0.8), this.getRandomY(), this.getRandomZ(0.8), 0, 0.02, 0);
            }
        }

        // ---- helpers for the moves
        protected List<Player> playersNear(ServerLevel level, double radius) {
            return level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(radius), p -> !p.isSpectator() && !p.isCreative());
        }

        protected DamageSource blow() {
            return this.damageSources().mobAttack(this);
        }

        protected void summon(ServerLevel level, EntityType<? extends Mob> type, int count) {
            for (int i = 0; i < count; i++) {
                Mob mob = type.create(level);
                if (mob == null) {
                    continue;
                }
                double a = this.random.nextDouble() * Mth.TWO_PI;
                double x = this.getX() + Math.cos(a) * 6;
                double z = this.getZ() + Math.sin(a) * 6;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                mob.moveTo(x, y, z, this.random.nextFloat() * 360, 0);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                mob.addTag(CALLED);
                if (this.getTarget() != null) {
                    mob.setTarget(this.getTarget());
                }
                level.addFreshEntity(mob);
                level.sendParticles(ParticleTypes.FLASH, x, y + 1, z, 1, 0, 0, 0, 0);
            }
        }

        // ---- the fall
        @Override
        public void die(DamageSource source) {
            super.die(source);
            if (this.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1, this.getZ(), 2, 1, 1, 1, 0);
                level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1, this.getZ(), 120, 2, 2, 2, 0.2);
                RealmStory.Echo echo = this.echo();
                if (echo != null) {
                    this.spawnAtLocation(new ItemStack(core(echo)));
                    RealmStory.conquer(level, echo, this.seat != null ? this.seat : this.blockPosition());
                } else {
                    RealmStory.victory(level);
                }
            }
        }
    }

    static Item core(RealmStory.Echo echo) {
        return switch (echo) {
            case FORCE -> Realm.CORE_FORCE.get();
            case SIGNAL -> Realm.CORE_SIGNAL.get();
            case RESONANCE -> Realm.CORE_RESONANCE.get();
            case HEAT -> Realm.CORE_HEAT.get();
            case FLOW -> Realm.CORE_FLOW.get();
        };
    }

    /** The seal was used: the Echo of this seat wakes above it. */
    static void awaken(ServerLevel level, RealmStory.Echo echo, BlockPos seal, ServerPlayer by) {
        EntityType<? extends EchoBoss> type = switch (echo) {
            case FORCE -> Realm.ECHO_FORCE.get();
            case SIGNAL -> Realm.ECHO_SIGNAL.get();
            case RESONANCE -> Realm.ECHO_RESONANCE.get();
            case HEAT -> Realm.ECHO_HEAT.get();
            case FLOW -> Realm.ECHO_FLOW.get();
        };
        spawnBoss(level, type, seal, echo == RealmStory.Echo.FLOW ? 2 : 7, by);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(seal)) < 96 * 96) {
                p.playNotifySound(RealmSounds.ECHO_AWAKEN.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
                p.sendSystemMessage(Component.translatable("story.redstoneplus.awakens", Component.translatable("story.redstoneplus.echo." + echo.id()))
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    private static void spawnBoss(ServerLevel level, EntityType<? extends EchoBoss> type, BlockPos seat, int above, @Nullable ServerPlayer by) {
        EchoBoss boss = type.create(level);
        if (boss == null) {
            return;
        }
        boss.moveTo(seat.getX() + 0.5, seat.getY() + above, seat.getZ() + 0.5, 0, 0);
        boss.setSeat(seat);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(seat), MobSpawnType.EVENT, null);
        if (by != null) {
            boss.setTarget(by);
        }
        level.addFreshEntity(boss);
        level.sendParticles(ParticleTypes.FLASH, boss.getX(), boss.getY() + 1, boss.getZ(), 3, 1, 1, 1, 0);
        level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 1, boss.getZ(), 80, 2, 2, 2, 0.1);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(seat.above()));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    // ============================================================================================ Echo of Force
    /** The Pistonarch: rams that fire, and the Slam that throws everyone near off their feet. */
    public static class Force extends EchoBoss {
        private int slamIn = -1;

        public Force(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return base(360, 12, 14);
        }

        @Override
        protected RealmStory.Echo echo() {
            return RealmStory.Echo.FORCE;
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.ECHO_FORCE;
        }

        @Override
        protected int moveA(ServerLevel level, LivingEntity target) {
            // the Rams: whoever is in front is struck and hurled away
            if (this.distanceToSqr(target) < 11 * 11) {
                this.swing(InteractionHand.MAIN_HAND);
                target.hurt(this.blow(), 7.0F);
                Vec3 away = target.position().subtract(this.position()).normalize();
                target.push(away.x * 2.4, 0.6, away.z * 2.4);
                target.hurtMarked = true;
                level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
            }
            return this.enraged() ? 40 : 60;
        }

        @Override
        protected int moveB(ServerLevel level, LivingEntity target) {
            this.playAbility();
            this.slamIn = 19;
            return this.enraged() ? 100 : 150;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.slamIn >= 0 && this.level() instanceof ServerLevel level && --this.slamIn == 0) {
                // the Slam lands
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
                for (Player p : this.playersNear(level, 8.0)) {
                    p.hurt(this.blow(), 10.0F);
                    Vec3 away = p.position().subtract(this.position()).normalize();
                    p.push(away.x * 1.5, 1.1, away.z * 1.5);
                    p.hurtMarked = true;
                }
                RealmMechanics.pulseTraps(level, this.blockPosition(), 16);
            }
        }

        @Override
        protected void summonHelpers(ServerLevel level) {
            this.summon(level, Realm.DETONATOR_HUSK.get(), 3);
        }
    }

    // ============================================================================================ Echo of Signal
    /** The Current: it dashes straight through you, and calls signal-lightning down around you. */
    public static class Signal extends EchoBoss {
        private int dashing;
        private Vec3 dashDir = Vec3.ZERO;

        public Signal(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return base(300, 9, 8);
        }

        @Override
        protected RealmStory.Echo echo() {
            return RealmStory.Echo.SIGNAL;
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.ECHO_SIGNAL;
        }

        @Override
        protected double flySpeed() {
            return 0.42;
        }

        @Override
        protected double hoverHeight() {
            return 2.0;
        }

        @Override
        protected int moveA(ServerLevel level, LivingEntity target) {
            // the Dash
            this.dashDir = target.getEyePosition().subtract(this.position()).normalize();
            this.dashing = 16;
            this.swing(InteractionHand.MAIN_HAND);
            return this.enraged() ? 45 : 70;
        }

        @Override
        protected int moveB(ServerLevel level, LivingEntity target) {
            // the Surge: lightning around the target
            this.playAbility();
            int bolts = this.enraged() ? 5 : 3;
            for (int i = 0; i < bolts; i++) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    double a = this.random.nextDouble() * Mth.TWO_PI;
                    double r = i == 0 ? 0 : 3 + this.random.nextDouble() * 3;
                    double x = target.getX() + Math.cos(a) * r;
                    double z = target.getZ() + Math.sin(a) * r;
                    bolt.moveTo(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)), z);
                    level.addFreshEntity(bolt);
                }
            }
            return this.enraged() ? 110 : 160;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.dashing > 0 && this.level() instanceof ServerLevel level) {
                this.dashing--;
                this.setDeltaMovement(this.dashDir.scale(1.1));
                level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.05F), 2.0F), this.getX(), this.getY() + 0.5, this.getZ(),
                        4, 0.3, 0.3, 0.3, 0.0);
                for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.6), e -> e != this && !(e instanceof EchoBoss))) {
                    if (hit.hurt(this.blow(), 9.0F)) {
                        hit.push(this.dashDir.x, 0.4, this.dashDir.z);
                    }
                }
            }
        }

        @Override
        protected void summonHelpers(ServerLevel level) {
            this.summon(level, Realm.SPARK_MITE.get(), 4);
            this.summon(level, Realm.RELAY_STRIDER.get(), 1);
        }
    }

    // ============================================================================================ Echo of Resonance
    /** The Choir: the Chorus lifts everyone near and takes their sight; between choruses, it strikes with sound. */
    public static class Resonance extends EchoBoss {
        public Resonance(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return base(320, 8, 10);
        }

        @Override
        protected RealmStory.Echo echo() {
            return RealmStory.Echo.RESONANCE;
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.ECHO_RESONANCE;
        }

        @Override
        protected double hoverHeight() {
            return 5.0;
        }

        @Override
        protected int moveA(ServerLevel level, LivingEntity target) {
            // a sonic strike: sound goes through armour
            if (this.distanceToSqr(target) < 24 * 24 && this.hasLineOfSight(target)) {
                this.swing(InteractionHand.MAIN_HAND);
                level.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getEyeY(), target.getZ(), 1, 0, 0, 0, 0);
                target.hurt(this.damageSources().sonicBoom(this), 6.0F);
                level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2.0F, 0.5F);
            }
            return this.enraged() ? 35 : 50;
        }

        @Override
        protected int moveB(ServerLevel level, LivingEntity target) {
            // the Chorus
            this.playAbility();
            for (Player p : this.playersNear(level, 20.0)) {
                p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 1), this);
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0), this);
                p.hurt(this.damageSources().magic(), 5.0F);
            }
            return this.enraged() ? 130 : 180;
        }

        @Override
        protected void summonHelpers(ServerLevel level) {
            this.summon(level, Realm.BELL_STALKER.get(), 2);
        }
    }

    // ============================================================================================ Echo of Heat
    /** The Kilnheart: volleys of fire, and the Bloom: a ring of flame and a burst of heat. */
    public static class Heat extends EchoBoss {
        public Heat(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return base(340, 10, 10);
        }

        @Override
        protected RealmStory.Echo echo() {
            return RealmStory.Echo.HEAT;
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.ECHO_HEAT;
        }

        @Override
        protected int moveA(ServerLevel level, LivingEntity target) {
            // a volley of fire
            int shots = this.enraged() ? 5 : 3;
            for (int i = 0; i < shots; i++) {
                Vec3 to = target.getEyePosition().subtract(this.getEyePosition()).add(this.random.nextGaussian() * 1.5, 0, this.random.nextGaussian() * 1.5);
                SmallFireball ball = new SmallFireball(level, this, to.normalize());
                ball.setPos(this.getX(), this.getY(0.6), this.getZ());
                level.addFreshEntity(ball);
            }
            this.swing(InteractionHand.MAIN_HAND);
            return this.enraged() ? 30 : 45;
        }

        @Override
        protected int moveB(ServerLevel level, LivingEntity target) {
            // the Bloom
            this.playAbility();
            BlockPos c = this.blockPosition();
            for (int i = 0; i < 20; i++) {
                double a = i * Mth.TWO_PI / 20;
                double r = 4 + this.random.nextDouble() * 3;
                int x = Mth.floor(this.getX() + Math.cos(a) * r);
                int z = Mth.floor(this.getZ() + Math.sin(a) * r);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos at = new BlockPos(x, y, z);
                if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isSolid() && Math.abs(y - c.getY()) < 12) {
                    level.setBlock(at, Blocks.FIRE.defaultBlockState(), 3);
                }
            }
            for (Player p : this.playersNear(level, 7.0)) {
                p.hurt(this.damageSources().onFire(), 8.0F);
                p.igniteForSeconds(5.0F);
            }
            return this.enraged() ? 140 : 200;
        }

        @Override
        protected void summonHelpers(ServerLevel level) {
            this.summon(level, Realm.KILNBOUND.get(), 2);
        }
    }

    // ============================================================================================ Echo of Flow
    /** The Sluicemother: it walks; the Floodtide pours molten redstone over its basin and drags everyone towards it. */
    public static class Flow extends EchoBoss {
        public Flow(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return base(380, 14, 12).add(Attributes.MOVEMENT_SPEED, 0.24);
        }

        @Override
        protected boolean flies() {
            return false;
        }

        @Override
        protected RealmStory.Echo echo() {
            return RealmStory.Echo.FLOW;
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.ECHO_FLOW;
        }

        @Override
        public boolean isAffectedByFluids() {
            return false;
        }

        @Override
        protected int moveA(ServerLevel level, LivingEntity target) {
            // the Tide: everyone near is pulled towards it
            for (Player p : this.playersNear(level, 16.0)) {
                Vec3 in = this.position().subtract(p.position()).normalize().scale(0.9);
                p.push(in.x, 0.15, in.z);
                p.hurtMarked = true;
            }
            return this.enraged() ? 60 : 90;
        }

        @Override
        protected int moveB(ServerLevel level, LivingEntity target) {
            // the Floodtide: molten redstone pours out around it, and runs away again
            this.playAbility();
            var flowing = RealmLiquids.Kind.MOLTEN_REDSTONE.liquid().flowing().get();
            for (int i = 0; i < (this.enraged() ? 14 : 9); i++) {
                double a = this.random.nextDouble() * Mth.TWO_PI;
                double r = 3 + this.random.nextDouble() * 7;
                int x = Mth.floor(this.getX() + Math.cos(a) * r);
                int z = Mth.floor(this.getZ() + Math.sin(a) * r);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos at = new BlockPos(x, y, z);
                if (level.getBlockState(at).isAir() && Math.abs(y - this.getBlockY()) < 6) {
                    level.setBlock(at, flowing.getFlowing(6, false).createLegacyBlock(), 3);
                }
            }
            return this.enraged() ? 140 : 200;
        }

        @Override
        protected void summonHelpers(ServerLevel level) {
            this.summon(level, Realm.SLUICE_CHAINJAW.get(), 2);
        }
    }

    // ============================================================================================ the Overtoll
    /**
     * The Great Bell come down. Its Toll is the third rule turned into a weapon: everyone who moves while it tolls is struck.
     * Its chains lash and drag. Hurt, it calls the Wirewraiths; near its end, fire rains around it.
     */
    public static class Overtoll extends EchoBoss {
        private final Map<UUID, Vec3> atToll = new HashMap<>();
        private int tollIn = -1;
        private boolean secondCall;

        public Overtoll(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.xpReward = 1000;
        }

        public static AttributeSupplier.Builder attributes() {
            return base(1200, 16, 16);
        }

        @Override
        protected RealmStory.Echo echo() {
            return null;
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.OVERTOLL;
        }

        @Override
        protected double hoverHeight() {
            return 7.0;
        }

        @Override
        protected double flySpeed() {
            return 0.16;
        }

        @Override
        protected int moveA(ServerLevel level, LivingEntity target) {
            // the Chains: they lash the target and drag it in
            if (this.distanceToSqr(target) < 18 * 18) {
                this.swing(InteractionHand.MAIN_HAND);
                target.hurt(this.blow(), 10.0F);
                Vec3 in = this.position().subtract(target.position()).normalize().scale(1.1);
                target.push(in.x, 0.4, in.z);
                target.hurtMarked = true;
                level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
            }
            if (this.getHealth() < this.getMaxHealth() * 0.25F) {
                // near its end, fire rains from it
                for (Player p : this.playersNear(level, 30.0)) {
                    SmallFireball ball = new SmallFireball(level, this, new Vec3(0, -1, 0));
                    ball.setPos(p.getX() + this.random.nextGaussian() * 2, p.getY() + 14, p.getZ() + this.random.nextGaussian() * 2);
                    level.addFreshEntity(ball);
                }
            }
            return this.enraged() ? 40 : 60;
        }

        @Override
        protected int moveB(ServerLevel level, LivingEntity target) {
            // the Toll: a warning, then everyone who moves while it rings is struck down
            for (Player p : this.playersNear(level, 40.0)) {
                p.displayClientMessage(Component.translatable("story.redstoneplus.overtoll_warning").withStyle(ChatFormatting.RED), true);
            }
            this.tollIn = 30;
            return this.enraged() ? 150 : 210;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (!(this.level() instanceof ServerLevel level) || this.tollIn < 0) {
                return;
            }
            this.tollIn--;
            if (this.tollIn == 0) {
                this.playAbility();
                level.playSound(null, this.blockPosition(), RealmSounds.GREAT_BELL.get(), SoundSource.HOSTILE, 6.0F, 0.7F);
                this.atToll.clear();
                for (Player p : this.playersNear(level, 40.0)) {
                    this.atToll.put(p.getUUID(), p.position());
                }
            } else if (this.tollIn < 0 && this.tollIn > -40) {
                for (Player p : this.playersNear(level, 40.0)) {
                    Vec3 was = this.atToll.get(p.getUUID());
                    if (was != null && p.position().distanceToSqr(was) > 1.0) {
                        this.atToll.remove(p.getUUID());
                        p.hurt(this.damageSources().magic(), 14.0F);
                        level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getEyeY(), p.getZ(), 1, 0, 0, 0, 0);
                    }
                }
            }
            if (this.tollIn <= -40) {
                this.tollIn = -1;
            }
        }

        @Override
        protected void summonHelpers(ServerLevel level) {
            this.summon(level, Realm.WIREWRAITH.get(), 2);
        }

        @Override
        protected void customServerAiStep() {
            super.customServerAiStep();
            if (!this.secondCall && this.getHealth() < this.getMaxHealth() * 0.25F && this.level() instanceof ServerLevel level) {
                this.secondCall = true;
                this.summon(level, Realm.BELL_STALKER.get(), 2);
            }
        }
    }

    // ============================================================================================ items
    /** An Echo's core: proof it fell. Five of them make the Heart of the Five. */
    public static class EchoCore extends Item {
        private final RealmStory.Echo echo;

        public EchoCore(Properties properties, RealmStory.Echo echo) {
            super(properties);
            this.echo = echo;
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.redstoneplus.core_" + this.echo.id() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    /** The Heart of the Five: laid on the bronze of a Foundry City's Cradle, it calls the Great Bell down. */
    public static class HeartOfTheFive extends Item {
        public HeartOfTheFive(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
                return InteractionResult.SUCCESS;
            }
            if (!level.getBlockState(context.getClickedPos()).is(Realm.BELL_BRONZE.get()) || !level.dimension().equals(Realm.REALM)) {
                player.displayClientMessage(Component.translatable("story.redstoneplus.heart_where").withStyle(ChatFormatting.GOLD), true);
                return InteractionResult.FAIL;
            }
            if (RealmStory.healed(level)) {
                player.displayClientMessage(Component.translatable("story.redstoneplus.heart_done").withStyle(ChatFormatting.GOLD), true);
                return InteractionResult.FAIL;
            }
            BlockPos cradle = RealmCities.nearestCradle(level, context.getClickedPos());
            if (cradle == null || cradle.distSqr(context.getClickedPos().atY(cradle.getY())) > 16 * 16) {
                player.displayClientMessage(Component.translatable("story.redstoneplus.heart_where").withStyle(ChatFormatting.GOLD), true);
                return InteractionResult.FAIL;
            }
            if (!level.getEntitiesOfClass(Overtoll.class, new net.minecraft.world.phys.AABB(cradle).inflate(80)).isEmpty()) {
                return InteractionResult.FAIL;
            }
            context.getItemInHand().shrink(1);
            spawnBoss(level, Realm.OVERTOLL.get(), cradle, 26, player);
            for (ServerPlayer p : level.players()) {
                p.playNotifySound(RealmSounds.GREAT_BELL.get(), SoundSource.HOSTILE, 3.0F, 0.5F);
                p.sendSystemMessage(Component.translatable("story.redstoneplus.overtoll_descends").withStyle(ChatFormatting.DARK_RED));
            }
            return InteractionResult.CONSUME;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.redstoneplus.heart_of_the_five.desc").withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * The Tuning Fork: strike it (use) and it hums towards the nearest seat whose Echo still stands; once all five have
     * fallen, towards the nearest Cradle where the Bell can be called down.
     */
    public static class TuningFork extends Item {
        public TuningFork(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
                return InteractionResultHolder.success(stack);
            }
            player.getCooldowns().addCooldown(this, 20);
            level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
            if (!level.dimension().equals(Realm.REALM)) {
                sp.displayClientMessage(Component.translatable("story.redstoneplus.fork_silent").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.success(stack);
            }
            if (RealmStory.healed(level)) {
                sp.displayClientMessage(Component.translatable("story.redstoneplus.fork_healed").withStyle(ChatFormatting.GOLD), true);
                return InteractionResultHolder.success(stack);
            }
            BlockPos goal;
            Component what;
            if (RealmStory.conquered(level) == RealmStory.ALL) {
                goal = RealmCities.nearestCradle(server, player.blockPosition());
                what = Component.translatable("story.redstoneplus.fork_cradle");
            } else {
                Sanctums.Seat seat = Sanctums.nearest(server, player.blockPosition(), false);
                goal = seat == null ? null : seat.pos();
                what = seat == null ? Component.empty() : Component.translatable("story.redstoneplus.echo." + seat.echo().id());
            }
            if (goal == null) {
                sp.displayClientMessage(Component.translatable("story.redstoneplus.fork_nothing").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.success(stack);
            }
            double dx = goal.getX() + 0.5 - player.getX();
            double dz = goal.getZ() + 0.5 - player.getZ();
            float yawTo = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float rel = Mth.wrapDegrees(yawTo - player.getYRot());
            String arrow = rel > -22.5 && rel <= 22.5 ? "↑" : rel > 22.5 && rel <= 67.5 ? "↗" : rel > 67.5 && rel <= 112.5 ? "→"
                    : rel > 112.5 && rel <= 157.5 ? "↘" : rel > -67.5 && rel <= -22.5 ? "↖" : rel > -112.5 && rel <= -67.5 ? "←"
                    : rel > -157.5 && rel <= -112.5 ? "↙" : "↓";
            sp.displayClientMessage(Component.translatable("story.redstoneplus.fork_points", what, arrow, (int) Math.sqrt(dx * dx + dz * dz))
                    .withStyle(ChatFormatting.GOLD), true);
            // a trail of light along the way for a few blocks
            Vec3 dir = new Vec3(dx, 0, dz).normalize();
            for (int i = 1; i <= 12; i++) {
                server.sendParticles(sp, new DustParticleOptions(new Vector3f(1.0F, 0.5F, 0.15F), 1.2F), true, player.getX() + dir.x * i,
                        player.getEyeY() - 0.3, player.getZ() + dir.z * i, 2, 0.05, 0.05, 0.05, 0.0);
            }
            return InteractionResultHolder.success(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.redstoneplus.tuning_fork.desc").withStyle(ChatFormatting.GRAY));
        }
    }
}
