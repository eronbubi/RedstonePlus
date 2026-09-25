package de.eron.redstoneplus.block.piston;

import de.eron.redstoneplus.menu.SuperPistonMenu;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A piston that pushes up to {@link #PUSH_LIMIT} blocks and whose arm reaches 1 to {@link #MAX_RANGE} blocks
 * (set with a slider, right click). The arm goes out one block at a time with the normal piston animation,
 * each step pushing the whole structure one block further.
 */
public class SuperPistonBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty EXTENDED = BlockStateProperties.EXTENDED;
    public static final int MAX_RANGE = 13;
    public static final int PUSH_LIMIT = 50;
    /** A vanilla moving block needs 2 ticks to arrive and up to 2 more to settle into a real block. */
    private static final int STEP_TICKS = 4;

    private final boolean sticky;

    public SuperPistonBlock(Properties properties, boolean sticky) {
        super(properties);
        this.sticky = sticky;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EXTENDED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EXTENDED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuperPistonBlockEntity(pos, state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.getValue(EXTENDED) ? state : state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.getValue(EXTENDED) ? state : state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---------- slider GUI ----------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SuperPistonBlockEntity be) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new SuperPistonMenu(id, inventory, be, ContainerLevelAccess.create(level, pos)),
                    Component.translatable(this.getDescriptionId())));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ---------- redstone ----------

    /** Same as vanilla: any side except the front, plus quasi-connectivity from the block above. */
    private static boolean hasSignal(SignalGetter level, BlockPos pos, Direction facing) {
        for (Direction d : Direction.values()) {
            if (d != facing && level.hasSignal(pos.relative(d), d)) {
                return true;
            }
        }
        if (level.hasSignal(pos, Direction.DOWN)) {
            return true;
        }
        BlockPos above = pos.above();
        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN && level.hasSignal(above.relative(d), d)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    private static final int EVENT_EXTEND = 0;
    private static final int EVENT_RETRACT = 1;

    /**
     * Decides on the server what the next step is and sends it as a block event. Like a vanilla piston, the step
     * itself then runs in {@link #triggerEvent} on the server AND on every client, so clients animate the move and
     * end up with exactly the same blocks (vanilla never sends the settled blocks of a piston move to clients).
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof SuperPistonBlockEntity be)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        int want = hasSignal(level, pos, facing) ? be.range() : 0;
        int length = be.length();
        if (length < want) {
            // book the next step first, so neighbour updates caused by this step can't pull it forward
            level.scheduleTick(pos, this, STEP_TICKS);
            level.blockEvent(pos, this, EVENT_EXTEND, length);
        } else if (length > want) {
            level.scheduleTick(pos, this, STEP_TICKS);
            BlockState atTip = level.getBlockState(pos.relative(facing, length));
            if (atTip.is(Blocks.MOVING_PISTON)) {
                return; // the head is still sliding, try again next step
            }
            if (!atTip.is(ModRegistry.SUPER_PISTON_HEAD.get())) {
                // something broke the arm: clean up whatever is left
                clearArm(level, pos, facing);
                level.setBlock(pos, state.setValue(EXTENDED, false), Block.UPDATE_ALL);
                be.setLength(0);
                return;
            }
            level.blockEvent(pos, this, EVENT_RETRACT, length);
        }
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int length) {
        Direction facing = state.getValue(FACING);
        SuperPistonBlockEntity be = level.isClientSide() ? null : level.getBlockEntity(pos) instanceof SuperPistonBlockEntity p ? p : null;
        if (!level.isClientSide() && (be == null || be.length() != length)) {
            return false; // stale event
        }
        if (id == EVENT_EXTEND) {
            if (!this.extendStep(level, pos, facing, length)) {
                return false;
            }
            if (be != null) {
                be.setLength(length + 1);
            }
            return true;
        }
        if (id == EVENT_RETRACT) {
            this.retractStep(level, pos, facing, length);
            if (be != null) {
                be.setLength(length - 1);
            }
            return true;
        }
        return false;
    }

    private BlockState movingState(Direction facing) {
        return Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, facing)
                .setValue(MovingPistonBlock.TYPE, this.sticky ? PistonType.STICKY : PistonType.DEFAULT);
    }

    private BlockState headState(Direction facing) {
        return ModRegistry.SUPER_PISTON_HEAD.get().defaultBlockState().setValue(SuperPistonParts.FACING, facing).setValue(SuperPistonParts.STICKY, this.sticky);
    }

    /** Pushes everything in front one block further and moves the head out by one. */
    private boolean extendStep(Level level, BlockPos pos, Direction facing, int length) {
        BlockPos head = pos.relative(facing, length);
        BlockPos tip = head.relative(facing);
        SuperPistonResolver resolver = new SuperPistonResolver(level, head, facing, true, PUSH_LIMIT);
        if (!resolver.resolve()) {
            return false;
        }
        Map<BlockPos, BlockState> vacated = moveBlocks(level, resolver, facing, true);
        vacated.remove(tip);
        BlockState moving = this.movingState(facing);
        level.setBlock(tip, moving, 68);
        level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(tip, moving, this.headState(facing), facing, true, false));
        if (length == 0) {
            level.setBlock(pos, level.getBlockState(pos).setValue(EXTENDED, true), Block.UPDATE_CLIENTS);
        } else {
            // If the head from the last step is still sliding, drop its moving block entity first. Otherwise
            // replacing it would make vanilla finish the move and put a head back on top of the new arm segment.
            if (level.getBlockState(head).is(Blocks.MOVING_PISTON)) {
                level.removeBlockEntity(head);
            }
            level.setBlock(head, ModRegistry.SUPER_PISTON_ARM.get().defaultBlockState().setValue(SuperPistonParts.FACING, facing), Block.UPDATE_ALL);
        }
        finishMove(level, vacated, resolver);
        level.updateNeighborsAt(tip, this);
        level.playSound(null, tip, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.25F + 0.6F);
        level.gameEvent(GameEvent.BLOCK_ACTIVATE, tip, GameEvent.Context.of(level.getBlockState(pos)));
        return true;
    }

    /** Pulls the head back by one block; the sticky version drags the structure in front along. */
    private void retractStep(Level level, BlockPos pos, Direction facing, int length) {
        BlockPos tip = pos.relative(facing, length);
        BlockPos prev = tip.relative(facing.getOpposite());
        level.setBlock(tip, Blocks.AIR.defaultBlockState(), 20);
        if (length > 1) {
            BlockState moving = this.movingState(facing);
            level.setBlock(prev, moving, 20);
            level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(prev, moving, this.headState(facing), facing, false, false));
        } else {
            level.setBlock(pos, level.getBlockState(pos).setValue(EXTENDED, false), Block.UPDATE_CLIENTS);
        }
        if (this.sticky) {
            SuperPistonResolver resolver = new SuperPistonResolver(level, prev, facing, false, PUSH_LIMIT);
            if (resolver.resolve()) {
                finishMove(level, moveBlocks(level, resolver, facing, false), resolver);
            }
        }
        level.updateNeighborsAt(tip, this);
        level.playSound(null, tip, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.15F + 0.6F);
        level.gameEvent(GameEvent.BLOCK_DEACTIVATE, tip, GameEvent.Context.of(level.getBlockState(pos)));
    }

    /** Vanilla's block moving: destroys fragile blocks, turns pushed blocks into moving blocks. Returns the vacated spots. */
    private static Map<BlockPos, BlockState> moveBlocks(Level level, SuperPistonResolver resolver, Direction facing, boolean extending) {
        Map<BlockPos, BlockState> vacated = new HashMap<>();
        List<BlockPos> toPush = resolver.getToPush();
        List<BlockState> states = new ArrayList<>();
        for (BlockPos p : toPush) {
            BlockState s = level.getBlockState(p);
            states.add(s);
            vacated.put(p, s);
        }
        List<BlockPos> toDestroy = resolver.getToDestroy();
        Direction direction = extending ? facing : facing.getOpposite();
        for (int j = toDestroy.size() - 1; j >= 0; j--) {
            BlockPos p = toDestroy.get(j);
            BlockState s = level.getBlockState(p);
            BlockEntity be = s.hasBlockEntity() ? level.getBlockEntity(p) : null;
            dropResources(s, level, p, be);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
            level.gameEvent(GameEvent.BLOCK_DESTROY, p, GameEvent.Context.of(s));
            if (!s.is(BlockTags.FIRE)) {
                level.addDestroyBlockEffect(p, s);
            }
        }
        for (int k = toPush.size() - 1; k >= 0; k--) {
            BlockPos target = toPush.get(k).relative(direction);
            vacated.remove(target);
            BlockState moving = Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, facing);
            level.setBlock(target, moving, 68);
            level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(target, moving, states.get(k), facing, extending, false));
        }
        return vacated;
    }

    private static void finishMove(Level level, Map<BlockPos, BlockState> vacated, SuperPistonResolver resolver) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos p : vacated.keySet()) {
            level.setBlock(p, air, 82);
        }
        for (Map.Entry<BlockPos, BlockState> e : vacated.entrySet()) {
            e.getValue().updateIndirectNeighbourShapes(level, e.getKey(), 2);
            air.updateNeighbourShapes(level, e.getKey(), 2);
            air.updateIndirectNeighbourShapes(level, e.getKey(), 2);
        }
        for (BlockPos p : resolver.getToDestroy()) {
            level.updateNeighborsAt(p, Blocks.AIR);
        }
        for (BlockPos p : resolver.getToPush()) {
            level.updateNeighborsAt(p, Blocks.AIR);
        }
    }

    /** Removes every head and arm block in front of this piston. */
    private static void clearArm(Level level, BlockPos pos, Direction facing) {
        for (int i = 1; i <= MAX_RANGE + 1; i++) {
            BlockPos p = pos.relative(facing, i);
            BlockState s = level.getBlockState(p);
            if ((s.getBlock() instanceof SuperPistonParts.Arm || s.getBlock() instanceof SuperPistonParts.Head)
                    && s.getValue(SuperPistonParts.FACING) == facing) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else if (!s.is(Blocks.MOVING_PISTON)) {
                return;
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && state.getValue(EXTENDED)) {
            clearArm(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
