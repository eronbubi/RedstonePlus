package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The seats of the five Echoes. Every {@link #REGION} x REGION blocks of the realm hold one sanctum of each Echo, a few
 * hundred blocks out from the region's middle, so wherever you are all five are within reach. Each is its own place:
 * the Press Throne of Force, the Switchboard of Signal, the Belfry Hollow of Resonance, the Furnace Crown of Heat and the
 * Sluice Basin of Flow. At its centre lies the Echo Seal; above it a shackle of bronze, and from the shackle a giant
 * chain rises into the sky to the Great Bell. Using the seal wakes the Echo.
 * <p>
 * Like the cities, a sanctum is built column by column from world coordinates and the seed. When its Echo falls, its chain
 * comes down link by link, and is never built there again.
 */
public final class Sanctums {
    public static final int REGION = 1536;
    /** Half the width of a sanctum. */
    public static final int RADIUS = 24;
    /** Where the giant chain starts above the seal, and how high it reaches. */
    private static final int CHAIN_START = 12;
    private static final int CHAIN_TOP = 300;

    private Sanctums() {
    }

    /** One sanctum: which Echo, its centre, and the height of its floor. */
    public record Seat(RealmStory.Echo echo, int x, int z, int base) {
        public BlockPos pos() {
            return new BlockPos(this.x, this.base, this.z);
        }
    }

    private static final Map<Long, List<Seat>> SEATS = new ConcurrentHashMap<>();

    /** The sanctums of the region around (x, z), worked out from the seed once and then remembered. */
    public static List<Seat> seats(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z) {
        int rx = Math.floorDiv(x, REGION);
        int rz = Math.floorDiv(z, REGION);
        long key = RealmCities.hash(level.getSeed(), rx, rz, 71);
        List<Seat> seats = SEATS.get(key);
        if (seats == null) {
            seats = new ArrayList<>();
            long h = RealmCities.hash(level.getSeed(), rx, rz, 73);
            double spin = Math.floorMod(h, 360L) * Mth.DEG_TO_RAD;
            for (RealmStory.Echo echo : RealmStory.Echo.values()) {
                for (int attempt = 0; attempt < 6; attempt++) {
                    long a = RealmCities.hash(level.getSeed(), rx * 8 + echo.ordinal(), rz, 80 + attempt);
                    double angle = spin + echo.ordinal() * Mth.TWO_PI / 5 + (Math.floorMod(a, 40L) - 20) * Mth.DEG_TO_RAD;
                    double dist = 300 + Math.floorMod(a >>> 8, 160L);
                    int cx = rx * REGION + REGION / 2 + (int) Math.round(Math.cos(angle) * dist);
                    int cz = rz * REGION + REGION / 2 + (int) Math.round(Math.sin(angle) * dist);
                    if (fits(level, gen, random, cx, cz)) {
                        int base = Math.max(level.getSeaLevel(), gen.getBaseHeight(cx, cz, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1);
                        seats.add(new Seat(echo, cx, cz, base));
                        break;
                    }
                }
            }
            if (SEATS.size() > 4096) {
                SEATS.clear();
            }
            SEATS.put(key, seats);
        }
        return seats;
    }

    /** A sanctum stands on dry land, outside the Sealed Reach and the cities, and away from the Grid's nodes. */
    private static boolean fits(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z) {
        int ground = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
        if (ground < level.getSeaLevel() - 4) {
            return false;
        }
        var biome = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(ground), QuartPos.fromBlock(z), random.sampler());
        if (biome.is(SealedReach.BIOME) || biome.is(ResourceKey.create(Registries.BIOME, Realm.id("tempest_shoals")))) {
            return false;
        }
        for (int[] o : new int[][]{{0, 0}, {RADIUS, 0}, {-RADIUS, 0}, {0, RADIUS}, {0, -RADIUS}}) {
            if (RealmCities.covers(level, gen, random, x + o[0], z + o[1])) {
                return false;
            }
        }
        int nx = Grid.line(Grid.cell(x));
        int nz = Grid.line(Grid.cell(z));
        return Math.abs(x - nx) > Grid.NODE_R + RADIUS + 30 || Math.abs(z - nz) > Grid.NODE_R + RADIUS + 30;
    }

    @Nullable
    private static Seat seatAt(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z, int pad) {
        for (Seat seat : seats(level, gen, random, x, z)) {
            if (Math.abs(x - seat.x) <= RADIUS + pad && Math.abs(z - seat.z) <= RADIUS + pad) {
                return seat;
            }
        }
        return null;
    }

    /** True on and around a sanctum: the Grid's lines and the scenery keep out. */
    public static boolean covers(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z) {
        return seatAt(level, gen, random, x, z, 4) != null;
    }

    // ============================================================================================ building
    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    private static BlockState litLamp() {
        return ModRegistry.INSTANT_LAMP.get().defaultBlockState().setValue(SimpleBlocks.Lamp.LIT, true);
    }

    /** One column of whatever sanctum covers it. */
    static boolean column(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z, int top) {
        Seat seat = seatAt(level, gen, random, x, z, 0);
        if (seat == null) {
            return false;
        }
        int dx = x - seat.x;
        int dz = z - seat.z;
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r > RADIUS) {
            return false;
        }
        int base = seat.base;
        long colHash = RealmCities.hash(level.getSeed(), x, z, 77);
        // the ground: filled up to the floor and cleared above it
        for (int y = Math.max(top - 1, base - 16); y < base; y++) {
            set(level, x, y, z, s(Realm.DEEP_REALMSTONE.get()));
        }
        for (int y = base + 1; y <= Math.max(top + 3, base + 20); y++) {
            if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                set(level, x, y, z, s(Blocks.AIR));
            }
        }
        switch (seat.echo) {
            case FORCE -> pressThrone(level, x, z, dx, dz, r, base, colHash);
            case SIGNAL -> switchboard(level, x, z, dx, dz, r, base, colHash);
            case RESONANCE -> belfryHollow(level, x, z, dx, dz, r, base, colHash);
            case HEAT -> furnaceCrown(level, x, z, dx, dz, r, base, colHash);
            case FLOW -> sluiceBasin(level, x, z, dx, dz, r, base, colHash);
        }
        int sealY = sealY(seat);
        if (dx == 0 && dz == 0) {
            set(level, x, sealY, z, Realm.ECHO_SEAL.get().defaultBlockState().setValue(EchoSeal.ECHO, seat.echo.ordinal()));
        }
        // the shackle and the giant chain, unless this Echo's chain has come down already
        boolean silent = level.getServer() != null && RealmStory.seatSilent(level.getServer(), seat.pos());
        if (!silent && Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
            chain(level, x, z, dx, dz, sealY + CHAIN_START);
        }
        return true;
    }

    /** The height of the seal in a sanctum (the Belfry Hollow lies in a bowl, the others on their floor). */
    static int sealY(Seat seat) {
        return seat.echo == RealmStory.Echo.RESONANCE ? seat.base - 5 : seat.echo == RealmStory.Echo.FORCE ? seat.base + 3 : seat.base + 1;
    }

    /**
     * A giant chain: links five blocks wide and six high, turned a quarter between one link and the next, from a shackle
     * of bell bronze up into the sky.
     */
    private static void chain(WorldGenLevel level, int x, int z, int dx, int dz, int from) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (ax <= 2 && az <= 2 && (ax == 2 || az == 2)) {
            set(level, x, from - 1, z, s(Realm.BELL_BRONZE.get()));
            set(level, x, from, z, s(Realm.BELL_BRONZE.get()));
        }
        int top = Math.min(CHAIN_TOP, level.getMaxBuildHeight() - 2);
        BlockState link = s(Realm.CHAIN_LINK.get());
        for (int y = from + 1; y <= top; y++) {
            if (isLink(dx, dz, y - from - 1)) {
                set(level, x, y, z, link);
            }
        }
    }

    /**
     * The shape of a giant chain: whether there is metal at ({@code dx, dz}) from its axis, {@code yy} blocks above its
     * foot. Each link is five wide and six high, and every other link is turned a quarter.
     */
    static boolean isLink(int dx, int dz, int yy) {
        int n = yy / 6;
        int k = yy % 6;
        boolean alongX = n % 2 == 0;
        int across = alongX ? dz : dx;
        int along = Math.abs(alongX ? dx : dz);
        if (across != 0 || along > 2) {
            return false;
        }
        return along == 2 && k >= 1 && k <= 4 || along <= 1 && (k == 0 || k == 5);
    }

    /** How high the giant chains reach. */
    static int chainTop(net.minecraft.world.level.LevelAccessor level) {
        return Math.min(CHAIN_TOP, level.getMaxBuildHeight() - 2);
    }

    // ---------------------------------------------------------------------------------------- the Press Throne of Force
    /** A square court of karst bricks ringed by eight giant pistons; the seal on a stepped dais in the middle. */
    private static void pressThrone(WorldGenLevel level, int x, int z, int dx, int dz, double r, int base, long h) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        int m = Math.max(ax, az);
        if (m > 22) {
            return;
        }
        set(level, x, base, z, m >= 21 ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : (ax + az) % 4 == 0 ? s(Realm.LICHEN_KARST.get())
                : s(Realm.KARST_BRICKS.get()));
        for (int k = 0; k < 8; k++) {
            double a = k * Mth.TWO_PI / 8;
            int px = (int) Math.round(Math.cos(a) * 17);
            int pz = (int) Math.round(Math.sin(a) * 17);
            if (Math.abs(dx - px) <= 1 && Math.abs(dz - pz) <= 1) {
                for (int y = base + 1; y <= base + 10; y++) {
                    set(level, x, y, z, y % 4 == 0 ? s(Realm.RUST_PLATING.get()) : s(Realm.KARST_BRICKS.get()));
                }
                set(level, x, base + 11, z, s(Blocks.PISTON).setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.UP));
                if (dx == px && dz == pz) {
                    set(level, x, base + 12, z, s(Realm.REDSTONE_VEIN.get()));
                }
            }
        }
        if (m <= 4) {
            set(level, x, base + 1, z, s(Realm.KARST_BRICKS.get()));
            if (m <= 3) {
                set(level, x, base + 2, z, m == 3 ? s(Realm.RUST_PLATING.get()) : s(Realm.KARST_BRICKS.get()));
            }
        }
    }

    // ---------------------------------------------------------------------------------------- the Switchboard of Signal
    /** A round floor of fossil circuits with a grid of glowing traces, ringed by twelve lit signal posts. */
    private static void switchboard(WorldGenLevel level, int x, int z, int dx, int dz, double r, int base, long h) {
        if (r > 22) {
            return;
        }
        BlockState floor;
        if (r >= 21) {
            floor = s(Realm.CHISELED_REALMSTONE_BRICKS.get());
        } else if (r >= 3 && r < 4.5) {
            floor = s(Blocks.WAXED_CUT_COPPER);
        } else if ((Math.floorMod(dx, 4) == 0 || Math.floorMod(dz, 4) == 0) && r < 20) {
            floor = s(Realm.REDSTONE_VEIN.get());
        } else {
            floor = s(Realm.FOSSIL_CIRCUIT.get());
        }
        set(level, x, base, z, floor);
        for (int k = 0; k < 12; k++) {
            double a = k * Mth.TWO_PI / 12;
            if (dx == (int) Math.round(Math.cos(a) * 19) && dz == (int) Math.round(Math.sin(a) * 19)) {
                for (int y = base + 1; y <= base + 6; y++) {
                    set(level, x, y, z, s(Realm.RUST_PLATING.get()));
                }
                set(level, x, base + 7, z, s(Blocks.REDSTONE_BLOCK));
                set(level, x, base + 8, z, litLamp());
            }
        }
    }

    // ---------------------------------------------------------------------------------------- the Belfry Hollow of Resonance
    /** A bowl sunk into the ground, crystal-lit, six bell towers around its rim and a bronze floor at the bottom. */
    private static void belfryHollow(WorldGenLevel level, int x, int z, int dx, int dz, double r, int base, long h) {
        if (r > 22) {
            return;
        }
        int depth = (int) Math.round(6 * (1 - (r / 22) * (r / 22)));
        for (int y = base; y > base - depth; y--) {
            set(level, x, y, z, s(Blocks.AIR));
        }
        int floorY = base - depth;
        BlockState floor;
        if (r <= 3) {
            floor = s(Realm.BELL_BRONZE.get());
        } else if (Math.floorMod(h, 9) == 0) {
            floor = s(Realm.RESONANT_CRYSTAL.get());
        } else {
            floor = Math.floorMod(h >>> 4, 3) == 0 ? s(Realm.DEEP_REALMSTONE.get()) : s(Realm.SALT_CRUST.get());
        }
        set(level, x, floorY, z, floor);
        if (Math.floorMod(h >>> 8, 23) == 0 && r > 5) {
            set(level, x, floorY + 1, z, s(Realm.REDSTONE_CLUSTER.get()));
        }
        for (int k = 0; k < 6; k++) {
            double a = k * Mth.TWO_PI / 6 + Mth.PI / 6;
            int px = (int) Math.round(Math.cos(a) * 18);
            int pz = (int) Math.round(Math.sin(a) * 18);
            if (Math.abs(dx - px) <= 1 && Math.abs(dz - pz) <= 1) {
                for (int y = floorY + 1; y <= base + 10; y++) {
                    set(level, x, y, z, y % 5 == 0 ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : s(Realm.DEEP_REALMSTONE.get()));
                }
                if (dx == px && dz == pz) {
                    set(level, x, base + 11, z, s(Blocks.BELL).setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
                } else if (Math.abs(dx - px) == 1 && Math.abs(dz - pz) == 1) {
                    set(level, x, base + 11, z, s(Blocks.REDSTONE_BLOCK));
                    set(level, x, base + 12, z, litLamp());
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------- the Furnace Crown of Heat
    /** A blackstone platform in a moat of lava, four bridges, a wall of cinder and magma, four chimney spires burning. */
    private static void furnaceCrown(WorldGenLevel level, int x, int z, int dx, int dz, double r, int base, long h) {
        if (r > 18) {
            return;
        }
        boolean bridge = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        if (r <= 12) {
            set(level, x, base, z, (Math.abs(dx) + Math.abs(dz)) % 5 == 0 ? s(Blocks.MAGMA_BLOCK) : s(Blocks.POLISHED_BLACKSTONE_BRICKS));
        } else if (r <= 15) {
            set(level, x, base - 1, z, s(Realm.CINDER_ROCK.get()));
            set(level, x, base, z, bridge ? s(Blocks.POLISHED_BLACKSTONE_BRICKS) : s(Blocks.LAVA));
        } else {
            set(level, x, base, z, s(Realm.CINDER_ROCK.get()));
            if (!bridge) {
                for (int y = base + 1; y <= base + 5; y++) {
                    set(level, x, y, z, y == base + 3 ? s(Blocks.MAGMA_BLOCK) : s(Realm.CINDER_ROCK.get()));
                }
            }
        }
        for (int[] c : new int[][]{{8, 8}, {-8, 8}, {8, -8}, {-8, -8}}) {
            if (Math.abs(dx - c[0]) <= 1 && Math.abs(dz - c[1]) <= 1) {
                for (int y = base + 1; y <= base + 12; y++) {
                    set(level, x, y, z, y % 4 == 0 ? s(Blocks.MAGMA_BLOCK) : s(Realm.CINDER_ROCK.get()));
                }
                if (dx == c[0] && dz == c[1]) {
                    set(level, x, base + 13, z, s(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true));
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------- the Sluice Basin of Flow
    /** A basin around a ring of molten redstone, an island in the middle with the seal, four floodgate towers in the pool. */
    private static void sluiceBasin(WorldGenLevel level, int x, int z, int dx, int dz, double r, int base, long h) {
        if (r > 21) {
            return;
        }
        BlockState molten = RealmLiquids.Kind.MOLTEN_REDSTONE.liquid().block().get().defaultBlockState();
        if (r <= 6) {
            set(level, x, base, z, r >= 5.5 ? s(Blocks.WAXED_CUT_COPPER) : s(Realm.REALMSTONE_BRICKS.get()));
        } else if (r <= 16) {
            set(level, x, base - 2, z, s(Realm.RED_CLAY.get()));
            boolean walk = dz == 0 && dx > 0;
            set(level, x, base - 1, z, molten);
            set(level, x, base, z, walk ? s(Realm.RUST_PLATING.get()) : molten);
        } else {
            set(level, x, base, z, r >= 20 ? s(Realm.CHISELED_REALMSTONE_BRICKS.get()) : s(Realm.REALMSTONE_BRICKS.get()));
        }
        for (int[] c : new int[][]{{11, 6}, {-11, 6}, {6, -11}, {-6, -11}}) {
            if (Math.abs(dx - c[0]) <= 1 && Math.abs(dz - c[1]) <= 1) {
                for (int y = base - 1; y <= base + 9; y++) {
                    set(level, x, y, z, y % 3 == 0 ? s(Blocks.WAXED_COPPER_BLOCK) : s(Blocks.WAXED_CUT_COPPER));
                }
                if (dx == c[0] && dz == c[1]) {
                    // trap blocks only face sideways: the gate looks out from the basin
                    Direction out = Math.abs(c[0]) > Math.abs(c[1]) ? (c[0] > 0 ? Direction.EAST : Direction.WEST) : (c[1] > 0 ? Direction.SOUTH : Direction.NORTH);
                    set(level, x, base + 10, z, s(Realm.FLOODGATE.get()).setValue(TrapBlock.FACING, out));
                }
            }
        }
    }

    // ============================================================================================ the chain comes down
    private record Fall(ServerLevel level, int x, int z, int bottom, int[] y) {
    }

    private static final Deque<Fall> FALLING = new ArrayDeque<>();

    /** The Echo of this seat has fallen: its giant chain comes down, a few links a tick, from the top. */
    static void breakChain(ServerLevel level, BlockPos seat) {
        FALLING.add(new Fall(level, seat.getX(), seat.getZ(), seat.getY() - 8, new int[]{Math.min(CHAIN_TOP, level.getMaxBuildHeight() - 2)}));
    }

    /** A whole column of giant chain falls at once (the healing realm sheds them). */
    static void dropChainColumn(ServerLevel level, int x, int z, int top) {
        for (int y = top; y > top - 360 && y > level.getMinBuildHeight(); y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (level.getBlockState(p).is(Realm.CHAIN_LINK.get())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                if (y % 6 == 0) {
                    level.sendParticles(ParticleTypes.LAVA, x + 0.5, y + 0.5, z + 0.5, 2, 0.4, 0.4, 0.4, 0.0);
                }
            }
        }
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FALLING.isEmpty()) {
            return;
        }
        Fall fall = FALLING.peek();
        for (int layer = 0; layer < 4 && fall.y[0] >= fall.bottom; layer++, fall.y[0]--) {
            int y = fall.y[0];
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = new BlockPos(fall.x + dx, y, fall.z + dz);
                    if (fall.level.isLoaded(p) && fall.level.getBlockState(p).is(Realm.CHAIN_LINK.get())) {
                        fall.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        fall.level.sendParticles(ParticleTypes.LAVA, p.getX() + 0.5, y + 0.5, p.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.0);
                    }
                }
            }
            if (y % 12 == 0) {
                fall.level.playSound(null, fall.x + 0.5, y, fall.z + 0.5, net.minecraft.sounds.SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 4.0F, 0.5F);
            }
        }
        if (fall.y[0] < fall.bottom) {
            FALLING.poll();
        }
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(Sanctums::tick);
    }

    // ============================================================================================ finding them
    /** The nearest sanctum whose Echo still stands (or, with {@code any}, the nearest of all), within two regions. */
    @Nullable
    static Seat nearest(ServerLevel level, BlockPos from, boolean any) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        Seat best = null;
        double bestD = Double.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (Seat seat : seats(level, gen, random, from.getX() + ox * REGION, from.getZ() + oz * REGION)) {
                    if (!any && RealmStory.conquered(level, seat.echo)) {
                        continue;
                    }
                    double d = from.distSqr(seat.pos().atY(from.getY()));
                    if (d < bestD) {
                        bestD = d;
                        best = seat;
                    }
                }
            }
        }
        return best;
    }

    // ============================================================================================ the Echo Seal
    /**
     * The seal at the heart of a sanctum. Use it to wake its Echo. Unbreakable. Its ECHO property says whose seat it is.
     */
    public static class EchoSeal extends Block {
        public static final IntegerProperty ECHO = IntegerProperty.create("echo", 0, RealmStory.Echo.values().length - 1);

        public EchoSeal(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(ECHO, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ECHO);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
                return InteractionResult.SUCCESS;
            }
            RealmStory.Echo echo = RealmStory.Echo.values()[state.getValue(ECHO)];
            if (RealmStory.conquered(level, echo)) {
                sp.displayClientMessage(Component.translatable("story.redstoneplus.seat_silent", Component.translatable("story.redstoneplus.echo." + echo.id()))
                        .withStyle(ChatFormatting.GRAY), true);
                if (!RealmStory.seatSilent(server.getServer(), pos)) {
                    RealmStory.conquer(server, echo, pos); // the Echo fell elsewhere: this seat's chain comes down too
                }
                return InteractionResult.CONSUME;
            }
            AABB around = new AABB(pos).inflate(48.0);
            if (!server.getEntitiesOfClass(Echoes.EchoBoss.class, around).isEmpty()) {
                return InteractionResult.CONSUME;
            }
            Echoes.awaken(server, echo, pos, sp);
            return InteractionResult.CONSUME;
        }
    }
}
