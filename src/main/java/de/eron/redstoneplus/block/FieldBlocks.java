package de.eron.redstoneplus.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Blocks that affect entities around them while powered. */
public final class FieldBlocks {
    private FieldBlocks() {
    }

    /** Throws everything standing on it high into the air on each rising edge. */
    public static class LaunchPad extends NewMachines.Pad {
        public LaunchPad(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
            AABB top = standingArea(pos).inflate(0.1, 0, 0.1);
            for (Entity entity : level.getEntities((Entity) null, top, e -> !e.isSpectator())) {
                Vec3 m = entity.getDeltaMovement();
                entity.setDeltaMovement(m.x, 2.4, m.z);
                entity.hurtMarked = true;
                entity.fallDistance = 0;
            }
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_FALL, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
    }

    /** Pulls dropped items within 10 blocks towards itself and feeds them into a container below. */
    public static class ItemMagnet extends PoweredBlock {
        public ItemMagnet(Properties properties) {
            super(properties);
        }

        @Override
        protected int workInterval() {
            return 1;
        }

        @Override
        protected void work(BlockState state, ServerLevel level, BlockPos pos) {
            Vec3 center = Vec3.atCenterOf(pos).add(0, 0.8, 0);
            Container below = level.getBlockEntity(pos.below()) instanceof Container c ? c : null;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(10.0))) {
                Vec3 diff = center.subtract(item.position());
                if (below != null && diff.lengthSqr() < 1.6) {
                    HopperBlockEntity.addItem(below, item);
                    continue;
                }
                item.setDeltaMovement(item.getDeltaMovement().scale(0.6).add(diff.normalize().scale(0.18)));
            }
        }
    }

    /** Loud alarm every second while powered. */
    public static class AlarmSiren extends PoweredBlock {
        public AlarmSiren(Properties properties) {
            super(properties);
        }

        @Override
        protected int workInterval() {
            return 20;
        }

        @Override
        protected void work(BlockState state, ServerLevel level, BlockPos pos) {
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4.0F, 0.7F);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 4.0F, 2.0F);
        }
    }

    /** Makes everything within 4 blocks sideways and 12 blocks upwards float up while powered. */
    public static class AntiGravity extends PoweredBlock {
        public AntiGravity(Properties properties) {
            super(properties);
        }

        @Override
        protected int workInterval() {
            return 1;
        }

        @Override
        protected void work(BlockState state, ServerLevel level, BlockPos pos) {
            AABB area = new AABB(pos.above()).inflate(4.0, 0, 4.0).expandTowards(0, 12.0, 0);
            for (Entity entity : level.getEntities((Entity) null, area, e -> !e.isSpectator())) {
                if (entity instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 10, 1, true, false));
                } else {
                    Vec3 m = entity.getDeltaMovement();
                    entity.setDeltaMovement(m.x * 0.9, Math.min(0.35, m.y + 0.12), m.z * 0.9);
                    entity.hurtMarked = true;
                }
                entity.fallDistance = 0;
            }
        }
    }

    /** Hurts living entities standing on it while powered. */
    public static class Spikes extends PoweredBlock {
        public Spikes(Properties properties) {
            super(properties);
        }

        @Override
        protected int workInterval() {
            return 10;
        }

        @Override
        protected void work(BlockState state, ServerLevel level, BlockPos pos) {
            AABB top = new AABB(pos.above()).expandTowards(0, -0.2, 0);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, top, e -> !(e instanceof Player p && p.isCreative()))) {
                living.hurt(level.damageSources().cactus(), 4.0F);
            }
        }
    }
}
