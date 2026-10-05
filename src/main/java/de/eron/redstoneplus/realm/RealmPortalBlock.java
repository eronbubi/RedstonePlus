package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The way into the Redstone Realm: a frame of redstone blocks, lit with flint and steel, like a Nether portal.
 * Standing in it carries you to the realm (or back); on the other side a matching portal is found or built.
 */
public class RealmPortalBlock extends Block implements Portal {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_SHAPE = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.box(6, 0, 0, 10, 16, 16);
    private static final int MAX_SIZE = 21;

    public RealmPortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            default -> state;
        };
    }

    /** The portal collapses as soon as its frame or its sheet is broken. */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis axis = state.getValue(AXIS);
        boolean inPlane = dir.getAxis() == axis || dir.getAxis() == Direction.Axis.Y;
        if (inPlane && !neighbor.is(this) && !isFrame(neighbor)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        // like the Nether portal: instant in creative, four seconds otherwise
        return entity instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().invulnerable ? 1 : 80;
    }

    @Override
    public Transition getLocalTransition() {
        return Transition.CONFUSION;
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        boolean inRealm = level.dimension() == Realm.REALM;
        ServerLevel target = inRealm ? level.getServer().overworld() : level.getServer().getLevel(Realm.REALM);
        if (target == null) {
            return null;
        }
        BlockPos from = entity.blockPosition();
        if (!inRealm) {
            // the realm is one artery, not a whole world: you come out on the land nearest to where you left
            double[] land = Artery.nearestLand(from.getX(), from.getZ());
            from = BlockPos.containing(land[0], from.getY(), land[1]);
        }
        BlockPos arrival = RealmPortals.findOrBuild(target, from);
        return new DimensionTransition(target, Vec3.atBottomCenterOf(arrival), entity.getDeltaMovement().scale(0.0), entity.getYRot(), entity.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(80) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.4F, random.nextFloat() * 0.4F + 0.5F, false);
        }
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0, 0, 0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(RealmFx.SPARK.get(), pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------------------------------------ frames

    public static boolean isFrame(BlockState state) {
        return state.is(Blocks.REDSTONE_BLOCK);
    }

    /** The inside of a portal frame: lower-left corner, along which axis, and size. */
    public record Shape(BlockPos bottomLeft, Direction.Axis axis, int width, int height) {
        public void fill(LevelAccessor level) {
            BlockState portal = Realm.REALM_PORTAL.get().defaultBlockState().setValue(AXIS, this.axis);
            Direction right = this.axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            for (int w = 0; w < this.width; w++) {
                for (int h = 0; h < this.height; h++) {
                    level.setBlock(this.bottomLeft.relative(right, w).above(h), portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    private static boolean empty(BlockState state) {
        return state.isAir() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
    }

    /** Finds a complete frame of redstone blocks around {@code inside}, in either direction. */
    @Nullable
    public static Shape findFrame(LevelAccessor level, BlockPos inside) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            Shape shape = findFrame(level, inside, axis);
            if (shape != null) {
                return shape;
            }
        }
        return null;
    }

    @Nullable
    private static Shape findFrame(LevelAccessor level, BlockPos inside, Direction.Axis axis) {
        if (!empty(level.getBlockState(inside))) {
            return null;
        }
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction left = right.getOpposite();
        // down to the floor of the frame
        BlockPos p = inside;
        for (int i = 0; i < MAX_SIZE && empty(level.getBlockState(p.below())); i++) {
            p = p.below();
        }
        if (!isFrame(level.getBlockState(p.below()))) {
            return null;
        }
        // left to the side of the frame
        for (int i = 0; i < MAX_SIZE && empty(level.getBlockState(p.relative(left))); i++) {
            p = p.relative(left);
        }
        if (!isFrame(level.getBlockState(p.relative(left)))) {
            return null;
        }
        BlockPos bottomLeft = p;
        int width = 0;
        while (width < MAX_SIZE && empty(level.getBlockState(bottomLeft.relative(right, width)))) {
            width++;
        }
        if (width < 2 || !isFrame(level.getBlockState(bottomLeft.relative(right, width)))) {
            return null;
        }
        int height = 0;
        outer:
        while (height < MAX_SIZE) {
            for (int w = 0; w < width; w++) {
                if (!empty(level.getBlockState(bottomLeft.relative(right, w).above(height)))) {
                    break outer;
                }
            }
            if (!isFrame(level.getBlockState(bottomLeft.relative(left).above(height)))
                    || !isFrame(level.getBlockState(bottomLeft.relative(right, width).above(height)))) {
                return null;
            }
            height++;
        }
        if (height < 3) {
            return null;
        }
        for (int w = 0; w < width; w++) {
            if (!isFrame(level.getBlockState(bottomLeft.relative(right, w).above(height)))
                    || !isFrame(level.getBlockState(bottomLeft.relative(right, w).below()))) {
                return null;
            }
        }
        return new Shape(bottomLeft, axis, width, height);
    }

    /** Lights a frame (flint and steel). Returns true if a portal opened. */
    public static boolean tryLight(Level level, BlockPos inside) {
        Shape shape = findFrame(level, inside);
        if (shape == null) {
            return false;
        }
        shape.fill(level);
        level.playSound(null, inside, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, inside, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.4F, 1.4F);
        if (level instanceof ServerLevel server) {
            RealmPortals.remember(server, shape.bottomLeft());
        }
        return true;
    }

    static BlockPos surface(ServerLevel level, BlockPos near) {
        level.getChunk(near.getX() >> 4, near.getZ() >> 4);
        return near.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ()));
    }
}
