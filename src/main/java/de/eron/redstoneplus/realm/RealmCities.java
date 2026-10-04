package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Foundry Cities of the Wirewrights: ruined cities hundreds of blocks wide. A city is laid out entirely from world
 * coordinates and the seed, so every chunk can build its own slice without seeing its neighbours: a broken outer wall
 * with gates and towers, a street grid whose avenues still carry power, blocks of halls, bell towers, foundries,
 * gardens and rubble, and at the centre a plaza with the empty Cradle where the Great Bell once hung.
 */
final class RealmCities {
    private RealmCities() {
    }

    private static final int CELL = 640;
    private static final int LOT = 22;
    private static final int STREET = 7;
    private static final int PLAZA = 27;

    /** A city: centre, radius, and the ground height at its centre (from the noise, before any features). */
    private record City(int x, int z, int r, int base, long hash) {
    }

    private static final City NONE = new City(0, 0, 0, 0, 0);
    private static final Map<Long, City> CITIES = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> LOT_BASE = new ConcurrentHashMap<>();

    static long hash(long seed, int a, int b, int salt) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static int bits(long h, int shift, int mod) {
        return (int) Math.floorMod(h >>> shift, (long) mod);
    }

    private static City city(WorldGenLevel level, ChunkGenerator gen, RandomState random, int cellX, int cellZ) {
        long key = ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
        City c = CITIES.get(key);
        if (c != null) {
            return c == NONE ? null : c;
        }
        long h = hash(level.getSeed(), cellX, cellZ, 1);
        c = NONE;
        if (bits(h, 0, 100) < 65) {
            // a few candidate centres in the cell; the city stands on the first that is dry land
            for (int attempt = 0; attempt < 8 && c == NONE; attempt++) {
                long a = hash(level.getSeed(), cellX, cellZ, 100 + attempt);
                int x = cellX * CELL + 140 + bits(a, 8, 360);
                int z = cellZ * CELL + 140 + bits(a, 20, 360);
                int r = 95 + bits(h, 32, 50);
                int base = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
                var biome = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(base), QuartPos.fromBlock(z), random.sampler());
                boolean sea = biome.is(ResourceKey.create(Registries.BIOME, Realm.id("tempest_shoals")));
                if (!sea && base >= level.getSeaLevel() - 1) {
                    c = new City(x, z, r, base, h);
                }
            }
        }
        if (CITIES.size() > 4096) {
            CITIES.clear();
        }
        CITIES.put(key, c);
        return c == NONE ? null : c;
    }

    private static int lotBase(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        Integer y = LOT_BASE.get(key);
        if (y == null) {
            y = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
            if (LOT_BASE.size() > 65536) {
                LOT_BASE.clear();
            }
            LOT_BASE.put(key, y);
        }
        return y;
    }

    // ============================================================================================ blocks
    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    private static BlockState bricks(long h) {
        int r = Math.floorMod(h, 10);
        return r < 3 ? s(Realm.CRACKED_REALMSTONE_BRICKS.get()) : s(Realm.REALMSTONE_BRICKS.get());
    }

    private static BlockState litLamp() {
        return ModRegistry.INSTANT_LAMP.get().defaultBlockState().setValue(SimpleBlocks.Lamp.LIT, true);
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    /** Fills from the ground up to {@code y} (foundations), and clears everything above up to the old surface. */
    private static void flatten(WorldGenLevel level, int x, int z, int top, int y, BlockState fill, int clearTo) {
        for (int yy = Math.max(top - 1, y - 14); yy < y; yy++) {
            set(level, x, yy, z, fill);
        }
        for (int yy = y + 1; yy <= Math.max(top + 2, clearTo); yy++) {
            if (!level.getBlockState(new BlockPos(x, yy, z)).isAir()) {
                set(level, x, yy, z, s(Blocks.AIR));
            }
        }
    }

    // ============================================================================================ the city
    /** True inside a city's walls: other scenery stays out so it does not land in a street or a house. */
    static boolean covers(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z) {
        City c = city(level, gen, random, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
        return c != null && (x - c.x) * (double) (x - c.x) + (z - c.z) * (double) (z - c.z) <= (c.r + 12) * (double) (c.r + 12);
    }

    /** One column of whatever city covers it. Returns true if it built anything. */
    static boolean column(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z, int top) {
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        City c = city(level, gen, random, cellX, cellZ);
        if (c == null) {
            return false;
        }
        int dx = x - c.x;
        int dz = z - c.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > c.r) {
            return false;
        }
        long colHash = hash(level.getSeed(), x, z, 7);
        if (d > c.r - 5) {
            return wall(level, c, x, z, dx, dz, d, top, colHash);
        }
        if (d < PLAZA) {
            return plaza(level, c, x, z, dx, dz, d, top, colHash);
        }
        int lx = Math.floorMod(dx + 3, LOT);
        int lz = Math.floorMod(dz + 3, LOT);
        if (lx < STREET || lz < STREET) {
            return street(level, x, z, dx, dz, lx, lz, top, colHash);
        }
        int li = Math.floorDiv(dx + 3, LOT);
        int lj = Math.floorDiv(dz + 3, LOT);
        int cx = c.x + li * LOT + 11;
        int cz = c.z + lj * LOT + 11;
        double lotDistance = Math.sqrt((cx - c.x) * (double) (cx - c.x) + (cz - c.z) * (double) (cz - c.z));
        if (lotDistance > c.r - 16) {
            return false;
        }
        long lotHash = hash(level.getSeed(), cx, cz, 3);
        int base = lotBase(level, gen, random, cx, cz);
        int bonus = (int) ((1.0 - lotDistance / c.r) * 14);
        return lot(level, x, z, lx - STREET, lz - STREET, base, bonus, lotHash, colHash, top);
    }

    // ---------------------------------------------------------------------------------------- walls
    private static boolean wall(WorldGenLevel level, City c, int x, int z, int dx, int dz, double d, int top, long colHash) {
        boolean gate = Math.abs(dx) <= 3 || Math.abs(dz) <= 3;
        double angle = Math.atan2(dz, dx);
        double health = Math.sin(angle * 7 + (c.hash & 63)) + 0.7 * Math.sin(angle * 17 + (c.hash >>> 6 & 63));
        // a round tower every thirty degrees
        double sector = Math.abs(Mth.positiveModulo(angle, Math.PI / 6) - Math.PI / 12);
        boolean tower = sector * c.r < 3.5;
        int height = tower ? 17 : 11;
        if (health < -0.9) {
            height = Math.floorMod(colHash, 3); // collapsed into rubble here
        } else if (health < -0.4) {
            height = 3 + Math.floorMod(colHash, 5);
        }
        boolean outer = d > c.r - 1;
        for (int y = 0; y < height; y++) {
            int wy = top + y;
            if (gate && y < 7) {
                continue; // the gate arch over the road
            }
            BlockState b = y == 8 && outer ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : bricks(colHash >>> y);
            set(level, x, wy, z, b);
        }
        if (gate) {
            set(level, x, top - 1, z, s(Realm.WIREWRIGHT_TILES.get()));
        }
        if (height >= 11 && outer && Math.floorMod(x + z, 2) == 0) {
            set(level, x, top + height, z, bricks(colHash)); // crenellations
        }
        if (tower && height >= 17 && Math.floorMod(colHash, 9) == 0) {
            set(level, x, top + height, z, s(Blocks.LIGHTNING_ROD));
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------- streets
    private static boolean street(WorldGenLevel level, int x, int z, int dx, int dz, int lx, int lz, int top, long colHash) {
        if (!level.getFluidState(new BlockPos(x, top, z)).isEmpty() || !level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty()) {
            return false;
        }
        boolean avenue = Math.abs(dx) <= 3 || Math.abs(dz) <= 3;
        int hole = Math.floorMod(colHash, 100);
        BlockState paving;
        if (avenue) {
            paving = (dx == 0 || dz == 0) ? s(Realm.REDSTONE_VEIN.get()) : hole < 12 ? s(Realm.CRACKED_REALMSTONE_BRICKS.get()) : s(Realm.WIREWRIGHT_TILES.get());
        } else {
            if (hole < 12) {
                return false;
            }
            paving = bricks(colHash >>> 9);
        }
        set(level, x, top - 1, z, paving);
        // clear plants and rocks off the road
        BlockPos above = new BlockPos(x, top, z);
        if (!level.getBlockState(above).isAir() && level.getBlockState(above).canBeReplaced()) {
            set(level, x, top, z, s(Blocks.AIR));
        }
        // a lamp post on each corner of a crossing: lit where power still flows
        if (lx == 0 && lz == 0) {
            BlockState post = s(Realm.RUST_PLATING.get());
            set(level, x, top, z, post);
            set(level, x, top + 1, z, post);
            set(level, x, top + 2, z, post);
            if (Math.floorMod(colHash >>> 12, 3) > 0) {
                set(level, x, top + 3, z, s(Blocks.REDSTONE_BLOCK));
                set(level, x, top + 4, z, litLamp());
            } else {
                set(level, x, top + 3, z, ModRegistry.INSTANT_LAMP.get().defaultBlockState());
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------- the plaza and the Cradle
    private static boolean plaza(WorldGenLevel level, City c, int x, int z, int dx, int dz, double d, int top, long colHash) {
        int base = c.base;
        flatten(level, x, z, top, base, s(Realm.REALMSTONE_BRICKS.get()), base + 3);
        double angle = Math.atan2(dz, dx);
        boolean ray = Math.abs(Math.sin(angle * 4)) * d < 0.6;
        BlockState floor;
        if (d >= 11 && d < 12 || d >= 19 && d < 20) {
            floor = s(Realm.CHISELED_REALMSTONE_BRICKS.get());
        } else if (ray) {
            floor = s(Realm.REDSTONE_VEIN.get());
        } else {
            floor = Math.floorMod(colHash, 7) == 0 ? s(Realm.CRACKED_REALMSTONE_BRICKS.get()) : s(Realm.WIREWRIGHT_TILES.get());
        }
        set(level, x, base, z, floor);

        // the crater where the bell fell before it rose
        if (d < 9) {
            int depth = (int) Math.round(6 * (1 - (d / 9) * (d / 9)));
            for (int y = base; y > base - depth; y--) {
                set(level, x, y, z, s(Blocks.AIR));
            }
            int floorY = base - depth;
            set(level, x, floorY, z, Math.floorMod(colHash, 3) == 0 ? s(Realm.BELL_BRONZE.get()) : s(Realm.REDSTONE_VEIN.get()));
            return true;
        }
        // shards of the bell scattered over the plaza
        if (Math.floorMod(colHash >>> 5, 40) == 0) {
            set(level, x, base + 1, z, s(Realm.BELL_BRONZE.get()));
            if (Math.floorMod(colHash >>> 11, 3) == 0) {
                set(level, x, base + 2, z, s(Realm.BELL_BRONZE.get()));
            }
        }
        // a ring of clock pylons
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int px = (int) Math.round(Math.cos(a) * 23.5);
            int pz = (int) Math.round(Math.sin(a) * 23.5);
            if (Math.abs(dx - px) <= 1 && Math.abs(dz - pz) <= 1) {
                boolean center = dx == px && dz == pz;
                for (int y = 1; y <= 9; y++) {
                    set(level, x, base + y, z, y % 4 == 0 ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : s(Realm.REALMSTONE_BRICKS.get()));
                }
                if (center) {
                    boolean powered = Math.floorMod(c.hash >>> k, 2) == 0;
                    set(level, x, base + 10, z, powered ? s(Blocks.REDSTONE_BLOCK) : s(Realm.RUST_PLATING.get()));
                    set(level, x, base + 11, z, powered ? litLamp() : ModRegistry.INSTANT_LAMP.get().defaultBlockState());
                } else if (Math.abs(dx - px) == 1 && Math.abs(dz - pz) == 1) {
                    set(level, x, base + 10, z, s(Blocks.LIGHTNING_ROD));
                }
            }
        }
        cradle(level, x, z, dx, dz, base, colHash);
        return true;
    }

    /** The empty Cradle: two great pillars and a crossbeam snapped in the middle, chains hanging from the stumps. */
    private static void cradle(WorldGenLevel level, int x, int z, int dx, int dz, int base, long colHash) {
        int top = 46;
        boolean pillar = Math.abs(Math.abs(dx) - 16) <= 2 && Math.abs(dz) <= 2;
        if (pillar) {
            boolean outerFace = Math.abs(dx) == 18 || Math.abs(dz) == 2;
            for (int y = 1; y <= top; y++) {
                BlockState b;
                if (y % 7 == 0) {
                    b = s(Realm.CHISELED_REALMSTONE_BRICKS.get());
                } else if (outerFace && dz == 0) {
                    b = s(Realm.REDSTONE_VEIN.get());
                } else {
                    b = bricks(colHash >>> (y % 40));
                }
                set(level, x, base + y, z, b);
            }
            return;
        }
        // buttresses at the foot of each pillar
        if (Math.abs(Math.abs(dx) - 16) <= 2 && Math.abs(dz) >= 3 && Math.abs(dz) <= 4) {
            int h = Math.abs(dz) == 3 ? 7 : 4;
            for (int y = 1; y <= h; y++) {
                set(level, x, base + y, z, s(Realm.REALMSTONE_BRICKS.get()));
            }
        }
        // the crossbeam, snapped: the two stumps end jaggedly short of the middle
        if (Math.abs(dz) <= 2 && Math.abs(dx) < 16) {
            int stump = 3 + Math.floorMod(colHash, 3);
            if (Math.abs(dx) > stump) {
                for (int y = top - 4; y <= top; y++) {
                    BlockState b = y == top ? s(Realm.BELL_BRONZE.get()) : s(Realm.RUST_PLATING.get());
                    set(level, x, base + y, z, b);
                }
            }
            // chains still hanging from the beam
            if (dz == 0 && (Math.abs(dx) == 7 || Math.abs(dx) == 11)) {
                int len = Math.abs(dx) == 7 ? 18 : 10;
                for (int y = top - 5; y > top - 5 - len; y--) {
                    set(level, x, base + y, z, s(Blocks.CHAIN).setValue(ChainBlock.AXIS, Direction.Axis.Y));
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------- city blocks
    /**
     * One column of a city block. {@code u, v} run 0..14 across the lot. The lot hash picks what stands there: a hall,
     * a bell tower, a foundry, a garden or rubble; and whether it still stands or has fallen in.
     */
    private static boolean lot(WorldGenLevel level, int x, int z, int u, int v, int base, int bonus, long h, long colHash, int top) {
        int type = bits(h, 0, 10);
        boolean ruined = bits(h, 8, 100) < 62;
        boolean powered = bits(h, 16, 3) > 0;
        if (type >= 8) {
            return rubble(level, x, z, top, colHash);
        }
        if (type == 7) {
            return garden(level, x, z, u, v, top, h, colHash);
        }
        int a;
        int b;
        int height;
        if (type >= 4 && type <= 5) {
            a = 4;
            b = 10;
            height = 18 + bits(h, 20, 14) + bonus;
        } else {
            a = 1;
            b = 13;
            height = type == 6 ? 9 : 7 + bits(h, 20, 9) + bonus / 2;
        }
        // outside the footprint: a paved yard
        if (u < a || u > b || v < a || v > b) {
            if (level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty()) {
                set(level, x, top - 1, z, Math.floorMod(colHash, 5) == 0 ? s(Realm.CRACKED_REALMSTONE_BRICKS.get()) : s(Realm.WIREWRIGHT_TILES.get()));
            }
            return true;
        }
        int colH = height;
        if (ruined) {
            double shape = 0.5 + 0.5 * Math.sin(u * 0.45 + v * 0.35 + (h & 31)) + (Math.floorMod(colHash, 100) - 50) / 250.0;
            colH = (int) (height * Mth.clamp(0.15 + 0.75 * shape, 0.1, 1.0));
        }
        flatten(level, x, z, top, base, s(Realm.REALMSTONE_BRICKS.get()), base + height + 3);
        boolean edge = u == a || u == b || v == a || v == b;
        boolean corner = (u == a || u == b) && (v == a || v == b);
        int along = (u == a || u == b) ? v : u;
        set(level, x, base, z, edge ? s(Realm.REALMSTONE_BRICKS.get()) : s(Realm.WIREWRIGHT_TILES.get()));
        boolean door = v == a && Math.abs(u - (a + b) / 2) <= 1;
        for (int y = 1; y <= colH; y++) {
            int wy = base + y;
            BlockState block = null;
            if (corner) {
                block = y % 6 == 0 ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : s(Realm.RUST_PLATING.get());
            } else if (edge) {
                if (door && y <= 3) {
                    continue;
                }
                int f = y % 5;
                if (f == 0) {
                    block = (along % 4 == 0) ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : bricks(colHash >>> y);
                } else if ((f == 2 || f == 3) && along % 3 == 1) {
                    block = ruined ? null : s(Blocks.RED_STAINED_GLASS);
                } else {
                    block = ruined && Math.floorMod(colHash >>> y, 4) == 0 ? s(Realm.CRACKED_REALMSTONE_BRICKS.get()) : bricks(colHash >>> (y + 3));
                }
            } else if (y % 5 == 0 && (!ruined || Math.floorMod(colHash >>> y, 5) < 3)) {
                // upper floors, and a lamp hanging under each in the middle of the room
                boolean middle = u == (a + b) / 2 && v == (a + b) / 2;
                block = middle && powered ? s(Blocks.REDSTONE_BLOCK) : s(Realm.WIREWRIGHT_TILES.get());
                if (middle) {
                    set(level, x, wy - 1, z, powered ? litLamp() : ModRegistry.INSTANT_LAMP.get().defaultBlockState());
                }
            }
            if (block != null) {
                set(level, x, wy, z, block);
            }
        }
        if (!ruined) {
            // a flat roof with a rim, and what stands on it
            set(level, x, base + colH + 1, z, edge ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : s(Realm.REALMSTONE_BRICKS.get()));
            int mid = (a + b) / 2;
            if (type >= 4 && type <= 5 && u == mid && v == mid) {
                set(level, x, base + colH + 2, z, s(Blocks.BELL).setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
            }
            if (corner && type >= 4 && type <= 5) {
                set(level, x, base + colH + 2, z, s(Blocks.LIGHTNING_ROD));
            }
            if (u == a + 1 && v == a + 1 && bits(h, 24, 3) == 0) {
                BlockPos chest = new BlockPos(x, base + 1, z);
                level.setBlock(chest, s(Blocks.CHEST).setValue(ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
                RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest,
                        ResourceKey.create(Registries.LOOT_TABLE, Realm.id("chests/realm_relic")));
            }
        } else if (!edge && Math.floorMod(colHash, 6) == 0) {
            // fallen masonry inside the shell
            set(level, x, base + 1, z, s(Realm.REALMSTONE.get()));
        }
        if (type == 6) {
            foundry(level, x, z, u, v, base, colH, ruined, colHash);
        }
        return true;
    }

    /** A bell foundry: a chimney in one corner, a bronze mould in the middle, and the furnace fires around it. */
    private static void foundry(WorldGenLevel level, int x, int z, int u, int v, int base, int colH, boolean ruined, long colHash) {
        if (u >= 2 && u <= 4 && v >= 9 && v <= 11) {
            boolean hollow = u == 3 && v == 10;
            int chimney = ruined ? colH + 4 : colH + 14;
            for (int y = 1; y <= chimney; y++) {
                set(level, x, base + y, z, hollow ? s(Blocks.AIR) : y % 6 == 0 ? s(Realm.RUST_PLATING.get()) : s(Realm.REALMSTONE_BRICKS.get()));
            }
            if (hollow) {
                set(level, x, base + 1, z, s(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, !ruined).setValue(CampfireBlock.SIGNAL_FIRE, true));
            }
        }
        if (u >= 6 && u <= 8 && v >= 6 && v <= 8) {
            set(level, x, base + 1, z, s(Realm.BELL_BRONZE.get()));
            if (u == 7 && v == 7) {
                set(level, x, base + 2, z, s(Realm.BELL_BRONZE.get()));
            }
        }
        if ((u == 5 || u == 9) && v == 7) {
            set(level, x, base + 1, z, s(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, false));
        }
    }

    private static boolean garden(WorldGenLevel level, int x, int z, int u, int v, int top, long h, long colHash) {
        boolean edge = u == 0 || u == 14 || v == 0 || v == 14;
        if (!level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty()) {
            return false;
        }
        if (edge) {
            if (Math.floorMod(colHash, 4) > 0) {
                set(level, x, top, z, bricks(colHash >>> 3));
            }
            return true;
        }
        set(level, x, top - 1, z, s(Realm.HEATHER_TURF.get()));
        set(level, x, top, z, Math.floorMod(colHash, 3) == 0 ? s(Realm.CRIMSON_HEATHER.get()) : s(Blocks.AIR));
        // a memorial obelisk in the middle, a crystal still burning on top
        if (u == 7 && v == 7) {
            for (int y = 0; y < 6; y++) {
                set(level, x, top + y, z, y % 2 == 0 ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : s(Realm.REALMSTONE_BRICKS.get()));
            }
            set(level, x, top + 6, z, s(Realm.REDSTONE_CLUSTER.get()).setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, Direction.UP));
        }
        return true;
    }

    private static boolean rubble(WorldGenLevel level, int x, int z, int top, long colHash) {
        if (!level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty()) {
            return false;
        }
        int pile = Math.floorMod(colHash, 7) - 3;
        for (int y = 0; y < pile; y++) {
            set(level, x, top + y, z, y == pile - 1 && Math.floorMod(colHash >>> 4, 4) == 0 ? s(Realm.REALMSTONE.get()) : bricks(colHash >>> y));
        }
        if (pile <= 0 && Math.floorMod(colHash >>> 8, 25) == 0) {
            set(level, x, top, z, s(Realm.BELL_BRONZE.get()));
        }
        return true;
    }
}
