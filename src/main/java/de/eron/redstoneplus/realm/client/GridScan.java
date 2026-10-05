package de.eron.redstoneplus.realm.client;

import de.eron.redstoneplus.realm.Grid;
import net.minecraft.client.multiplayer.ClientLevel;

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * What the HUD and the Grid Map know about the lightlines: which columns of the loaded chunks have a tile on top,
 * scanned chunk by chunk from the client's own copy of the world and refreshed every few seconds, so tiles the
 * Trackwrights lay show up while you watch. A few chunks are scanned per frame so drawing never stalls.
 */
final class GridScan {
    /** Chunks scanned per tick by the HUD, and per frame by the open map. */
    static final int HUD_BUDGET = 6;
    static final int MAP_BUDGET = 48;
    private static final int REFRESH = 100;

    private record Chunk(BitSet tiles, long scanned) {
    }

    private static final Map<Long, Chunk> CHUNKS = new HashMap<>();
    @Nullable
    private static ClientLevel level;
    private static long now;
    private static int budget;

    private GridScan() {
    }

    static void tick(@Nullable ClientLevel current) {
        if (current != level) {
            CHUNKS.clear();
            level = current;
        }
        if (current != null) {
            now = current.getGameTime();
        }
        budget = HUD_BUDGET;
        if (CHUNKS.size() > 20000) {
            CHUNKS.clear();
        }
    }

    /** Lets the open map scan more per frame. */
    static void allowMore(int chunks) {
        budget = Math.max(budget, chunks);
    }

    /**
     * The tiles of chunk (cx, cz) as a bit per column (index (z & 15) * 16 + (x & 15)), or null if the chunk is not loaded
     * or not scanned yet.
     */
    @Nullable
    static BitSet chunk(int cx, int cz) {
        if (level == null || !level.hasChunk(cx, cz)) {
            return null;
        }
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Chunk c = CHUNKS.get(key);
        if ((c == null || now - c.scanned > REFRESH) && budget > 0) {
            budget--;
            BitSet bits = new BitSet(256);
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    if (Grid.surfaceTileY(level, (cx << 4) + dx, (cz << 4) + dz) != Integer.MIN_VALUE) {
                        bits.set(dz * 16 + dx);
                    }
                }
            }
            c = new Chunk(bits, now);
            CHUNKS.put(key, c);
        }
        return c == null ? null : c.tiles;
    }

    /** True if column (x, z) is known to carry a tile. */
    static boolean tile(int x, int z) {
        BitSet bits = chunk(x >> 4, z >> 4);
        return bits != null && bits.get((z & 15) * 16 + (x & 15));
    }
}
