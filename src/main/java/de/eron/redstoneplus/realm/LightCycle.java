package de.eron.redstoneplus.realm;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;

/**
 * The light cycle: a redstone racing cycle every visitor of the realm is given (see {@link CycleBond}).
 * <p>
 * Off the Grid it drives like a fast cart: W/S, it turns where you look. Roll onto a lightline and it locks on: it
 * follows the line on its own, at up to {@link #TRACK_MAX} blocks a tick (hold jump for more), stays centred on it, and
 * glides up and down steps on its grav-lift. At a junction it goes straight on, unless you tapped A or D before it (or
 * the way on is blocked, then it takes the turn you are looking towards). A dead end drops it off the line. Hold S to
 * brake; hold S standing still to turn around.
 * <p>
 * In the realm it brakes by itself before the Great Bell tolls and holds still until the toll has passed: moving
 * then would break the third rule.
 * <p>
 * Movement runs on the driver's client (like a boat). While someone drives it, the server trusts the positions it is
 * sent (it does no collisions of its own then), which is what lets the cycle ride its grav-lift over steep steps.
 */
public class LightCycle extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(LightCycle.class, EntityDataSerializers.OPTIONAL_UUID);

    /** Blocks per tick. */
    public static final double FREE_MAX = 0.55;
    public static final double TRACK_MAX = 1.4;
    public static final double BOOST_MAX = 2.2;
    private static final double REVERSE_MAX = 0.15;
    private static final double HALF_WIDTH = 0.45;
    /** The cycle starts braking this many ticks before the Great Bell tolls, and holds still until the toll is past. */
    private static final int BELL_BRAKE = 40;

    // ---- driving state (lives on whichever side drives: the driver's client, or the server when nobody rides)
    private double speed;
    @Nullable
    private Direction trackDir;
    private int queuedTurn;
    private int queuedTicks;
    private long lastDecision = Long.MIN_VALUE;
    private int reverseHold;
    private double fallSpeed;

    // ---- client side: smoothing for other players' cycles, and the visuals
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    public float wheelAngle;
    public float wheelAngleO;
    public float lean;
    public float leanO;
    /** Recent positions for the light trail (client only), newest last. */
    public final Deque<Vec3> trail = new ArrayDeque<>();
    private Vec3 lastClientPos;
    private float lastYaw;

    public LightCycle(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
    }

    @Nullable
    public UUID owner() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public void setOwner(@Nullable UUID owner) {
        this.entityData.set(OWNER, Optional.ofNullable(owner));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.setOwner(tag.getUUID("Owner"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = this.owner();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    // ============================================================================================ basics
    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false; // the grav-lift catches every fall, the rider's too
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    protected void onBelowWorld() {
        this.discard(); // the bond notices and brings a new one to its rider
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
    public ItemStack getPickResult() {
        return new ItemStack(Realm.CYCLE_KEY.get());
    }

    public boolean onTrack() {
        return this.trackDir != null;
    }

    @Nullable
    public Direction trackDirection() {
        return this.trackDir;
    }

    public double speed() {
        return this.speed;
    }

    /** -1 a left turn is queued, 1 right, 0 none. */
    public int queuedTurn() {
        return this.queuedTicks > 0 ? this.queuedTurn : 0;
    }

    /** True while the cycle holds still for the Great Bell. */
    public boolean holdingForBell() {
        if (!RealmRules.inRealm(this.level())) {
            return false;
        }
        long time = this.level().getGameTime();
        return RealmRules.ticksToToll(time) <= BELL_BRAKE || RealmRules.ticksSinceToll(time) <= RealmRules.TOLL_STILL + 5;
    }

    // ============================================================================================ riding
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || this.isVehicle()) {
            return InteractionResult.PASS;
        }
        UUID owner = this.owner();
        if (owner != null && !owner.equals(player.getUUID()) && !player.getAbilities().instabuild) {
            if (!this.level().isClientSide()) {
                player.displayClientMessage(Component.translatable("message.redstoneplus.cycle_not_yours").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        if (!this.level().isClientSide()) {
            if (owner == null && player instanceof ServerPlayer sp) {
                CycleBond.claim(sp, this);
            }
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer player && player.isShiftKeyDown() && !this.isVehicle()) {
            UUID owner = this.owner();
            if (player.getUUID().equals(owner)) {
                CycleBond.park(player, this); // the owner puts it away; the key brings it back
                return true;
            }
            if (player.getAbilities().instabuild) {
                CycleBond.derez(this);
                return true;
            }
        }
        return false;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        for (float side : new float[]{90.0F, -90.0F, 180.0F}) {
            Vec3 spot = this.position().add(Vec3.directionFromRotation(0.0F, this.getYRot() + side).scale(1.2));
            if (this.level().noCollision(passenger, passenger.getBoundingBox().move(spot.subtract(passenger.position())))) {
                return spot;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ============================================================================================ ticking
    @Override
    public void tick() {
        super.tick();
        LivingEntity driver = this.getControllingPassenger();
        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            if (driver != null) {
                this.drive(driver);
            } else {
                this.coast();
            }
        } else if (this.lerpSteps > 0) {
            // somebody else's cycle: glide to where the server says it is
            double f = 1.0 / this.lerpSteps;
            this.setPos(this.getX() + (this.lerpX - this.getX()) * f, this.getY() + (this.lerpY - this.getY()) * f,
                    this.getZ() + (this.lerpZ - this.getZ()) * f);
            this.setYRot(this.getYRot() + (float) Mth.wrapDegrees(this.lerpYRot - this.getYRot()) * (float) f);
            this.lerpSteps--;
        }
        if (this.level() instanceof ServerLevel server) {
            // while someone drives it on a lightline, the server takes the driver's word for where the cycle is (see the class
            // comment); off the lines it checks collisions like any vehicle, so plates, tripwires and portals work as usual
            this.noPhysics = driver != null && this.overLightline();
            if (this.tickCount % 40 == 0) {
                CycleBond.checkCycle(server, this);
            }
        } else {
            this.clientVisuals();
        }
    }

    /** A lightline in the column under the cycle, or in the one it is climbing towards. */
    private boolean overLightline() {
        int y = (int) Math.floor(this.getY()) - 1;
        BlockPos at = this.blockPosition();
        if (this.tileAt(at, y) != Integer.MIN_VALUE) {
            return true;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (this.tileAt(at.relative(d), y) != Integer.MIN_VALUE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = Math.max(steps, 3);
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float) this.lerpYRot : this.getYRot();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        this.resetDriving();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        this.resetDriving();
    }

    /** A new ride starts from standstill, off the line, with nothing queued (that state only lived on the last driver's client). */
    private void resetDriving() {
        this.speed = 0;
        this.trackDir = null;
        this.queuedTurn = 0;
        this.queuedTicks = 0;
        this.reverseHold = 0;
        this.fallSpeed = 0;
        this.lastDecision = Long.MIN_VALUE;
    }

    /** Nobody rides it: it rolls to a stop and sits on the ground. */
    private void coast() {
        this.trackDir = null;
        this.noPhysics = false;
        this.speed *= 0.8;
        if (Math.abs(this.speed) < 0.01) {
            this.speed = 0;
        }
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(-Mth.sin(yaw) * this.speed, motion.y - this.getGravity(), Mth.cos(yaw) * this.speed);
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }
    }

    private void drive(LivingEntity driver) {
        float forward = driver.zza;
        float strafe = driver.xxa;
        boolean boost = this.level().isClientSide() && de.eron.redstoneplus.realm.client.CycleClient.boostHeld();
        boolean hold = this.holdingForBell();
        if (strafe > 0.1F) {
            this.queuedTurn = -1;
            this.queuedTicks = 30;
        } else if (strafe < -0.1F) {
            this.queuedTurn = 1;
            this.queuedTicks = 30;
        } else if (this.queuedTicks > 0) {
            this.queuedTicks--;
        }
        if (this.trackDir != null) {
            this.driveTrack(driver, forward, boost, hold);
        } else {
            this.driveFree(driver, forward, hold);
        }
    }

    private void approach(double target, double accel, double brake) {
        if (this.speed < target) {
            this.speed = Math.min(target, this.speed + accel);
        } else {
            this.speed = Math.max(target, this.speed - brake);
        }
    }

    /** True if the chunks ahead have not arrived yet: the cycle waits for the world instead of falling into nothing. */
    private boolean worldAheadMissing(Vec3 dir) {
        for (int d = 12; d <= 24; d += 12) {
            BlockPos ahead = BlockPos.containing(this.getX() + dir.x * d, this.getY(), this.getZ() + dir.z * d);
            if (!this.level().hasChunkAt(ahead)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------- off the Grid
    private void driveFree(LivingEntity driver, float forward, boolean hold) {
        this.noPhysics = false;
        float diff = Mth.wrapDegrees(driver.getYRot() - this.getYRot());
        float maxTurn = (float) Mth.clamp(14.0 - Math.abs(this.speed) * 10.0, 5.0, 14.0);
        this.setYRot(this.getYRot() + Mth.clamp(diff, -maxTurn, maxTurn));
        double target = forward > 0 ? FREE_MAX : forward < 0 ? -REVERSE_MAX : 0;
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 heading = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        boolean stop = hold || this.worldAheadMissing(heading);
        if (stop) {
            target = 0;
        }
        this.approach(target, 0.025, stop ? 0.08 : forward < 0 && this.speed > 0 ? 0.05 : 0.015);
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(heading.x * this.speed, motion.y - this.getGravity(), heading.z * this.speed);
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.horizontalCollision) {
            this.speed *= 0.5;
        }
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }
        if (this.speed > 0.04) {
            this.tryLockOn(driver);
        }
    }

    /** Rolling onto a lightline: lock onto it along the axis closest to the way the cycle faces. */
    private void tryLockOn(LivingEntity driver) {
        BlockPos under = BlockPos.containing(this.getX(), this.getY() - 0.2, this.getZ());
        if (!Grid.isTile(this.level().getBlockState(under))) {
            return;
        }
        Direction facing = Direction.fromYRot(this.getYRot());
        Direction chosen = null;
        for (Direction d : new Direction[]{facing, facing.getClockWise(), facing.getCounterClockWise()}) {
            float off = Math.abs(Mth.wrapDegrees(d.toYRot() - this.getYRot()));
            if (off > 55.0F) {
                continue;
            }
            if (this.tileAt(under.relative(d), under.getY()) != Integer.MIN_VALUE
                    || this.tileAt(under.relative(d.getOpposite()), under.getY()) != Integer.MIN_VALUE) {
                chosen = d;
                break;
            }
        }
        if (chosen == null) {
            return;
        }
        this.trackDir = chosen;
        this.lastDecision = Long.MIN_VALUE;
        this.fallSpeed = 0;
        double x = this.getX();
        double z = this.getZ();
        if (chosen.getAxis() == Direction.Axis.Z) {
            x = under.getX() + 0.5;
        } else {
            z = under.getZ() + 0.5;
        }
        this.setPos(x, under.getY() + 1.0, z);
        this.setYRot(chosen.toYRot());
        this.speed = Math.max(this.speed, 0.2);
        this.noPhysics = true;
    }

    // ---------------------------------------------------------------------------------------- on the Grid
    private int tileAt(BlockPos column, int nearY) {
        return Grid.tileY(this.level(), column.getX(), column.getZ(), nearY);
    }

    /** The tile in that column the cycle can ride onto: there is room for it on top. MIN_VALUE if none, or if it is blocked. */
    private int openTileAt(BlockPos column, int nearY) {
        int y = this.tileAt(column, nearY);
        if (y == Integer.MIN_VALUE) {
            return y;
        }
        net.minecraft.world.phys.AABB box = this.getType().getDimensions().makeBoundingBox(column.getX() + 0.5, y + 1.0, column.getZ() + 0.5).deflate(0.07);
        return this.level().noCollision(this, box) ? y : Integer.MIN_VALUE;
    }

    private void driveTrack(LivingEntity driver, float forward, boolean boost, boolean hold) {
        this.noPhysics = true;
        Direction dir = this.trackDir;
        // braking, turning around, and the target speed
        double target;
        if (forward > 0) {
            target = boost ? BOOST_MAX : TRACK_MAX;
        } else if (forward < 0) {
            target = 0;
        } else {
            target = Math.min(this.speed, TRACK_MAX) * 0.985;
        }
        Vec3 heading = new Vec3(dir.getStepX(), 0, dir.getStepZ());
        boolean stop = hold || this.worldAheadMissing(heading);
        if (stop) {
            target = 0;
        }
        this.approach(target, boost ? 0.05 : 0.035, stop ? 0.12 : forward < 0 ? 0.09 : 0.01);
        if (forward < 0 && this.speed < 0.01) {
            if (++this.reverseHold > 10) {
                this.reverseHold = 0;
                this.trackDir = dir.getOpposite();
                this.lastDecision = Long.MIN_VALUE;
                this.setYRot(this.trackDir.toYRot());
                return;
            }
        } else {
            this.reverseHold = 0;
        }
        // the tile under the cycle; lost it (broken under the wheels): back to free riding
        BlockPos column = BlockPos.containing(this.getX(), this.getY(), this.getZ());
        int curY = this.tileAt(column, (int) Math.floor(this.getY()) - 1);
        if (curY == Integer.MIN_VALUE) {
            this.leaveTrack();
            return;
        }
        double remaining = this.speed;
        double x = this.getX();
        double z = this.getZ();
        double y = this.getY();
        double minY = Double.NEGATIVE_INFINITY; // set while the grav-lift climbs to a higher tile ahead
        boolean climbed = false; // the grav-lift climbs at its own pace: once a tick
        for (int step = 0; step < 12 && remaining > 1.0E-4 && this.trackDir != null; step++) {
            dir = this.trackDir;
            int sign = dir.getAxisDirection().getStep();
            boolean alongX = dir.getAxis() == Direction.Axis.X;
            int tx = Mth.floor(x);
            int tz = Mth.floor(z);
            BlockPos tile = new BlockPos(tx, curY, tz);
            double along = alongX ? x : z;
            double center = (alongX ? tx : tz) + 0.5;
            double toCenter = (center - along) * sign;
            if (toCenter > 1.0E-4) {
                // ride up to the centre of this tile
                double d = Math.min(remaining, toCenter);
                along += d * sign;
                remaining -= d;
            } else if (this.lastDecision != tile.asLong()) {
                // at the centre: which way on?
                this.lastDecision = tile.asLong();
                Direction next = this.decide(driver, tile, dir, curY);
                if (next == null) {
                    if (alongX) {
                        x = along;
                    } else {
                        z = along;
                    }
                    this.setPos(x, y, z);
                    this.leaveTrack();
                    return;
                }
                if (next != dir) {
                    this.trackDir = next;
                    // turning eats some speed, like a hard corner should
                    this.speed *= 0.82;
                    remaining = Math.min(remaining, this.speed);
                    x = tx + 0.5;
                    z = tz + 0.5;
                    continue;
                }
            } else {
                // past the centre: on towards the next tile, unless it is higher than we are (the grav-lift climbs first)
                BlockPos nextColumn = tile.relative(dir);
                int nextY = this.openTileAt(nextColumn, curY);
                double boundary = center + 0.5 * sign;
                double limit = 1.0; // up to the next tile's centre, where the next decision is made
                if (nextY == Integer.MIN_VALUE) {
                    limit = 0.5 - HALF_WIDTH - 0.02;
                } else if (y < nextY + 1.0 - 0.02) {
                    limit = 0.5 - HALF_WIDTH - 0.02;
                    if (!climbed) {
                        climbed = true;
                        double climb = Math.min(nextY + 1.0 - y, 0.55 + this.speed * 0.4);
                        y += climb;
                        minY = y;
                        remaining = Math.max(0, remaining - climb * 0.5);
                    }
                }
                double room = limit - (along - center) * sign;
                double d = Math.min(remaining, Math.max(0, room));
                along += d * sign;
                remaining -= d;
                if (d <= 1.0E-4) {
                    break;
                }
                if ((along - boundary) * sign > 0 && nextY != Integer.MIN_VALUE) {
                    curY = nextY;
                }
            }
            if (alongX) {
                x = along;
                z = tz + 0.5;
            } else {
                z = along;
                x = tx + 0.5;
            }
        }
        // height: on top of the tile under the cycle, and of any higher tile ahead or behind that its body overhangs
        double floor = Math.max(curY + 1.0, minY);
        if (this.trackDir != null) {
            Direction d = this.trackDir;
            boolean alongX = d.getAxis() == Direction.Axis.X;
            double along = alongX ? x : z;
            double tileCenter = Mth.floor(along) + 0.5;
            BlockPos here = new BlockPos(Mth.floor(x), curY, Mth.floor(z));
            for (Direction side : new Direction[]{d, d.getOpposite()}) {
                double boundary = tileCenter + 0.5 * side.getAxisDirection().getStep() * (side.getAxis() == d.getAxis() ? 1 : 0);
                if (Math.abs(along - boundary) < HALF_WIDTH) {
                    int otherY = this.tileAt(here.relative(side), curY);
                    if (otherY != Integer.MIN_VALUE) {
                        floor = Math.max(floor, otherY + 1.0);
                    }
                }
            }
        }
        if (y > floor + 0.01) {
            this.fallSpeed = Math.min(1.2, this.fallSpeed + 0.08);
            y = Math.max(floor, y - this.fallSpeed);
        } else {
            this.fallSpeed = 0;
            y = Math.max(y, floor);
        }
        Vec3 old = this.position();
        this.setPos(x, y, z);
        this.setDeltaMovement(this.position().subtract(old));
        this.setYRot(this.trackDir != null ? this.trackDir.toYRot() : this.getYRot());
    }

    /** At the centre of a tile: straight on, a queued turn, a corner, or the turn the driver looks at. Null: dead end. */
    @Nullable
    private Direction decide(LivingEntity driver, BlockPos tile, Direction dir, int y) {
        Direction left = dir.getCounterClockWise();
        Direction right = dir.getClockWise();
        boolean f = this.openTileAt(tile.relative(dir), y) != Integer.MIN_VALUE;
        boolean l = this.openTileAt(tile.relative(left), y) != Integer.MIN_VALUE;
        boolean r = this.openTileAt(tile.relative(right), y) != Integer.MIN_VALUE;
        if (this.queuedTicks > 0) {
            if (this.queuedTurn < 0 && l) {
                this.queuedTicks = 0;
                return left;
            }
            if (this.queuedTurn > 0 && r) {
                this.queuedTicks = 0;
                return right;
            }
        }
        if (f) {
            return dir;
        }
        if (l && !r) {
            return left;
        }
        if (r && !l) {
            return right;
        }
        if (l) {
            float look = driver.getYRot();
            float toLeft = Math.abs(Mth.wrapDegrees(left.toYRot() - look));
            float toRight = Math.abs(Mth.wrapDegrees(right.toYRot() - look));
            return toLeft <= toRight ? left : right;
        }
        return null;
    }

    private void leaveTrack() {
        this.trackDir = null;
        this.noPhysics = false;
        this.speed = Math.min(this.speed, FREE_MAX * 0.6);
        this.fallSpeed = 0;
    }

    // ---------------------------------------------------------------------------------------- client visuals
    private void clientVisuals() {
        Vec3 now = this.position();
        double moved = this.lastClientPos == null ? 0 : now.distanceTo(this.lastClientPos);
        this.lastClientPos = now;
        this.wheelAngleO = this.wheelAngle;
        this.wheelAngle += (float) (moved / (0.75 * Math.PI) * 360.0);
        this.leanO = this.lean;
        float turn = Mth.wrapDegrees(this.getYRot() - this.lastYaw);
        this.lastYaw = this.getYRot();
        float targetLean = Mth.clamp(-turn * 0.9F, -28.0F, 28.0F) * (float) Math.min(1.0, moved * 2.0);
        this.lean += (targetLean - this.lean) * 0.35F;
        if (moved > 0.3) {
            this.trail.addLast(now);
            while (this.trail.size() > 48) {
                this.trail.removeFirst();
            }
        } else if (!this.trail.isEmpty()) {
            this.trail.removeFirst();
        }
        if (moved > 0.05 && this.isVehicle()) {
            de.eron.redstoneplus.realm.client.CycleClient.engine(this, moved);
        }
    }
}
