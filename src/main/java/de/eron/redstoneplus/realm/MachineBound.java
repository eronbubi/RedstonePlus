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
            }
            return hurt;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.spillCooldown > 0) {
                this.spillCooldown--;
            }
            if (this.level().isClientSide() && this.random.nextInt(6) == 0) {
                this.level().addParticle(ParticleTypes.ITEM_SLIME, this.getRandomX(0.5), this.getY() + 0.6, this.getRandomZ(0.5), 0, -0.05, 0);
            }
        }

        @Override
        public boolean canBeAffected(MobEffectInstance effect) {
            return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
        }
    }

    /** Creeper wired to a detonator: when it is about to blow, every Husk within 8 blocks starts its fuse too. */
    public static class DetonatorHusk extends Creeper implements RealmAnimated {
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
            super.tick();
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
    }

    /** Skeleton caged in a furnace frame: fire proof, its arrows burn. */
    public static class Kilnbound extends Skeleton implements RealmAnimated {
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
            AbstractArrow projectile = super.getArrow(arrow, velocity, weapon);
            projectile.igniteForSeconds(100);
            return projectile;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide() && this.random.nextInt(8) == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 2.1, this.getZ(), 0, 0.03, 0);
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
            }
            return hurt;
        }
    }

    /** Enderman strung with relay cables: hostile on sight, and its hits can relay you a few blocks away. */
    public static class RelayStrider extends EnderMan implements RealmAnimated {
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
                        break;
                    }
                }
            }
            return hit;
        }
    }

    /** Pig with bellows strapped on: fire proof, and when hurt it blasts hot air that throws attackers back. */
    public static class BellowsHog extends Pig implements RealmAnimated {
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
    }

    /** Iron golem made of a flesh press: guards the realm, its blows crush through armor. */
    public static class FleshPress extends IronGolem implements RealmAnimated {
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
    }
}
