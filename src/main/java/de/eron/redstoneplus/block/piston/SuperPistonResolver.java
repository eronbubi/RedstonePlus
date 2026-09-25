package de.eron.redstoneplus.block.piston;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla's PistonStructureResolver with the 12 block limit replaced by {@link #limit}.
 * "pistonPos" is where the pushing face currently is: the base or the arm segment behind the head.
 */
public class SuperPistonResolver {
    private final Level level;
    private final BlockPos pistonPos;
    private final boolean extending;
    private final BlockPos startPos;
    private final Direction pushDirection;
    private final Direction pistonDirection;
    private final int limit;
    private final List<BlockPos> toPush = new ArrayList<>();
    private final List<BlockPos> toDestroy = new ArrayList<>();

    public SuperPistonResolver(Level level, BlockPos pistonPos, Direction pistonDirection, boolean extending, int limit) {
        this.level = level;
        this.pistonPos = pistonPos;
        this.pistonDirection = pistonDirection;
        this.extending = extending;
        this.limit = limit;
        if (extending) {
            this.pushDirection = pistonDirection;
            this.startPos = pistonPos.relative(pistonDirection);
        } else {
            this.pushDirection = pistonDirection.getOpposite();
            this.startPos = pistonPos.relative(pistonDirection, 2);
        }
    }

    public boolean resolve() {
        this.toPush.clear();
        this.toDestroy.clear();
        BlockState state = this.level.getBlockState(this.startPos);
        if (!PistonBaseBlock.isPushable(state, this.level, this.startPos, this.pushDirection, false, this.pistonDirection)) {
            if (this.extending && state.getPistonPushReaction() == PushReaction.DESTROY) {
                this.toDestroy.add(this.startPos);
                return true;
            }
            return false;
        }
        if (!this.addBlockLine(this.startPos, this.pushDirection)) {
            return false;
        }
        for (int i = 0; i < this.toPush.size(); i++) {
            BlockPos pos = this.toPush.get(i);
            if (this.level.getBlockState(pos).isStickyBlock() && !this.addBranchingBlocks(pos)) {
                return false;
            }
        }
        return true;
    }

    private boolean addBlockLine(BlockPos origin, Direction direction) {
        BlockState state = this.level.getBlockState(origin);
        if (this.level.isEmptyBlock(origin)
                || !PistonBaseBlock.isPushable(state, this.level, origin, this.pushDirection, false, direction)
                || origin.equals(this.pistonPos)
                || this.toPush.contains(origin)) {
            return true;
        }
        int i = 1;
        if (i + this.toPush.size() > this.limit) {
            return false;
        }
        while (state.isStickyBlock()) {
            BlockPos pos = origin.relative(this.pushDirection.getOpposite(), i);
            BlockState previous = state;
            state = this.level.getBlockState(pos);
            if (state.isAir()
                    || !(previous.canStickTo(state) && state.canStickTo(previous))
                    || !PistonBaseBlock.isPushable(state, this.level, pos, this.pushDirection, false, this.pushDirection.getOpposite())
                    || pos.equals(this.pistonPos)) {
                break;
            }
            if (++i + this.toPush.size() > this.limit) {
                return false;
            }
        }
        int added = 0;
        for (int k = i - 1; k >= 0; k--) {
            this.toPush.add(origin.relative(this.pushDirection.getOpposite(), k));
            added++;
        }
        int j = 1;
        while (true) {
            BlockPos pos = origin.relative(this.pushDirection, j);
            int index = this.toPush.indexOf(pos);
            if (index > -1) {
                this.reorderListAtCollision(added, index);
                for (int k = 0; k <= index + added; k++) {
                    BlockPos p = this.toPush.get(k);
                    if (this.level.getBlockState(p).isStickyBlock() && !this.addBranchingBlocks(p)) {
                        return false;
                    }
                }
                return true;
            }
            state = this.level.getBlockState(pos);
            if (state.isAir()) {
                return true;
            }
            if (!PistonBaseBlock.isPushable(state, this.level, pos, this.pushDirection, true, this.pushDirection) || pos.equals(this.pistonPos)) {
                return false;
            }
            if (state.getPistonPushReaction() == PushReaction.DESTROY) {
                this.toDestroy.add(pos);
                return true;
            }
            if (this.toPush.size() >= this.limit) {
                return false;
            }
            this.toPush.add(pos);
            added++;
            j++;
        }
    }

    private void reorderListAtCollision(int added, int index) {
        List<BlockPos> a = new ArrayList<>(this.toPush.subList(0, index));
        List<BlockPos> b = new ArrayList<>(this.toPush.subList(this.toPush.size() - added, this.toPush.size()));
        List<BlockPos> c = new ArrayList<>(this.toPush.subList(index, this.toPush.size() - added));
        this.toPush.clear();
        this.toPush.addAll(a);
        this.toPush.addAll(b);
        this.toPush.addAll(c);
    }

    private boolean addBranchingBlocks(BlockPos origin) {
        BlockState state = this.level.getBlockState(origin);
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != this.pushDirection.getAxis()) {
                BlockPos pos = origin.relative(direction);
                BlockState other = this.level.getBlockState(pos);
                if (other.canStickTo(state) && state.canStickTo(other) && !this.addBlockLine(pos, direction)) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<BlockPos> getToPush() {
        return this.toPush;
    }

    public List<BlockPos> getToDestroy() {
        return this.toDestroy;
    }
}
