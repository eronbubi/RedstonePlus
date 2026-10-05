package de.eron.redstoneplus.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.eron.redstoneplus.block.PoweredBlock;
import de.eron.redstoneplus.block.Sensors;
import de.eron.redstoneplus.block.Wireless;
import de.eron.redstoneplus.block.gate.GateBlock;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/** World generation of the realm: rock spires, the six trap sites, rail lines and circuit workshops. */
public final class RealmFeatures {
    private RealmFeatures() {
    }

    // =====================================================================================================
    // Spires: tapering rock towers with a flat cap (karst pillars, basalt chimneys)

    public record SpireConfig(BlockState body, BlockState accent, int minHeight, int maxHeight, int minRadius, int maxRadius)
            implements FeatureConfiguration {
        public static final Codec<SpireConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("body").forGetter(SpireConfig::body),
                BlockState.CODEC.fieldOf("accent").forGetter(SpireConfig::accent),
                Codec.intRange(1, 96).fieldOf("min_height").forGetter(SpireConfig::minHeight),
                Codec.intRange(1, 96).fieldOf("max_height").forGetter(SpireConfig::maxHeight),
                Codec.intRange(1, 8).fieldOf("min_radius").forGetter(SpireConfig::minRadius),
                Codec.intRange(1, 8).fieldOf("max_radius").forGetter(SpireConfig::maxRadius)
        ).apply(i, SpireConfig::new));
    }

    public static class Spire extends Feature<SpireConfig> {
        public Spire() {
            super(SpireConfig.CODEC);
        }

        @Override
        public boolean place(FeaturePlaceContext<SpireConfig> context) {
            WorldGenLevel level = context.level();
            RandomSource random = context.random();
            SpireConfig cfg = context.config();
            BlockPos origin = context.origin();
            if (!level.getBlockState(origin.below()).isSolid() || Artery.s(origin.getX(), origin.getZ()) < 0.1
                    || RealmCities.covers(level, context.chunkGenerator(), level.getLevel().getChunkSource().randomState(), origin.getX(), origin.getZ())
                    || Sanctums.covers(level, context.chunkGenerator(), level.getLevel().getChunkSource().randomState(), origin.getX(), origin.getZ())) {
                return false;
            }
            int height = cfg.minHeight() + random.nextInt(Math.max(1, cfg.maxHeight() - cfg.minHeight() + 1));
            int radius = cfg.minRadius() + random.nextInt(Math.max(1, cfg.maxRadius() - cfg.minRadius() + 1));
            double leanX = (random.nextDouble() - 0.5) * 0.25;
            double leanZ = (random.nextDouble() - 0.5) * 0.25;
            for (int y = -4; y <= height; y++) {
                double t = Math.max(0, y) / (double) height;
                double r = radius * (1.0 - t * 0.55) + (random.nextDouble() - 0.5) * 0.6;
                int cx = (int) Math.round(leanX * y);
                int cz = (int) Math.round(leanZ * y);
                int ri = (int) Math.ceil(r);
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        double d = Math.sqrt(dx * dx + dz * dz);
                        if (d > r) {
                            continue;
                        }
                        BlockPos p = origin.offset(cx + dx, y, cz + dz);
                        boolean edge = d > r - 1.0;
                        boolean accent = edge && (y > height * 0.6 || random.nextInt(7) == 0);
                        level.setBlock(p, accent ? cfg.accent() : cfg.body(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            // flat cap, a little wider than the top
            int capR = Math.max(2, (int) Math.round(radius * 0.6) + 1);
            int cx = (int) Math.round(leanX * height);
            int cz = (int) Math.round(leanZ * height);
            for (int dx = -capR; dx <= capR; dx++) {
                for (int dz = -capR; dz <= capR; dz++) {
                    if (dx * dx + dz * dz <= capR * capR + 1) {
                        level.setBlock(origin.offset(cx + dx, height + 1, cz + dz), cfg.accent(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            return true;
        }
    }

    // =====================================================================================================
    // Build helper: places blocks in a local frame (x across, z along, y up from the floor), rotated as a whole

    static final class Build {
        final LevelAccessor level;
        final BlockPos origin;
        final Rotation rotation;
        final RandomSource random;
        private final boolean worldgen;
        /** 0 for a building still standing; above 0 the chance that a block has fallen away (ruins). */
        float decay;

        Build(LevelAccessor level, BlockPos origin, Rotation rotation, RandomSource random, boolean worldgen) {
            this.level = level;
            this.origin = origin;
            this.rotation = rotation;
            this.random = random;
            this.worldgen = worldgen;
        }

        BlockPos at(int x, int y, int z) {
            return this.origin.offset(new BlockPos(x, y, z).rotate(this.rotation));
        }

        Direction dir(Direction local) {
            return this.rotation.rotate(local);
        }

        BlockState get(int x, int y, int z) {
            return this.level.getBlockState(this.at(x, y, z));
        }

        void set(int x, int y, int z, BlockState state) {
            BlockPos pos = this.at(x, y, z);
            if (this.worldgen && (Math.abs((pos.getX() >> 4) - (this.origin.getX() >> 4)) > 1 || Math.abs((pos.getZ() >> 4) - (this.origin.getZ() >> 4)) > 1)) {
                // a feature may only write into the chunks next to the one it starts in: long pieces are cut off there
                return;
            }
            if (this.decay > 0 && !state.isAir() && y >= 0) {
                // a ruin: the higher up, the more has fallen away; what is left is cracked, rusted and its glass broken
                if (this.random.nextFloat() < this.decay * Math.min(1F, 0.2F + y / 18F)) {
                    return;
                }
                state = this.weathered(state);
            }
            BlockState rotated = state.rotate(this.rotation);
            this.level.setBlock(pos, rotated, this.worldgen ? Block.UPDATE_CLIENTS : Block.UPDATE_ALL);
            if (this.worldgen) {
                // generated blocks get no onPlace call: wake up everything that runs on scheduled ticks
                Block block = rotated.getBlock();
                if (block instanceof GateBlock || block instanceof Sensors.Sensor || block instanceof PoweredBlock
                        || block instanceof Wireless.Receiver) {
                    this.level.scheduleTick(pos, block, 2 + this.random.nextInt(4));
                }
            }
        }

        private BlockState weathered(BlockState state) {
            if (this.random.nextFloat() >= this.decay) {
                return state;
            }
            if (state.is(Realm.REALMSTONE_BRICKS.get()) || state.is(Realm.KARST_BRICKS.get()) || state.is(Realm.FROST_REALMSTONE.get())) {
                return Realm.REALMSTONE.get().defaultBlockState();
            }
            if (state.is(Realm.SHELL_PLATING.get()) || state.is(Blocks.WAXED_EXPOSED_COPPER) || state.is(Blocks.WAXED_CUT_COPPER)
                    || state.is(Blocks.WAXED_COPPER_BLOCK)) {
                return Realm.RUST_PLATING.get().defaultBlockState();
            }
            if (state.is(Blocks.RED_STAINED_GLASS) || state.is(Blocks.GLASS)) {
                return Blocks.AIR.defaultBlockState();
            }
            if (state.is(Blocks.REDSTONE_BLOCK)) {
                return Realm.REDSTONE_VEIN.get().defaultBlockState(); // drained
            }
            return state;
        }

        void set(int x, int y, int z, Block block) {
            this.set(x, y, z, block.defaultBlockState());
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        this.set(x, y, z, state);
                    }
                }
            }
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
            this.fill(x1, y1, z1, x2, y2, z2, block.defaultBlockState());
        }

        /** Floor at y = -1, air above up to {@code height}, and pillars down to the ground below the floor. */
        void foundation(int x1, int z1, int x2, int z2, BlockState floor, BlockState support, int height) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    this.set(x, -1, z, floor);
                    for (int y = 0; y <= height; y++) {
                        this.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                    for (int y = -2; y >= -10; y--) {
                        BlockState below = this.get(x, y, z);
                        if (below.isSolid() && !below.is(Blocks.WATER)) {
                            break;
                        }
                        this.set(x, y, z, support);
                    }
                }
            }
        }

        void chest(int x, int y, int z, Direction facing, String lootTable) {
            this.set(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
            RandomizableContainer.setBlockEntityLootTable(this.level, this.random, this.at(x, y, z),
                    ResourceKey.create(Registries.LOOT_TABLE, Realm.id("chests/" + lootTable)));
        }

        BlockState gate(Block gate, Direction output) {
            return gate.defaultBlockState().setValue(GateBlock.FACING, output);
        }

        BlockState lever(Direction facing) {
            return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, facing);
        }

        BlockState button() {
            return Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR).setValue(ButtonBlock.FACING, Direction.NORTH);
        }

        BlockState trap(Block trap, Direction facing) {
            return trap.defaultBlockState().setValue(TrapBlock.FACING, facing);
        }

        /** Tripwire across x from {@code x1} to {@code x2} at z, with hooks one block further out on posts. */
        void tripwire(int x1, int x2, int y, int z, BlockState post) {
            this.set(x1 - 2, y, z, post);
            this.set(x2 + 2, y, z, post);
            this.set(x1 - 1, y, z, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.EAST)
                    .setValue(TripWireHookBlock.ATTACHED, true));
            this.set(x2 + 1, y, z, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.WEST)
                    .setValue(TripWireHookBlock.ATTACHED, true));
            for (int x = x1; x <= x2; x++) {
                this.set(x, y, z, Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.ATTACHED, true)
                        .setValue(TripWireBlock.EAST, true).setValue(TripWireBlock.WEST, true));
            }
        }

        BlockState rail(RailShape shape) {
            return Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
        }
    }

    // =====================================================================================================
    // Sites

    public enum Kind {
        // trap sites
        CRUSHER_PASSAGE(true), SWITCHYARD_JUNCTION(true), RESONANCE_GATEHOUSE(false), SLUICE_BRIDGE(false), KILN_BRIDGE(true), BRIAR_AMBUSH(true),
        // scenery
        RAIL_LINE(false), TOWER(false), RUIN(true), MONOLITH(false), GIANT_TREE(false), CRASHED_SHELL(false), CRYSTAL_DOME(true), BOARDWALK(false),
        KILN_HUT(true), AQUEDUCT(false), ICE_RAILS(true), SCRAP(false),
        // machines that run by themselves
        LAMP_PYLON(false), CRUSHER_MILL(true), PUMP_STATION(true), MINECART_LOOP(true), STORM_SPIRE(false), BELL_TOWER(true), BEAST_CAGE(true),
        LASER_POST(true),
        // the great buildings of the old world, standing or in ruins
        GENERATOR_HALL(false), RELAY_SPIRE(false), CIRCUIT_TEMPLE(false),
        // the great chains that bind the trapped realm to the Bell
        CHAIN_ANCHOR(true);

        /** Needs fairly flat, dry ground. */
        final boolean flat;

        Kind(boolean flat) {
            this.flat = flat;
        }

        public String id() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static class Site extends Feature<NoneFeatureConfiguration> {
        private final Kind kind;

        public Site(Kind kind) {
            super(NoneFeatureConfiguration.CODEC);
            this.kind = kind;
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            WorldGenLevel level = context.level();
            RandomSource random = context.random();
            BlockPos origin = context.origin();
            Rotation rotation = Rotation.getRandom(random);
            if (Artery.s(origin.getX(), origin.getZ()) < 0.1
                    || RealmCities.covers(level, context.chunkGenerator(), level.getLevel().getChunkSource().randomState(), origin.getX(), origin.getZ())
                    || Sanctums.covers(level, context.chunkGenerator(), level.getLevel().getChunkSource().randomState(), origin.getX(), origin.getZ())) {
                return false; // the cities and the sanctums are built whole; nothing else lands inside them
            }
            if (this.kind.flat && !flatEnough(level, origin, 6, 4)) {
                return false;
            }
            if (this.kind == Kind.CHAIN_ANCHOR && level.getServer() != null && RealmStory.healedForWorldgen(level.getServer())) {
                return false; // the chains fell when the realm was freed
            }
            boolean relic = this.kind == Kind.GENERATOR_HALL || this.kind == Kind.RELAY_SPIRE || this.kind == Kind.CIRCUIT_TEMPLE;
            if (relic && !flatEnough(level, origin, 12, 7)) {
                return false;
            }
            boolean waterside = this.kind == Kind.BOARDWALK || this.kind == Kind.AQUEDUCT || this.kind == Kind.STORM_SPIRE
                    || this.kind == Kind.SLUICE_BRIDGE || this.kind == Kind.RESONANCE_GATEHOUSE;
            if (!waterside && (!level.getFluidState(origin.below()).isEmpty() || !level.getFluidState(origin).isEmpty())) {
                return false;
            }
            if (this.kind == Kind.BOARDWALK && level.getFluidState(origin.below()).isEmpty()) {
                return false; // walkways only cross the fen's ponds
            }
            Build b = new Build(level, origin, rotation, random, true);
            switch (this.kind) {
                case CRUSHER_PASSAGE -> crusherPassage(b);
                case SWITCHYARD_JUNCTION -> switchyardJunction(b);
                case RESONANCE_GATEHOUSE -> resonanceGatehouse(b);
                case SLUICE_BRIDGE -> sluiceBridge(b);
                case KILN_BRIDGE -> kilnBridge(b);
                case BRIAR_AMBUSH -> briarAmbush(b);
                case RAIL_LINE -> railLine(b);
                case TOWER -> RealmStructures.tower(b);
                case RUIN -> RealmStructures.ruin(b);
                case MONOLITH -> RealmStructures.monolith(b);
                case GIANT_TREE -> RealmStructures.giantTree(b);
                case CRASHED_SHELL -> RealmStructures.crashedShell(b);
                case CRYSTAL_DOME -> RealmStructures.crystalDome(b);
                case BOARDWALK -> RealmStructures.boardwalk(b);
                case KILN_HUT -> RealmStructures.kilnHut(b);
                case AQUEDUCT -> RealmStructures.aqueduct(b);
                case ICE_RAILS -> RealmStructures.iceRails(b);
                case SCRAP -> RealmStructures.scrap(b);
                case LAMP_PYLON -> RealmStructures.lampPylon(b);
                case CRUSHER_MILL -> RealmStructures.crusherMill(b);
                case PUMP_STATION -> RealmStructures.pumpStation(b);
                case MINECART_LOOP -> RealmStructures.minecartLoop(b);
                case STORM_SPIRE -> RealmStructures.stormSpire(b);
                case BELL_TOWER -> RealmStructures.bellTower(b);
                case BEAST_CAGE -> RealmStructures.beastCage(b);
                case LASER_POST -> RealmStructures.laserPost(b);
                case GENERATOR_HALL -> RealmRelics.generatorHall(b);
                case RELAY_SPIRE -> RealmRelics.relaySpire(b);
                case CIRCUIT_TEMPLE -> RealmRelics.circuitTemple(b);
                case CHAIN_ANCHOR -> RealmStructures.chainAnchor(b);
            }
            return true;
        }
    }

    static boolean flatEnough(WorldGenLevel level, BlockPos origin, int radius, int maxDiff) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += radius) {
            for (int dz = -radius; dz <= radius; dz += radius) {
                int h = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX() + dx, origin.getZ() + dz);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        return max - min <= maxDiff;
    }

    // ---------- Piston Karst: pressure plate -> crushing passage; AND-gate vault at the end ----------
    static void crusherPassage(Build b) {
        BlockState bricks = Realm.KARST_BRICKS.get().defaultBlockState();
        BlockState stone = Realm.KARST_LIMESTONE.get().defaultBlockState();
        b.foundation(-3, -7, 3, 9, bricks, stone, 4);
        // passage walls: two layers of crushers facing in, outer limestone, roof
        for (int z = -4; z <= 2; z++) {
            for (int y = 0; y <= 1; y++) {
                b.set(-1, y, z, b.trap(Realm.CRUSHER.get(), Direction.EAST));
                b.set(1, y, z, b.trap(Realm.CRUSHER.get(), Direction.WEST));
                b.set(-2, y, z, stone);
                b.set(2, y, z, stone);
            }
            b.fill(-2, 2, z, 2, 2, z, bricks);
        }
        b.set(0, 0, -2, Blocks.STONE_PRESSURE_PLATE);
        b.set(0, 0, 2, Blocks.STONE_PRESSURE_PLATE);
        // end of the passage: two levers in niches -> AND gate -> iron door of the vault
        b.fill(-2, 0, 3, 2, 2, 3, bricks);
        b.set(0, 0, 3, Blocks.AIR);
        b.set(0, 1, 3, Blocks.AIR);
        b.set(-1, 0, 4, b.lever(Direction.SOUTH));
        b.set(1, 0, 4, b.lever(Direction.SOUTH));
        b.set(-1, 1, 4, ModRegistry.INSTANT_LAMP.get());
        b.set(1, 1, 4, ModRegistry.INSTANT_LAMP.get());
        b.set(0, 0, 4, b.gate(ModRegistry.AND_GATE.get(), Direction.SOUTH));
        b.fill(-2, 0, 5, 2, 2, 5, bricks);
        b.set(0, 0, 5, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
        b.set(0, 1, 5, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
        b.fill(-2, 0, 9, 2, 2, 9, bricks);
        b.fill(-2, 0, 6, -2, 2, 8, bricks);
        b.fill(2, 0, 6, 2, 2, 8, bricks);
        b.fill(-2, 3, 5, 2, 3, 9, bricks);
        b.fill(-2, 3, -4, 2, 3, 4, Realm.LICHEN_KARST.get().defaultBlockState());
        b.chest(0, 0, 8, Direction.NORTH, "realm_piston_karst");
        b.set(-1, 2, 7, ModRegistry.INSTANT_LAMP.get());
        b.set(-1, 2, 8, Blocks.REDSTONE_BLOCK);
    }

    // ---------- Switchyard Flats: tripper rail -> hazard switch, spike pit, detector signal posts ----------
    static void switchyardJunction(Build b) {
        BlockState soil = Realm.RUSTED_SOIL.get().defaultBlockState();
        BlockState slag = Realm.SLAG.get().defaultBlockState();
        b.foundation(-5, -10, 6, 10, soil, slag, 4);
        for (int z = -9; z <= 9; z++) {
            b.set(0, -1, z, Blocks.GRAVEL);
            b.set(0, 0, z, z == 0 ? Realm.TRIPPER_RAIL.get().defaultBlockState() : b.rail(RailShape.NORTH_SOUTH));
        }
        b.set(-1, 0, 0, b.trap(Realm.HAZARD_SWITCH.get(), Direction.EAST));
        // the rail strongly powers the gravel under it: the siren beside that block rings when something passes
        b.set(-1, -1, 0, ModRegistry.ALARM_SIREN.get());
        // spike pit on the other side of the track: mod Spike Blocks kept live by redstone blocks
        b.fill(1, -5, -2, 5, -1, 2, slag);
        b.fill(1, -4, -1, 4, -1, 1, Blocks.AIR.defaultBlockState());
        for (int x = 1; x <= 4; x++) {
            for (int z = -1; z <= 1; z++) {
                b.set(x, -6, z, Blocks.REDSTONE_BLOCK);
                b.set(x, -5, z, ModRegistry.SPIKE_BLOCK.get().defaultBlockState().setValue(PoweredBlock.POWERED, true));
            }
        }
        // signal posts: player detector lights the lamp above it when someone is within 8 blocks
        for (int z : new int[]{-5, 5}) {
            b.set(-3, 0, z, Blocks.DARK_OAK_FENCE);
            b.set(-3, 1, z, Blocks.DARK_OAK_FENCE);
            b.set(-3, 2, z, ModRegistry.PLAYER_DETECTOR.get());
            b.set(-3, 3, z, ModRegistry.INSTANT_LAMP.get());
        }
        b.set(-4, 0, -7, Blocks.COAL_BLOCK);
        b.set(-4, 1, -7, Blocks.COAL_BLOCK);
        b.set(-3, 0, -7, Blocks.COAL_BLOCK);
        b.chest(-3, 0, -8, Direction.EAST, "realm_switchyard_flats");
    }

    // ---------- Resonance Hollows: sculk sensors -> lockdown gates, signal display shows the sensor level ----------
    static void resonanceGatehouse(Build b) {
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState roof = Blocks.DEEPSLATE_TILES.defaultBlockState();
        b.fill(-5, -1, -6, 5, 5, 6, wall);
        b.fill(-4, 0, -5, 4, 4, 5, Blocks.AIR.defaultBlockState());
        b.fill(-4, -1, -5, 4, -1, 5, floor);
        b.fill(-4, 5, -5, 4, 5, 5, roof);
        b.fill(-1, 0, -6, 1, 1, -6, Blocks.AIR.defaultBlockState());
        b.fill(-1, 0, 6, 1, 1, 6, Blocks.AIR.defaultBlockState());
        b.fill(-4, 0, 0, 4, 4, 0, wall);
        b.fill(-1, 0, 0, 1, 1, 0, Realm.LOCKDOWN_GATE.get().defaultBlockState());
        b.set(-2, 0, 0, Blocks.SCULK_SENSOR);
        b.set(2, 0, 0, Blocks.SCULK_SENSOR);
        b.set(-3, 0, 0, ModRegistry.SIGNAL_DISPLAY.get());
        for (int x : new int[]{-4, 4}) {
            for (int z : new int[]{-5, 5}) {
                b.set(x, 4, z, Realm.RESONANT_CRYSTAL.get());
            }
        }
        b.set(0, 4, -3, Realm.RESONANT_CRYSTAL.get());
        b.set(0, 4, 3, Realm.RESONANT_CRYSTAL.get());
        b.chest(0, 0, 4, Direction.NORTH, "realm_resonance_hollows");
    }

    // ---------- Sluice Gardens: bridge tripwire -> floodgates along the bank ----------
    static void sluiceBridge(Build b) {
        BlockState bank = Realm.CRACKED_REALMSTONE_BRICKS.get().defaultBlockState();
        BlockState copper = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        b.foundation(-7, -7, 7, 7, Realm.CANAL_MOSS.get().defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(), 4);
        // channel with water along z
        for (int z = -7; z <= 7; z++) {
            b.set(-3, -5, z, bank);
            b.set(3, -5, z, bank);
            for (int y = -5; y <= -1; y++) {
                b.set(-3, y, z, y == -1 ? copper : bank);
                b.set(3, y, z, y == -1 ? copper : bank);
            }
            for (int x = -2; x <= 2; x++) {
                b.set(x, -5, z, Blocks.MUD);
                for (int y = -4; y <= -2; y++) {
                    b.set(x, y, z, Blocks.WATER);
                }
                b.set(x, -1, z, Blocks.AIR);
            }
        }
        // bridge deck and the tripwire across it
        b.fill(-2, -1, -1, 2, -1, 1, Blocks.SPRUCE_PLANKS);
        b.tripwire(-2, 2, 0, 0, bank);
        for (int side : new int[]{-4, 4}) {
            b.set(side, 1, 0, bank);
            b.set(side, 2, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
            // floodgates beside the post (the hook powers the post), the rest follow as a ripple along the bank
            for (int z = -4; z <= -1; z++) {
                b.set(side, 0, z, b.trap(Realm.FLOODGATE.get(), Direction.SOUTH));
            }
            b.set(side, 0, 1, b.trap(Realm.FLOODGATE.get(), Direction.SOUTH));
            b.set(side, 1, -1, b.trap(Realm.FLOODGATE.get(), Direction.SOUTH));
            // lamp behind the post shows when the wire was tripped
            b.set(side + Integer.signum(side), 0, 0, ModRegistry.INSTANT_LAMP.get());
        }
        b.chest(5, 0, 3, Direction.WEST, "realm_sluice_gardens");
    }

    // ---------- Kiln Barrens: pressure plates -> turret walls; a counter keeps score of the volleys ----------
    static void kilnBridge(Build b) {
        BlockState deck = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState rock = Blocks.BLACKSTONE.defaultBlockState();
        b.foundation(-4, -9, 4, 9, deck, Blocks.BASALT.defaultBlockState(), 4);
        for (int z = -9; z <= 9; z++) {
            for (int side : new int[]{-1, 1}) {
                b.set(side * 3, -1, z, Blocks.LAVA);
                b.set(side * 3, -2, z, rock);
                b.set(side * 4, -1, z, rock);
            }
        }
        for (int z = -5; z <= 5; z++) {
            b.set(-2, 0, z, b.trap(Realm.KILN_TURRET.get(), Direction.EAST));
            b.set(2, 0, z, b.trap(Realm.KILN_TURRET.get(), Direction.WEST));
            b.set(-2, 1, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
            b.set(2, 1, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        }
        for (int z : new int[]{-5, 5}) {
            b.set(-1, 0, z, Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE);
            b.set(1, 0, z, Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE);
        }
        // volley counter: the plate is the counter's back input, the display shows how often the trap went off
        b.set(1, 0, -6, b.gate(ModRegistry.COUNTER.get(), Direction.NORTH));
        b.set(1, 0, -7, ModRegistry.SIGNAL_DISPLAY.get());
        b.chest(0, 0, 8, Direction.NORTH, "realm_kiln_barrens");
    }

    // ---------- Tripwire Briar: tripwire -> launcher walls; a laser sensor watch post further on ----------
    static void briarAmbush(Build b) {
        BlockState moss = Realm.BRIAR_SOIL.get().defaultBlockState();
        BlockState cobble = Realm.CRACKED_REALMSTONE_BRICKS.get().defaultBlockState();
        b.foundation(-5, -8, 5, 9, moss, Blocks.DIRT.defaultBlockState(), 4);
        b.fill(-1, -1, -8, 1, -1, 9, Blocks.COARSE_DIRT.defaultBlockState());
        b.tripwire(-1, 1, 0, 0, cobble);
        for (int side : new int[]{-3, 3}) {
            Direction face = side < 0 ? Direction.EAST : Direction.WEST;
            b.set(side, 1, 0, cobble);
            b.set(side, 2, 0, cobble);
            for (int z = -2; z <= 2; z++) {
                if (z == 0) {
                    continue;
                }
                b.set(side, 0, z, b.trap(Realm.VOLLEY_LAUNCHER.get(), face));
                b.set(side, 1, z, b.trap(Realm.VOLLEY_LAUNCHER.get(), face));
            }
            for (int z = -6; z <= 8; z += 2) {
                if (b.random.nextInt(3) > 0) {
                    b.set(side + Integer.signum(side), 0, z, Realm.BRIAR_THORNS.get());
                }
            }
        }
        // watch post: a laser beam across the path; whoever breaks it sets off the siren
        b.set(-3, 0, 5, cobble);
        b.set(-3, 1, 5, ModRegistry.LASER_SENSOR.get().defaultBlockState().setValue(Sensors.LaserSensor.FACING, Direction.EAST));
        b.set(-3, 2, 5, ModRegistry.ALARM_SIREN.get());
        b.set(3, 0, 5, cobble);
        b.set(3, 1, 5, cobble);
        b.chest(0, 0, 8, Direction.NORTH, "realm_tripwire_briar");
    }

    // ---------- Switchyard decoration: straight track with a signal lamp at each end ----------
    static void railLine(Build b) {
        int length = 8 + b.random.nextInt(10);
        for (int z = 0; z <= length; z++) {
            BlockState below = b.get(0, -1, z);
            if (!below.isSolid() || !b.get(0, 0, z).canBeReplaced()) {
                length = z - 1;
                break;
            }
            b.set(0, -1, z, Blocks.GRAVEL);
            b.set(0, 0, z, b.rail(RailShape.NORTH_SOUTH));
        }
        if (length < 3) {
            return;
        }
        for (int z : new int[]{0, length}) {
            if (b.get(-1, 0, z).canBeReplaced() && b.get(-1, -1, z).isSolid()) {
                b.set(-1, 0, z, Blocks.DARK_OAK_FENCE);
                b.set(-1, 1, z, ModRegistry.PLAYER_DETECTOR.get());
                b.set(-1, 2, z, ModRegistry.INSTANT_LAMP.get());
            }
        }
    }

    static BlockState state(Block block) {
        return block.defaultBlockState();
    }

    static BlockState wireless(Block block, int channel) {
        return block.defaultBlockState().setValue(Wireless.CHANNEL, channel);
    }

    static BlockState lit(Block lamp) {
        return lamp.defaultBlockState().setValue(BlockStateProperties.LIT, false);
    }
}
