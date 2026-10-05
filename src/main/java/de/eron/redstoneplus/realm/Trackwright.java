package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

import java.util.EnumSet;

/**
 * The Trackwright: the last of the Wirewrights' road builders, a crab-legged paving engine with a spool of lightline on
 * its back. It keeps the Grid alive. Near a lattice line with a gap in it, it walks to the broken end and lays tile after
 * tile until the line is whole again. Where the lines are whole, it starts new branch lines off them, at right angles,
 * and lays them out across the land until they meet another line or it has laid enough; at the end of a branch it builds a
 * waystop (a little platform and a beacon post). Every branch is a new way a light cycle can turn down.
 * <p>
 * It keeps the Concordance like every creature of the realm: it hunts only rule-breakers, and fights back when struck.
 * Its work can be stopped with the mobGriefing game rule.
 */
public class Trackwright extends PathfinderMob implements RealmAnimated {
    private static final DustParticleOptions LIGHT = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.1F), 1.0F);
    private int abilityStart = -10000;

    // the job in hand: the next column to pave, the way the line runs, how many tiles are left, and what kind of job it is
    private int jobX;
    private int jobZ;
    private int jobY = Integer.MIN_VALUE;
    private Direction jobDir = Direction.NORTH;
    private int jobLeft;
    private int jobLaid;
    private boolean branch;

    public Trackwright(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.ARMOR, 6.0).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        this.goalSelector.addGoal(4, new BuildGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, RealmRules::condemned));
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, net.minecraft.world.level.LevelReader level) {
        return 0.0F;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false; // builders stay at their work
    }

    // ---------------------------------------------------------------------------------------- animation and sounds
    @Override
    public int abilityStart() {
        return this.abilityStart;
    }

    void playAbility() {
        this.level().broadcastEntityEvent(this, ABILITY_EVENT);
        this.playSound(RealmSounds.TRACKWRIGHT.ability().get(), 1.2F, 0.9F + this.random.nextFloat() * 0.2F);
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
        return RealmSounds.TRACKWRIGHT.ambient().get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RealmSounds.TRACKWRIGHT.hurt().get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RealmSounds.TRACKWRIGHT.death().get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(RealmSounds.TRACKWRIGHT.step().get(), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    // ---------------------------------------------------------------------------------------- saving the job
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.jobLeft > 0) {
            CompoundTag job = new CompoundTag();
            job.putInt("X", this.jobX);
            job.putInt("Z", this.jobZ);
            job.putInt("Y", this.jobY);
            job.putInt("Dir", this.jobDir.get2DDataValue());
            job.putInt("Left", this.jobLeft);
            job.putInt("Laid", this.jobLaid);
            job.putBoolean("Branch", this.branch);
            tag.put("Job", job);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Job")) {
            CompoundTag job = tag.getCompound("Job");
            this.jobX = job.getInt("X");
            this.jobZ = job.getInt("Z");
            this.jobY = job.getInt("Y");
            this.jobDir = Direction.from2DDataValue(job.getInt("Dir"));
            this.jobLeft = job.getInt("Left");
            this.jobLaid = job.getInt("Laid");
            this.branch = job.getBoolean("Branch");
        }
    }

    // ---------------------------------------------------------------------------------------- planning
    private boolean tile(ServerLevel level, int x, int z) {
        return Grid.surfaceTileY(level, x, z) != Integer.MIN_VALUE;
    }

    /** Looks for work: first a gap in a nearby lattice line, otherwise a place to start a branch. */
    private boolean plan(ServerLevel level) {
        if (!net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level, this)) {
            return false;
        }
        boolean gapFirst = this.random.nextFloat() < 0.65F;
        return gapFirst ? this.planGap(level) || this.planBranch(level) : this.planBranch(level) || this.planGap(level);
    }

    /** The broken end of a lattice line within reach: a tile with no tile after it, pointing away from its line. */
    private boolean planGap(ServerLevel level) {
        int bx = this.blockPosition().getX();
        int bz = this.blockPosition().getZ();
        int best = Integer.MAX_VALUE;
        boolean found = false;
        int lineZ = Grid.line(Grid.cell(bz));
        int lineX = Grid.line(Grid.cell(bx));
        if (Math.abs(lineZ - bz) <= 40) {
            for (int x = bx - 40; x <= bx + 40; x++) {
                if (!level.isLoaded(new BlockPos(x, 0, lineZ))) {
                    continue;
                }
                for (Direction d : new Direction[]{Direction.EAST, Direction.WEST}) {
                    int nx = x + d.getStepX();
                    if (this.tile(level, x, lineZ) && !this.tile(level, nx, lineZ) && level.isLoaded(new BlockPos(nx, 0, lineZ))
                            && !Grid.sealed(level, new BlockPos(nx, 64, lineZ))) {
                        int dist = Math.abs(x - bx) + Math.abs(lineZ - bz);
                        if (dist < best) {
                            best = dist;
                            this.startJob(nx, lineZ, Grid.surfaceTileY(level, x, lineZ), d, 96, false);
                            found = true;
                        }
                    }
                }
            }
        }
        if (Math.abs(lineX - bx) <= 40) {
            for (int z = bz - 40; z <= bz + 40; z++) {
                if (!level.isLoaded(new BlockPos(lineX, 0, z))) {
                    continue;
                }
                for (Direction d : new Direction[]{Direction.SOUTH, Direction.NORTH}) {
                    int nz = z + d.getStepZ();
                    if (this.tile(level, lineX, z) && !this.tile(level, lineX, nz) && level.isLoaded(new BlockPos(lineX, 0, nz))
                            && !Grid.sealed(level, new BlockPos(lineX, 64, nz))) {
                        int dist = Math.abs(z - bz) + Math.abs(lineX - bx);
                        if (dist < best) {
                            best = dist;
                            this.startJob(lineX, nz, Grid.surfaceTileY(level, lineX, z), d, 96, false);
                            found = true;
                        }
                    }
                }
            }
        }
        return found;
    }

    /**
     * A straight stretch of line nearby with open ground to one side and no other branch close by: a new line starts
     * there at a right angle.
     */
    private boolean planBranch(ServerLevel level) {
        BlockPos at = this.blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = at.getX() + this.random.nextInt(25) - 12;
            int z = at.getZ() + this.random.nextInt(25) - 12;
            if (!this.tile(level, x, z)) {
                continue;
            }
            Direction along = this.tile(level, x + 1, z) && this.tile(level, x - 1, z) ? Direction.EAST
                    : this.tile(level, x, z + 1) && this.tile(level, x, z - 1) ? Direction.SOUTH : null;
            if (along == null) {
                continue;
            }
            Direction side = this.random.nextBoolean() ? along.getClockWise() : along.getCounterClockWise();
            // not next to another branch: nothing off the line for 6 tiles either way, on this side
            boolean clear = true;
            for (int k = -6; k <= 6 && clear; k++) {
                int cx = x + along.getStepX() * k + side.getStepX();
                int cz = z + along.getStepZ() * k + side.getStepZ();
                clear = !this.tile(level, cx, cz);
            }
            int sx = x + side.getStepX();
            int sz = z + side.getStepZ();
            if (!clear || Grid.sealed(level, new BlockPos(sx, 64, sz))) {
                continue;
            }
            this.startJob(sx, sz, Grid.surfaceTileY(level, x, z), side, 20 + this.random.nextInt(44), true);
            return true;
        }
        return false;
    }

    private void startJob(int x, int z, int refY, Direction dir, int length, boolean branch) {
        this.jobX = x;
        this.jobZ = z;
        this.jobY = refY;
        this.jobDir = dir;
        this.jobLeft = length;
        this.jobLaid = 0;
        this.branch = branch;
    }

    private void endJob(ServerLevel level, boolean waystop) {
        if (waystop && this.branch && this.jobLaid >= 8) {
            this.waystop(level, this.jobX - this.jobDir.getStepX(), this.jobZ - this.jobDir.getStepZ());
        }
        this.jobLeft = 0;
    }

    /** At the end of a branch: a little platform around the last tile and a beacon post beside it. */
    private void waystop(ServerLevel level, int x, int z) {
        int y = Grid.surfaceTileY(level, x, z);
        if (y == Integer.MIN_VALUE) {
            return;
        }
        Direction side = this.jobDir.getClockWise();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                BlockPos p = new BlockPos(x + a, y, z + b);
                BlockState s = level.getBlockState(p);
                if (Grid.pavable(level, p, s)) {
                    level.setBlock(p, Realm.REALMSTONE_BRICKS.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        BlockPos post = new BlockPos(x + side.getStepX() * 2, y + 1, z + side.getStepZ() * 2);
        if (level.getBlockState(post).canBeReplaced() && level.getBlockState(post.above()).canBeReplaced()
                && level.getBlockState(post.above(2)).canBeReplaced() && level.getBlockState(post.below()).isSolid()) {
            level.setBlock(post, Realm.RUST_PLATING.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(post.above(), Realm.RUST_PLATING.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(post.above(2), Realm.GRID_BEACON.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.sendParticles(ParticleTypes.END_ROD, x + 0.5, y + 1.5, z + 0.5, 30, 1.0, 0.8, 1.0, 0.05);
    }

    // ---------------------------------------------------------------------------------------- building
    /** Walks to the next column of its job and paves it, tile by tile. */
    static final class BuildGoal extends Goal {
        private final Trackwright mob;
        private int nextPlan;
        private int work;
        private int walking;

        BuildGoal(Trackwright mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean calm() {
            return this.mob.getTarget() == null && !this.mob.isPassenger();
        }

        @Override
        public boolean canUse() {
            if (!this.calm() || !(this.mob.level() instanceof ServerLevel level)) {
                return false;
            }
            if (this.mob.jobLeft > 0) {
                return true;
            }
            if (this.mob.tickCount < this.nextPlan) {
                return false;
            }
            this.nextPlan = this.mob.tickCount + 160 + this.mob.random.nextInt(160);
            return this.mob.plan(level);
        }

        @Override
        public boolean canContinueToUse() {
            return this.mob.jobLeft > 0 && this.calm();
        }

        @Override
        public void start() {
            this.work = 0;
            this.walking = 0;
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (!(this.mob.level() instanceof ServerLevel level)) {
                return;
            }
            Trackwright m = this.mob;
            BlockPos column = new BlockPos(m.jobX, 0, m.jobZ);
            if (!level.isLoaded(column)) {
                m.endJob(level, false);
                return;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, m.jobX, m.jobZ);
            double tx = m.jobX + 0.5;
            double tz = m.jobZ + 0.5;
            m.getLookControl().setLookAt(tx, top, tz);
            double dx = m.getX() - tx;
            double dz = m.getZ() - tz;
            if (dx * dx + dz * dz > 3.2 * 3.2) {
                this.work = 0;
                if (++this.walking > 240) {
                    m.endJob(level, true); // could not get there: the branch ends where it is
                    return;
                }
                if (this.walking % 10 == 1) {
                    // walk up beside the column, on the line already laid
                    m.getNavigation().moveTo(tx - m.jobDir.getStepX() * 1.5, top, tz - m.jobDir.getStepZ() * 1.5, 1.0);
                }
                return;
            }
            this.walking = 0;
            m.getNavigation().stop();
            if (++this.work == 1) {
                m.playAbility();
            }
            if (this.work < 16) {
                return;
            }
            this.work = 0;
            if (Grid.surfaceTileY(level, m.jobX, m.jobZ) != Integer.MIN_VALUE && m.jobLaid > 0) {
                m.endJob(level, false); // met another line: joined up
                return;
            }
            int y = Grid.lay(level, m.jobX, m.jobZ, m.jobY);
            if (y == Integer.MIN_VALUE) {
                m.endJob(level, true);
                return;
            }
            level.playSound(null, m.jobX + 0.5, y + 1, m.jobZ + 0.5, RealmSounds.LIGHTLINE_LAY.get(), SoundSource.NEUTRAL, 1.0F,
                    0.9F + m.random.nextFloat() * 0.2F);
            level.sendParticles(LIGHT, m.jobX + 0.5, y + 1.1, m.jobZ + 0.5, 16, 0.35, 0.1, 0.35, 0.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.jobX + 0.5, y + 1.1, m.jobZ + 0.5, 8, 0.3, 0.1, 0.3, 0.15);
            m.jobY = y;
            m.jobLaid++;
            m.jobX += m.jobDir.getStepX();
            m.jobZ += m.jobDir.getStepZ();
            if (--m.jobLeft <= 0) {
                m.endJob(level, true);
            } else if (Grid.surfaceTileY(level, m.jobX, m.jobZ) != Integer.MIN_VALUE) {
                m.endJob(level, false); // the next column is a line already: joined up
            }
        }
    }
}
