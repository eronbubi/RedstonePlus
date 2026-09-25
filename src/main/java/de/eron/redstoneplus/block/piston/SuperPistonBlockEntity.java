package de.eron.redstoneplus.block.piston;

import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Remembers how far the arm should go (the slider) and how far it currently is out. */
public class SuperPistonBlockEntity extends BlockEntity {
    private int range = 1;
    private int length;

    public SuperPistonBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SUPER_PISTON_BE.get(), pos, state);
    }

    public int range() {
        return this.range;
    }

    public void setRange(int range) {
        this.range = Math.max(1, Math.min(SuperPistonBlock.MAX_RANGE, range));
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            // re-evaluate: grow or shrink the arm to the new range
            this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
        }
    }

    public int length() {
        return this.length;
    }

    void setLength(int length) {
        this.length = length;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("range", this.range);
        tag.putInt("length", this.length);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.range = tag.contains("range") ? tag.getInt("range") : 1;
        this.length = tag.getInt("length");
    }
}
