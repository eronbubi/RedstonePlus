package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * The Sealed Reach: where the Wirewrights walled in the things that never kept the Concordance. Its borders are a wall
 * of quarantine plating with warning lamps along the top, gates shut tight where the Grid's lines once ran in, and
 * crimson signs all around the outside telling you to turn back. Inside, under a sky the colour of old blood and a fog
 * that never lifts, walk the Wirewraith and the Maw Engine. The Concordance does not bind them: they hunt everyone,
 * and nobody breaks a rule by fighting them. They never leave the Reach; if one strays past the wall it turns back.
 */
public final class SealedReach {
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, Realm.id("sealed_reach"));
    /** Wall height above the ground. */
    private static final int WALL = 10;

    private SealedReach() {
    }

    /** The creatures of the Reach: outside the Concordance (see {@link RealmRules#lawless}). */
    public interface Lawless {
    }

    static void init() {
    }

    static boolean inReach(Level level, BlockPos pos) {
        return level.getBiome(pos).is(BIOME);
    }

    // ============================================================================================ staying inside
    /** Remembers where it was born, and if it wanders out of the Reach it drops everything and walks back. */
    static final class StayInsideGoal extends Goal {
        private final Monster mob;
        private BlockPos home;
        private int outside;
        private int clock;

        StayInsideGoal(Monster mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.TARGET));
        }

        private boolean out() {
            return !inReach(this.mob.level(), this.mob.blockPosition());
        }

        @Override
        public boolean canUse() {
            if (this.home == null && !this.out()) {
                this.home = this.mob.blockPosition();
            }
            // goals are only polled every other tick: count calls, not ticks
            return this.home != null && ++this.clock % 10 == 0 && this.out();
        }

        @Override
        public boolean canContinueToUse() {
            return this.out();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.mob.setTarget(null);
            this.outside = 0;
            this.mob.getNavigation().moveTo(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5, 1.2);
        }

        @Override
        public void tick() {
            this.mob.setTarget(null);
            if (++this.outside % 40 == 0) {
                this.mob.getNavigation().moveTo(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5, 1.2);
            }
            if (this.outside > 400 && this.mob.level() instanceof ServerLevel level) {
                // it cannot find the way back: the Reach takes it home
                level.sendParticles(ParticleTypes.LARGE_SMOKE, this.mob.getX(), this.mob.getY() + 1, this.mob.getZ(), 30, 0.5, 1, 0.5, 0.02);
                this.mob.teleportTo(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5);
                this.outside = 0;
            }
        }
    }

    /** Shared base: animation event, sounds, no light rules, stays inside. */
    abstract static class Thing extends Monster implements RealmAnimated, Lawless {
        private int abilityStart = -10000;
        protected int abilityCooldown = 100;

        protected Thing(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.xpReward = 25;
        }

        protected abstract RealmSounds.Set sounds();

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(1, new StayInsideGoal(this));
            this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true));
            this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
            this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
            this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }

        @Override
        public float getWalkTargetValue(BlockPos pos, net.minecraft.world.level.LevelReader level) {
            return 0.0F;
        }

        @Override
        public int abilityStart() {
            return this.abilityStart;
        }

        void playAbility() {
            this.level().broadcastEntityEvent(this, ABILITY_EVENT);
            this.playSound(this.sounds().ability().get(), 2.4F, 0.9F + this.random.nextFloat() * 0.2F);
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
        protected void playStepSound(BlockPos pos, BlockState state) {
            this.playSound(this.sounds().step().get(), 0.8F, 0.9F + this.random.nextFloat() * 0.2F);
        }

        @Override
        public int getAmbientSoundInterval() {
            return 160;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.level() instanceof ServerLevel level && this.isAlive()) {
                if (this.abilityCooldown > 0) {
                    this.abilityCooldown--;
                } else if (inReach(level, this.blockPosition())) {
                    LivingEntity target = this.getTarget();
                    if (target != null && target.isAlive() && this.hasLineOfSight(target)) {
                        this.ability(level, target);
                    }
                }
            }
        }

        /** The creature's special move. Sets its own cooldown when it fires. */
        protected abstract void ability(ServerLevel level, LivingEntity target);
    }

    // ============================================================================================ the Wirewraith
    /**
     * A gaunt stilt-walker of tangled wire with a hollow bell for a head. It screams: everyone near goes dark and slow, and
     * its prey is dragged a little closer to its claws.
     */
    public static class Wirewraith extends Thing {
        public Wirewraith(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                    .add(Attributes.ATTACK_DAMAGE, 9.0).add(Attributes.ARMOR, 4.0).add(Attributes.FOLLOW_RANGE, 40.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.STEP_HEIGHT, 1.5);
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.WIREWRAITH;
        }

        @Override
        protected void ability(ServerLevel level, LivingEntity target) {
            double d = this.distanceToSqr(target);
            if (d > 16 * 16 || d < 3 * 3) {
                return;
            }
            this.abilityCooldown = 220 + this.random.nextInt(120);
            this.playAbility();
            for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(14.0), p -> !p.isCreative() && !p.isSpectator())) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0), this);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
            }
            Vec3 pull = this.position().subtract(target.position()).normalize().scale(0.9);
            target.push(pull.x, 0.25, pull.z);
            target.hurtMarked = true;
            level.sendParticles(ParticleTypes.SONIC_BOOM, this.getX(), this.getEyeY(), this.getZ(), 1, 0, 0, 0, 0);
        }
    }

    // ============================================================================================ the Maw Engine
    /** A furnace on four legs that is mostly mouth. It gapes and lunges; its bite holds you and burns. */
    public static class MawEngine extends Thing {
        private int lunging;

        public MawEngine(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder attributes() {
            return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 90.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                    .add(Attributes.ATTACK_DAMAGE, 12.0).add(Attributes.ARMOR, 10.0).add(Attributes.FOLLOW_RANGE, 32.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.STEP_HEIGHT, 1.2);
        }

        @Override
        protected RealmSounds.Set sounds() {
            return RealmSounds.MAW_ENGINE;
        }

        @Override
        protected void ability(ServerLevel level, LivingEntity target) {
            double d = this.distanceToSqr(target);
            if (d > 11 * 11 || d < 4 * 4 || !this.onGround()) {
                return;
            }
            this.abilityCooldown = 120 + this.random.nextInt(80);
            this.playAbility();
            this.lunging = 24;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (this.lunging > 0 && this.level() instanceof ServerLevel level) {
                this.lunging--;
                LivingEntity target = this.getTarget();
                if (this.lunging == 14 && target != null) {
                    // after gaping, it springs
                    Vec3 leap = target.position().subtract(this.position()).normalize().scale(1.35);
                    this.setDeltaMovement(leap.x, 0.45, leap.z);
                    this.hasImpulse = true;
                }
                if (this.lunging < 14 && target != null && this.getBoundingBox().inflate(0.6).intersects(target.getBoundingBox())) {
                    this.doHurtTarget(target);
                    this.lunging = 0;
                }
                if (this.lunging % 3 == 0) {
                    level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 1.2, this.getZ(), 4, 0.5, 0.3, 0.5, 0.02);
                }
            }
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3), this);
                living.igniteForSeconds(3.0F);
            }
            return hit;
        }

        @Override
        public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data) {
            this.abilityCooldown = 60;
            return super.finalizeSpawn(level, difficulty, reason, data);
        }
    }

    // ============================================================================================ the walls
    private static final int[][] NEAR = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
    private static final int[][] FAR = {{5, 0}, {-5, 0}, {0, 5}, {0, -5}};

    private static boolean sealedAt(WorldGenLevel level, int x, int y, int z) {
        return level.getBiome(new BlockPos(x, y, z)).is(BIOME);
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    /**
     * One column of the Reach's border. Inside the Reach and within two blocks of its edge: the wall. Just outside it:
     * now and then a warning sign, and thorns along the foot of the wall. Returns true if it built anything.
     */
    static boolean column(WorldGenLevel level, int x, int z, int top) {
        int y = top - 1;
        boolean inside = sealedAt(level, x, y, z);
        if (inside) {
            boolean edge = false;
            for (int[] o : NEAR) {
                // the wall closes the Reach off from the rest of the realm; where it meets the void, the artery's rim is wall enough
                if (!sealedAt(level, x + o[0], y, z + o[1]) && Artery.s(x + o[0], z + o[1]) > 0.02) {
                    edge = true;
                    break;
                }
            }
            if (!edge) {
                return false;
            }
            wall(level, x, z, top);
            return true;
        }
        // outside: is the wall a few blocks off?
        int[] toward = null;
        for (int[] o : FAR) {
            if (sealedAt(level, x + o[0], y, z + o[1])) {
                toward = o;
                break;
            }
        }
        if (toward == null) {
            return false;
        }
        for (int[] o : NEAR) {
            if (sealedAt(level, x + o[0], y, z + o[1])) {
                return false; // too close: this is the foot of the wall itself
            }
        }
        if (!level.getFluidState(new BlockPos(x, top, z)).isEmpty() || !level.getBlockState(new BlockPos(x, top - 1, z)).isSolid()
                || !level.getBlockState(new BlockPos(x, top, z)).canBeReplaced()) {
            return false;
        }
        long h = RealmCities.hash(level.getSeed(), x, z, 61);
        if (Math.floorMod(h, 23) == 0) {
            sign(level, x, top, z, toward, h);
            return true;
        }
        if (Math.floorMod(h >>> 8, 4) == 0) {
            set(level, x, top, z, Realm.BRIAR_THORNS.get().defaultBlockState());
            return true;
        }
        return false;
    }

    private static void wall(WorldGenLevel level, int x, int z, int top) {
        long h = RealmCities.hash(level.getSeed(), x, z, 59);
        // where a lattice line of the Grid runs into the Reach, and every so often along the way: a sealed gate
        boolean gate = Grid.onNorthSouthLine(x) || Grid.onEastWestLine(z) || Grid.onNorthSouthLine(x + 1) || Grid.onNorthSouthLine(x - 1)
                || Grid.onEastWestLine(z + 1) || Grid.onEastWestLine(z - 1);
        BlockState plating = Realm.QUARANTINE_PLATING.get().defaultBlockState();
        BlockState rust = Realm.RUST_PLATING.get().defaultBlockState();
        // a footing down to solid ground, then the wall
        for (int y = top - 1; y > top - 6; y--) {
            BlockState below = level.getBlockState(new BlockPos(x, y, z));
            if (below.isSolid() && below.getFluidState().isEmpty()) {
                break;
            }
            set(level, x, y, z, Realm.DEEP_REALMSTONE.get().defaultBlockState());
        }
        for (int i = 0; i < WALL; i++) {
            int y = top + i;
            BlockState b;
            if (gate && i < 4) {
                b = Realm.LOCKDOWN_GATE.get().defaultBlockState();
            } else if (i == WALL - 1) {
                b = Realm.CHISELED_REALMSTONE_BRICKS.get().defaultBlockState();
            } else if (i % 4 == 3) {
                b = rust;
            } else {
                b = plating;
            }
            set(level, x, y, z, b);
        }
        // crenellations, and a warning lamp kept lit every few blocks
        if (Math.floorMod(x + z, 2) == 0) {
            set(level, x, top + WALL, z, Realm.CHISELED_REALMSTONE_BRICKS.get().defaultBlockState());
        }
        if (Math.floorMod(h, 9) == 0) {
            set(level, x, top + WALL - 1, z, Blocks.REDSTONE_BLOCK.defaultBlockState());
            set(level, x, top + WALL, z, ModRegistry.INSTANT_LAMP.get().defaultBlockState().setValue(SimpleBlocks.Lamp.LIT, true));
        }
    }

    /** The signs: crimson, glowing red letters, facing away from the wall. */
    private static final int SIGNS = 4;

    private static void sign(WorldGenLevel level, int x, int y, int z, int[] toward, long h) {
        float yawTowardWall = (float) (Mth.atan2(-toward[0], toward[1]) * Mth.RAD_TO_DEG);
        int rotation = RotationSegment.convertToSegment(yawTowardWall + 180.0F);
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, Blocks.CRIMSON_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            int which = (int) Math.floorMod(h >>> 16, (long) SIGNS);
            SignText text = new SignText().setColor(DyeColor.RED).setHasGlowingText(true);
            for (int line = 0; line < 4; line++) {
                text = text.setMessage(line, Component.translatable("quarantine.redstoneplus.sign." + which + "." + line));
            }
            // during world generation the sign has no level yet, and its setters would try to notify one: load it as saved data instead
            var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            net.minecraft.nbt.Tag encoded = SignText.DIRECT_CODEC.encodeStart(ops, text).getOrThrow();
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.put("front_text", encoded);
            tag.put("back_text", encoded.copy());
            tag.putBoolean("is_waxed", true);
            sign.loadWithComponents(tag, level.registryAccess());
        }
    }
}
