package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.PoweredBlock;
import de.eron.redstoneplus.block.Sensors;
import de.eron.redstoneplus.block.gate.GateBlock;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * Scenery and working machines of the realm. Every piece takes its materials from the biome it stands in.
 * The machines run on their own (clocks, detectors, rails) so they can be understood by watching them;
 * there is never any text in the world.
 */
final class RealmStructures {
    private RealmStructures() {
    }

    /** Materials of one biome: main building block, a metal, an accent and something that glows. */
    record Palette(BlockState bricks, BlockState metal, BlockState accent, BlockState glow, BlockState ground) {
        static Palette of(RealmFeatures.Build b) {
            String biome = b.level.getBiome(b.origin).unwrapKey().map(k -> k.location().getPath()).orElse("");
            BlockState bricks = s(Realm.REALMSTONE_BRICKS.get());
            BlockState rust = s(Realm.RUST_PLATING.get());
            BlockState vein = s(Realm.REDSTONE_VEIN.get());
            return switch (biome) {
                case "piston_karst" -> new Palette(s(Realm.KARST_BRICKS.get()), rust, s(Realm.LICHEN_KARST.get()), vein, s(Realm.KARST_LIMESTONE.get()));
                case "kiln_barrens" -> new Palette(s(Blocks.POLISHED_BLACKSTONE_BRICKS), rust, s(Realm.SULFUR_CRUST.get()), s(Blocks.MAGMA_BLOCK),
                        s(Realm.CINDER_ROCK.get()));
                // waxed copper: it never turns teal, the realm stays red, orange and yellow
                case "sluice_gardens", "tripwire_briar" -> new Palette(bricks, s(Blocks.WAXED_CUT_COPPER), s(Blocks.WAXED_COPPER_BLOCK), vein,
                        s(Realm.CANAL_MOSS.get()));
                case "frostwork_wastes" -> new Palette(s(Realm.FROST_REALMSTONE.get()), s(Realm.SHELL_PLATING.get()), s(Realm.SALT_CRUST.get()), vein,
                        s(Realm.SALT_CRUST.get()));
                case "tempest_shoals" -> new Palette(s(Realm.TEMPEST_BASALT.get()), s(Realm.SHELL_PLATING.get()), s(Realm.REDSTONE_CLUSTER.get()), vein,
                        s(Realm.TEMPEST_BASALT.get()));
                case "oxide_salt_flats" -> new Palette(bricks, s(Blocks.WAXED_EXPOSED_COPPER), s(Realm.SALT_CRUST.get()), vein, s(Realm.SALT_CRUST.get()));
                case "hematite_scarps" -> new Palette(s(Realm.DARK_HEMATITE.get()), rust, s(Realm.HEMATITE.get()), vein, s(Realm.HEMATITE.get()));
                case "arsenal_dunes" -> new Palette(bricks, s(Realm.SHELL_PLATING.get()), rust, vein, s(Realm.RUST_SAND.get()));
                case "lamplit_grove" -> new Palette(s(Realm.DEEP_REALMSTONE.get()), rust, s(Realm.GROVE_MOSS.get()), vein, s(Realm.GROVE_MOSS.get()));
                default -> new Palette(bricks, rust, s(Realm.REALMSTONE.get()), vein, s(Realm.REALMSTONE.get()));
            };
        }

        private static BlockState s(Block block) {
            return block.defaultBlockState();
        }
    }

    // =================================================================================================== scenery

    /** A derelict lattice pylon; leans in the dunes and the frost. A detector at the top lights its lamp when someone comes close. */
    static void tower(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        String biome = b.level.getBiome(b.origin).unwrapKey().map(k -> k.location().getPath()).orElse("");
        boolean leans = biome.equals("arsenal_dunes") || biome.equals("frostwork_wastes") || b.random.nextInt(4) == 0;
        int height = 12 + b.random.nextInt(14);
        double lean = leans ? 0.12 + b.random.nextDouble() * 0.12 : 0;
        for (int y = -3; y <= height; y++) {
            int shift = (int) Math.round(Math.max(0, y) * lean);
            for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                b.set(c[0] + shift, y, c[1], y % 5 == 0 ? p.bricks : p.metal);
            }
            if (y > 0 && y % 4 == 0) {
                for (int i = -1; i <= 1; i++) {
                    b.set(i + shift, y, -1, p.metal);
                    b.set(i + shift, y, 1, p.metal);
                    b.set(-1 + shift, y, i, p.metal);
                    b.set(1 + shift, y, i, p.metal);
                }
            }
        }
        int top = height + 1;
        int shift = (int) Math.round(height * lean);
        b.fill(-1 + shift, top, -1, 1 + shift, top, 1, p.bricks);
        b.set(shift, top + 1, 0, ModRegistry.PLAYER_DETECTOR.get());
        b.set(shift, top + 2, 0, ModRegistry.INSTANT_LAMP.get());
        if (b.random.nextBoolean()) {
            b.set(shift + 1, top + 1, 0, Blocks.LIGHTNING_ROD);
        }
    }

    /** Broken walls of an old outpost. */
    static void ruin(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        int w = 4 + b.random.nextInt(5);
        int d = 4 + b.random.nextInt(5);
        for (int x = -w; x <= w; x++) {
            for (int z = -d; z <= d; z++) {
                boolean edge = Math.abs(x) == w || Math.abs(z) == d;
                if (!edge) {
                    if (b.random.nextInt(4) == 0) {
                        b.set(x, -1, z, p.bricks);
                    }
                    continue;
                }
                int h = b.random.nextInt(5) - 1;
                for (int y = -1; y <= h; y++) {
                    b.set(x, y, z, b.random.nextInt(6) == 0 ? p.accent : p.bricks);
                }
            }
        }
        // a doorway and a fallen block or two
        b.fill(0, 0, -d, 0, 2, -d, Blocks.AIR.defaultBlockState());
        b.set(b.random.nextInt(3) - 1, 0, 0, p.bricks);
    }

    /** A tall split monolith of dark rock with glowing seams (the moors' landmark). */
    static void monolith(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        int h = 18 + b.random.nextInt(14);
        for (int y = -4; y <= h; y++) {
            int r = y < h * 0.6 ? 2 : 1;
            for (int x = -r; x <= r; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (x == 0 && y > h * 0.35) {
                        continue; // the split
                    }
                    if (Math.abs(z) == 2 && b.random.nextInt(3) == 0) {
                        continue;
                    }
                    b.set(x, y, z, b.random.nextInt(18) == 0 ? p.glow : p.bricks);
                }
            }
        }
    }

    /**
     * A chain anchor: one of the great chains that bind the trapped realm to the Bell, bolted into a bronze anchor block
     * on a stepped plinth, rising straight up out of sight. When the realm is freed they fall (see RealmStory).
     */
    static void chainAnchor(RealmFeatures.Build b) {
        BlockState bricks = Realm.REALMSTONE_BRICKS.get().defaultBlockState();
        BlockState chiselled = Realm.CHISELED_REALMSTONE_BRICKS.get().defaultBlockState();
        BlockState cracked = Realm.CRACKED_REALMSTONE_BRICKS.get().defaultBlockState();
        BlockState vein = Realm.REDSTONE_VEIN.get().defaultBlockState();
        BlockState bronze = Realm.BELL_BRONZE.get().defaultBlockState();
        BlockState rust = Realm.RUST_PLATING.get().defaultBlockState();
        BlockState link = Realm.CHAIN_LINK.get().defaultBlockState();
        // the plinth: three steps, with glowing cracks where the chain pulls on it
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z));
                if (m == 6 && Math.abs(x) == Math.abs(z)) {
                    continue;
                }
                int h = m >= 5 ? 0 : m >= 3 ? 1 : 2;
                for (int y = -4; y <= h; y++) {
                    boolean crack = y == h && (x == z || x == -z) && m >= 2 && b.random.nextInt(3) > 0;
                    b.set(x, y, z, crack ? vein : y == h && m == 2 ? chiselled : b.random.nextInt(6) == 0 ? cracked : bricks);
                }
            }
        }
        // the anchor block of bronze and its shackle
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 3; y <= 4; y++) {
                    b.set(x, y, z, (x == 0 || z == 0) ? bronze : rust);
                }
            }
        }
        for (int y = 5; y <= 6; y++) {
            b.set(-1, y, 0, bronze);
            b.set(1, y, 0, bronze);
        }
        b.set(0, 7, 0, bronze);
        // four bolts at the corners, pinned with chains of their own
        for (int[] c : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
            b.set(c[0], 2, c[1], rust);
            b.set(c[0], 3, c[1], net.minecraft.world.level.block.Blocks.CHAIN.defaultBlockState());
        }
        // the giant chain, straight up into the sky
        int top = Sanctums.chainTop(b.level) - b.origin.getY();
        for (int y = 8; y <= top; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Sanctums.isLink(x, z, y - 8)) {
                        b.set(x, y, z, link);
                    }
                }
            }
        }
    }

    /** A giant pale-root tree with veins of light and lamps hanging from its branches on chains. */
    static void giantTree(RealmFeatures.Build b) {
        BlockState log = Realm.PALE_ROOT_LOG.get().defaultBlockState();
        BlockState vein = Realm.VEIN_LOG.get().defaultBlockState();
        BlockState moss = Realm.GROVE_MOSS.get().defaultBlockState();
        int h = 14 + b.random.nextInt(10);
        for (int y = -3; y <= h; y++) {
            int r = y < 3 ? 3 : 2;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r + 1) {
                        continue;
                    }
                    boolean seam = (x == 0 || z == 0) && (x + z + y) % 5 == 0;
                    b.set(x, y, z, seam ? vein : log);
                }
            }
        }
        // root flares
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int i = 3; i <= 6; i++) {
                b.set(dir.getStepX() * i, 3 - i, dir.getStepZ() * i, log);
                b.set(dir.getStepX() * i, 2 - i, dir.getStepZ() * i, log);
            }
        }
        // branches with hanging lamps
        int branches = 4 + b.random.nextInt(3);
        for (int i = 0; i < branches; i++) {
            double a = i * Math.PI * 2 / branches + b.random.nextDouble() * 0.4;
            int len = 6 + b.random.nextInt(5);
            int by = h - 3 + b.random.nextInt(4);
            int ex = 0;
            int ez = 0;
            for (int k = 2; k <= len; k++) {
                ex = (int) Math.round(Math.cos(a) * k);
                ez = (int) Math.round(Math.sin(a) * k);
                int ey = by + k / 3;
                b.set(ex, ey, ez, log.setValue(RotatedPillarBlock.AXIS, Math.abs(Math.cos(a)) > 0.7 ? Direction.Axis.X : Direction.Axis.Z));
                if (b.random.nextInt(3) == 0) {
                    b.set(ex, ey + 1, ez, moss);
                }
            }
            int ey = by + len / 3;
            b.fill(ex - 1, ey + 1, ez - 1, ex + 1, ey + 2, ez + 1, moss);
            int chain = 3 + b.random.nextInt(5);
            for (int c = 1; c <= chain; c++) {
                b.set(ex, ey - c, ez, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            }
            b.set(ex, ey - chain - 1, ez, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
        }
    }

    /** A spent shell casing that fell from the sky and lies half buried in the sand. */
    static void crashedShell(RealmFeatures.Build b) {
        BlockState shell = Realm.SHELL_PLATING.get().defaultBlockState();
        BlockState rust = Realm.RUST_PLATING.get().defaultBlockState();
        int len = 12 + b.random.nextInt(8);
        for (int x = 0; x <= len; x++) {
            int y0 = x / 3 - 2;
            double r = x > len - 3 ? 2.6 - (x - (len - 3)) * 0.7 : 2.6;
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    double d = Math.sqrt(y * y + z * z);
                    if (d <= r && d > r - 1.2) {
                        b.set(x, y0 + y, z, x % 5 == 0 ? rust : shell);
                    } else if (d <= r - 1.2) {
                        b.set(x, y0 + y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        // fins at the tail
        for (int k = 1; k <= 3; k++) {
            b.set(0, -2 + k + 2, 0, rust);
            b.set(0, -2, -2 - k, rust);
            b.set(0, -2, 2 + k, rust);
        }
    }

    /** A glass dome over a growing redstone crystal, on a terrace of red pools. */
    static void crystalDome(RealmFeatures.Build b) {
        BlockState bricks = Realm.REALMSTONE_BRICKS.get().defaultBlockState();
        b.foundation(-6, -6, 6, 6, bricks, Realm.REALMSTONE.get().defaultBlockState(), 7);
        for (int x = -5; x <= 5; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    double d = Math.sqrt(x * x + y * y * 1.2 + z * z);
                    if (d > 4.3 && d <= 5.3) {
                        b.set(x, y, z, Blocks.RED_STAINED_GLASS.defaultBlockState());
                    }
                }
            }
        }
        b.fill(-1, -1, -1, 1, -1, 1, Realm.REDSTONE_VEIN.get().defaultBlockState());
        b.set(0, 0, 0, Realm.REDSTONE_CLUSTER.get().defaultBlockState());
        b.set(1, 0, 1, Realm.REDSTONE_CLUSTER.get().defaultBlockState());
        b.set(-1, 0, 1, Realm.RED_CORAL_SHRUB.get().defaultBlockState());
        for (int x = -6; x <= 6; x++) {
            b.set(x, -1, 6, Blocks.WATER.defaultBlockState());
            b.set(x, -2, 6, bricks);
        }
    }

    /** A plank walkway on posts across the fen, with a lamp post now and then. */
    static void boardwalk(RealmFeatures.Build b) {
        int len = 10 + b.random.nextInt(14);
        for (int z = 0; z <= len; z++) {
            for (int x = -1; x <= 1; x++) {
                b.set(x, 0, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
            if (z % 4 == 0) {
                for (int y = -1; y >= -5; y--) {
                    b.set(-1, y, z, Blocks.SPRUCE_LOG.defaultBlockState());
                    b.set(1, y, z, Blocks.SPRUCE_LOG.defaultBlockState());
                }
            }
            if (z % 8 == 4) {
                b.set(1, 1, z, Blocks.SPRUCE_FENCE);
                b.set(1, 2, z, Blocks.SPRUCE_FENCE);
                b.set(1, 3, z, ModRegistry.PLAYER_DETECTOR.get());
                b.set(1, 4, z, ModRegistry.INSTANT_LAMP.get());
            }
        }
    }

    /** A stepped brick kiln with a fire burning inside. */
    static void kilnHut(RealmFeatures.Build b) {
        BlockState brick = Realm.RED_CLAY.get().defaultBlockState();
        BlockState bricks = Realm.REALMSTONE_BRICKS.get().defaultBlockState();
        b.foundation(-4, -4, 4, 4, bricks, Realm.FEN_MUD.get().defaultBlockState(), 7);
        for (int y = 0; y <= 5; y++) {
            int r = 4 - y * 4 / 6;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean shell = Math.abs(x) == r || Math.abs(z) == r;
                    b.set(x, y, z, shell ? (y % 2 == 0 ? bricks : brick) : Blocks.AIR.defaultBlockState());
                }
            }
        }
        b.fill(0, 0, -4, 0, 1, -4, Blocks.AIR.defaultBlockState());
        b.set(0, 0, 0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true));
        b.set(0, 6, 0, Blocks.AIR.defaultBlockState());
    }

    /** Aqueduct arches carrying a channel of water that spills off the broken end. */
    static void aqueduct(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        int len = 12 + b.random.nextInt(12);
        int h = 6 + b.random.nextInt(4);
        for (int x = 0; x <= len; x++) {
            if (x % 4 == 0) {
                for (int y = -6; y < h; y++) {
                    b.set(x, y, 0, p.bricks);
                }
            } else {
                b.set(x, h - 1, 0, p.bricks);
                if (x % 4 == 2) {
                    b.set(x, h - 2, 0, p.bricks);
                }
            }
            b.set(x, h, -1, p.metal);
            b.set(x, h, 1, p.metal);
            b.set(x, h, 0, x == len ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState());
            b.set(x, h - 1, 0, p.bricks);
        }
    }

    /** A patch of old rails running under a crust of amber rime. */
    static void iceRails(RealmFeatures.Build b) {
        int len = 8 + b.random.nextInt(12);
        for (int z = 0; z <= len; z++) {
            b.set(0, -2, z, Realm.FROST_REALMSTONE.get());
            b.set(0, -1, z, b.rail(RailShape.NORTH_SOUTH));
            for (int x = -1; x <= 1; x++) {
                b.set(x, 0, z, Blocks.ORANGE_STAINED_GLASS);
            }
            if (z % 7 == 3) {
                b.set(1, -1, z, Realm.REDSTONE_VEIN.get());
            }
        }
    }

    /** Heaps of scrap and a length of sunken track on the salt. */
    static void scrap(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        for (int i = 0; i < 8; i++) {
            int x = b.random.nextInt(9) - 4;
            int z = b.random.nextInt(9) - 4;
            int h = b.random.nextInt(3);
            for (int y = -1; y <= h; y++) {
                b.set(x, y, z, b.random.nextBoolean() ? p.metal : Realm.RUST_PLATING.get().defaultBlockState());
            }
        }
        for (int x = -6; x <= 6; x++) {
            if (b.random.nextInt(4) > 0) {
                b.set(x, 0, 5, b.rail(RailShape.EAST_WEST));
            }
        }
    }

    // =================================================================================================== machines

    /** A pylon whose clock drives a sequencer: its three lamps chase each other day and night. */
    static void lampPylon(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        int h = 5 + b.random.nextInt(5);
        for (int y = -3; y < h; y++) {
            b.set(0, y, 0, p.bricks);
        }
        b.fill(-2, h, -2, 2, h, 2, p.bricks);
        b.set(-1, h + 1, 0, b.gate(ModRegistry.CLOCK.get(), Direction.EAST).setValue(GateBlock.DELAY, 4));
        b.set(0, h + 1, 0, b.gate(ModRegistry.SEQUENCER.get(), Direction.EAST));
        b.set(0, h + 1, -1, ModRegistry.INSTANT_LAMP.get());
        b.set(1, h + 1, 0, ModRegistry.INSTANT_LAMP.get());
        b.set(0, h + 1, 1, ModRegistry.INSTANT_LAMP.get());
    }

    /** Two walls of crushers stamping into a trough, driven by a clock at the head of each wall. */
    static void crusherMill(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        b.foundation(-3, -2, 3, 6, p.bricks, p.ground, 4);
        for (int z = 0; z <= 4; z++) {
            b.set(0, -1, z, Realm.SLAG.get());
            for (int y = 0; y <= 1; y++) {
                b.set(-1, y, z, b.trap(Realm.CRUSHER.get(), Direction.EAST));
                b.set(1, y, z, b.trap(Realm.CRUSHER.get(), Direction.WEST));
                b.set(-2, y, z, p.bricks);
                b.set(2, y, z, p.bricks);
            }
            b.fill(-2, 2, z, 2, 2, z, p.metal);
        }
        b.set(-1, 0, -1, b.gate(ModRegistry.CLOCK.get(), Direction.SOUTH).setValue(GateBlock.DELAY, 12));
        b.set(1, 0, -1, b.gate(ModRegistry.CLOCK.get(), Direction.SOUTH).setValue(GateBlock.DELAY, 12));
    }

    /** A basin whose floodgates surge on a slow clock. */
    static void pumpStation(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        b.foundation(-4, -3, 4, 4, p.bricks, p.bricks, 3);
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 3; z++) {
                b.set(x, -1, z, Blocks.WATER.defaultBlockState());
                b.set(x, -2, z, p.bricks);
            }
        }
        for (int x = -3; x <= 3; x++) {
            b.set(x, 0, -3, b.trap(Realm.FLOODGATE.get(), Direction.SOUTH));
            b.set(x, 1, -3, p.metal);
        }
        b.set(-4, 0, -3, b.gate(ModRegistry.CLOCK.get(), Direction.EAST).setValue(GateBlock.DELAY, 20));
    }

    /** A loop of track with a minecart that never stops; a tripper rail counts its laps on a display. */
    static void minecartLoop(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        b.foundation(-6, -4, 6, 5, p.ground, p.bricks, 3);
        for (int x = -4; x <= 4; x++) {
            b.set(x, -1, -2, Blocks.GRAVEL);
            b.set(x, -1, 2, Blocks.GRAVEL);
            boolean powered = x >= -2 && x <= 2;
            if (powered) {
                b.set(x, -1, -2, Blocks.REDSTONE_BLOCK);
                b.set(x, 0, -2, Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST)
                        .setValue(PoweredRailBlock.POWERED, true));
            } else {
                b.set(x, 0, -2, b.rail(RailShape.EAST_WEST));
            }
            b.set(x, 0, 2, x == 0 ? Realm.TRIPPER_RAIL.get().defaultBlockState().setValue(
                    net.minecraft.world.level.block.DetectorRailBlock.SHAPE, RailShape.EAST_WEST) : b.rail(RailShape.EAST_WEST));
        }
        for (int z = -1; z <= 1; z++) {
            b.set(-4, -1, z, Blocks.GRAVEL);
            b.set(4, -1, z, Blocks.GRAVEL);
            b.set(-4, 0, z, b.rail(RailShape.NORTH_SOUTH));
            b.set(4, 0, z, b.rail(RailShape.NORTH_SOUTH));
        }
        b.set(-4, 0, -2, b.rail(RailShape.SOUTH_EAST));
        b.set(4, 0, -2, b.rail(RailShape.SOUTH_WEST));
        b.set(4, 0, 2, b.rail(RailShape.NORTH_WEST));
        b.set(-4, 0, 2, b.rail(RailShape.NORTH_EAST));
        // lap counter: the tripper rail is the counter's input, the display shows the laps
        b.set(0, 0, 3, b.gate(ModRegistry.COUNTER.get(), Direction.SOUTH));
        b.set(0, 0, 4, ModRegistry.SIGNAL_DISPLAY.get());
        b.set(1, 0, 3, ModRegistry.INSTANT_LAMP.get());
        // the cart itself
        Minecart cart = EntityType.MINECART.create(((net.minecraft.world.level.ServerLevelAccessor) b.level).getLevel());
        if (cart != null) {
            Vec3 at = Vec3.atBottomCenterOf(b.at(-1, 0, -2));
            Direction east = b.dir(Direction.EAST);
            cart.moveTo(at.x, at.y + 0.1, at.z);
            cart.setDeltaMovement(east.getStepX() * 0.5, 0, east.getStepZ() * 0.5);
            b.level.addFreshEntity(cart);
        }
    }

    /** A basalt spire whose clock, divided by three flip-flops, calls down lightning every few seconds. */
    static void stormSpire(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        int h = 12 + b.random.nextInt(8);
        for (int y = -4; y < h; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    b.set(x, y, z, (y % 6 == 0 && x != 0 && z != 0) ? p.glow : p.bricks);
                }
            }
        }
        b.fill(-3, h, -1, 3, h, 1, p.bricks);
        b.set(-3, h + 1, 0, b.gate(ModRegistry.CLOCK.get(), Direction.EAST).setValue(GateBlock.DELAY, 20));
        b.set(-2, h + 1, 0, b.gate(ModRegistry.T_FLIP_FLOP.get(), Direction.EAST));
        b.set(-1, h + 1, 0, b.gate(ModRegistry.T_FLIP_FLOP.get(), Direction.EAST));
        b.set(0, h + 1, 0, b.gate(ModRegistry.T_FLIP_FLOP.get(), Direction.EAST));
        b.set(1, h + 1, 0, ModRegistry.LIGHTNING_CALLER.get().defaultBlockState()
                .setValue(de.eron.redstoneplus.block.MachineBlock.FACING, Direction.UP));
        b.set(2, h + 1, 0, Blocks.LIGHTNING_ROD);
    }

    /** A bell tower on the moors: it tolls on a slow clock, and its gate slams when the sensor hears footsteps. */
    static void bellTower(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        b.foundation(-3, -3, 3, 4, p.bricks, p.bricks, 12);
        int h = 10 + b.random.nextInt(4);
        for (int y = 0; y <= h; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                    boolean open = y > h - 4 && Math.abs(x) < 2 && Math.abs(z) == 2 || y > h - 4 && Math.abs(z) < 2 && Math.abs(x) == 2;
                    b.set(x, y, z, wall && !open ? (b.random.nextInt(10) == 0 ? p.accent : p.bricks) : Blocks.AIR.defaultBlockState());
                }
            }
        }
        b.fill(-2, h + 1, -2, 2, h + 1, 2, p.bricks);
        // a clock divided by a flip-flop powers the block under the bell: every pulse rings it
        b.fill(-1, h - 5, -1, 1, h - 5, 1, p.bricks);
        b.set(-1, h - 4, 0, b.gate(ModRegistry.CLOCK.get(), Direction.EAST).setValue(GateBlock.DELAY, 20));
        b.set(0, h - 4, 0, b.gate(ModRegistry.T_FLIP_FLOP.get(), Direction.EAST));
        b.set(1, h - 4, 0, p.bricks);
        b.set(1, h - 3, 0, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        // the doorway: lockdown gates with a sculk sensor beside them
        b.fill(-1, 0, -2, 0, 1, -2, Realm.LOCKDOWN_GATE.get().defaultBlockState());
        b.set(1, 0, -2, Blocks.SCULK_SENSOR);
        b.set(-2, 0, -3, Blocks.REDSTONE_LAMP);
    }

    /** A cage cut into the rock with something inside; the lever on the lintel opens its iron door. */
    static void beastCage(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        b.foundation(-3, -2, 3, 4, p.bricks, p.bricks, 4);
        for (int x = -2; x <= 2; x++) {
            for (int z = -1; z <= 3; z++) {
                for (int y = 0; y <= 3; y++) {
                    boolean wall = Math.abs(x) == 2 || z == -1 || z == 3 || y == 3;
                    b.set(x, y, z, wall ? (z == -1 && y < 3 ? Blocks.IRON_BARS.defaultBlockState() : p.bricks) : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // the door, its lintel, and the lever that powers the lintel (a powered block above opens both halves)
        b.set(0, 0, -1, Blocks.IRON_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.SOUTH)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        b.set(0, 1, -1, Blocks.IRON_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.SOUTH)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        b.set(0, 2, -1, p.bricks);
        b.set(0, 2, -2, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.NORTH));
        EntityType<?> type = b.random.nextBoolean() ? Realm.LEAKING_CELL.get() : Realm.DETONATOR_HUSK.get();
        if (type.create(((net.minecraft.world.level.ServerLevelAccessor) b.level).getLevel()) instanceof Mob mob) {
            Vec3 at = Vec3.atBottomCenterOf(b.at(0, 0, 1));
            mob.moveTo(at.x, at.y, at.z, b.random.nextFloat() * 360, 0);
            mob.setPersistenceRequired();
            b.level.addFreshEntity(mob);
        }
    }

    /** Laser watch post: whoever crosses the beam sets off the siren and the lamp. */
    static void laserPost(RealmFeatures.Build b) {
        Palette p = Palette.of(b);
        b.set(-4, 0, 0, p.bricks);
        b.set(-4, 1, 0, ModRegistry.LASER_SENSOR.get().defaultBlockState().setValue(Sensors.LaserSensor.FACING, Direction.EAST));
        b.set(-4, 2, 0, ModRegistry.ALARM_SIREN.get());
        b.set(-4, 3, 0, ModRegistry.INSTANT_LAMP.get());
        b.set(4, 0, 0, p.bricks);
        b.set(4, 1, 0, p.bricks);
        b.set(4, 2, 0, p.metal);
    }

    static boolean isPowered(BlockState state) {
        return state.hasProperty(PoweredBlock.POWERED) && state.getValue(PoweredBlock.POWERED);
    }
}
