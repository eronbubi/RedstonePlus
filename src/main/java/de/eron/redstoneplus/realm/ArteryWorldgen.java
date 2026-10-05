package de.eron.redstoneplus.realm;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.eron.redstoneplus.RedstonePlus;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Turns the {@link Artery} into world generation: a density function that cuts the floating vessel out of the terrain,
 * and a biome source that lays the biomes along it.
 */
public final class ArteryWorldgen {
    private ArteryWorldgen() {
    }

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, RedstonePlus.MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, RedstonePlus.MODID);

    static {
        DENSITY_TYPES.register("artery", () -> Shape.MAP_CODEC);
        BIOME_SOURCES.register("artery", () -> Biomes.CODEC);
    }

    static void init(IEventBus modBus) {
        DENSITY_TYPES.register(modBus);
        BIOME_SOURCES.register(modBus);
    }

    // ============================================================================================ the shape of the land
    /** The values of one column that do not depend on the height, worked out once per column. */
    static final class Column {
        long key = Long.MIN_VALUE;
        double s;
        double under;
        double dive;
        double seaFloor;
        double lipTop;
        double lip;
    }

    private static final ThreadLocal<Column[]> COLUMNS = ThreadLocal.withInitial(() -> {
        Column[] c = new Column[256];
        for (int i = 0; i < c.length; i++) {
            c[i] = new Column();
        }
        return c;
    });

    static Column column(int x, int z) {
        Column c = COLUMNS.get()[((x & 15) << 4) | (z & 15)];
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        if (c.key != key) {
            c.key = key;
            fill(c, x, z);
        }
        return c;
    }

    private static void fill(Column c, int x, int z) {
        double s = Artery.s(x, z);
        c.s = s;
        double inside = Math.sqrt(Math.max(0.0, s));
        // the underside: a tube's cross-section, deepest under the spine, with lobes of tissue hanging from it
        double lobes = Artery.noise(x / 23.0, z / 23.0, 41) * 0.7 + Artery.noise(x / 7.0, z / 7.0, 42) * 0.3;
        c.under = 50.0 - 125.0 * inside - 16.0 * lobes * Math.min(1.0, s * 6.0);
        // the two roots dive into the Blood Below
        c.dive = Artery.dive(x, z);
        c.under -= 70.0 * c.dive;
        // the Red Sea: a basin below the waterline
        double sea = Artery.sea(x, z);
        c.seaFloor = sea > 0.02 ? 61.0 - 30.0 * sea + Math.max(0.0, 0.25 - sea) * 400.0 : 1.0e6;
        // the lip: the vessel's wall rises along the rim
        c.lip = s > 0.0 ? Math.max(0.0, 1.0 - Math.abs(s - 0.03) / 0.03) : 0.0;
        c.lipTop = 90.0 + 28.0 * (0.55 + 0.45 * Artery.noise(x / 31.0, z / 31.0, 43)) + 10.0 * Artery.noise(x / 9.0, z / 9.0, 44);
    }

    /** The density of the realm at a block, given the overworld-style terrain density there. */
    static double density(int x, int y, int z, double terrain) {
        Column c = column(x, z);
        if (c.s < -0.06) {
            return -2.0; // the void
        }
        double d = terrain;
        if (c.dive > 0.0) {
            // the land slopes down into the Blood Below
            double capTop = 150.0 - 230.0 * c.dive;
            d = Math.min(d, (capTop - y) / 10.0);
        }
        if (c.seaFloor < 1.0e5) {
            d = Math.min(d, (c.seaFloor - y) / 6.0);
        }
        // the edge: nothing beyond the rim
        d = Math.min(d, c.s * 40.0);
        // the underside: nothing below the vessel
        double below = (y - c.under) / 6.0;
        d = Math.min(d, below);
        if (c.lip > 0.0) {
            double wall = Math.min((c.lipTop - y) / 5.0, below) * c.lip;
            d = Math.max(d, wall);
        }
        return d;
    }

    /** Wraps the terrain density (the overworld's shape) and cuts the artery out of it. */
    public record Shape(DensityFunction argument) implements DensityFunction {
        public static final MapCodec<Shape> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(Shape::argument)).apply(i, Shape::new));
        public static final KeyDispatchDataCodec<Shape> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

        @Override
        public double compute(FunctionContext context) {
            int x = context.blockX();
            int z = context.blockZ();
            Column c = column(x, z);
            if (c.s < -0.06) {
                return -2.0; // the void: the terrain is not even asked
            }
            return density(x, context.blockY(), z, this.argument.compute(context));
        }

        @Override
        public void fillArray(double[] array, ContextProvider provider) {
            provider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new Shape(this.argument.mapAll(visitor)));
        }

        @Override
        public double minValue() {
            return Math.min(-2.0, this.argument.minValue());
        }

        @Override
        public double maxValue() {
            return Math.max(this.argument.maxValue(), 40.0);
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    // ============================================================================================ what the vessel is made of
    private static void put(net.minecraft.world.level.WorldGenLevel level, int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {
        level.setBlock(new net.minecraft.core.BlockPos(x, y, z), state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    private static boolean air(net.minecraft.world.level.WorldGenLevel level, int x, int y, int z) {
        return level.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).isAir();
    }

    /** The bottom of the void: the Blood Below, a sea of molten redstone, ten blocks deep. */
    public static final int BLOOD_TOP = -55;

    /**
     * One column of the abyss: the Blood Below wherever there is no land down to the bottom, a skin of vessel wall on the
     * underside of the land, and the veins that hang from the rims and the underside down into the Blood Below.
     */
    static boolean abyss(net.minecraft.world.level.WorldGenLevel level, int x, int z) {
        int min = level.getMinBuildHeight();
        var molten = RealmLiquids.Kind.MOLTEN_REDSTONE.liquid().block().get().defaultBlockState();
        var wall = Realm.ARTERY_WALL.get().defaultBlockState();
        boolean built = false;
        for (int y = min; y <= BLOOD_TOP; y++) {
            // the terrain fills the bottom of the world with plain lava wherever nothing stands; the Blood replaces it
            var here = level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
            if (here.isAir() || here.is(net.minecraft.world.level.block.Blocks.LAVA)) {
                put(level, x, y, z, molten);
                built = true;
            }
        }
        // the underside: the lowest solid blocks of a floating column are vessel wall
        Column c = column(x, z);
        if (c.s > 0.0) {
            int y = BLOOD_TOP + 1;
            while (y < 200 && air(level, x, y, z)) {
                y++;
            }
            if (y > BLOOD_TOP + 1 && y < 200) {
                for (int k = 0; k < 3; k++) {
                    if (!air(level, x, y + k, z)) {
                        put(level, x, y + k, z, wall);
                    }
                }
                built = true;
            }
        }
        built |= hangingVeins(level, x, z, wall);
        return built;
    }

    private static final int VEIN_CELL = 40;

    /** The veins: tapering, swaying columns of vessel wall around a glowing redstone core. */
    private static boolean hangingVeins(net.minecraft.world.level.WorldGenLevel level, int x, int z, net.minecraft.world.level.block.state.BlockState wall) {
        var core = Realm.REDSTONE_VEIN.get().defaultBlockState();
        boolean built = false;
        int ci = Math.floorDiv(x, VEIN_CELL);
        int cj = Math.floorDiv(z, VEIN_CELL);
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                long h = Artery.hash(i, j, 51);
                int hx = i * VEIN_CELL + (int) Math.floorMod(h, (long) VEIN_CELL);
                int hz = j * VEIN_CELL + (int) Math.floorMod(h >>> 8, (long) VEIN_CELL);
                double sh = Artery.s(hx, hz);
                double chance;
                if (sh > -0.05 && sh < 0.1) {
                    chance = 0.55; // the rims
                } else if (sh >= 0.1 && Artery.dive(hx, hz) > 0.15) {
                    chance = 0.75; // the roots
                } else if (sh >= 0.1) {
                    chance = 0.1;  // under the land
                } else {
                    chance = 0.0;
                }
                if (Math.floorMod(h >>> 16, 1000L) >= chance * 1000) {
                    continue;
                }
                double top = sh > 0.0 ? column(hx, hz).under + 2 : 50.0 + sh * 200.0;
                double bottom = BLOOD_TOP - 4;
                double r0 = 3.0 + Math.floorMod(h >>> 26, 5L) + (sh < 0.1 ? 1.5 : 0.0);
                double phase = Math.floorMod(h >>> 34, 628L) / 100.0;
                for (int y = (int) bottom; y <= (int) top; y++) {
                    double frac = (y - bottom) / Math.max(1.0, top - bottom);
                    // thick where it leaves the land, thin where it meets the Blood, a knot now and then
                    double r = r0 * (0.3 + 0.7 * frac) + 1.5 * Math.max(0.0, Math.sin(y / 9.0 + phase * 3)) * frac;
                    double cx = hx + 6.0 * Math.sin(y / 17.0 + phase) * (1 - frac * 0.5);
                    double cz = hz + 6.0 * Math.cos(y / 23.0 + phase) * (1 - frac * 0.5);
                    double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                    if (d <= r && air(level, x, y, z)) {
                        put(level, x, y, z, d < r * 0.45 ? core : wall);
                        built = true;
                    }
                }
            }
        }
        return built;
    }

    /** One column of the rim: the vessel's wall, streaked with veins, from its lip down to its underside. */
    static boolean rim(net.minecraft.world.level.WorldGenLevel level, int x, int z, int top) {
        Column c = column(x, z);
        if (c.s <= 0.0 || c.s > 0.075) {
            return false;
        }
        var wall = Realm.ARTERY_WALL.get().defaultBlockState();
        var vein = Realm.REDSTONE_VEIN.get().defaultBlockState();
        boolean built = false;
        for (int y = top; y >= Math.max(level.getMinBuildHeight(), (int) c.under - 2); y--) {
            if (!air(level, x, y, z) && level.getFluidState(new net.minecraft.core.BlockPos(x, y, z)).isEmpty()) {
                boolean streak = Math.floorMod(Artery.hash(x, z, 61) + y / 5, 13L) == 0;
                put(level, x, y, z, streak ? vein : wall);
                built = true;
            }
        }
        return built;
    }

    /** One column of the Red Sea: rust brine up to the waterline. */
    static boolean sea(net.minecraft.world.level.WorldGenLevel level, int x, int z, int top) {
        if (Artery.sea(x, z) < 0.03 || Artery.s(x, z) <= 0.0) {
            return false;
        }
        var brine = RealmLiquids.Kind.RUST_BRINE.liquid().block().get().defaultBlockState();
        boolean built = false;
        for (int y = top; y <= 62; y++) {
            if (air(level, x, y, z)) {
                put(level, x, y, z, brine);
                built = true;
            }
        }
        return built;
    }

    // ============================================================================================ the biomes
    /** Places the realm's biomes along the artery, exactly where the map says (see {@link Artery#ZONES}). */
    public static final class Biomes extends BiomeSource {
        public static final MapCodec<Biomes> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RegistryOps.retrieveGetter(Registries.BIOME)).apply(i, Biomes::new));

        private final Map<String, Holder<Biome>> byName = new HashMap<>();
        private final Holder<Biome> fallback;

        public Biomes(HolderGetter<Biome> biomes) {
            for (String name : Realm.BIOMES) {
                this.byName.put(name, biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Realm.id(name))));
            }
            this.fallback = this.byName.get(Artery.VOID);
        }

        @Override
        protected MapCodec<? extends BiomeSource> codec() {
            return CODEC;
        }

        @Override
        protected Stream<Holder<Biome>> collectPossibleBiomes() {
            return List.copyOf(this.byName.values()).stream();
        }

        @Override
        public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
            Holder<Biome> biome = this.byName.get(Artery.biome(qx << 2, qz << 2, qy << 2));
            return biome != null ? biome : this.fallback;
        }
    }
}
