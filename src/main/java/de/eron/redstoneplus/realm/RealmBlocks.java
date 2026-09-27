package de.eron.redstoneplus.realm;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The smaller blocks of the realm. */
public final class RealmBlocks {
    private RealmBlocks() {
    }

    /**
     * A detector rail that also notices anything alive walking over it, not only minecarts.
     * It is the trigger of the Switchyard's hazard diversion.
     */
    public static class TripperRail extends DetectorRailBlock {
        public TripperRail(Properties properties) {
            super(properties);
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            super.entityInside(state, level, pos, entity);
            if (level.isClientSide() || !(entity instanceof LivingEntity) || entity.isSpectator()) {
                return;
            }
            BlockState now = level.getBlockState(pos);
            if (now.is(this) && !now.getValue(POWERED)) {
                level.setBlock(pos, now.setValue(POWERED, true), Block.UPDATE_ALL);
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.below(), this);
                // the vanilla check switches it off again once nothing is on it
                level.scheduleTick(pos, this, 20);
            }
        }
    }

    /** Thorny briar bush: slows and scratches whoever walks through it. */
    public static class BriarThorns extends BushBlock {
        public static final MapCodec<BriarThorns> CODEC = simpleCodec(BriarThorns::new);
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 13, 15);

        public BriarThorns(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends BushBlock> codec() {
            return CODEC;
        }

        @Override
        protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
            return state.is(BlockTags.DIRT) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PODZOL);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            if (entity instanceof LivingEntity living && !(entity instanceof Constructs.SpoolWeaver)) {
                entity.makeStuckInBlock(state, new Vec3(0.7, 0.75, 0.7));
                if (!level.isClientSide() && (entity.xOld != entity.getX() || entity.zOld != entity.getZ()) && level.getRandom().nextInt(4) == 0) {
                    living.hurt(level.damageSources().sweetBerryBush(), 1.0F);
                }
            }
        }
    }

    /**
     * Decoy Beacon: chimes like footsteps. Bell Stalkers within 24 blocks go to it instead of hunting players
     * and Lockdown Gates within 16 blocks stay open.
     */
    public static class DecoyBeacon extends BaseEntityBlock {
        public static final MapCodec<DecoyBeacon> CODEC = simpleCodec(DecoyBeacon::new);
        private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

        public DecoyBeacon(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return CODEC;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new DecoyBeaconEntity(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide() ? null : createTickerHelper(type, Realm.DECOY_BEACON_BE.get(), DecoyBeaconEntity::serverTick);
        }
    }

    /** Keeps a list of the loaded decoys per dimension, so constructs and gates can find them cheaply. */
    public static class DecoyBeaconEntity extends BlockEntity {
        private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE = new HashMap<>();

        public DecoyBeaconEntity(BlockPos pos, BlockState state) {
            super(Realm.DECOY_BEACON_BE.get(), pos, state);
        }

        @Override
        public void onLoad() {
            super.onLoad();
            if (this.level != null && !this.level.isClientSide()) {
                ACTIVE.computeIfAbsent(this.level.dimension(), k -> new HashSet<>()).add(this.worldPosition.immutable());
            }
        }

        @Override
        public void setRemoved() {
            super.setRemoved();
            this.forget();
        }

        @Override
        public void onChunkUnloaded() {
            super.onChunkUnloaded();
            this.forget();
        }

        private void forget() {
            if (this.level != null && !this.level.isClientSide()) {
                Set<BlockPos> set = ACTIVE.get(this.level.dimension());
                if (set != null) {
                    set.remove(this.worldPosition);
                }
            }
        }

        static void serverTick(Level level, BlockPos pos, BlockState state, DecoyBeaconEntity beacon) {
            if (level.getGameTime() % 40 == 0 && level instanceof ServerLevel server) {
                server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 0.6F + level.getRandom().nextFloat() * 0.4F);
                server.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.5);
                server.sendParticles(ParticleTypes.SCULK_CHARGE_POP, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.4, 0.2, 0.4, 0.02);
            }
        }

        /** Closest loaded decoy within {@code radius}, or null. */
        @Nullable
        public static BlockPos nearest(Level level, Vec3 from, double radius) {
            Set<BlockPos> set = ACTIVE.get(level.dimension());
            if (set == null) {
                return null;
            }
            BlockPos best = null;
            double bestDist = radius * radius;
            for (BlockPos pos : set) {
                double d = Vec3.atCenterOf(pos).distanceToSqr(from);
                if (d <= bestDist) {
                    bestDist = d;
                    best = pos;
                }
            }
            return best;
        }
    }
}
