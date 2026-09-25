package de.eron.redstoneplus.entity;

import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * TNT from the FreezeTnter: it never explodes and has no gravity. Only water currents and pistons move it,
 * explosions don't push it. Passing through an Activator Net turns it into normal primed TNT.
 */
public class FrozenTntEntity extends PrimedTnt {
    /** Kept at a value where the TNT renderer draws no white flash. */
    public static final int FROZEN_FUSE = 86;

    public FrozenTntEntity(EntityType<? extends PrimedTnt> type, Level level) {
        super(type, level);
        this.setFuse(FROZEN_FUSE);
    }

    @Override
    public void tick() {
        this.setFuse(FROZEN_FUSE);
        boolean inWater = this.updateInWaterStateAndDoFluidPushing();
        Vec3 motion = this.getDeltaMovement();
        if (inWater || motion.lengthSqr() > 1.0E-7) {
            this.move(MoverType.SELF, motion);
            // strong drag: once the current stops pushing, the TNT stops too
            this.setDeltaMovement(motion.scale(inWater ? 0.8 : 0.5));
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }
        this.checkInsideBlocks();
        if (!this.level().isClientSide() && this.isInsideActivatorNet()) {
            this.activate();
        }
    }

    private boolean isInsideActivatorNet() {
        AABB box = this.getBoundingBox().deflate(1.0E-3);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (this.level().getBlockState(pos).is(ModRegistry.ACTIVATOR_NET.get())) {
                return true;
            }
        }
        return false;
    }

    /** Replaces this frozen TNT with a normal, burning one at the same spot. */
    public void activate() {
        if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        PrimedTnt tnt = new PrimedTnt(level, this.getX(), this.getY(), this.getZ(), null);
        tnt.setDeltaMovement(this.getDeltaMovement());
        tnt.setFuse(80);
        level.addFreshEntity(tnt);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.5, this.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        this.discard();
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return true;
    }
}
