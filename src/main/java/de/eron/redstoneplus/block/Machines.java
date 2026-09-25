package de.eron.redstoneplus.block;

import de.eron.redstoneplus.entity.FrozenTntEntity;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Directional machines that act on the block in front of them. */
public final class Machines {
    private Machines() {
    }

    /** TnTer / FreezeTnter: every redstone pulse spawns a primed TNT in front, no TNT needed. */
    public static class Tnter extends MachineBlock {
        private final boolean frozen;

        public Tnter(Properties properties, boolean frozen) {
            super(properties);
            this.frozen = frozen;
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            BlockPos front = pos.relative(facing(state));
            double x = front.getX() + 0.5, y = front.getY(), z = front.getZ() + 0.5;
            Entity tnt;
            if (this.frozen) {
                FrozenTntEntity frozenTnt = new FrozenTntEntity(ModRegistry.FROZEN_TNT.get(), level);
                frozenTnt.setPos(x, y, z);
                tnt = frozenTnt;
            } else {
                tnt = new PrimedTnt(level, x, y, z, null);
            }
            level.addFreshEntity(tnt);
            level.playSound(null, x, y, z, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, this.frozen ? 1.6F : 1.0F);
            level.gameEvent(null, GameEvent.PRIME_FUSE, front);
        }
    }

    /** Breaks the block in front and drops it. */
    public static class BlockBreaker extends MachineBlock {
        public BlockBreaker(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            BlockPos front = pos.relative(facing(state));
            BlockState target = level.getBlockState(front);
            if (!target.isAir() && target.getDestroySpeed(level, front) >= 0 && !target.is(Blocks.BEDROCK)) {
                level.destroyBlock(front, true);
            }
        }
    }

    /** Strikes lightning at the first solid block up to 64 blocks in front. */
    public static class LightningCaller extends MachineBlock {
        public LightningCaller(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = facing(state);
            Vec3 start = frontPoint(pos, facing, 0.6);
            Vec3 end = start.add(facing.getStepX() * 64.0, facing.getStepY() * 64.0, facing.getStepZ() * 64.0);
            BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null));
            Vec3 target = hit.getType() == HitResult.Type.MISS ? end : Vec3.atBottomCenterOf(hit.getBlockPos().above());
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(target.x, target.y, target.z);
                level.addFreshEntity(bolt);
            }
        }
    }

    /** Launches a primed TNT in a high arc, no TNT needed. */
    public static class TntCannon extends MachineBlock {
        public TntCannon(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = facing(state);
            Vec3 at = frontPoint(pos, facing, 1.0);
            PrimedTnt tnt = new PrimedTnt(level, at.x, at.y - 0.5, at.z, null);
            double up = facing.getAxis().isHorizontal() ? 0.55 : 0.0;
            tnt.setDeltaMovement(facing.getStepX() * 1.6, facing.getStepY() * 1.6 + up, facing.getStepZ() * 1.6);
            tnt.setFuse(60);
            level.addFreshEntity(tnt);
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 1.8F);
        }
    }

    /** Shoots a ghast fireball straight ahead. */
    public static class FireballLauncher extends MachineBlock {
        public FireballLauncher(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = facing(state);
            Vec3 at = frontPoint(pos, facing, 1.1);
            Vec3 dir = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
            LargeFireball fireball = new LargeFireball(EntityType.FIREBALL, level);
            fireball.moveTo(at.x, at.y, at.z);
            fireball.setDeltaMovement(dir.scale(0.1));
            fireball.accelerationPower = 0.1;
            level.addFreshEntity(fireball);
            level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /** Harvests and replants fully grown crops in a 5x5 area in front. */
    public static class CropHarvester extends MachineBlock {
        public CropHarvester(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = facing(state);
            BlockPos center = pos.relative(facing, 3);
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
                BlockState crop = level.getBlockState(p);
                if (crop.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(crop)) {
                    Block.dropResources(crop, level, p);
                    level.setBlock(p, cropBlock.getStateForAge(0), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Lights a fire in front when powered and puts it out when the power goes away. */
    public static class FireStarter extends MachineBlock {
        public FireStarter(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean reactsToFall() {
            return true;
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            BlockPos front = pos.relative(facing(state));
            if (level.getBlockState(front).isAir()) {
                level.setBlock(front, BaseFireBlock.getState(level, front), Block.UPDATE_ALL);
                level.playSound(null, front, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

        @Override
        protected void onFall(BlockState state, ServerLevel level, BlockPos pos) {
            BlockPos front = pos.relative(facing(state));
            if (level.getBlockState(front).getBlock() instanceof BaseFireBlock) {
                level.removeBlock(front, false);
                level.playSound(null, front, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.0F);
            }
        }
    }

    /** Blows every entity in the 8 blocks in front away while powered. */
    public static class Fan extends MachineBlock {
        public Fan(Properties properties) {
            super(properties);
        }

        @Override
        protected int workInterval() {
            return 1;
        }

        @Override
        protected void work(BlockState state, ServerLevel level, BlockPos pos) {
            Direction facing = facing(state);
            BlockPos front = pos.relative(facing);
            AABB area = new AABB(front).expandTowards(facing.getStepX() * 7.0, facing.getStepY() * 7.0, facing.getStepZ() * 7.0);
            for (Entity entity : level.getEntities((Entity) null, area, e -> !e.isSpectator() && !(e instanceof Player p && p.getAbilities().flying))) {
                entity.push(facing.getStepX() * 0.11, facing.getStepY() * 0.11, facing.getStepZ() * 0.11);
                entity.hurtMarked = true;
                entity.fallDistance = 0;
            }
        }
    }
}
