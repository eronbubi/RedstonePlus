package de.eron.redstoneplus.mixin;

import de.eron.redstoneplus.redstone.NuclearNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wires driven by a Nuclear Repeater see a full strength source next to them, so they never decay. */
@Mixin(RedStoneWireBlock.class)
public abstract class RedStoneWireBlockMixin {
    @Inject(method = "calculateTargetStrength", at = @At("RETURN"), cancellable = true)
    private void redstoneplus$nuclearBoost(Level level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() < 15 && NuclearNetwork.isBoosted(level, pos)) {
            cir.setReturnValue(15);
        }
    }
}
