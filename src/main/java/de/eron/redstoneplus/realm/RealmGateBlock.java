package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The way into the Redstone Realm and back. It only works while it gets a redstone signal (LIT);
 * then right click it to travel. In the realm a return gate on a redstone block is built next to where you arrive.
 */
public class RealmGateBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public RealmGateBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean lit = level.hasNeighborSignal(pos);
            if (lit != state.getValue(LIT)) {
                level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!level.hasNeighborSignal(pos)) {
            player.displayClientMessage(Component.translatable("message.redstoneplus.gate_unpowered"), true);
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            RealmTravel.travel(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            level.addParticle(DustParticleOptions.REDSTONE, pos.getX() + random.nextDouble(), pos.getY() + 1.0 + random.nextDouble() * 0.6,
                    pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 2, -random.nextDouble(), (random.nextDouble() - 0.5) * 2);
        }
    }
}
