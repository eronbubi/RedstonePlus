package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The realm's power lines: rows of lattice pylons that follow the artery down both flanks of the trunk and along each
 * side vessel. The pylons are blocks laid down with the world; the cables between them are drawn by the client
 * ({@code client.PowerLines}) as sagging lines with pulses of power running along them, from pylon to pylon.
 * Every pylon's place is worked out from the artery alone, so the server and the client agree without talking.
 */
public final class RealmPower {
    private RealmPower() {
    }

    /** Distance between pylons along a line. */
    public static final int SPACING = 48;
    /** Height of a pylon's crown above the ground at its centre. */
    static final int HEIGHT = 24;
    /** How far the cross-arm reaches out to each side. */
    public static final int ARM = 3;

    /**
     * A pylon: centre, the axis its cross-arm lies along (0 = x, 1 = z) and the sign that puts the line's left-hand cable
     * on the plus side.
     */
    public record Pylon(int x, int z, int armAxis, int leftSign) {
    }

    /** The lines, each a run of pylons in order; a null entry is a gap (the land fell away there). */
    public static final List<List<Pylon>> LINES = new ArrayList<>();
    /** The columns each pylon stands on, by chunk. */
    private static final Map<Long, List<Pylon>> BY_CHUNK = new HashMap<>();

    static {
        for (Artery.Vessel v : Artery.ALL) {
            double[] sides = v == Artery.TRUNK ? new double[]{0.55, -0.55} : new double[]{0.45};
            for (double across : sides) {
                List<Pylon> line = new ArrayList<>();
                for (double d = SPACING; d < v.length - SPACING; d += SPACING) {
                    double t = d / v.length;
                    double[] p = v.at(t);
                    double w = v.width(t);
                    // the left normal of the direction of travel, as Artery.place uses it
                    double nx = p[3];
                    double nz = -p[2];
                    int x = (int) Math.round(p[0] + nx * across * w);
                    int z = (int) Math.round(p[1] + nz * across * w);
                    if (Artery.s(x, z) < 0.12) {
                        line.add(null);
                        continue;
                    }
                    int axis = Math.abs(nx) >= Math.abs(nz) ? 0 : 1;
                    int sign = (axis == 0 ? nx : nz) >= 0 ? 1 : -1;
                    Pylon pylon = new Pylon(x, z, axis, sign);
                    line.add(pylon);
                    for (int i = -ARM; i <= ARM; i++) {
                        for (int j = -1; j <= 1; j++) {
                            int cx = x + (axis == 0 ? i : j);
                            int cz = z + (axis == 0 ? j : i);
                            BY_CHUNK.computeIfAbsent(key(cx >> 4, cz >> 4), k -> new ArrayList<>()).add(pylon);
                        }
                    }
                }
                LINES.add(line);
            }
        }
        BY_CHUNK.replaceAll((k, l) -> l.stream().distinct().toList());
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    /** One column of whichever pylon stands on it. */
    static boolean column(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z, int top) {
        List<Pylon> here = BY_CHUNK.get(key(x >> 4, z >> 4));
        if (here == null) {
            return false;
        }
        for (Pylon p : here) {
            int dx = x - p.x;
            int dz = z - p.z;
            int along = p.armAxis == 0 ? dx : dz;   // along the cross-arm
            int across = p.armAxis == 0 ? dz : dx;
            if (Math.abs(along) > ARM || Math.abs(across) > 1) {
                continue;
            }
            int crown = gen.getBaseHeight(p.x, p.z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) + HEIGHT;
            build(level, x, z, along, across, top, crown);
            return true;
        }
        return false;
    }

    private static void build(WorldGenLevel level, int x, int z, int along, int across, int top, int crown) {
        BlockState steel = Realm.RUST_PLATING.get().defaultBlockState();
        BlockState band = Realm.CHISELED_REALMSTONE_BRICKS.get().defaultBlockState();
        boolean leg = Math.abs(along) == 1 && Math.abs(across) == 1;
        if (leg) {
            // a lattice leg from the ground to the crown, braced every few blocks
            for (int y = top - 2; y < crown; y++) {
                set(level, x, y, z, (y - top) % 6 == 0 ? band : steel);
            }
        } else if (Math.abs(along) <= 1 && (crown - top) > 0) {
            // the bracing between the legs
            for (int y = top + 5; y < crown; y += 6) {
                set(level, x, y, z, steel);
            }
        }
        if (Math.abs(along) <= ARM && across == 0 || Math.abs(along) <= 1) {
            set(level, x, crown, z, steel); // the crown and the cross-arm
        }
        if (across == 0 && (Math.abs(along) == ARM || along == 0)) {
            // the insulators the cables hang from
            set(level, x, crown + 1, z, Blocks.LIGHTNING_ROD.defaultBlockState());
            set(level, x, crown - 1, z, Realm.REDSTONE_VEIN.get().defaultBlockState());
        }
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }
}
