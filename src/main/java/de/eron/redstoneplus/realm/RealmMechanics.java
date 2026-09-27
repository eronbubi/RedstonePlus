package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** What the creatures do to the realm around them. */
public final class RealmMechanics {
    private RealmMechanics() {
    }

    /**
     * A redstone shock through the ground: every realm trap within {@code radius} goes off, the closest first.
     * Returns how many traps were set off.
     */
    public static int pulseTraps(ServerLevel level, BlockPos center, int radius) {
        int fired = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, 3, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof TrapBlock trap && !state.getValue(TrapBlock.DISARMED)) {
                int delay = 1 + (int) Math.sqrt(pos.distSqr(center));
                trap.arm(state, level, pos.immutable(), delay);
                level.sendParticles(RealmFx.SPARK.get(), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.1);
                fired++;
            }
        }
        return fired;
    }

    /**
     * Smelts dropped items near a hot creature, like a furnace would: up to {@code perItem} of every stack
     * that has a smelting recipe. Returns true if anything was smelted.
     */
    public static boolean smeltNearby(ServerLevel level, Vec3 center, double radius, int perItem) {
        boolean any = false;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(radius))) {
            ItemStack stack = item.getItem();
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level);
            if (recipe.isEmpty()) {
                continue;
            }
            ItemStack result = recipe.get().value().getResultItem(level.registryAccess());
            if (result.isEmpty()) {
                continue;
            }
            int n = Math.min(perItem, stack.getCount());
            ItemStack out = result.copy();
            out.setCount(result.getCount() * n);
            stack.shrink(n);
            if (stack.isEmpty()) {
                item.discard();
            }
            ItemEntity cooked = new ItemEntity(level, item.getX(), item.getY() + 0.2, item.getZ(), out);
            cooked.setDeltaMovement(0, 0.2, 0);
            level.addFreshEntity(cooked);
            level.sendParticles(ParticleTypes.FLAME, item.getX(), item.getY() + 0.3, item.getZ(), 8, 0.15, 0.15, 0.15, 0.02);
            level.sendParticles(RealmFx.EMBER.get(), item.getX(), item.getY() + 0.3, item.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
            level.playSound(null, item.blockPosition(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 1.0F, 1.2F);
            any = true;
        }
        return any;
    }
}
