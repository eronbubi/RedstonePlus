package de.eron.redstoneplus.realm;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * "The Machine-Bound": creatures of the realm that were caught and rebuilt by its machines.
 * Each one keeps the instincts (vanilla AI) of the animal or monster it was, but has its own body and a new trick.
 * They are separate from the mod's original mobs, which stay as they are.
 */
public final class MachineBound {
    private MachineBound() {
    }

    /** Zombie in a leaking uranium cage: poisons on hit and spills toxic puddles when damaged. Never burns. */
    public static class LeakingCell extends Zombie implements RealmAnimated {
        @Override
        public float getWalkTargetValue(net.minecraft.core.BlockPos pos, net.minecraft.world.level.LevelReader level) {
            return 0.0F; // a machine: daylight or dark is all the same to it, so it spawns and roams in both
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 12, 80, s -> s.is(Realm.REDSTONE_VEIN.get()), RealmJobs::sparks, RealmJobs::drink).exposed());
        }

        private int abilityStart = -10000;

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(RealmSounds.LEAKING_CELL.ability().get(), 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
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
        protected net.minecraft.sounds.SoundEvent getAmbientSound() {
            return RealmSounds.LEAKING_CELL.ambient().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
            return RealmSounds.LEAKING_CELL.hurt().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getDeathSound() {
            return RealmSounds.LEAKING_CELL.death().get();
        }

        @Override
        protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            this.playSound(RealmSounds.LEAKING_CELL.step().get(), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        private int spillCooldown;

        public LeakingCell(EntityType<? extends Zombie> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 34.0).add(Attributes.ATTACK_DAMAGE, 5.0).add(Attributes.ARMOR, 4.0);
        }

        @Override
        protected boolean isSunSensitive() {
            return false;
        }

        @Override
        protected boolean convertsInWater() {
            return false;
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1), this);
            }
            return hit;
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            boolean hurt = super.hurt(source, amount);
            if (hurt && !this.level().isClientSide() && this.spillCooldown <= 0 && this.isAlive()) {
                this.spillCooldown = 60;
                AreaEffectCloud puddle = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
                puddle.setOwner(this);
                puddle.setRadius(2.2F);
                puddle.setRadiusPerTick(-0.01F);
                puddle.setDuration(100);
                puddle.setParticle(ParticleTypes.ITEM_SLIME);
                puddle.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                this.level().addFreshEntity(puddle);
                this.playAbility();
                // radioactive fertilizer: crops, saplings and grass around the spill grow a step
                if (this.level() instanceof ServerLevel server) {
                    for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.randomInCube(this.random, 12, this.blockPosition(), 3)) {
                        net.minecraft.world.level.block.state.BlockState st = server.getBlockState(pos);
                        if (st.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock grow
                                && grow.isValidBonemealTarget(server, pos, st) && grow.isBonemealSuccess(server, this.random, pos, st)) {
                            grow.performBonemeal(server, this.random, pos, st);
                            server.sendParticles(RealmFx.DRIP.get(), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.02);
                        }
                    }
                }
            }
            return hurt;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.spillCooldown > 0) {
                this.spillCooldown--;
            }
            if (this.level().isClientSide()) {
                if (this.random.nextInt(4) == 0) {
                    RealmFx.emit(this, RealmFx.DRIP.get(), (this.random.nextDouble() - 0.5) * 0.4, 0.9 + this.random.nextDouble() * 0.3, 0.3, 0, -0.02, 0);
                }
                if (this.random.nextInt(25) == 0) {
                    RealmFx.emit(this, RealmFx.SPARK.get(), this.random.nextBoolean() ? 0.3 : -0.3, 1.85, -0.1, 0, 0.1, 0);
                }
            }
        }

        @Override
        public boolean canBeAffected(MobEffectInstance effect) {
            return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
        }
    }

    /** Creeper wired to a detonator: when it is about to blow, every Husk within 8 blocks starts its fuse too. */
    public static class DetonatorHusk extends Creeper implements RealmAnimated {
        @Override
        public float getWalkTargetValue(net.minecraft.core.BlockPos pos, net.minecraft.world.level.LevelReader level) {
            return 0.0F; // a machine: daylight or dark is all the same to it, so it spawns and roams in both
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 14, 80, s -> s.is(net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK), RealmJobs::sparks, RealmJobs::drink));
        }

        private int abilityStart = -10000;

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(RealmSounds.DETONATOR_HUSK.ability().get(), 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
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
        protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
            return RealmSounds.DETONATOR_HUSK.hurt().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getDeathSound() {
            return RealmSounds.DETONATOR_HUSK.death().get();
        }

        @Override
        protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            this.playSound(RealmSounds.DETONATOR_HUSK.step().get(), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        private boolean relayed;

        @Override
        public float abilityOverride(float partialTick) {
            float swell = this.getSwelling(partialTick);
            return swell > 0.0F ? swell : -1.0F;
        }

        public DetonatorHusk(EntityType<? extends Creeper> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return Creeper.createAttributes().add(Attributes.MAX_HEALTH, 26.0).add(Attributes.ARMOR, 3.0);
        }

        @Override
        public void tick() {
            boolean aboutToBlow = this.getSwelling(1.0F) > 0.85F;
            super.tick();
            if (this.isRemoved() && aboutToBlow && this.level() instanceof ServerLevel blast) {
                // the detonation runs down the wiring into every realm trap nearby
                RealmMechanics.pulseTraps(blast, this.blockPosition(), 10);
                RealmFx.ring(blast, this.position().add(0, 0.3, 0), 3.0, RealmFx.SPARK.get(), 30);
                return;
            }
            if (this.level().isClientSide() && this.isAlive()) {
                float swell = this.getSwelling(1.0F);
                if (swell > 0 && this.random.nextFloat() < 0.3F + swell) {
                    RealmFx.emit(this, RealmFx.SPARK.get(), (this.random.nextDouble() - 0.5) * 0.5, 1.9, 0, 0, 0.12, 0);
                }
                if (this.tickCount % 30 < 3) {
                    RealmFx.emit(this, RealmFx.EMBER.get(), this.random.nextBoolean() ? 0.3 : -0.3, 2.05, 0.3, 0, 0.01, 0);
                }
            }
            if (!this.level().isClientSide() && this.isAlive()) {
                if (this.getSwellDir() > 0 && this.getSwelling(1.0F) > 0.75F && !this.relayed) {
                    this.relayed = true;
                    for (DetonatorHusk other : this.level().getEntitiesOfClass(DetonatorHusk.class, this.getBoundingBox().inflate(8.0), h -> h != this)) {
                        other.ignite();
                    }
                    this.playAbility();
                } else if (this.getSwellDir() < 0) {
                    this.relayed = false;
                }
                if (this.tickCount % 20 == 0 && this.level() instanceof ServerLevel server) {
                    server.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE, this.getX(), this.getY() + 1.2, this.getZ(), 2, 0.3, 0.4, 0.3, 0);
                }
            }
        }
    }

    /** Spider with a crystal egg sack: its bite roots you in place. */
    public static class TripwireBrood extends Spider implements RealmAnimated {
        @Override
        public float getWalkTargetValue(net.minecraft.core.BlockPos pos, net.minecraft.world.level.LevelReader level) {
            return 0.0F; // a machine: daylight or dark is all the same to it, so it spawns and roams in both
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 14, 60, s -> s.is(net.minecraft.world.level.block.Blocks.TRIPWIRE) || s.is(Realm.BRIAR_THORNS.get()), RealmJobs::sparks, RealmJobs::web));
        }

        private int abilityStart = -10000;

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(RealmSounds.TRIPWIRE_BROOD.ability().get(), 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
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
        protected net.minecraft.sounds.SoundEvent getAmbientSound() {
            return RealmSounds.TRIPWIRE_BROOD.ambient().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
            return RealmSounds.TRIPWIRE_BROOD.hurt().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getDeathSound() {
            return RealmSounds.TRIPWIRE_BROOD.death().get();
        }

        @Override
        protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            this.playSound(RealmSounds.TRIPWIRE_BROOD.step().get(), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        public TripwireBrood(EntityType<? extends Spider> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return Spider.createAttributes().add(Attributes.MAX_HEALTH, 28.0).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.ARMOR, 4.0);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4), this);
                this.playAbility();
            }
            return hit;
        }

        @Override
        public void die(DamageSource source) {
            super.die(source);
            if (this.level() instanceof ServerLevel server) {
                int n = 2 + this.random.nextInt(2);
                for (int i = 0; i < n; i++) {
                    net.minecraft.world.entity.monster.CaveSpider baby = EntityType.CAVE_SPIDER.create(server);
                    if (baby != null) {
                        baby.moveTo(this.getX() + this.random.nextGaussian() * 0.5, this.getY() + 0.3, this.getZ() + this.random.nextGaussian() * 0.5,
                                this.random.nextFloat() * 360, 0);
                        server.addFreshEntity(baby);
                    }
                }
                server.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.8, this.getZ(), 20, 0.5, 0.4, 0.5, 0.05);
                this.playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.5F, 1.0F);
            }
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide() && this.random.nextInt(10) == 0) {
                RealmFx.emit(this, ParticleTypes.END_ROD, (this.random.nextDouble() - 0.5) * 0.8, 1.2, -0.7, 0, 0.01, 0);
            }
        }
    }

    /** Skeleton caged in a furnace frame: fire proof, its arrows burn. */
    public static class Kilnbound extends Skeleton implements RealmAnimated {
        @Override
        public float getWalkTargetValue(net.minecraft.core.BlockPos pos, net.minecraft.world.level.LevelReader level) {
            return 0.0F; // a machine: daylight or dark is all the same to it, so it spawns and roams in both
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 16, 50, s -> s.is(Realm.KILN_TURRET.get()) || RealmJobs.fire(s), RealmJobs::sparks, RealmJobs::stoke));
        }

        private int abilityStart = -10000;

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(RealmSounds.KILNBOUND.ability().get(), 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
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
        protected net.minecraft.sounds.SoundEvent getAmbientSound() {
            return RealmSounds.KILNBOUND.ambient().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
            return RealmSounds.KILNBOUND.hurt().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getDeathSound() {
            return RealmSounds.KILNBOUND.death().get();
        }

        @Override
        protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            this.playSound(RealmSounds.KILNBOUND.step().get(), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        public Kilnbound(EntityType<? extends Skeleton> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return Skeleton.createAttributes().add(Attributes.MAX_HEALTH, 26.0).add(Attributes.ARMOR, 4.0);
        }

        @Override
        protected boolean isSunBurnTick() {
            return false;
        }

        @Override
        protected AbstractArrow getArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
            this.playAbility();
            if (this.level() instanceof ServerLevel server) {
                // it commands the kilns: every Kiln Turret close by fires with it
                for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(this.blockPosition().offset(-6, -2, -6),
                        this.blockPosition().offset(6, 2, 6))) {
                    net.minecraft.world.level.block.state.BlockState st = server.getBlockState(pos);
                    if (st.getBlock() instanceof TrapBlock.KilnTurret turret) {
                        turret.arm(st, server, pos.immutable(), 2);
                    }
                }
            }
            AbstractArrow projectile = super.getArrow(arrow, velocity, weapon);
            projectile.igniteForSeconds(100);
            return projectile;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide()) {
                if (this.random.nextInt(3) == 0) {
                    RealmFx.emit(this, RealmFx.EMBER.get(), (this.random.nextDouble() - 0.5) * 0.2, 2.35, 0, 0, 0.04, 0);
                }
                if (this.random.nextInt(6) == 0) {
                    RealmFx.emit(this, ParticleTypes.SMOKE, 0, 2.4, 0, 0, 0.04, 0);
                }
                if (this.random.nextInt(8) == 0) {
                    RealmFx.emit(this, RealmFx.EMBER.get(), 0, 1.5, 0.15, 0, 0.02, 0.01);
                }
            }
        }
    }

    /** Slime held in a capacitor frame: every hit it takes discharges a shock into everything around it. */
    public static class LivingCapacitor extends Slime implements RealmAnimated {
        private int abilityStart = -10000;

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(RealmSounds.LIVING_CAPACITOR.ability().get(), 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
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
        protected net.minecraft.sounds.SoundEvent getAmbientSound() {
            return RealmSounds.LIVING_CAPACITOR.ambient().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
            return RealmSounds.LIVING_CAPACITOR.hurt().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getDeathSound() {
            return RealmSounds.LIVING_CAPACITOR.death().get();
        }

        @Override
        protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            this.playSound(RealmSounds.LIVING_CAPACITOR.step().get(), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        public LivingCapacitor(EntityType<? extends Slime> type, Level level) {
            super(type, level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            boolean hurt = super.hurt(source, amount);
            if (hurt && this.level() instanceof ServerLevel server && !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
                float shock = 1.0F + this.getSize();
                for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(1.5),
                        e -> e != this && !(e instanceof LivingCapacitor))) {
                    near.hurt(this.damageSources().lightningBolt(), shock);
                }
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(0.5), this.getZ(), 16, this.getBbWidth() * 0.6, 0.4, this.getBbWidth() * 0.6, 0.2);
                this.playAbility();
                RealmMechanics.pulseTraps(server, this.blockPosition(), 4);
                for (LivingCapacitor other : server.getEntitiesOfClass(LivingCapacitor.class, this.getBoundingBox().inflate(6.0), c -> c != this)) {
                    RealmFx.line(server, this.position().add(0, 0.6, 0), other.position().add(0, 0.6, 0), RealmFx.SPARK.get(), 0.3);
                }
            }
            return hurt;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level() instanceof ServerLevel server && this.tickCount % 60 == 0 && this.getTarget() == null) {
                RealmJobs.arcIntoLamps(server, this, 6);
            }
            if (this.level().isClientSide() && this.random.nextInt(5) == 0) {
                double s = this.getSize() * 0.12;
                RealmFx.emit(this, RealmFx.SPARK.get(), (this.random.nextBoolean() ? 1 : -1) * s, this.getSize() * 0.55, 0,
                        (this.random.nextDouble() - 0.5) * 0.1, 0.08, (this.random.nextDouble() - 0.5) * 0.1);
            }
        }
    }

    /** Enderman strung with relay cables: hostile on sight, and its hits can relay you a few blocks away. */
    public static class RelayStrider extends EnderMan implements RealmAnimated {
        @Override
        public float getWalkTargetValue(net.minecraft.core.BlockPos pos, net.minecraft.world.level.LevelReader level) {
            return 0.0F; // a machine: daylight or dark is all the same to it, so it spawns and roams in both
        }

        private int abilityStart = -10000;

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(RealmSounds.RELAY_STRIDER.ability().get(), 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
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
        protected net.minecraft.sounds.SoundEvent getAmbientSound() {
            return RealmSounds.RELAY_STRIDER.ambient().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
            return RealmSounds.RELAY_STRIDER.hurt().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getDeathSound() {
            return RealmSounds.RELAY_STRIDER.death().get();
        }

        @Override
        protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            this.playSound(RealmSounds.RELAY_STRIDER.step().get(), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        public RelayStrider(EntityType<? extends EnderMan> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return EnderMan.createAttributes().add(Attributes.MAX_HEALTH, 50.0).add(Attributes.ATTACK_DAMAGE, 8.0).add(Attributes.ARMOR, 4.0);
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 20, 60, s -> s.is(net.minecraft.world.level.block.Blocks.LIGHTNING_ROD), RealmJobs::sparks, RealmJobs::charge).exposed());
            this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof LivingEntity living && this.random.nextInt(3) == 0) {
                Vec3 at = living.position();
                for (int i = 0; i < 8; i++) {
                    double x = at.x + (this.random.nextDouble() - 0.5) * 12;
                    double z = at.z + (this.random.nextDouble() - 0.5) * 12;
                    if (living.randomTeleport(x, at.y + this.random.nextInt(5) - 2, z, true)) {
                        this.playAbility();
                        if (this.level() instanceof ServerLevel server) {
                            RealmFx.line(server, at.add(0, 1, 0), living.position().add(0, 1, 0), ParticleTypes.REVERSE_PORTAL, 0.4);
                        }
                        break;
                    }
                }
            }
            return hit;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide() && this.random.nextInt(4) == 0) {
                RealmFx.emit(this, ParticleTypes.REVERSE_PORTAL, this.random.nextBoolean() ? 0.35 : -0.35, 0.6, 0.1, 0, 0.02, 0);
            }
        }
    }

    /** Pig with bellows strapped on: fire proof, and when hurt it blasts hot air that throws attackers back. */
    public static class BellowsHog extends Pig implements RealmAnimated {
        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 14, 60, RealmJobs::fire, RealmJobs::sparks, RealmJobs::stoke));
        }

        private int abilityStart = -10000;

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(RealmSounds.BELLOWS_HOG.ability().get(), 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
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
        protected net.minecraft.sounds.SoundEvent getAmbientSound() {
            return RealmSounds.BELLOWS_HOG.ambient().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
            return RealmSounds.BELLOWS_HOG.hurt().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getDeathSound() {
            return RealmSounds.BELLOWS_HOG.death().get();
        }

        @Override
        protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            this.playSound(RealmSounds.BELLOWS_HOG.step().get(), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        public BellowsHog(EntityType<? extends Pig> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return Pig.createAttributes().add(Attributes.MAX_HEALTH, 16.0);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            boolean hurt = super.hurt(source, amount);
            if (hurt && this.level() instanceof ServerLevel server && source.getEntity() instanceof LivingEntity attacker) {
                Vec3 away = attacker.position().subtract(this.position()).multiply(1, 0, 1).normalize();
                attacker.setDeltaMovement(away.x * 1.1, 0.4, away.z * 1.1);
                attacker.hurtMarked = true;
                attacker.igniteForSeconds(2);
                server.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.6, this.getZ(), 20, 0.6, 0.3, 0.6, 0.08);
                server.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.8, this.getZ(), 8, 0.4, 0.3, 0.4, 0.05);
                this.playAbility();
            }
            return hurt;
        }

        @Nullable
        @Override
        public Pig getBreedOffspring(ServerLevel level, AgeableMob partner) {
            return Realm.BELLOWS_HOG.get().create(level);
        }

        private int stoked;

        private static boolean isFuel(ItemStack stack) {
            return stack.is(net.minecraft.world.item.Items.COAL) || stack.is(net.minecraft.world.item.Items.CHARCOAL);
        }

        @Override
        public boolean isFood(ItemStack stack) {
            return isFuel(stack) || super.isFood(stack);
        }

        /** Coal stokes its bellows: for a minute it smelts everything dropped around it. */
        @Override
        public net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (isFuel(stack) && this.stoked <= 0 && !this.isBaby()) {
                if (!this.level().isClientSide()) {
                    this.stoked = 1200;
                    this.usePlayerItem(player, hand, stack);
                    this.playAbility();
                    this.playSound(SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
                }
                return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide());
            }
            return super.mobInteract(player, hand);
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level() instanceof ServerLevel server) {
                if (this.stoked > 0) {
                    this.stoked--;
                    if (this.tickCount % 30 == 0) {
                        RealmMechanics.smeltNearby(server, this.position().add(0, 0.4, 0), 2.5, 2);
                    }
                    if (this.tickCount % 4 == 0) {
                        RealmFx.burst(this, RealmFx.EMBER.get(), 0, 1.35, -0.4, 2, 0.05, 0.02);
                    }
                }
            } else if (this.random.nextInt(10) == 0) {
                RealmFx.emit(this, ParticleTypes.SMOKE, 0.12, 1.35, -0.4, 0, 0.05, 0);
            }
        }

        @Override
        public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            tag.putInt("Stoked", this.stoked);
        }

        @Override
        public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            this.stoked = tag.getInt("Stoked");
        }
    }

    /** Iron golem made of a flesh press: guards the realm, its blows crush through armor. */
    public static class FleshPress extends IronGolem implements RealmAnimated {
        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 16, 60, s -> s.is(Realm.REALMSTONE.get()), RealmJobs::dust, RealmJobs::repair).where(RealmJobs::rubble));
        }

        private int abilityStart = -10000;

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(RealmSounds.FLESH_PRESS.ability().get(), 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        @Override
        public void handleEntityEvent(byte id) {
            if (id == ABILITY_EVENT || id == 4) {
                this.abilityStart = this.tickCount;
            }
            if (id != ABILITY_EVENT) {
                super.handleEntityEvent(id);
            }
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getAmbientSound() {
            return RealmSounds.FLESH_PRESS.ambient().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
            return RealmSounds.FLESH_PRESS.hurt().get();
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getDeathSound() {
            return RealmSounds.FLESH_PRESS.death().get();
        }

        @Override
        protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            this.playSound(RealmSounds.FLESH_PRESS.step().get(), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        public FleshPress(EntityType<? extends IronGolem> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return IronGolem.createAttributes().add(Attributes.MAX_HEALTH, 160.0).add(Attributes.ATTACK_DAMAGE, 20.0).add(Attributes.ARMOR, 6.0);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), this);
                this.playAbility();
            }
            return hit;
        }

        @Override
        public boolean isAlliedTo(Entity other) {
            return other instanceof Constructs.Construct || super.isAlliedTo(other);
        }

        /** Give it a redstone block and it becomes your guardian: it will never turn on players again. */
        @Override
        protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(net.minecraft.world.item.Items.REDSTONE_BLOCK) && !this.isPlayerCreated()) {
                if (!this.level().isClientSide()) {
                    this.setPlayerCreated(true);
                    this.setTarget(null);
                    stack.consume(1, player);
                    this.playAbility();
                    if (this.level() instanceof ServerLevel server) {
                        server.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 2.8, this.getZ(), 6, 0.5, 0.3, 0.5, 0.1);
                        server.sendParticles(RealmFx.SPARK.get(), this.getX(), this.getY() + 1.6, this.getZ(), 30, 0.6, 0.6, 0.6, 0.2);
                    }
                }
                return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide());
            }
            return super.mobInteract(player, hand);
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide()) {
                if (this.random.nextInt(14) == 0) {
                    RealmFx.emit(this, RealmFx.STEAM.get(), this.random.nextBoolean() ? 0.7 : -0.7, 2.0, 0.45, 0, 0.05, 0);
                }
                if (this.random.nextInt(20) == 0) {
                    RealmFx.emit(this, RealmFx.SPARK.get(), 0, 1.6, 0.55, 0, 0.1, 0.05);
                }
            }
        }
    }
}
