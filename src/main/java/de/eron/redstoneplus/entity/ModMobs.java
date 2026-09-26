package de.eron.redstoneplus.entity;

import de.eron.redstoneplus.content.Extras;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
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

import javax.annotation.Nullable;

/** The eight new mobs. They reuse vanilla bodies and AI with their own looks, stats and tricks. */
public final class ModMobs {
    private ModMobs() {
    }

    /** Glowing green zombie that does not burn in daylight and poisons what it hits. */
    public static class UraniumZombie extends Zombie {
        public UraniumZombie(EntityType<? extends Zombie> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ATTACK_DAMAGE, 4.0);
        }

        @Override
        protected boolean isSunSensitive() {
            return false;
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
            }
            return hit;
        }
    }

    /** Red creeper with twice the blast radius. */
    public static class RedstoneCreeper extends Creeper {
        private boolean radiusApplied;

        public RedstoneCreeper(EntityType<? extends Creeper> type, Level level) {
            super(type, level);
        }

        @Override
        public void tick() {
            if (!this.radiusApplied && !this.level().isClientSide()) {
                // the radius field is private; a save/load round trip is the supported way to change it
                this.radiusApplied = true;
                CompoundTag tag = new CompoundTag();
                this.addAdditionalSaveData(tag);
                tag.putByte("ExplosionRadius", (byte) 6);
                this.readAdditionalSaveData(tag);
            }
            super.tick();
        }
    }

    /** Fast cyan spider from the End that slows its victims. */
    public static class CrystalSpider extends Spider {
        public CrystalSpider(EntityType<? extends Spider> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return Spider.createAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.36);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2), this);
            }
            return hit;
        }
    }

    /** Fire proof skeleton whose arrows set targets on fire. */
    public static class MagmaSkeleton extends Skeleton {
        public MagmaSkeleton(EntityType<? extends Skeleton> type, Level level) {
            super(type, level);
        }

        @Override
        protected boolean isSunBurnTick() {
            return false;
        }

        @Override
        protected AbstractArrow getArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
            AbstractArrow projectile = super.getArrow(arrow, velocity, weapon);
            projectile.igniteForSeconds(100);
            return projectile;
        }
    }

    /** Red slime that drops rubies. */
    public static class RubySlime extends Slime {
        public RubySlime(EntityType<? extends Slime> type, Level level) {
            super(type, level);
        }
    }

    /** Purple enderman of the new End biomes: stronger and it attacks on sight. */
    public static class VoidEnderman extends EnderMan {
        public VoidEnderman(EntityType<? extends EnderMan> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return EnderMan.createAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.ATTACK_DAMAGE, 10.0);
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
    }

    /** Orange fire proof pig; it drops Ember Pork. */
    public static class EmberPig extends Pig {
        public EmberPig(EntityType<? extends Pig> type, Level level) {
            super(type, level);
        }

        @Nullable
        @Override
        public Pig getBreedOffspring(ServerLevel level, AgeableMob partner) {
            return Extras.EMBER_PIG.get().create(level);
        }
    }

    /** Red iron golem that guards the Redstone Fields. */
    public static class RedstoneGolem extends IronGolem {
        public RedstoneGolem(EntityType<? extends IronGolem> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return IronGolem.createAttributes().add(Attributes.MAX_HEALTH, 140.0).add(Attributes.ATTACK_DAMAGE, 18.0);
        }
    }

}
