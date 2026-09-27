package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.Sensors;
import de.eron.redstoneplus.block.Wireless;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Working example circuits built from the mod's own blocks, each with a sign that explains it.
 * The arrival station shows all of them; generated workshops show a random few.
 * <p>
 * Every exhibit fits in x -1..1, z -2..0 around its own origin with its sign at z = 1 facing south.
 * Signals flow from west to east. Gates output to their FACING and read the back (and left/right) side.
 */
public final class Exhibits {
    private Exhibits() {
    }

    @FunctionalInterface
    interface Exhibit {
        void build(RealmFeatures.Build b, int ox, int oz);
    }

    private record Named(String key, Exhibit exhibit) {
    }

    private static final List<Named> ALL = new ArrayList<>();

    static {
        // Clock -> lamp: the simplest clock circuit
        add("ex_blinker", (b, x, z) -> {
            b.set(x - 1, 0, z - 1, b.gate(ModRegistry.CLOCK.get(), Direction.EAST));
            b.set(x, 0, z - 1, ModRegistry.INSTANT_LAMP.get());
            b.set(x + 1, 0, z - 1, ModRegistry.INSTANT_LAMP.get());
        });
        // two levers into the sides of an AND gate
        add("ex_and", (b, x, z) -> {
            b.set(x, 0, z - 2, b.lever(Direction.EAST));
            b.set(x, 0, z, b.lever(Direction.EAST));
            b.set(x, 0, z - 1, b.gate(ModRegistry.AND_GATE.get(), Direction.EAST));
            b.set(x + 1, 0, z - 1, ModRegistry.INSTANT_LAMP.get());
        });
        // button on a block -> T flip-flop -> lamp: a push-on push-off switch
        add("ex_toggle", (b, x, z) -> {
            b.set(x - 1, 0, z - 1, Realm.KARST_BRICKS.get());
            b.set(x - 1, 1, z - 1, b.button());
            b.set(x, 0, z - 1, b.gate(ModRegistry.T_FLIP_FLOP.get(), Direction.EAST));
            b.set(x + 1, 0, z - 1, ModRegistry.INSTANT_LAMP.get());
        });
        // button -> counter -> signal display, second button on the side resets
        add("ex_counter", (b, x, z) -> {
            b.set(x - 1, 0, z - 1, Realm.KARST_BRICKS.get());
            b.set(x - 1, 1, z - 1, b.button());
            b.set(x, 0, z - 1, b.gate(ModRegistry.COUNTER.get(), Direction.EAST));
            b.set(x, 0, z - 2, Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
            b.set(x + 1, 0, z - 1, ModRegistry.SIGNAL_DISPLAY.get());
        });
        // clock -> sequencer -> three lamps: running lights
        add("ex_sequencer", (b, x, z) -> {
            b.set(x - 1, 0, z - 1, b.gate(ModRegistry.CLOCK.get(), Direction.EAST));
            b.set(x, 0, z - 1, b.gate(ModRegistry.SEQUENCER.get(), Direction.EAST));
            b.set(x, 0, z - 2, ModRegistry.INSTANT_LAMP.get());
            b.set(x + 1, 0, z - 1, ModRegistry.INSTANT_LAMP.get());
            b.set(x, 0, z, ModRegistry.INSTANT_LAMP.get());
        });
        // lever -> transmitter ... receiver -> lamp on channel 15
        add("ex_wireless", (b, x, z) -> {
            b.set(x - 1, 0, z, b.lever(Direction.NORTH));
            b.set(x - 1, 0, z - 1, RealmFeatures.wireless(ModRegistry.WIRELESS_TRANSMITTER.get(), 15));
            b.set(x + 1, 0, z - 2, RealmFeatures.wireless(ModRegistry.WIRELESS_RECEIVER.get(), 15));
            b.set(x + 1, 1, z - 2, ModRegistry.INSTANT_LAMP.get());
        });
        // player detector with a normal and an inverted lamp: NOT logic without a gate
        add("ex_detector", (b, x, z) -> {
            b.set(x, 0, z - 1, ModRegistry.PLAYER_DETECTOR.get());
            b.set(x + 1, 0, z - 1, ModRegistry.INSTANT_LAMP.get());
            b.set(x - 1, 0, z - 1, ModRegistry.INVERTED_LAMP.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
        });
        // laser sensor across a gap -> alarm siren and lamp
        add("ex_laser", (b, x, z) -> {
            b.set(x - 1, 0, z - 2, ModRegistry.INSTANT_LAMP.get());
            b.set(x - 1, 1, z - 2, ModRegistry.LASER_SENSOR.get().defaultBlockState().setValue(Sensors.LaserSensor.FACING, Direction.EAST));
            b.set(x - 1, 2, z - 2, ModRegistry.ALARM_SIREN.get());
            b.set(x + 1, 0, z - 2, Realm.KARST_BRICKS.get());
            b.set(x + 1, 1, z - 2, Realm.KARST_BRICKS.get());
        });
        // lever -> two crushers side by side: a realm trap, and how the ripple works
        add("ex_trap", (b, x, z) -> {
            b.set(x - 1, 0, z - 1, b.lever(Direction.NORTH));
            b.set(x, 0, z - 1, b.trap(Realm.CRUSHER.get(), Direction.NORTH));
            b.set(x + 1, 0, z - 1, b.trap(Realm.CRUSHER.get(), Direction.NORTH));
            b.set(x + 1, 1, z - 1, b.trap(Realm.CRUSHER.get(), Direction.NORTH));
        });
        // tripper rail -> hazard switch next to it, shows a trigger block of the realm
        add("ex_rail", (b, x, z) -> {
            b.set(x, 0, z - 2, b.rail(net.minecraft.world.level.block.state.properties.RailShape.NORTH_SOUTH));
            b.set(x, 0, z - 1, Realm.TRIPPER_RAIL.get().defaultBlockState());
            b.set(x, 0, z, b.rail(net.minecraft.world.level.block.state.properties.RailShape.NORTH_SOUTH));
            b.set(x - 1, 0, z - 1, ModRegistry.INSTANT_LAMP.get());
            b.set(x + 1, 0, z - 1, b.gate(ModRegistry.PULSE_EXTENDER.get(), Direction.EAST));
            b.set(x + 2, 0, z - 1, ModRegistry.INSTANT_LAMP.get());
        });
    }

    private static void add(String key, Exhibit exhibit) {
        ALL.add(new Named(key, exhibit));
    }

    private static void place(RealmFeatures.Build b, Named named, int x, int z) {
        named.exhibit.build(b, x, z);
        b.sign(x, 0, z + 1, Direction.SOUTH, named.key);
    }

    /** All exhibits in two rows of five, on the arrival platform. Rows at z = -1 and z = -6, slots 5 blocks apart. */
    static void station(RealmFeatures.Build b) {
        for (int i = 0; i < ALL.size(); i++) {
            int row = i / 5;
            int col = i % 5;
            place(b, ALL.get(i), -10 + col * 5, -1 - row * 5);
        }
    }

    /** A small roofed workshop with three random exhibits and a chest, found around the realm. */
    static void workshop(RealmFeatures.Build b) {
        BlockState floor = Blocks.POLISHED_TUFF.defaultBlockState();
        b.foundation(-7, -4, 7, 3, floor, Blocks.TUFF.defaultBlockState(), 4);
        for (int x : new int[]{-7, 7}) {
            for (int z : new int[]{-4, 3}) {
                b.fill(x, 0, z, x, 3, z, Blocks.COPPER_BLOCK.defaultBlockState());
            }
        }
        b.fill(-7, 4, -4, 7, 4, 3, Blocks.CUT_COPPER_SLAB.defaultBlockState());
        List<Named> pool = new ArrayList<>(ALL);
        for (int i = 0; i < 3; i++) {
            Named pick = pool.remove(b.random.nextInt(pool.size()));
            place(b, pick, -5 + i * 5, 0);
        }
        b.chest(6, 0, -3, Direction.WEST, "realm_workshop");
        b.set(-6, 3, -3, Realm.RESONANT_CRYSTAL.get());
        b.set(6, 3, 2, Realm.RESONANT_CRYSTAL.get());
    }

    static Block lamp() {
        return ModRegistry.INSTANT_LAMP.get();
    }

    static BlockState channel(Block block, int channel) {
        return block.defaultBlockState().setValue(Wireless.CHANNEL, channel);
    }
}
