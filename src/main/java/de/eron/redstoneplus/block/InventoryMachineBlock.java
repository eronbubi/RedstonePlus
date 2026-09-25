package de.eron.redstoneplus.block;

import de.eron.redstoneplus.block.entity.InventoryMachineBlockEntity;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/** Machine with a 3x3 inventory: the Arrow Shooter or the Block Placer. */
public class InventoryMachineBlock extends MachineBlock implements EntityBlock {
    private final boolean arrowShooter;

    public InventoryMachineBlock(Properties properties, boolean arrowShooter) {
        super(properties);
        this.arrowShooter = arrowShooter;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InventoryMachineBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !this.arrowShooter || type != ModRegistry.INVENTORY_MACHINE_BE.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<InventoryMachineBlockEntity>) InventoryMachineBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof InventoryMachineBlockEntity be) {
            player.openMenu(be);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRise(BlockState state, ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof InventoryMachineBlockEntity be) {
            if (this.arrowShooter) {
                be.startSalvo(level);
            } else {
                be.placeBlock(level, pos, facing(state));
            }
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
}
