package de.eron.redstoneplus.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import javax.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks which redstone wires are driven by a powered Nuclear Repeater. The wire mixin asks
 * {@link #isBoosted} and treats those wires as if a full strength source sat next to them, so
 * the signal stays at 15 along the whole traced network instead of losing 1 per block.
 */
public final class NuclearNetwork {
    public static final int MAX_WIRES = 200;

    private static final Map<Level, Map<BlockPos, Set<BlockPos>>> BOOSTS = new WeakHashMap<>();

    private NuclearNetwork() {
    }

    public static boolean isBoosted(Level level, BlockPos pos) {
        Map<BlockPos, Set<BlockPos>> perLevel = BOOSTS.get(level);
        if (perLevel == null || perLevel.isEmpty()) {
            return false;
        }
        for (Set<BlockPos> wires : perLevel.values()) {
            if (wires.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-traces the wires driven by the repeater at {@code repeater}. {@code start} is the block in front of
     * the repeater while it is powered, or null to drop its boost.
     */
    public static void refresh(Level level, BlockPos repeater, @Nullable BlockPos start) {
        Map<BlockPos, Set<BlockPos>> perLevel = BOOSTS.computeIfAbsent(level, l -> new HashMap<>());
        Set<BlockPos> before = perLevel.getOrDefault(repeater, Collections.emptySet());
        Set<BlockPos> after = start == null ? Collections.emptySet() : trace(level, start);
        if (after.isEmpty()) {
            perLevel.remove(repeater);
        } else {
            perLevel.put(repeater.immutable(), after);
        }
        if (before.equals(after)) {
            return;
        }
        // wake every wire whose boost changed; the wire logic spreads the change from there
        for (BlockPos pos : before) {
            if (!after.contains(pos)) {
                wake(level, pos);
            }
        }
        for (BlockPos pos : after) {
            if (!before.contains(pos)) {
                wake(level, pos);
            }
        }
    }

    private static void wake(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.REDSTONE_WIRE)) {
            level.neighborChanged(pos, Blocks.REDSTONE_WIRE, pos);
        }
    }

    private static Set<BlockPos> trace(Level level, BlockPos start) {
        Set<BlockPos> found = new HashSet<>();
        if (!level.getBlockState(start).is(Blocks.REDSTONE_WIRE)) {
            return found;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        found.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && found.size() < MAX_WIRES) {
            BlockPos pos = queue.poll();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos side = pos.relative(dir);
                for (BlockPos next : new BlockPos[]{side, side.above(), side.below()}) {
                    if (found.size() >= MAX_WIRES) {
                        return found;
                    }
                    if (!found.contains(next) && level.isLoaded(next) && level.getBlockState(next).is(Blocks.REDSTONE_WIRE)) {
                        found.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return found;
    }
}
