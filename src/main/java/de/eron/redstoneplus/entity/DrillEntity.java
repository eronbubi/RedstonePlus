package de.eron.redstoneplus.entity;

import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A drivable drill. Right click to get in, W/S to drive, it turns where you look. While driving forward it bores
 * a 3x3 tunnel in front of it; look down (steeper than 35 degrees) to dig down, look up to dig up.
 * Sneak to get out, hit it to pick it up again.
 */
public class DrillEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DRILLING = SynchedEntityData.defineId(DrillEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double SPEED = 0.2;
    private static final int DIG_INTERVAL = 3;

    /** Client only: rotation of the drill bit for the renderer. */
    public float bitAngle;
    public float bitAngleO;

    public DrillEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DRILLING, false);
    }

    public boolean isDrilling() {
        return this.entityData.get(DRILLING);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || this.isVehicle()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide()) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoved()) {
            return true;
        }
        if (source.getEntity() instanceof Player player) {
            if (!player.getAbilities().instabuild) {
                this.spawnAtLocation(ModRegistry.DRILL.get());
            }
            this.ejectPassengers();
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity driver = this.getControllingPassenger();
        float forward = driver == null ? 0.0F : driver.zza;

        if (this.isControlledByLocalInstance()) {
            this.drive(driver, forward);
        }

        if (this.level() instanceof ServerLevel level) {
            boolean drilling = driver != null && forward > 0.0F;
            if (drilling != this.isDrilling()) {
                this.entityData.set(DRILLING, drilling);
            }
            if (drilling && this.tickCount % DIG_INTERVAL == 0) {
                this.dig(level, driver);
            }
        } else {
            this.bitAngleO = this.bitAngle;
            if (this.isDrilling()) {
                this.bitAngle += 40.0F;
            }
        }
    }

    /** Movement runs on whoever controls the drill: the driving player's client, or the server when empty. */
    private void drive(@Nullable LivingEntity driver, float forward) {
        Vec3 motion = this.getDeltaMovement();
        double y = motion.y;
        double x = 0;
        double z = 0;
        if (driver != null) {
            this.setYRot(driver.getYRot());
            this.yRotO = this.getYRot();
            float yaw = this.getYRot() * Mth.DEG_TO_RAD;
            double speed = forward > 0 ? SPEED : forward < 0 ? -SPEED * 0.6 : 0;
            x = -Mth.sin(yaw) * speed;
            z = Mth.cos(yaw) * speed;
            // climb the tunnel it bores upwards
            if (forward > 0 && driver.getXRot() < -35.0F) {
                y = Math.max(y, 0.15);
            }
        }
        y -= this.getGravity();
        this.setDeltaMovement(x, y, z);
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }
    }

    /** Breaks a 3x3 wall in front (or below / above when the driver looks down / up). */
    private void dig(ServerLevel level, LivingEntity driver) {
        Direction facing = Direction.fromYRot(this.getYRot());
        Direction side = facing.getClockWise();
        BlockPos base = this.blockPosition();
        float pitch = driver.getXRot();
        boolean any = false;
        for (int w = -1; w <= 1; w++) {
            for (int h = 0; h <= 2; h++) {
                BlockPos front = base.relative(facing).relative(side, w).above(h);
                any |= this.breakBlock(level, front, driver);
            }
            for (int d = 0; d <= 2; d++) {
                if (pitch > 35.0F) {
                    any |= this.breakBlock(level, base.below().relative(facing, d).relative(side, w), driver);
                } else if (pitch < -35.0F) {
                    any |= this.breakBlock(level, base.above(3).relative(facing, d).relative(side, w), driver);
                }
            }
        }
        if (any || this.tickCount % 12 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.NEUTRAL, 0.5F, 0.6F + this.random.nextFloat() * 0.2F);
        }
    }

    private boolean breakBlock(ServerLevel level, BlockPos pos, LivingEntity driver) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        return level.destroyBlock(pos, true, driver);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 behind = Vec3.directionFromRotation(0.0F, this.getYRot()).scale(-1.2);
        Vec3 spot = this.position().add(behind);
        return this.level().noCollision(passenger, passenger.getBoundingBox().move(spot.subtract(passenger.position())))
                ? spot : super.getDismountLocationForPassenger(passenger);
    }
}
