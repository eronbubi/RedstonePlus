package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * The Grid: the lightlines of the realm, glowing tracks the light cycles lock onto and race along.
 * <p>
 * The Wirewrights laid them out as a lattice: a line every {@link #SPACING} blocks north-south and east-west, and at
 * every crossing a node with a structure on it (a cycle depot, a junction spire, the trackworks where the Trackwrights are
 * made, or a bell gate). Like the cities, all of it is laid out from world coordinates and the seed, so every chunk builds
 * its own slice. Many lines broke over the ages; the Trackwrights that still walk the realm fill the gaps and lay new
 * branch lines of their own (see {@link Trackwright}), so the Grid keeps growing while you play.
 * <p>
 * A lightline is a row of {@link Realm#LIGHTLINE} tiles at ground level, one block wide. Lines meet at right angles;
 * a tile with tiles on more than two sides is a junction. Tiles may step up or down by up to {@link #MAX_STEP} blocks
 * from one to the next: the cycles ride a grav-lift over such steps.
 */
public final class Grid {
    /** Distance between the lattice lines. */
    public static final int SPACING = 256;
    /** Where the lattice lines run: x and z are lines where (coordinate - OFFSET) is a multiple of SPACING. */
    public static final int OFFSET = 96;
    /** Half the width of a node's plaza. */
    public static final int NODE_R = 13;
    /** How far a lightline may climb or drop from one tile to the next. */
    public static final int MAX_STEP = 6;
    /** Stub length laid out from every node, even where the line to the next node fell; Trackwrights extend them. */
    private static final int STUB = 22;

    private Grid() {
    }

    // ============================================================================================ the lattice
    /** The lattice index of the line or node nearest to a coordinate. */
    public static int cell(int coordinate) {
        return Math.floorDiv(coordinate - OFFSET + SPACING / 2, SPACING);
    }

    /** The coordinate of lattice line / node {@code index}. */
    public static int line(int index) {
        return index * SPACING + OFFSET;
    }

    /** True if x runs along a north-south lattice line. */
    public static boolean onNorthSouthLine(int x) {
        return Math.floorMod(x - OFFSET, SPACING) == 0;
    }

    /** True if z runs along an east-west lattice line. */
    public static boolean onEastWestLine(int z) {
        return Math.floorMod(z - OFFSET, SPACING) == 0;
    }

    // ============================================================================================ tiles
    public static boolean isTile(BlockState state) {
        return state.is(Realm.LIGHTLINE.get());
    }

    /**
     * The y of the lightline tile in column (x, z) closest to {@code nearY}, searching {@link #MAX_STEP} + 2 blocks up and down,
     * or {@link Integer#MIN_VALUE} if the column has none.
     */
    public static int tileY(BlockGetter level, int x, int z, int nearY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int range = MAX_STEP + 2;
        for (int d = 0; d <= range; d++) {
            if (isTile(level.getBlockState(p.set(x, nearY - d, z)))) {
                return nearY - d;
            }
            if (d > 0 && isTile(level.getBlockState(p.set(x, nearY + d, z)))) {
                return nearY + d;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * The highest tile near the top of a column (what maps and Trackwrights look at), or MIN_VALUE. It looks a little way
     * down from the heightmap, so tiles under a roof (depot halls, spires, bell gates) or a post still count.
     */
    public static int surfaceTileY(Level level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = top; y >= top - 14; y--) {
            if (isTile(level.getBlockState(p.set(x, y, z)))) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Natural ground a Trackwright may pave (block tag redstoneplus:lightline_pavable): never builds, valuables or tree trunks. */
    public static final net.minecraft.tags.TagKey<Block> PAVABLE = net.minecraft.tags.TagKey.create(Registries.BLOCK, Realm.id("lightline_pavable"));

    /** Ground a lightline may be laid into: natural, solid, whole blocks, nothing the Wirewrights built, nothing with contents. */
    static boolean pavable(Level level, BlockPos pos, BlockState state) {
        return state.is(PAVABLE) && state.getFluidState().isEmpty() && !state.hasBlockEntity() && state.isCollisionShapeFullBlock(level, pos);
    }

    /** True inside the walls of the Sealed Reach: nothing of the Grid goes there. */
    static boolean sealed(LevelAccessor level, BlockPos pos) {
        return level.getBiome(pos).is(SealedReach.BIOME);
    }

    /**
     * Lays one tile in column (x, z) as a Trackwright does: into the ground, or on the surface of shallow water as a
     * causeway. {@code refY} is the tile before it (MIN_VALUE for none): no step steeper than MAX_STEP. Returns the tile's
     * y, or MIN_VALUE if the column cannot take a tile.
     */
    static int lay(ServerLevel level, int x, int z, int refY) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos pos = new BlockPos(x, top, z);
        if (!level.isLoaded(pos) || sealed(level, pos)) {
            return Integer.MIN_VALUE;
        }
        BlockState state = level.getBlockState(pos);
        if (isTile(state)) {
            return top;
        }
        if (refY != Integer.MIN_VALUE && Math.abs(top - refY) > MAX_STEP) {
            return Integer.MIN_VALUE;
        }
        if (state.is(Blocks.WATER)) {
            // a causeway over shallow water only
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            if (top - floor > 10) {
                return Integer.MIN_VALUE;
            }
        } else if (!pavable(level, pos, state)) {
            return Integer.MIN_VALUE;
        }
        level.setBlock(pos, Realm.LIGHTLINE.get().defaultBlockState(), Block.UPDATE_ALL);
        for (int y = 1; y <= 2; y++) {
            BlockPos above = pos.above(y);
            BlockState a = level.getBlockState(above);
            if (!a.isAir() && a.canBeReplaced() && a.getFluidState().isEmpty()) {
                level.destroyBlock(above, false);
            }
        }
        return top;
    }

    // ============================================================================================ world generation
    private static final Map<Long, Integer> NODE_BASE = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> NODE_STANDS = new ConcurrentHashMap<>();

    /** What stands at a node. */
    public enum NodeType {
        DEPOT, SPIRE, WORKS, BELL_GATE
    }

    static NodeType nodeType(long seed, int i, int j) {
        int r = (int) Math.floorMod(RealmCities.hash(seed, i, j, 41), 100L);
        return r < 35 ? NodeType.DEPOT : r < 60 ? NodeType.SPIRE : r < 80 ? NodeType.WORKS : NodeType.BELL_GATE;
    }

    /** Ground height at a node's centre (from the noise, before features), never below the sea's surface. */
    private static int nodeBase(WorldGenLevel level, ChunkGenerator gen, RandomState random, int i, int j) {
        long key = RealmCities.hash(level.getSeed(), i, j, 97); // per world: a second world in the same session has other nodes
        Integer y = NODE_BASE.get(key);
        if (y == null) {
            y = Math.max(level.getSeaLevel(), gen.getBaseHeight(line(i), line(j), Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1);
            if (NODE_BASE.size() > 16384) {
                NODE_BASE.clear();
            }
            NODE_BASE.put(key, y);
        }
        return y;
    }

    /** False where a node cannot stand: in the sea, inside a city or inside the Sealed Reach. */
    private static boolean nodeStands(WorldGenLevel level, ChunkGenerator gen, RandomState random, int i, int j) {
        long key = RealmCities.hash(level.getSeed(), i, j, 97); // per world: a second world in the same session has other nodes
        Boolean known = NODE_STANDS.get(key);
        if (known == null) {
            known = computeNodeStands(level, gen, random, i, j);
            if (NODE_STANDS.size() > 16384) {
                NODE_STANDS.clear();
            }
            NODE_STANDS.put(key, known);
        }
        return known;
    }

    private static boolean computeNodeStands(WorldGenLevel level, ChunkGenerator gen, RandomState random, int i, int j) {
        int x = line(i);
        int z = line(j);
        int base = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
        if (base < level.getSeaLevel() - 6) {
            return false;
        }
        var biome = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(base), QuartPos.fromBlock(z), random.sampler());
        return !biome.is(SealedReach.BIOME) && !biome.is(ResourceKey.create(Registries.BIOME, Realm.id("tempest_shoals")))
                && !RealmCities.covers(level, gen, random, x, z) && !Sanctums.coversStatic(x, z, NODE_R + 30) && Artery.s(x, z) > 0.15;
    }

    /**
     * Whether the lattice line between nodes survived at this point: {@code along} is the distance from the node at
     * {@code index}. Some lines are whole, some broke in the middle, some are only stubs at both ends.
     */
    static boolean lineSurvives(long seed, int index, int across, boolean eastWest, int along) {
        if (along <= STUB || along >= SPACING - STUB) {
            return true;
        }
        long h = RealmCities.hash(seed, index, across, eastWest ? 11 : 13);
        int state = (int) Math.floorMod(h, 100L);
        if (state < 55) {
            return true;
        }
        if (state < 85) {
            int a = 40 + (int) Math.floorMod(h >>> 8, 80L);
            int b = a + 24 + (int) Math.floorMod(h >>> 20, 90L);
            return along < a || along > b;
        }
        return false;
    }

    /** One column of the Grid. Returns true if it built anything. */
    static boolean column(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z, int top) {
        long seed = level.getSeed();
        int i = cell(x);
        int j = cell(z);
        int dx = x - line(i);
        int dz = z - line(j);
        if (Math.abs(dx) <= NODE_R && Math.abs(dz) <= NODE_R) {
            if (!nodeStands(level, gen, random, i, j)) {
                return false;
            }
            return node(level, gen, random, x, z, dx, dz, nodeBase(level, gen, random, i, j), nodeType(seed, i, j), seed, i, j, top);
        }
        BlockPos at = new BlockPos(x, top - 1, z);
        if (sealed(level, at) || RealmCities.covers(level, gen, random, x, z) || Sanctums.covers(level, gen, random, x, z)) {
            return false;
        }
        boolean built = false;
        // near a node the line ramps from the ground to the plaza's level, so it never ends at a step too high to ride
        int fromNode = Math.max(Math.abs(dx), Math.abs(dz));
        int rampTo = fromNode <= RAMP && (dx == 0 || dz == 0) && nodeStands(level, gen, random, i, j) ? nodeBase(level, gen, random, i, j)
                : Integer.MIN_VALUE;
        if (dz == 0) {
            int edge = Math.floorDiv(x - OFFSET, SPACING);
            if (lineSurvives(seed, edge, j, true, x - line(edge))) {
                built = lineTile(level, x, z, top, Math.floorMod(x - OFFSET, 48) == 24, rampTo, fromNode);
            }
        } else if (dx == 0) {
            int edge = Math.floorDiv(z - OFFSET, SPACING);
            if (lineSurvives(seed, edge, i, false, z - line(edge))) {
                built = lineTile(level, x, z, top, Math.floorMod(z - OFFSET, 48) == 24, rampTo, fromNode);
            }
        }
        // a light pylon beside the line every 48 blocks, where the line still runs
        if (Math.abs(dz) == 2 && Math.floorMod(x - OFFSET, 48) == 24 && dz > 0) {
            int edge = Math.floorDiv(x - OFFSET, SPACING);
            if (lineSurvives(seed, edge, j, true, x - line(edge))) {
                built |= pylon(level, x, z, top);
            }
        } else if (Math.abs(dx) == 2 && Math.floorMod(z - OFFSET, 48) == 24 && dx > 0) {
            int edge = Math.floorDiv(z - OFFSET, SPACING);
            if (lineSurvives(seed, edge, i, false, z - line(edge))) {
                built |= pylon(level, x, z, top);
            }
        }
        return built;
    }

    private static void set(LevelAccessor level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    private static BlockState s(Block block) {
        return block.defaultBlockState();
    }

    /** How far out from a node's centre the lines ramp to the plaza's level. */
    private static final int RAMP = NODE_R + 28;

    /**
     * A tile of a lattice line: into the ground, or as a causeway on shallow water with a pier now and then. Within
     * {@link #RAMP} of a node ({@code rampTo} is then the plaza's level) the tile sits on a ramp between the ground and the plaza.
     */
    private static boolean lineTile(WorldGenLevel level, int x, int z, int top, boolean pier, int rampTo, int fromNode) {
        int sea = level.getSeaLevel();
        BlockPos ground = new BlockPos(x, top - 1, z);
        boolean wet = !level.getFluidState(new BlockPos(x, top, z)).isEmpty() || !level.getFluidState(ground).isEmpty();
        if (wet) {
            // the water's surface (lakes above sea level too), and only over shallow water: the line ends at the open sea
            int y = top;
            while (y < top + 24 && !level.getFluidState(new BlockPos(x, y + 1, z)).isEmpty()) {
                y++;
            }
            if (y - top > 12 || !level.getFluidState(new BlockPos(x, y, z)).is(net.minecraft.tags.FluidTags.WATER)) {
                return false;
            }
            set(level, x, y, z, s(Realm.LIGHTLINE.get()));
            if (pier || Math.floorMod(x + z, 8) == 0) {
                for (int yy = y - 1; yy >= top - 1 && yy > y - 16; yy--) {
                    set(level, x, yy, z, s(Realm.REALMSTONE_BRICKS.get()));
                }
            }
            return true;
        }
        BlockState below = level.getBlockState(ground);
        if (below.isAir() || below.is(Blocks.LAVA)) {
            return false;
        }
        int tileY = top - 1;
        if (rampTo != Integer.MIN_VALUE) {
            double t = (fromNode - NODE_R) / (double) (RAMP - NODE_R);
            tileY = (int) Math.round(rampTo + (top - 1 - rampTo) * t);
            for (int y = top - 1; y < tileY; y++) {
                set(level, x, y, z, s(Realm.REALMSTONE_BRICKS.get())); // the ramp's embankment
            }
            for (int y = tileY + 1; y < top; y++) {
                set(level, x, y, z, s(Blocks.AIR)); // the ramp's cutting
            }
        }
        set(level, x, tileY, z, s(Realm.LIGHTLINE.get()));
        top = Math.max(top, tileY + 1);
        for (int y = top; y <= top + 2; y++) {
            BlockState a = level.getBlockState(new BlockPos(x, y, z));
            if (!a.isAir() && (a.canBeReplaced() || a.is(Realm.REDSTONE_CLUSTER.get())) && a.getFluidState().isEmpty()) {
                set(level, x, y, z, s(Blocks.AIR));
            }
        }
        return true;
    }

    /** A post of rust plating with a grid beacon on top, beside the line. */
    private static boolean pylon(WorldGenLevel level, int x, int z, int top) {
        if (!level.getFluidState(new BlockPos(x, top, z)).isEmpty() || !level.getBlockState(new BlockPos(x, top - 1, z)).isSolid()) {
            return false;
        }
        set(level, x, top, z, s(Realm.RUST_PLATING.get()));
        set(level, x, top + 1, z, s(Realm.RUST_PLATING.get()));
        set(level, x, top + 2, z, s(Realm.GRID_BEACON.get()));
        return true;
    }

    // ---------------------------------------------------------------------------------------- nodes
    /** One column of a node: the shared plaza with its roundabout, then whatever the node type adds. */
    private static boolean node(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z, int dx, int dz, int base,
                                NodeType type, long seed, int i, int j, int top) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        int r = Math.max(ax, az);
        long colHash = RealmCities.hash(seed, x, z, 43);
        // the plaza: filled up to the base, cleared above it
        for (int y = Math.max(top - 1, base - 14); y < base; y++) {
            set(level, x, y, z, s(Realm.REALMSTONE_BRICKS.get()));
        }
        int clear = Math.max(top + 3, base + (type == NodeType.SPIRE ? 4 : 10));
        for (int y = base + 1; y <= clear; y++) {
            if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                set(level, x, y, z, s(Blocks.AIR));
            }
        }
        boolean spoke = dx == 0 || dz == 0;
        boolean ring = r == 8;
        BlockState floor;
        if (spoke || ring) {
            floor = s(Realm.LIGHTLINE.get());
        } else if (r == NODE_R) {
            floor = s(Realm.CHISELED_REALMSTONE_BRICKS.get());
        } else {
            floor = Math.floorMod(colHash, 9) == 0 ? s(Realm.CRACKED_REALMSTONE_BRICKS.get()) : s(Realm.WIREWRIGHT_TILES.get());
        }
        set(level, x, base, z, floor);
        switch (type) {
            case DEPOT -> depot(level, x, z, dx, dz, ax, az, base, colHash);
            case SPIRE -> spire(level, x, z, dx, dz, ax, az, base, colHash);
            case WORKS -> works(level, x, z, dx, dz, ax, az, base, colHash, i, j);
            case BELL_GATE -> bellGate(level, x, z, dx, dz, ax, az, base);
        }
        return true;
    }

    /** Cycle depot: a roofed hall over the roundabout, a beacon hanging in the middle, racks of lit posts, a supply chest. */
    private static void depot(WorldGenLevel level, int x, int z, int dx, int dz, int ax, int az, int base, long colHash) {
        int roof = base + 8;
        if (ax == 11 && az == 11) {
            for (int y = base + 1; y < roof; y++) {
                set(level, x, y, z, y % 3 == 0 ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : s(Realm.RUST_PLATING.get()));
            }
        }
        if (ax <= 11 && az <= 11) {
            boolean skylight = (ax == 4 || az == 4) && Math.max(ax, az) <= 9;
            set(level, x, roof, z, skylight ? s(Blocks.ORANGE_STAINED_GLASS) : ax == 11 || az == 11 ? s(Realm.RUST_PLATING.get())
                    : s(Realm.REALMSTONE_BRICKS.get()));
        }
        if (dx == 0 && dz == 0) {
            set(level, x, roof - 1, z, s(Blocks.CHAIN).setValue(ChainBlock.AXIS, Direction.Axis.Y));
            set(level, x, roof - 2, z, s(Realm.GRID_BEACON.get()));
        }
        // racks: lit posts where cycles wait, in the four corners inside the ring
        if (ax >= 10 && az >= 3 && az <= 6 && (az & 1) == 1 || az >= 10 && ax >= 3 && ax <= 6 && (ax & 1) == 1) {
            set(level, x, base + 1, z, s(Blocks.REDSTONE_BLOCK));
            set(level, x, base + 2, z, ModRegistry.INSTANT_LAMP.get().defaultBlockState().setValue(SimpleBlocks.Lamp.LIT, true));
        }
        if (dx == 10 && dz == 10) {
            BlockPos chest = new BlockPos(x, base + 1, z);
            level.setBlock(chest, s(Blocks.CHEST).setValue(ChestBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest, ResourceKey.create(Registries.LOOT_TABLE, Realm.id("chests/grid_depot")));
        }
    }

    /** Junction spire: four legs arch over the crossing, a tower with glowing corner seams rises from them, a beacon on top. */
    private static void spire(WorldGenLevel level, int x, int z, int dx, int dz, int ax, int az, int base, long colHash) {
        int legTop = base + 9;
        int height = base + 34;
        if (ax == 3 && az == 3) {
            for (int y = base + 1; y < legTop; y++) {
                set(level, x, y, z, s(Realm.RUST_PLATING.get()));
            }
        }
        if (ax <= 3 && az <= 3) {
            boolean corner = ax == 3 && az == 3;
            boolean shell = ax == 3 || az == 3;
            for (int y = legTop; y <= height; y++) {
                BlockState b;
                if (corner) {
                    b = s(Realm.LIGHTLINE.get());
                } else if (shell) {
                    b = y % 6 == 0 ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : s(Realm.REALMSTONE_BRICKS.get());
                } else {
                    b = y == legTop ? s(Realm.REALMSTONE_BRICKS.get()) : s(Blocks.AIR);
                }
                set(level, x, y, z, b);
            }
            set(level, x, height + 1, z, ax <= 1 && az <= 1 ? s(Realm.GRID_BEACON.get()) : s(Realm.RUST_PLATING.get()));
            if (dx == 0 && dz == 0) {
                set(level, x, height + 2, z, s(Realm.GRID_BEACON.get()));
                set(level, x, height + 3, z, s(Blocks.LIGHTNING_ROD));
            }
        }
        // signal lamps on posts at the four ways in
        if (ax == 2 && az == 11 || az == 2 && ax == 11) {
            set(level, x, base + 1, z, s(Blocks.REDSTONE_BLOCK));
            set(level, x, base + 2, z, ModRegistry.INSTANT_LAMP.get().defaultBlockState().setValue(SimpleBlocks.Lamp.LIT, true));
        }
    }

    /**
     * Trackworks: a walled yard where Trackwrights are built. Gates where the lines come in, an open hall, a stockpile of
     * lightline, a gantry crane with a tile hanging from it, and two Trackwrights at work.
     */
    private static void works(WorldGenLevel level, int x, int z, int dx, int dz, int ax, int az, int base, long colHash, int i, int j) {
        boolean wall = Math.max(ax, az) == 12;
        boolean gate = ax <= 1 || az <= 1;
        if (wall && !gate) {
            for (int y = base + 1; y <= base + 4; y++) {
                set(level, x, y, z, y == base + 4 ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : s(Realm.REALMSTONE_BRICKS.get()));
            }
        }
        // the hall in the south-east quarter
        if (dx >= 2 && dx <= 11 && dz >= 2 && dz <= 11) {
            if ((dx == 2 || dx == 11) && (dz == 2 || dz == 11)) {
                for (int y = base + 1; y <= base + 5; y++) {
                    set(level, x, y, z, s(Realm.RUST_PLATING.get()));
                }
            }
            set(level, x, base + 6, z, (dx + dz) % 4 == 0 ? s(Blocks.ORANGE_STAINED_GLASS) : s(Realm.REALMSTONE_BRICKS.get()));
            if (dx == 6 && dz == 6) {
                set(level, x, base + 5, z, s(Realm.GRID_BEACON.get()));
            }
        }
        // a stockpile of crated tiles in the south-west quarter (crates, not track: a cycle must not mistake it for a line)
        if (dx >= -12 && dx <= -10 && dz >= 3 && dz <= 6) {
            int h = 1 + (int) Math.floorMod(colHash, 2L);
            for (int y = base + 1; y <= base + h; y++) {
                set(level, x, y, z, y == base + h ? s(Realm.GRID_BEACON.get()) : s(Realm.RUST_PLATING.get()));
            }
        }
        // gantry crane in the north-west quarter
        if ((dx == -11 || dx == -3) && dz == -7) {
            for (int y = base + 1; y <= base + 9; y++) {
                set(level, x, y, z, s(Realm.RUST_PLATING.get()));
            }
        }
        if (dx >= -11 && dx <= -3 && dz == -7) {
            set(level, x, base + 10, z, s(Realm.RUST_PLATING.get()));
            if (dx == -7) {
                for (int y = base + 6; y <= base + 9; y++) {
                    set(level, x, y, z, s(Blocks.CHAIN).setValue(ChainBlock.AXIS, Direction.Axis.Y));
                }
                set(level, x, base + 5, z, s(Realm.GRID_BEACON.get()));
            }
        }
        // the Trackwrights: spawned once, from the centre column
        if (dx == 0 && dz == 0 && level instanceof net.minecraft.server.level.WorldGenRegion region) {
            RandomSource rnd = RandomSource.create(RealmCities.hash(level.getSeed(), i, j, 47));
            for (int k = 0; k < 2; k++) {
                Trackwright tw = Realm.TRACKWRIGHT.get().create(region.getLevel());
                if (tw != null) {
                    tw.moveTo(x + 0.5 + (k == 0 ? 4 : -4), base + 1, z + 0.5 + (k == 0 ? -4 : 4), rnd.nextFloat() * 360.0F, 0.0F);
                    tw.setPersistenceRequired();
                    tw.finalizeSpawn(region, region.getCurrentDifficultyAt(tw.blockPosition()), MobSpawnType.STRUCTURE, null);
                    region.addFreshEntity(tw);
                }
            }
        }
    }

    /** Bell gate: an arch over each of the four ways in, a bell hanging over the track; the plaza is inlaid with bronze. */
    private static void bellGate(WorldGenLevel level, int x, int z, int dx, int dz, int ax, int az, int base) {
        boolean postNS = az == 10 && ax == 2;
        boolean postEW = ax == 10 && az == 2;
        if (postNS || postEW) {
            for (int y = base + 1; y <= base + 6; y++) {
                set(level, x, y, z, y == base + 6 ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : s(Realm.REALMSTONE_BRICKS.get()));
            }
            set(level, x, base + 7, z, ModRegistry.INSTANT_LAMP.get().defaultBlockState().setValue(SimpleBlocks.Lamp.LIT, true));
            set(level, x, base + 6, z, s(Blocks.REDSTONE_BLOCK));
        }
        if (az == 10 && ax <= 1 || ax == 10 && az <= 1) {
            set(level, x, base + 6, z, s(Realm.BELL_BRONZE.get()));
            if (dx == 0 || dz == 0) {
                Direction facing = ax == 10 ? Direction.NORTH : Direction.EAST;
                set(level, x, base + 5, z, s(Blocks.BELL).setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING).setValue(BellBlock.FACING, facing));
            }
        }
        // bronze inlay in a diamond around the centre
        if (ax + az == 5 && ax != 0 && az != 0) {
            set(level, x, base, z, s(Realm.BELL_BRONZE.get()));
        }
    }
}
