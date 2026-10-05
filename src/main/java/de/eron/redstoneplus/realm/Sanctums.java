package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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
 * The seats of the five Echoes and the Great Cradle. The realm is one artery (see {@link Artery}), and its places are
 * fixed on it: each Echo keeps one arena along the way, and the Heart holds the Great Cradle where the last fight is.
 * The arenas are drawn from {@link Arenas}: the Press Throne of Force, the Switchboard of Signal, the Belfry Hollow of
 * Resonance, the Furnace Crown of Heat and the Sluice Basin of Flow. At an arena's centre lies the Echo Seal; above it a
 * shackle of bronze, and from the shackle a giant chain rises into the sky to the Great Bell. Using the seal wakes the Echo.
 * <p>
 * An arena is built column by column. When its Echo falls, its chain comes down link by link, and is never built again.
 */
public final class Sanctums {
    /** Where the giant chain starts above the seal, and how high it reaches. */
    private static final int CHAIN_START = 12;
    private static final int CHAIN_TOP = 300;

    private Sanctums() {
    }

    /** One arena: whose it is (null for the Great Cradle), its centre, and the height of its floor. */
    public record Seat(@Nullable RealmStory.Echo echo, int x, int z, int base) {
        public BlockPos pos() {
            return new BlockPos(this.x, this.base, this.z);
        }

        /** The arena's plan: 0..4 for the Echoes, 5 for the Great Cradle. */
        public int kind() {
            return this.echo == null ? 5 : this.echo.ordinal();
        }

        public int radius() {
            return Arenas.of(this.kind()).radius();
        }
    }

    private static final Map<Long, List<Seat>> SEATS = new ConcurrentHashMap<>();

    /** The arenas of the realm: fixed places on the artery, their floors set to the land there (once per world). */
    public static List<Seat> seats(WorldGenLevel level, ChunkGenerator gen, RandomState random) {
        return SEATS.computeIfAbsent(level.getSeed(), seed -> {
            List<Seat> list = new ArrayList<>();
            for (RealmStory.Echo echo : RealmStory.Echo.values()) {
                double[] p = Artery.SEATS[echo.ordinal()];
                list.add(seat(level, gen, random, echo, (int) p[0], (int) p[1]));
            }
            list.add(seat(level, gen, random, null, (int) Artery.HEART[0], (int) Artery.HEART[1]));
            return List.copyOf(list);
        });
    }

    private static Seat seat(WorldGenLevel level, ChunkGenerator gen, RandomState random, @Nullable RealmStory.Echo echo, int x, int z) {
        // the floor: the land's height at the centre, but never lower than the land around would allow a level arena
        int base = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
        int around = 0;
        int n = 0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int h = gen.getBaseHeight(x + (int) (Math.cos(a) * 30), z + (int) (Math.sin(a) * 30), Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
            if (h > level.getMinBuildHeight() + 20) {
                around += h;
                n++;
            }
        }
        if (n > 0) {
            base = (base + around / n) / 2;
        }
        return new Seat(echo, x, z, Math.max(level.getSeaLevel() + 4, Math.min(150, base)));
    }

    /** The Great Cradle. */
    public static Seat heart(ServerLevel level) {
        List<Seat> all = seats(level, level.getChunkSource().getGenerator(), level.getChunkSource().randomState());
        return all.get(all.size() - 1);
    }

    @Nullable
    private static Seat seatAt(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z, int pad) {
        if (!coversStatic(x, z, pad)) {
            return null;
        }
        for (Seat seat : seats(level, gen, random)) {
            int r = seat.radius() + pad;
            if ((x - seat.x) * (double) (x - seat.x) + (z - seat.z) * (double) (z - seat.z) <= r * (double) r) {
                return seat;
            }
        }
        return null;
    }

    /** True within {@code pad} blocks of an arena (from the fixed places alone, no world needed). */
    public static boolean coversStatic(double x, double z, double pad) {
        for (int k = 0; k < 6; k++) {
            double[] p = k < 5 ? Artery.SEATS[k] : Artery.HEART;
            double r = Arenas.of(k).radius() + pad;
            if ((x - p[0]) * (x - p[0]) + (z - p[1]) * (z - p[1]) <= r * r) {
                return true;
            }
        }
        return false;
    }

    /** True on and around an arena: the Grid's lines and the scenery keep out. */
    public static boolean covers(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z) {
        return coversStatic(x, z, 6);
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

    /** The block an arena plan means by a character, or null to leave the world as it is. */
    @Nullable
    static BlockState block(char c) {
        return switch (c) {
            case '.' -> s(Blocks.AIR);
            case '#' -> s(Realm.REALMSTONE_BRICKS.get());
            case 'c' -> s(Realm.CRACKED_REALMSTONE_BRICKS.get());
            case 'C' -> s(Realm.CHISELED_REALMSTONE_BRICKS.get());
            case 'k' -> s(Realm.KARST_BRICKS.get());
            case 'l' -> s(Realm.LICHEN_KARST.get());
            case 'r' -> s(Realm.RUST_PLATING.get());
            case 'v' -> s(Realm.REDSTONE_VEIN.get());
            case 'b' -> s(Realm.BELL_BRONZE.get());
            case 'R' -> s(Blocks.REDSTONE_BLOCK);
            case 'p' -> s(Blocks.PISTON).setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.UP);
            case 'f' -> s(Realm.FOSSIL_CIRCUIT.get());
            case 'w' -> s(Realm.WIREWRIGHT_TILES.get());
            case 'u' -> s(Blocks.WAXED_CUT_COPPER);
            case 'U' -> s(Blocks.WAXED_COPPER_BLOCK);
            case 'i' -> s(Blocks.IRON_BARS);
            case 'm' -> s(Blocks.MAGMA_BLOCK);
            case 'x' -> s(Blocks.POLISHED_BLACKSTONE_BRICKS);
            case 'n' -> s(Realm.CINDER_ROCK.get());
            case 'V' -> s(Blocks.LAVA);
            case 'M' -> RealmLiquids.Kind.MOLTEN_REDSTONE.liquid().block().get().defaultBlockState();
            case 'd' -> s(Realm.DEEP_REALMSTONE.get());
            case 's' -> s(Realm.SALT_CRUST.get());
            case 'z' -> s(Realm.RESONANT_CRYSTAL.get());
            case 'h' -> s(Blocks.CHAIN);
            case 'T' -> litLamp();
            case 'o' -> s(Blocks.BELL).setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR);
            case 'a' -> s(Realm.ARTERY_WALL.get());
            case 'O' -> s(Blocks.SHROOMLIGHT);
            case 'j' -> s(Blocks.LIGHTNING_ROD);
            case 'F' -> s(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true);
            default -> null;
        };
    }

    private static final ThreadLocal<char[]> COLUMN = ThreadLocal.withInitial(() -> new char[Arenas.SIZE]);

    /** One column of whatever arena covers it. */
    static boolean column(WorldGenLevel level, ChunkGenerator gen, RandomState random, int x, int z, int top) {
        Seat seat = seatAt(level, gen, random, x, z, 0);
        if (seat == null) {
            return false;
        }
        int dx = x - seat.x;
        int dz = z - seat.z;
        Arenas.Plan plan = Arenas.of(seat.kind());
        char[] col = COLUMN.get();
        java.util.Arrays.fill(col, ' ');
        plan.column(dx, dz, col);
        int min = level.getMinBuildHeight();
        int max = level.getMaxBuildHeight() - 1;
        boolean built = false;
        for (int i = 0; i < col.length; i++) {
            if (col[i] == ' ') {
                continue;
            }
            int y = seat.base + i - Arenas.DEPTH;
            if (y < min || y > max) {
                continue;
            }
            BlockState state = block(col[i]);
            if (state != null) {
                if (state.isAir() && level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                    continue;
                }
                set(level, x, y, z, state);
                built = true;
            }
        }
        if (seat.echo == null) {
            return built;
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

    /** The height of the seal in an arena (or of the socket, in the Great Cradle). */
    static int sealY(Seat seat) {
        return seat.base + Arenas.of(seat.kind()).sealDy();
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
    /** The nearest Echo arena whose Echo still stands (or, with {@code any}, the nearest of all). */
    @Nullable
    static Seat nearest(ServerLevel level, BlockPos from, boolean any) {
        Seat best = null;
        double bestD = Double.MAX_VALUE;
        for (Seat seat : seats(level, level.getChunkSource().getGenerator(), level.getChunkSource().randomState())) {
            if (seat.echo == null || !any && RealmStory.conquered(level, seat.echo)) {
                continue;
            }
            double d = from.distSqr(seat.pos().atY(from.getY()));
            if (d < bestD) {
                bestD = d;
                best = seat;
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
            AABB around = new AABB(pos).inflate(90.0);
            if (!server.getEntitiesOfClass(Echoes.EchoBoss.class, around).isEmpty()) {
                return InteractionResult.CONSUME;
            }
            Echoes.awaken(server, echo, pos, sp);
            return InteractionResult.CONSUME;
        }
    }
}
