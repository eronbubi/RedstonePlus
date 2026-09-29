package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Landforms the shared terrain noise cannot make: dune ridges, mesas, ponds, crevasses, lava channels, terraced
 * pools, the long rail tracks of the switchyard, and through every biome glowing redstone veins and the broken roads
 * of the world that ran on redstone before. Each runs once per chunk over its 16x16 columns, driven by world-space noise so neighbouring chunks line up,
 * and only touches columns of its own biome.
 */
public final class RealmTerrain {
    private RealmTerrain() {
    }

    public enum Shape {
        DUNES("arsenal_dunes"), MESAS("hematite_scarps"), PONDS("red_clay_fen", "vein_mire"), CREVASSES("frostwork_wastes"),
        LAVA_CHANNELS("kiln_barrens"), TERRACE_POOLS("rubedo_gardens"), TRACKS("switchyard_flats"),
        // everywhere: glowing veins in the ground, and the broken roads of the old world
        VEINS(), ROADS();

        final String[] biomes;

        Shape(String... biomes) {
            this.biomes = biomes;
        }

        public String id() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final Map<Long, SimplexNoise> NOISES = new ConcurrentHashMap<>();

    private static SimplexNoise noise(long seed, int salt) {
        return NOISES.computeIfAbsent(seed * 31 + salt, s -> new SimplexNoise(RandomSource.create(s)));
    }

    public static class Overlay extends Feature<NoneFeatureConfiguration> {
        private final Shape shape;

        public Overlay(Shape shape) {
            super(NoneFeatureConfiguration.CODEC);
            this.shape = shape;
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            WorldGenLevel level = context.level();
            ChunkPos chunk = new ChunkPos(context.origin());
            long seed = level.getSeed();
            SimplexNoise a = noise(seed, this.shape.ordinal() * 7 + 1);
            SimplexNoise b = noise(seed, this.shape.ordinal() * 7 + 2);
            int sea = level.getLevel().getSeaLevel();
            boolean any = false;
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int x = chunk.getMinBlockX() + dx;
                    int z = chunk.getMinBlockZ() + dz;
                    int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                    BlockPos surface = new BlockPos(x, top - 1, z);
                    if (!this.inBiome(level, surface)) {
                        continue;
                    }
                    any |= switch (this.shape) {
                        case DUNES -> dune(level, a, b, x, z, top, sea);
                        case MESAS -> mesa(level, a, b, x, z, top, sea);
                        case PONDS -> pond(level, a, x, z, top, sea, false);
                        case CREVASSES -> crevasse(level, a, b, x, z, top);
                        case LAVA_CHANNELS -> lavaChannel(level, a, b, x, z, top, sea);
                        case TERRACE_POOLS -> pond(level, a, x, z, top, sea, true);
                        case TRACKS -> track(level, a, x, z, top, sea);
                        case VEINS -> vein(level, a, b, x, z, top);
                        case ROADS -> road(level, a, b, x, z, top, sea);
                    };
                }
            }
            return any;
        }

        private boolean inBiome(WorldGenLevel level, BlockPos pos) {
            var biome = level.getBiome(pos);
            if (this.shape.biomes.length == 0) {
                // the shapes that run through every biome; roads stay out of the sea
                return this.shape != Shape.ROADS || !biome.is(ResourceKey.create(Registries.BIOME, Realm.id("tempest_shoals")));
            }
            for (String name : this.shape.biomes) {
                if (biome.is(ResourceKey.create(Registries.BIOME, Realm.id(name)))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    private static boolean dry(WorldGenLevel level, int x, int top, int z, int sea) {
        return top > sea && level.getFluidState(new BlockPos(x, top, z)).isEmpty();
    }

    /** Long wind-blown ridges of rust sand, bent by a slow noise so they do not run in straight lines. */
    private static boolean dune(WorldGenLevel level, SimplexNoise a, SimplexNoise b, int x, int z, int top, int sea) {
        if (!dry(level, x, top, z, sea)) {
            return false;
        }
        double bend = a.getValue(x * 0.006, z * 0.006) * 40;
        double ridge = Math.sin((x * 0.9 + z * 0.4 + bend) * 0.07);
        double h = Math.pow((ridge + 1) / 2, 1.8) * 9 + b.getValue(x * 0.05, z * 0.05) * 1.5;
        int height = (int) Math.round(h);
        BlockState sand = Realm.RUST_SAND.get().defaultBlockState();
        for (int y = 0; y < height; y++) {
            set(level, x, top + y, z, sand);
        }
        return height > 0;
    }

    /** Flat-topped mesas in two tiers, their sides banded like the scarps' cliffs. */
    private static boolean mesa(WorldGenLevel level, SimplexNoise a, SimplexNoise b, int x, int z, int top, int sea) {
        if (!dry(level, x, top, z, sea)) {
            return false;
        }
        double n = a.getValue(x * 0.011, z * 0.011) + b.getValue(x * 0.04, z * 0.04) * 0.15;
        int height = n > 0.62 ? 22 : n > 0.3 ? 12 : 0;
        BlockState light = Realm.HEMATITE.get().defaultBlockState();
        BlockState dark = Realm.DARK_HEMATITE.get().defaultBlockState();
        for (int y = 0; y < height; y++) {
            int wy = top + y;
            set(level, x, wy, z, Math.floorMod(wy, 7) < 3 ? dark : light);
        }
        if (height > 0 && b.getValue(x * 0.3, z * 0.3) > 0.6) {
            set(level, x, top + height, z, Realm.RED_CORAL_SHRUB.get().defaultBlockState());
        }
        return height > 0;
    }

    /** Shallow ponds of the biome's water in the low ground; terraced pools get a rim of bricks and glowing crystal. */
    private static boolean pond(WorldGenLevel level, SimplexNoise a, int x, int z, int top, int sea, boolean terraced) {
        if (top < sea - 2 || top > sea + 12) {
            return false;
        }
        double n = a.getValue(x * 0.035, z * 0.035);
        if (terraced) {
            double band = n * 4 - Math.floor(n * 4);
            if (n > 0.1 && band > 0.82) {
                set(level, x, top - 1, z, Realm.REALMSTONE_BRICKS.get().defaultBlockState());
                set(level, x, top, z, band > 0.93 ? Realm.REDSTONE_VEIN.get().defaultBlockState() : Realm.REALMSTONE_BRICKS.get().defaultBlockState());
                return true;
            }
            if (n > 0.1 && band < 0.7) {
                set(level, x, top - 1, z, Blocks.WATER.defaultBlockState());
                set(level, x, top - 2, z, Realm.RED_CLAY.get().defaultBlockState());
                return true;
            }
            return false;
        }
        if (n < 0.05) {
            return false;
        }
        int depth = n > 0.5 ? 2 : 1;
        for (int d = 1; d <= depth; d++) {
            set(level, x, top - d, z, Blocks.WATER.defaultBlockState());
        }
        set(level, x, top - depth - 1, z, Realm.FEN_MUD.get().defaultBlockState());
        return true;
    }

    /** Deep cracks in the ice sheet: narrow, with walls of packed ice. */
    private static boolean crevasse(WorldGenLevel level, SimplexNoise a, SimplexNoise b, int x, int z, int top) {
        double n = Math.abs(a.getValue(x * 0.012, z * 0.012));
        if (n > 0.05) {
            return false;
        }
        int depth = 8 + (int) ((b.getValue(x * 0.05, z * 0.05) + 1) * 5);
        for (int y = 0; y < depth; y++) {
            set(level, x, top - 1 - y, z, n > 0.035 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        return true;
    }

    /** Winding channels of lava across the barrens, edged with magma. */
    private static boolean lavaChannel(WorldGenLevel level, SimplexNoise a, SimplexNoise b, int x, int z, int top, int sea) {
        if (!dry(level, x, top, z, sea)) {
            return false;
        }
        double n = Math.abs(a.getValue(x * 0.009, z * 0.009) + b.getValue(x * 0.03, z * 0.03) * 0.08);
        if (n > 0.045) {
            return false;
        }
        if (n > 0.03) {
            set(level, x, top - 1, z, Blocks.MAGMA_BLOCK.defaultBlockState());
        } else {
            set(level, x, top - 1, z, Blocks.LAVA.defaultBlockState());
            set(level, x, top - 2, z, Blocks.LAVA.defaultBlockState());
        }
        return true;
    }

    // switchyard grid: pairs of parallel north-south tracks every 56 blocks, single east-west tracks every 72 blocks
    private static final int NS_SPACING = 56;
    private static final int EW_SPACING = 72;

    private static boolean nsTrack(SimplexNoise a, int x, int z) {
        int m = Math.floorMod(x - 7, NS_SPACING);
        return (m == 0 || m == 3) && a.getValue(Math.floorDiv(x - 7, NS_SPACING) * 1.7, z * 0.006) > -0.15;
    }

    private static boolean ewTrack(SimplexNoise a, int x, int z) {
        return Math.floorMod(z - 23, EW_SPACING) == 0 && a.getValue(x * 0.006, Math.floorDiv(z - 23, EW_SPACING) * 1.7 + 50) > -0.15;
    }

    /**
     * Long straight tracks across the switchyard that run on from chunk to chunk, climbing one-block steps. Every so
     * often a powered rail sits on a redstone block, and signal posts light up when someone walks by.
     */
    private static boolean track(WorldGenLevel level, SimplexNoise a, int x, int z, int top, int sea) {
        boolean ns = nsTrack(a, x, z);
        boolean ew = ewTrack(a, x, z);
        if ((!ns && !ew) || !dry(level, x, top, z, sea) || !level.getBlockState(new BlockPos(x, top, z)).canBeReplaced()) {
            return false;
        }
        int before = ns ? height(level, x, z - 1) : height(level, x - 1, z);
        int after = ns ? height(level, x, z + 1) : height(level, x + 1, z);
        if (Math.abs(before - top) > 1 && Math.abs(after - top) > 1) {
            return false; // a cliff: the track breaks off here
        }
        RailShape shape;
        if (ns && ew) {
            shape = RailShape.NORTH_SOUTH;
        } else if (ns) {
            shape = after == top + 1 ? RailShape.ASCENDING_SOUTH : before == top + 1 ? RailShape.ASCENDING_NORTH : RailShape.NORTH_SOUTH;
        } else {
            shape = after == top + 1 ? RailShape.ASCENDING_EAST : before == top + 1 ? RailShape.ASCENDING_WEST : RailShape.EAST_WEST;
        }
        int along = ns ? z : x;
        boolean flat = !shape.isAscending() && !(ns && ew);
        if (flat && Math.floorMod(along, 16) == 5) {
            set(level, x, top - 1, z, Blocks.REDSTONE_BLOCK.defaultBlockState());
            set(level, x, top, z, Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape).setValue(PoweredRailBlock.POWERED, true));
        } else {
            set(level, x, top - 1, z, Realm.SLAG.get().defaultBlockState());
            set(level, x, top, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape));
        }
        // a signal post beside the outer track now and then
        if (flat && Math.floorMod(along, 40) == 17) {
            int px = ns ? x - 1 : x;
            int pz = ns ? z : z - 1;
            boolean outer = !ns || Math.floorMod(x - 7, NS_SPACING) == 0;
            if (outer && height(level, px, pz) == top && dry(level, px, top, pz, sea)) {
                set(level, px, top, pz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                set(level, px, top + 1, pz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                BlockPos sensor = new BlockPos(px, top + 2, pz);
                level.setBlock(sensor, ModRegistry.PLAYER_DETECTOR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                level.scheduleTick(sensor, ModRegistry.PLAYER_DETECTOR.get(), 2);
                set(level, px, top + 3, pz, ModRegistry.INSTANT_LAMP.get().defaultBlockState());
            }
        }
        return true;
    }

    private static int height(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
    }

    /**
     * Redstone veins in every biome: long winding lines of glowing vein rock at the surface with thinner branches off
     * them, a crystal breaking through here and there. Under water they glow on the sea floor.
     */
    private static boolean vein(WorldGenLevel level, SimplexNoise a, SimplexNoise b, int x, int z, int top) {
        double main = Math.abs(a.getValue(x * 0.011, z * 0.011));
        double branch = Math.abs(b.getValue(x * 0.035, z * 0.035));
        boolean trunk = main < 0.022;
        if (!trunk && !(branch < 0.012 && a.getValue(x * 0.004, z * 0.004) > -0.2)) {
            return false;
        }
        BlockState vein = Realm.REDSTONE_VEIN.get().defaultBlockState();
        int depth = trunk ? 3 : 1;
        for (int d = 1; d <= depth; d++) {
            set(level, x, top - d, z, d == depth && trunk ? Realm.REALM_REDSTONE_ORE.get().defaultBlockState() : vein);
        }
        BlockPos above = new BlockPos(x, top, z);
        if (trunk && main < 0.004 && level.getBlockState(above).isAir() && Math.floorMod(x * 31 + z * 17, 13) == 0) {
            set(level, x, top, z, Realm.REDSTONE_CLUSTER.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, net.minecraft.core.Direction.UP));
        }
        return true;
    }

    private static final int ROAD_GRID = 176;

    /** Distance across the nearest road running east-west (0 = middle), or -1 off the road; the roads wind with the noise. */
    private static int across(double pos, double warp) {
        int d = Math.floorMod((int) Math.round(pos + warp), ROAD_GRID) - ROAD_GRID / 2;
        return Math.abs(d) <= 2 ? d : Integer.MIN_VALUE;
    }

    /**
     * The roads of the old world: five blocks wide with kerbs, a glowing power line down the middle where the conduit
     * still carries current, and lamp posts along the side. Long stretches are ruined: paving missing, lamps dead or
     * fallen; some stretches are gone altogether.
     */
    private static boolean road(WorldGenLevel level, SimplexNoise a, SimplexNoise b, int x, int z, int top, int sea) {
        if (!dry(level, x, top, z, sea)) {
            return false;
        }
        int ew = across(z, a.getValue(x * 0.004, 7.3) * 30);
        int ns = across(x, a.getValue(3.1, z * 0.004) * 30);
        if (ew == Integer.MIN_VALUE && ns == Integer.MIN_VALUE) {
            return false;
        }
        boolean eastWest = ew != Integer.MIN_VALUE;
        int d = eastWest ? ew : ns;
        int along = eastWest ? x : z;
        // how well this stretch has survived: gone, broken, or intact
        double state = b.getValue(eastWest ? x * 0.006 : 11.7, eastWest ? 5.9 : z * 0.006);
        if (state < -0.45) {
            return false;
        }
        boolean broken = state < 0.05;
        int hash = Math.floorMod(x * 734287 + z * 912931, 100);
        if (broken && hash < 35) {
            return false; // a hole in the paving
        }
        BlockState paving;
        if (Math.abs(d) == 2) {
            paving = Realm.DEEP_REALMSTONE.get().defaultBlockState(); // kerb
        } else if (d == 0 && !broken) {
            paving = Realm.REDSTONE_VEIN.get().defaultBlockState(); // the live conduit
        } else {
            paving = broken && hash < 60 ? Realm.REALMSTONE.get().defaultBlockState() : Realm.REALMSTONE_BRICKS.get().defaultBlockState();
        }
        set(level, x, top - 1, z, paving);
        // a lamp post on the kerb every 24 blocks: lit where the road still has power, dark or toppled where not
        if (d == -2 && Math.floorMod(along, 24) == 0 && level.getBlockState(new BlockPos(x, top, z)).canBeReplaced()) {
            BlockState plating = Realm.RUST_PLATING.get().defaultBlockState();
            if (!broken) {
                set(level, x, top, z, plating);
                set(level, x, top + 1, z, plating);
                set(level, x, top + 2, z, Blocks.REDSTONE_BLOCK.defaultBlockState());
                set(level, x, top + 3, z, ModRegistry.INSTANT_LAMP.get().defaultBlockState()
                        .setValue(de.eron.redstoneplus.block.SimpleBlocks.Lamp.LIT, true));
            } else if (hash < 80) {
                set(level, x, top, z, plating);
                if (hash < 65) {
                    set(level, x, top + 1, z, plating);
                    set(level, x, top + 2, z, ModRegistry.INSTANT_LAMP.get().defaultBlockState());
                }
            }
        }
        return true;
    }
}
