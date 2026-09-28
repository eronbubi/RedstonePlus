package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers where realm portals stand in each dimension, so a trip always lands at the nearest partner portal.
 * When there is none close enough, a new one (frame of redstone blocks) is built on solid ground.
 */
public final class RealmPortals extends SavedData {
    private static final String NAME = "redstoneplus_realm_portals";
    private static final int LINK_RANGE = 128;
    private final List<BlockPos> portals = new ArrayList<>();

    private RealmPortals() {
    }

    private static RealmPortals of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RealmPortals::new, RealmPortals::load, null), NAME);
    }

    private static RealmPortals load(CompoundTag tag, HolderLookup.Provider registries) {
        RealmPortals data = new RealmPortals();
        for (long l : tag.getLongArray("portals")) {
            data.portals.add(BlockPos.of(l));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("portals", new LongArrayTag(this.portals.stream().mapToLong(BlockPos::asLong).toArray()));
        return tag;
    }

    /** Notes a lit portal (any block of it). */
    public static void remember(ServerLevel level, BlockPos pos) {
        RealmPortals data = of(level);
        for (BlockPos p : data.portals) {
            if (p.distSqr(pos) < 16) {
                return;
            }
        }
        data.portals.add(pos.immutable());
        data.setDirty();
    }

    /**
     * Where to arrive in {@code level} for someone who left from {@code from}: in front of the nearest portal within
     * 128 blocks, or at a newly built one.
     */
    public static BlockPos findOrBuild(ServerLevel level, BlockPos from) {
        RealmPortals data = of(level);
        BlockPos best = null;
        double bestDist = LINK_RANGE * LINK_RANGE;
        for (BlockPos p : new ArrayList<>(data.portals)) {
            double d = p.distSqr(from.atY(p.getY()));
            if (d > bestDist) {
                continue;
            }
            level.getChunk(p.getX() >> 4, p.getZ() >> 4);
            if (!level.getBlockState(p).is(Realm.REALM_PORTAL.get())) {
                data.portals.remove(p);
                data.setDirty();
                continue;
            }
            bestDist = d;
            best = p;
        }
        if (best != null) {
            return bottomOf(level, best);
        }
        return build(level, from);
    }

    private static BlockPos bottomOf(ServerLevel level, BlockPos portal) {
        BlockPos p = portal;
        while (level.getBlockState(p.below()).is(Realm.REALM_PORTAL.get())) {
            p = p.below();
        }
        return p;
    }

    /** Builds a 4x5 frame of redstone blocks with a lit 2x3 portal on the ground near {@code near}. */
    private static BlockPos build(ServerLevel level, BlockPos near) {
        BlockPos ground = RealmPortalBlock.surface(level, near);
        int y = Math.max(ground.getY(), level.getSeaLevel() + 1);
        y = Math.min(y, level.getMaxBuildHeight() - 8);
        BlockPos base = new BlockPos(near.getX(), y, near.getZ());
        BlockState frame = Blocks.REDSTONE_BLOCK.defaultBlockState();
        BlockState floor = Realm.REALMSTONE_BRICKS.get().defaultBlockState();
        // a small landing: solid floor under and in front of the portal, air around it
        for (int x = -2; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos f = base.offset(x, -1, z);
                if (!level.getBlockState(f).isSolid()) {
                    level.setBlock(f, floor, Block.UPDATE_ALL);
                }
                for (int h = 0; h < 5; h++) {
                    level.setBlock(base.offset(x, h, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        for (int x = -1; x <= 2; x++) {
            level.setBlock(base.offset(x, -1, 0), frame, Block.UPDATE_ALL);
            level.setBlock(base.offset(x, 3, 0), frame, Block.UPDATE_ALL);
        }
        for (int h = 0; h < 3; h++) {
            level.setBlock(base.offset(-1, h, 0), frame, Block.UPDATE_ALL);
            level.setBlock(base.offset(2, h, 0), frame, Block.UPDATE_ALL);
        }
        new RealmPortalBlock.Shape(base, Direction.Axis.X, 2, 3).fill(level);
        remember(level, base);
        return base;
    }
}
