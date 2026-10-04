package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.gate.GateBlock;
import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The great buildings of the world that ran on redstone before it fell silent: a generator hall, a relay spire and a
 * circuit temple. Each one is either still standing, its machines humming, or a collapsed ruin whose upper floors
 * have come down and whose machines are broken.
 */
final class RealmRelics {
    private RealmRelics() {
    }

    /** 40% still standing and working, 60% ruins at varying stages of collapse. */
    static void weather(RealmFeatures.Build b) {
        if (b.random.nextFloat() < 0.6F) {
            b.decay = 0.25F + b.random.nextFloat() * 0.35F;
        }
    }

    private static BlockState s(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }

    private static BlockState litLamp() {
        return ModRegistry.INSTANT_LAMP.get().defaultBlockState().setValue(SimpleBlocks.Lamp.LIT, true);
    }

    private static BlockState lamp() {
        return ModRegistry.INSTANT_LAMP.get().defaultBlockState();
    }

    /** A clock driving a sequencer that chases light around three lamps; the heart of a working machine. */
    private static void chaser(RealmFeatures.Build b, int x, int y, int z, int delay) {
        b.set(x - 1, y, z, b.gate(ModRegistry.CLOCK.get(), Direction.EAST).setValue(GateBlock.DELAY, delay));
        b.set(x, y, z, b.gate(ModRegistry.SEQUENCER.get(), Direction.EAST));
        b.set(x, y, z - 1, lamp());
        b.set(x + 1, y, z, lamp());
        b.set(x, y, z + 1, lamp());
    }

    /** Fallen blocks around a ruin. */
    private static void rubble(RealmFeatures.Build b, RealmStructures.Palette p, int radius, int count) {
        if (b.decay <= 0) {
            return;
        }
        float decay = b.decay;
        b.decay = 0;
        for (int i = 0; i < count; i++) {
            int x = b.random.nextInt(radius * 2 + 1) - radius;
            int z = b.random.nextInt(radius * 2 + 1) - radius;
            for (int y = 3; y >= -1; y--) {
                if (!b.get(x, y - 1, z).isAir() && b.get(x, y, z).isAir()) {
                    b.set(x, y, z, b.random.nextInt(3) == 0 ? s(Realm.REALMSTONE.get()) : b.random.nextInt(4) == 0 ? p.metal() : p.bricks());
                    break;
                }
            }
        }
        b.decay = decay;
    }

    // =========================================================================================== generator hall

    /**
     * A long gabled power hall with two smokestacks. Inside, a caged redstone core pulses on a chaser circuit and rows
     * of dynamos line the walls; a gantry runs along the back wall under the roof.
     */
    static void generatorHall(RealmFeatures.Build b) {
        RealmStructures.Palette p = RealmStructures.Palette.of(b);
        weather(b);
        BlockState bricks = p.bricks();
        BlockState metal = p.metal();
        BlockState glass = s(Blocks.RED_STAINED_GLASS);
        int w = 13;
        int d = 9;
        int h = 8;
        // plinth one wider than the walls, with a paved apron in front of the doors
        b.foundation(-w - 1, -d - 4, w + 1, d + 1, bricks, bricks, h + d + 4);
        for (int x = -w; x <= w; x++) {
            for (int z = -d; z <= d; z++) {
                boolean bus = z == 0 || Math.floorMod(x, 6) == 0;
                b.set(x, -1, z, bus ? (x == 0 || z == 0 ? p.glow() : metal) : bricks);
            }
        }
        // walls: metal pilasters every four blocks, tall windows between them, a metal band under the eaves
        for (int y = 0; y <= h; y++) {
            for (int x = -w; x <= w; x++) {
                for (int z = -d; z <= d; z++) {
                    if (Math.abs(x) != w && Math.abs(z) != d) {
                        continue;
                    }
                    boolean pilaster = Math.abs(z) == d ? Math.floorMod(x, 4) == 1 : Math.floorMod(z, 4) == 1;
                    boolean corner = Math.abs(x) == w && Math.abs(z) == d;
                    boolean window = !pilaster && !corner && y >= 2 && y <= 6;
                    b.set(x, y, z, y == h || pilaster || corner ? metal : window ? glass : bricks);
                }
            }
        }
        // doors: a tall opening in the front wall framed in metal
        b.fill(-2, 0, -d, 2, 4, -d, s(Blocks.AIR));
        b.fill(-3, 5, -d, 3, 5, -d, metal);
        // gabled roof spanning the short way, a glass skylight along the ridge
        for (int z = -d - 1; z <= d + 1; z++) {
            int ry = h + 1 + (d + 1 - Math.abs(z)) / 2;
            for (int x = -w - 1; x <= w + 1; x++) {
                boolean ridge = Math.abs(z) <= 1;
                b.set(x, ry, z, ridge && Math.floorMod(x, 3) != 0 && Math.abs(x) < w ? glass : Math.floorMod(x, 4) == 1 ? metal : bricks);
            }
            for (int y = h + 1; y < ry; y++) {
                b.set(-w, y, z, bricks);
                b.set(w, y, z, bricks);
            }
        }
        // two smokestacks with glowing lips
        for (int sx : new int[]{-w + 3, w - 3}) {
            int sz = d - 3;
            for (int y = h; y <= h + d + 10; y++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        boolean shell = Math.abs(x) == 1 || Math.abs(z) == 1;
                        b.set(sx + x, y, sz + z, shell ? (y % 5 == 0 ? metal : bricks) : s(Blocks.AIR));
                    }
                }
            }
            int top = h + d + 11;
            b.set(sx - 1, top, sz, p.glow());
            b.set(sx + 1, top, sz, p.glow());
            b.set(sx, top, sz - 1, p.glow());
            b.set(sx, top, sz + 1, p.glow());
        }
        // the core: a dais, a chaser circuit, and a caged column of redstone blocks above it
        b.fill(-2, 0, 0, 2, 0, 4, metal);
        chaser(b, 0, 1, 2, 6);
        b.fill(-1, 2, 1, 1, 2, 3, s(Blocks.GLASS));
        for (int y = 3; y <= 7; y++) {
            b.set(0, y, 2, s(Blocks.REDSTONE_BLOCK));
            for (int[] c : new int[][]{{-1, 1}, {1, 1}, {-1, 3}, {1, 3}}) {
                b.set(c[0], y, c[1], s(Blocks.IRON_BARS));
            }
            b.set(-1, y, 2, s(Blocks.GLASS));
            b.set(1, y, 2, s(Blocks.GLASS));
            b.set(0, y, 1, s(Blocks.GLASS));
            b.set(0, y, 3, s(Blocks.GLASS));
        }
        b.fill(-1, 8, 1, 1, 8, 3, metal);
        // dynamos along both side walls: metal housings with a glowing coil and a rod
        for (int z = -6; z <= 6; z += 4) {
            for (int side : new int[]{-1, 1}) {
                int x = side * (w - 2);
                b.fill(x, 0, z, x + side, 1, z + 1, metal);
                b.set(x, 2, z, p.glow());
                b.set(x, 3, z, s(Blocks.LIGHTNING_ROD));
            }
        }
        // gantry along the back wall, hung from the roof on chains
        b.fill(-w + 1, 6, d - 2, w - 1, 6, d - 1, metal);
        for (int x = -w + 3; x <= w - 3; x += 5) {
            b.set(x, 7, d - 2, s(Blocks.CHAIN).setValue(ChainBlock.AXIS, Direction.Axis.Y));
            b.set(x, 8, d - 2, s(Blocks.CHAIN).setValue(ChainBlock.AXIS, Direction.Axis.Y));
        }
        // lights hanging over the floor, kept burning by the power still in the walls
        for (int x = -8; x <= 8; x += 8) {
            b.set(x, h, -4, s(Blocks.REDSTONE_BLOCK));
            b.set(x, h - 1, -4, litLamp());
        }
        if (b.decay <= 0) {
            b.chest(w - 2, 0, -d + 2, Direction.WEST, "realm_relic");
        }
        rubble(b, p, 18, 40);
    }

    // ============================================================================================== relay spire

    /**
     * A tall square broadcast tower on a stepped plinth. Glowing traces run up its faces, two balconies ring it, and at
     * the top a chaser circuit still blinks under a crown of lightning rods. A detector lights the lamp over the door.
     */
    static void relaySpire(RealmFeatures.Build b) {
        RealmStructures.Palette p = RealmStructures.Palette.of(b);
        weather(b);
        BlockState bricks = p.bricks();
        BlockState metal = p.metal();
        b.foundation(-8, -8, 8, 8, bricks, bricks, 50);
        // stepped plinth
        for (int step = 0; step < 3; step++) {
            int r = 7 - step * 2;
            b.fill(-r, step, -r, r, step, r, step == 2 ? metal : bricks);
        }
        int body = 24;
        int neck = 36;
        for (int y = 3; y <= neck; y++) {
            int r = y <= body ? 3 : 2;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean shell = Math.abs(x) == r || Math.abs(z) == r;
                    if (!shell) {
                        b.set(x, y, z, s(Blocks.AIR));
                        continue;
                    }
                    boolean corner = Math.abs(x) == r && Math.abs(z) == r;
                    boolean trace = (x == 0 || z == 0) && y % 2 == 0;
                    boolean window = !corner && (Math.abs(x) == 1 || Math.abs(z) == 1) && y % 6 == 3;
                    b.set(x, y, z, corner ? metal : trace ? p.glow() : window ? s(Blocks.RED_STAINED_GLASS) : bricks);
                }
            }
        }
        // chains hanging down the hollow shaft
        for (int y = 4; y < body; y++) {
            b.set(0, y, 0, s(Blocks.CHAIN).setValue(ChainBlock.AXIS, Direction.Axis.Y));
        }
        // balconies with lamp posts at the corners
        for (int y : new int[]{12, body + 1}) {
            int r = y > body ? 4 : 5;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) > (y > body ? 2 : 3)) {
                        b.set(x, y, z, metal);
                    }
                }
            }
            for (int[] c : new int[][]{{-r, -r}, {r, -r}, {-r, r}, {r, r}}) {
                b.set(c[0], y + 1, c[1], s(Blocks.IRON_BARS));
                b.set(c[0], y + 2, c[1], s(Blocks.REDSTONE_BLOCK));
                b.set(c[0], y + 3, c[1], litLamp());
            }
        }
        // the crown: a platform, a working chaser, an antenna mast and lightning rods
        int top = neck + 1;
        b.fill(-3, top, -3, 3, top, 3, metal);
        chaser(b, 0, top + 1, 0, 4);
        for (int y = top + 1; y <= top + 8; y++) {
            b.set(-2, y, -2, metal);
        }
        b.set(-2, top + 9, -2, s(Blocks.LIGHTNING_ROD));
        for (int[] c : new int[][]{{2, -2}, {2, 2}, {-2, 2}}) {
            b.set(c[0], top + 1, c[1], metal);
            b.set(c[0], top + 2, c[1], s(Blocks.LIGHTNING_ROD));
        }
        // door on the front, a detector beside it and a lamp above
        b.fill(0, 3, -3, 0, 5, -3, s(Blocks.AIR));
        b.set(-1, 3, -4, ModRegistry.PLAYER_DETECTOR.get());
        b.set(-1, 4, -4, lamp());
        rubble(b, p, 12, 36);
    }

    // =========================================================================================== circuit temple

    /**
     * A stepped temple whose terraces are inlaid with circuit traces. A stair climbs the front to a shrine of redstone
     * on the summit; a passage at ground level leads to a chamber whose ceiling lights up when someone walks in.
     */
    static void circuitTemple(RealmFeatures.Build b) {
        RealmStructures.Palette p = RealmStructures.Palette.of(b);
        weather(b);
        BlockState bricks = p.bricks();
        BlockState metal = p.metal();
        BlockState fossil = s(Realm.FOSSIL_CIRCUIT.get());
        int base = 13;
        int tiers = 5;
        b.foundation(-base - 1, -base - 1, base + 1, base + 1, bricks, bricks, tiers * 3 + 8);
        for (int t = 0; t < tiers; t++) {
            int r = base - t * 3;
            for (int y = t * 3; y < t * 3 + 3; y++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        boolean face = Math.abs(x) == r || Math.abs(z) == r;
                        BlockState block = bricks;
                        if (face && y == t * 3 + 2) {
                            block = metal; // trim along each terrace
                        } else if (face && y == t * 3 + 1) {
                            int along = Math.abs(x) == r ? z : x;
                            block = Math.floorMod(along, 5) == 0 ? fossil : p.glow(); // a trace with vias
                        } else if (!face) {
                            block = y == t * 3 + 2 ? bricks : s(Realm.DEEP_REALMSTONE.get());
                        }
                        b.set(x, y, z, block);
                    }
                }
            }
        }
        // the front stair: one block up for every block in, three wide, with metal cheeks
        for (int k = 0; k <= tiers * 3; k++) {
            int z = -base - 1 + k;
            for (int x = -1; x <= 1; x++) {
                b.set(x, k - 1, z, bricks);
                b.fill(x, k, z, x, k + 3, z, s(Blocks.AIR));
            }
            b.set(-2, k, z, metal);
            b.set(2, k, z, metal);
        }
        // the summit shrine: a column of redstone crowned with a crystal, four rods around it
        int summit = tiers * 3;
        b.fill(-2, summit, -2, 2, summit, 2, metal);
        for (int y = summit + 1; y <= summit + 3; y++) {
            b.set(0, y, 0, s(Blocks.REDSTONE_BLOCK));
        }
        b.set(0, summit + 4, 0, s(Realm.REDSTONE_CLUSTER.get()).setValue(AmethystClusterBlock.FACING, Direction.UP));
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            b.set(c[0], summit + 1, c[1], metal);
            b.set(c[0], summit + 2, c[1], s(Blocks.LIGHTNING_ROD));
        }
        // the inner chamber and the passage to it from the back
        b.fill(-3, 0, -3, 3, 4, 3, s(Blocks.AIR));
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                b.set(x, -1, z, (x == 0 || z == 0) ? p.glow() : fossil);
            }
        }
        b.fill(0, 0, 4, 0, 2, base, s(Blocks.AIR));
        b.set(0, 4, 0, ModRegistry.PLAYER_DETECTOR.get());
        b.set(1, 4, 0, lamp());
        b.set(-1, 4, 0, lamp());
        b.set(0, 4, 1, lamp());
        b.set(0, 4, -1, lamp());
        if (b.decay <= 0) {
            b.chest(0, 0, -3, Direction.SOUTH, "realm_relic");
        }
        rubble(b, p, 17, 44);
    }
}
