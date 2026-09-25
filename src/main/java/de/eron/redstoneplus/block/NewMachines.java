package de.eron.redstoneplus.block;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** The 20 newer machines and pads. */
public final class NewMachines {
    private NewMachines() {
    }

    /** Freezes all water sources in a 5x5x5 area centred 3 blocks in front. */
    public static class IceMaker extends MachineBlock {
        public IceMaker(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            BlockPos c = pos.relative(facing(state), 3);
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 2, 2))) {
                if (level.getFluidState(p).is(Fluids.WATER) && level.getFluidState(p).isSource() && level.getBlockState(p).is(Blocks.WATER)) {
                    level.setBlock(p, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
    }

    /** Removes water and lava in a 5x5x5 area centred 3 blocks in front. */
    public static class WaterPump extends MachineBlock {
        public WaterPump(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            BlockPos c = pos.relative(facing(state), 3);
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 2, 2))) {
                if (level.getBlockState(p).getBlock() instanceof LiquidBlock) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 0.8F);
        }
    }

    /** Launches a random firework in its facing direction. */
    public static class FireworkLauncher extends MachineBlock {
        private static final FireworkExplosion.Shape[] SHAPES = FireworkExplosion.Shape.values();

        public FireworkLauncher(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            RandomSource r = level.getRandom();
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            FireworkExplosion explosion = new FireworkExplosion(SHAPES[r.nextInt(SHAPES.length)],
                    IntList.of(r.nextInt(0xFFFFFF), r.nextInt(0xFFFFFF)), IntList.of(r.nextInt(0xFFFFFF)), r.nextBoolean(), r.nextBoolean());
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + r.nextInt(2), List.of(explosion)));
            Direction facing = facing(state);
            Vec3 at = MachineBlock.frontPoint(pos, facing, 0.7);
            FireworkRocketEntity entity = new FireworkRocketEntity(level, rocket, at.x, at.y, at.z, true);
            entity.shoot(facing.getStepX(), facing.getStepY(), facing.getStepZ(), 1.2F, 2.0F);
            level.addFreshEntity(entity);
        }
    }

    /** Fires a spread of 8 snowballs. */
    public static class SnowballTurret extends MachineBlock {
        public SnowballTurret(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = facing(state);
            Vec3 at = MachineBlock.frontPoint(pos, facing, 0.8);
            for (int i = 0; i < 8; i++) {
                Snowball ball = new Snowball(level, at.x, at.y, at.z);
                ball.setItem(new ItemStack(Items.SNOWBALL));
                ball.shoot(facing.getStepX(), facing.getStepY() + 0.05, facing.getStepZ(), 1.8F, 9.0F);
                level.addFreshEntity(ball);
            }
            level.playSound(null, pos, SoundEvents.SNOWBALL_THROW, SoundSource.BLOCKS, 1.0F, 0.7F);
        }
    }

    /** Drops an anvil from 12 blocks above the spot 6 blocks in front. */
    public static class AnvilDropper extends MachineBlock {
        public AnvilDropper(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            BlockPos drop = pos.relative(facing(state), 6).above(12);
            if (level.getBlockState(drop).isAir()) {
                FallingBlockEntity anvil = FallingBlockEntity.fall(level, drop, Blocks.ANVIL.defaultBlockState());
                anvil.setHurtsEntities(2.0F, 40);
            }
        }
    }

    /** Spawns 5 primed TNT spread out in front. */
    public static class ClusterTnter extends MachineBlock {
        public ClusterTnter(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            Vec3 at = MachineBlock.frontPoint(pos, facing(state), 1.0);
            RandomSource r = level.getRandom();
            for (int i = 0; i < 5; i++) {
                PrimedTnt tnt = new PrimedTnt(level, at.x, at.y - 0.5, at.z, null);
                double angle = i * Math.PI * 2 / 5;
                tnt.setDeltaMovement(Math.cos(angle) * 0.3, 0.35 + r.nextDouble() * 0.1, Math.sin(angle) * 0.3);
                tnt.setFuse(60 + r.nextInt(20));
                level.addFreshEntity(tnt);
            }
            level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.5F, 0.8F);
        }
    }

    /** Lets 9 primed TNT rain down from 20 blocks above the area 5 blocks in front. */
    public static class TntRain extends MachineBlock {
        public TntRain(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            BlockPos c = pos.relative(facing(state), 5).above(20);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    PrimedTnt tnt = new PrimedTnt(level, c.getX() + 0.5 + dx * 3, c.getY(), c.getZ() + 0.5 + dz * 3, null);
                    tnt.setDeltaMovement(0, -0.5, 0);
                    tnt.setFuse(80);
                    level.addFreshEntity(tnt);
                }
            }
            level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 2.0F, 0.6F);
        }
    }

    /** Swaps the block in front with the block behind (not blocks with inventories, not unbreakable ones). */
    public static class BlockSwapper extends MachineBlock {
        public BlockSwapper(Properties properties) {
            super(properties);
        }

        private static boolean movable(ServerLevel level, BlockPos p, BlockState s) {
            return !s.hasBlockEntity() && s.getDestroySpeed(level, p) >= 0;
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = facing(state);
            BlockPos front = pos.relative(facing), back = pos.relative(facing.getOpposite());
            BlockState a = level.getBlockState(front), b = level.getBlockState(back);
            if (a == b || !movable(level, front, a) || !movable(level, back, b)) {
                return;
            }
            level.setBlock(front, b, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
            level.setBlock(back, a, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
            level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 1.4F);
        }
    }

    /** A 5 pixel high pad. Entities standing on it are inside the block space. */
    public abstract static class Pad extends PoweredBlock {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 5, 16);

        protected Pad(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        protected static AABB standingArea(BlockPos pos) {
            return new AABB(pos.getX(), pos.getY() + 0.25, pos.getZ(), pos.getX() + 1, pos.getY() + 1.5, pos.getZ() + 1);
        }
    }

    public static class HealPad extends Pad {
        public HealPad(Properties properties) {
            super(properties);
        }

        @Override
        protected int workInterval() {
            return 20;
        }

        @Override
        protected void work(BlockState state, ServerLevel level, BlockPos pos) {
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, standingArea(pos))) {
                living.heal(2.0F);
                level.sendParticles(ParticleTypes.HEART, living.getX(), living.getY() + 1.2, living.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
            }
        }
    }

    public static class SpeedPad extends Pad {
        public SpeedPad(Properties properties) {
            super(properties);
        }

        @Override
        protected int workInterval() {
            return 5;
        }

        @Override
        protected void work(BlockState state, ServerLevel level, BlockPos pos) {
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, standingArea(pos))) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 3, true, true));
            }
        }
    }

    public static class SmokeEmitter extends PoweredBlock {
        public SmokeEmitter(Properties properties) {
            super(properties);
        }

        @Override
        protected int workInterval() {
            return 3;
        }

        @Override
        protected void work(BlockState state, ServerLevel level, BlockPos pos) {
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.25, 0.1, 0.25, 0.02);
        }
    }

    /** Helper for the conveyor collision box. */
    public static final VoxelShape CONVEYOR_SHAPE = Block.box(0, 0, 0, 16, 10, 16);
}
