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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Landforms the shared terrain noise cannot make: dune ridges, mesas, ponds, crevasses, lava channels and terraced
 * pools. Each runs once per chunk over its 16x16 columns, driven by world-space noise so neighbouring chunks line up,
 * and only touches columns of its own biome.
 */
public final class RealmTerrain {
    private RealmTerrain() {
    }

    public enum Shape {
        DUNES("arsenal_dunes"), MESAS("hematite_scarps"), PONDS("red_clay_fen", "vein_mire"), CREVASSES("frostwork_wastes"),
        LAVA_CHANNELS("kiln_barrens"), TERRACE_POOLS("rubedo_gardens");

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
                    };
                }
            }
            return any;
        }

        private boolean inBiome(WorldGenLevel level, BlockPos pos) {
            var biome = level.getBiome(pos);
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
        if (n < 0.25) {
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
}
