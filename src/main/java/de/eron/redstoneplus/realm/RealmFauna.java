package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The wildlife of the realm, the bottom of its food chain. Spark Mites graze on the glowing veins in herds, Lamp
 * Moths live on the light of the old lamps, and packs of Scrap Jackals hunt the mites and dig through ruins.
 * Constructs and Machine-Bound leave them alone; they belong to the realm as much as the machines do.
 */
public final class RealmFauna {
    private RealmFauna() {
    }

    /** Shared by the realm's wildlife: own sounds, the ability animation and gentle despawning. */
    public abstract static class Critter extends PathfinderMob implements RealmAnimated {
        private int abilityStart = -10000;

        protected Critter(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
            this.xpReward = 3;
        }

        protected abstract RealmSounds.Set sounds();

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
        protected SoundEvent getHurtSound(DamageSource source) {
            return this.sounds().hurt().get();
        }

        @Override
        protected SoundEvent getDeathSound() {
            return this.sounds().death().get();
        }

        @Override
        protected void playStepSound(BlockPos pos, BlockState state) {
            this.playSound(this.sounds().step().get(), 0.4F, 0.9F + this.random.nextFloat() * 0.3F);
        }

        @Override
        public int getAmbientSoundInterval() {
            return 200;
        }

        @Override
        public boolean removeWhenFarAway(double distance) {
            return distance > 96 * 96 && this.tickCount > 2400;
        }
    }

    // =====================================================================================================
    /** A copper beetle that grazes on redstone veins in herds. It glows brighter the more it has eaten. */
    public static class SparkMite extends Critter {
        public SparkMite(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 0.28);
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.SPARK_MITE;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
            this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, ScrapJackal.class, 10.0F, 1.2, 1.6));
            this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 5.0F, 1.1, 1.4));
            // grazing: walk to a vein and feed on it
            this.goalSelector.addGoal(4, new RealmJobs.TendGoal(this, 10, 80, s -> s.is(Realm.REDSTONE_VEIN.get()),
                    RealmJobs::sparks, (level, mob, pos, state) -> mob.heal(2.0F)).exposed());
            this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
            this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide() && RealmFx.chance(this, 18)) {
                RealmFx.emit(this, RealmFx.SPARK.get(), 0, 0.35, 0, 0, 0.05, 0);
            }
        }
    }

    // =====================================================================================================
    /** A moth of rusted foil that lives on the light of the old lamps and circles them in flocks. */
    public static class LampMoth extends Critter {
        public LampMoth(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
            this.moveControl = new FlyingMoveControl(this, 20, true);
            this.setNoGravity(true);
        }

        public static AttributeSupplier.Builder attributes() {
            return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                    .add(Attributes.FLYING_SPEED, 0.5);
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.LAMP_MOTH;
        }

        @Override
        protected PathNavigation createNavigation(Level level) {
            FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
            nav.setCanOpenDoors(false);
            nav.setCanFloat(true);
            nav.setCanPassDoors(true);
            return nav;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(1, new PanicGoal(this, 1.8));
            // drawn to anything still giving light: lit lamps, crystals, lanterns
            this.goalSelector.addGoal(3, new RealmJobs.TendGoal(this, 14, 120, RealmJobs::litLamp,
                    (level, mob, pos) -> level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.02),
                    (level, mob, pos, state) -> mob.heal(1.0F)));
            this.goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0));
            this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        }

        @Override
        public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
            return false;
        }

        @Override
        protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level().isClientSide() && RealmFx.chance(this, 10)) {
                RealmFx.emit(this, RealmFx.EMBER.get(), 0, 0.2, -0.1, 0, -0.02, 0);
            }
        }
    }

    // =====================================================================================================
    /**
     * A lean scrap-built dog that hunts in packs. It chases down Spark Mites and Lamp Moths and scavenges the ruins,
     * digging up scraps; it leaves players alone unless one of the pack is hurt.
     */
    public static class ScrapJackal extends Critter {
        private int digs;

        public ScrapJackal(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
            this.xpReward = 5;
        }

        public static AttributeSupplier.Builder attributes() {
            return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.MOVEMENT_SPEED, 0.33)
                    .add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.FOLLOW_RANGE, 24.0);
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.SCRAP_JACKAL;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
            // scavenging: dig at the rubble of ruins, sometimes turning up something useful
            this.goalSelector.addGoal(5, new RealmJobs.TendGoal(this, 12, 70,
                    s -> s.is(Realm.REALMSTONE.get()), RealmJobs::dust,
                    (level, mob, pos, state) -> ((ScrapJackal) mob).dig(level, pos)).where(RealmJobs::rubble));
            this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
            this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
            this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, SparkMite.class, 20, true, false, e -> true));
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LampMoth.class, 40, true, false, e -> true));
        }

        void dig(ServerLevel level, BlockPos pos) {
            if (++this.digs % 3 == 0 && this.random.nextInt(3) == 0) {
                ItemStack find = switch (this.random.nextInt(4)) {
                    case 0 -> new ItemStack(Items.IRON_NUGGET, 1 + this.random.nextInt(3));
                    case 1 -> new ItemStack(Items.REDSTONE, 1 + this.random.nextInt(2));
                    case 2 -> new ItemStack(Realm.REALM_COG.get());
                    default -> new ItemStack(Items.COPPER_INGOT);
                };
                this.spawnAtLocation(find, 0.5F);
            }
        }

        @Override
        public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && this.level() instanceof ServerLevel server) {
                server.broadcastEntityEvent(this, ABILITY_EVENT);
                if (target instanceof LivingEntity prey && !prey.isAlive()) {
                    this.heal(4.0F);
                }
            }
            return hit;
        }
    }
}
