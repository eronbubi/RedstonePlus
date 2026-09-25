package de.eron.redstoneplus.block;

import de.eron.redstoneplus.block.entity.EntityTeleporterBlockEntity;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/** Collects entities standing above it. POWERED only shows whether something is stored. */
public class EntityTeleporterBlock extends Block implements EntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public EntityTeleporterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EntityTeleporterBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModRegistry.ENTITY_TELEPORTER_BE.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<EntityTeleporterBlockEntity>) EntityTeleporterBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        EntityTeleporterBlockEntity be = EntityTeleporterBlockEntity.at(level, pos);
        if (!level.isClientSide() && be != null) {
            player.displayClientMessage(Component.translatable("message.redstoneplus.teleporter_count", be.storedCount(), be.groupCount()), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        EntityTeleporterBlockEntity be = EntityTeleporterBlockEntity.at(level, pos);
        return be == null ? 0 : Math.min(15, be.storedCount());
    }
}
