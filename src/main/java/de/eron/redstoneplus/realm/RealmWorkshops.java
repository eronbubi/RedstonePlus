package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.MachineBlock;
import de.eron.redstoneplus.block.PoweredBlock;
import de.eron.redstoneplus.block.gate.GateBlock;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;

/**
 * The Wirewrights' workshops: one building for each family of RedstonePlus parts, each a machine that still runs.
 * They follow docs/redstone-components.md; every one is tested in all four rotations. Local frame: x across, z into
 * the building (the door is at the low z side), y up from the floor.
 */
final class RealmWorkshops {
    private RealmWorkshops() {
    }

    private static BlockState s(Block block) {
        return block.defaultBlockState();
    }

    private static BlockState gate(RealmFeatures.Build b, net.minecraftforge.registries.RegistryObject<Block> gate, Direction out) {
        return b.gate(gate.get(), out);
    }

    private static BlockState machine(Block block, Direction facing) {
        return block.defaultBlockState().setValue(MachineBlock.FACING, facing);
    }

    /** Four walls, a flat roof with a rim, windows and a door in the middle of the low z wall. */
    static void shell(RealmFeatures.Build b, RealmStructures.Palette p, int x1, int z1, int x2, int z2, int height) {
        b.foundation(x1, z1, x2, z2, s(Realm.WIREWRIGHT_TILES.get()), p.bricks(), height + 2);
        for (int y = 0; y < height; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    if (!edge) {
                        continue;
                    }
                    boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                    int along = (x == x1 || x == x2) ? z : x;
                    boolean window = !corner && y >= 2 && y <= 3 && Math.floorMod(along, 3) == 1;
                    b.set(x, y, z, corner ? p.metal() : window ? s(Blocks.RED_STAINED_GLASS) : p.bricks());
                }
            }
        }
        b.fill(x1, height, z1, x2, height, z2, p.bricks());
        for (int x = x1; x <= x2; x++) {
            b.set(x, height + 1, z1, s(Realm.CHISELED_REALMSTONE_BRICKS.get()));
            b.set(x, height + 1, z2, s(Realm.CHISELED_REALMSTONE_BRICKS.get()));
        }
        int door = (x1 + x2) / 2;
        b.fill(door, 0, z1, door, 1, z1, s(Blocks.AIR));
    }

    // ================================================================================================ logic hall
    /**
     * A row of gates on a console against the back wall. Each gate has two clocks of different speed at its sides
     * (A on its left at 6 redstone ticks, B on its right at 9), so its lamp blinks out the gate's truth table. The
     * AND gate's output also feeds a Counter whose count shows on a Signal Display.
     */
    static void logicHall(RealmFeatures.Build b) {
        RealmStructures.Palette p = RealmStructures.Palette.of(b);
        shell(b, p, -7, -4, 7, 4, 6);
        var gates = java.util.List.of(ModRegistry.AND_GATE, ModRegistry.OR_GATE, ModRegistry.XOR_GATE, ModRegistry.NAND_GATE);
        b.fill(-6, 0, 1, 6, 0, 3, p.bricks()); // the console
        for (int i = 0; i < gates.size(); i++) {
            int cx = -5 + i * 3;
            // facing +z: left is +x (east), right is -x (west)
            b.set(cx + 1, 1, 1, gate(b, ModRegistry.CLOCK, Direction.WEST).setValue(GateBlock.DELAY, 6));
            b.set(cx, 1, 1, gate(b, gates.get(i), Direction.SOUTH));
            b.set(cx - 1, 1, 1, gate(b, ModRegistry.CLOCK, Direction.EAST).setValue(GateBlock.DELAY, 9));
            if (i == 0) {
                // AND: count its rising edges and show the count
                b.set(cx, 1, 2, gate(b, ModRegistry.COUNTER, Direction.SOUTH));
                b.set(cx, 1, 3, s(ModRegistry.SIGNAL_DISPLAY.get()));
            } else {
                // the gate strongly powers the block in front, which lights the lamps on and beside it
                b.set(cx, 1, 2, s(Realm.CHISELED_REALMSTONE_BRICKS.get()));
                b.set(cx, 2, 2, s(ModRegistry.INSTANT_LAMP.get()));
                b.set(cx, 1, 3, s(ModRegistry.INSTANT_LAMP.get()));
            }
        }
        b.set(-6, 0, -3, s(Blocks.REDSTONE_BLOCK));
        b.set(-6, 1, -3, s(ModRegistry.INSTANT_LAMP.get()).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
    }

    // ================================================================================================ observatory
    /**
     * A tower full of sensors. Its door is two Phantom Blocks between Player Detectors: it opens as you come near.
     * Inside, a Mob Detector sounds an Alarm Siren, sends up smoke and turns the inverted lamps dark while monsters
     * are about; an Entity Counter shows how many creatures are near. On the roof a Night Sensor lights a ring of
     * lamps after dark, and the Light and Weather Sensors show their readings.
     */
    static void observatory(RealmFeatures.Build b) {
        RealmStructures.Palette p = RealmStructures.Palette.of(b);
        shell(b, p, -3, -3, 3, 3, 10);
        // the door: phantom blocks with a player detector on each side
        b.set(0, 0, -3, s(ModRegistry.PHANTOM_BLOCK.get()));
        b.set(0, 1, -3, s(ModRegistry.PHANTOM_BLOCK.get()));
        b.set(-1, 0, -3, s(ModRegistry.PLAYER_DETECTOR.get()));
        b.set(-1, 1, -3, s(ModRegistry.PLAYER_DETECTOR.get()));
        b.set(1, 0, -3, s(ModRegistry.PLAYER_DETECTOR.get()));
        b.set(1, 1, -3, s(ModRegistry.PLAYER_DETECTOR.get()));
        // the alarm: mob detector in the middle of the back wall
        b.set(0, 1, 2, s(ModRegistry.MOB_DETECTOR.get()));
        b.set(-1, 1, 2, s(ModRegistry.ALARM_SIREN.get()));
        b.set(1, 1, 2, s(ModRegistry.SMOKE_EMITTER.get()));
        b.set(0, 2, 2, s(ModRegistry.INVERTED_LAMP.get()));
        b.set(0, 0, 2, s(ModRegistry.INVERTED_LAMP.get()));
        // the census: entity counter and its display
        b.set(-2, 1, 0, s(ModRegistry.ENTITY_COUNTER.get()));
        b.set(-2, 2, 0, s(ModRegistry.SIGNAL_DISPLAY.get()));
        // a ladder to the roof
        for (int y = 0; y < 10; y++) {
            b.set(2, y, 2, s(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.WEST));
        }
        b.set(2, 10, 2, s(Blocks.AIR));
        // the roof: night sensor ringed by lamps, light and weather sensors with their displays
        b.set(0, 11, 0, s(ModRegistry.NIGHT_SENSOR.get()));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            b.set(d.getStepX(), 11, d.getStepZ(), s(ModRegistry.INSTANT_LAMP.get()));
        }
        b.set(-2, 11, -2, s(ModRegistry.LIGHT_SENSOR.get()));
        b.set(-2, 11, -1, s(ModRegistry.SIGNAL_DISPLAY.get()));
        b.set(2, 11, -2, s(ModRegistry.WEATHER_SENSOR.get()));
        b.set(2, 11, -1, s(ModRegistry.SIGNAL_DISPLAY.get()));
    }

    // ================================================================================================ farm
    /**
     * A walled field of wheat. A Clock wakes a Crop Harvester every few seconds; it cuts and replants the ripe wheat,
     * and an Item Magnet over a chest pulls the harvest in.
     */
    static void farm(RealmFeatures.Build b) {
        RealmStructures.Palette p = RealmStructures.Palette.of(b);
        b.foundation(-4, -6, 4, 4, p.bricks(), p.bricks(), 4);
        for (int x = -4; x <= 4; x++) {
            for (int z = -6; z <= 4; z++) {
                boolean wall = Math.abs(x) == 4 || z == -6 || z == 4;
                if (wall) {
                    b.set(x, 0, z, p.bricks());
                }
            }
        }
        // the field: 5 x 5 around the spot three blocks in front of the harvester, water in the middle
        for (int x = -2; x <= 2; x++) {
            for (int z = -3; z <= 1; z++) {
                if (x == 0 && z == -1) {
                    b.set(x, -1, z, s(Blocks.WATER));
                    continue;
                }
                b.set(x, -1, z, s(Blocks.FARMLAND).setValue(FarmBlock.MOISTURE, 7));
                b.set(x, 0, z, s(Blocks.WHEAT).setValue(CropBlock.AGE, b.random.nextInt(8)));
            }
        }
        // clock -> harvester (facing into the field)
        b.set(0, 0, -5, gate(b, ModRegistry.CLOCK, Direction.SOUTH).setValue(GateBlock.DELAY, 20));
        b.set(0, 0, -4, machine(ModRegistry.CROP_HARVESTER.get(), Direction.SOUTH));
        // the magnet over a chest, powered for good by a redstone block
        b.chest(3, 0, -5, Direction.NORTH, "realm_relic");
        b.set(3, 2, -5, s(Blocks.REDSTONE_BLOCK)); // the source first: a live world checks power the moment the magnet is placed
        b.set(3, 1, -5, s(ModRegistry.ITEM_MAGNET.get()).setValue(PoweredBlock.POWERED, true));
        b.set(-3, 0, -5, s(Blocks.COMPOSTER));
    }

    // ================================================================================================ clock tower
    /**
     * A tower whose roof counts in binary: Clock -> T Flip-Flop -> block -> T Flip-Flop -> block -> T Flip-Flop -> block.
     * Each powered block lights the lamp on it; the last carries a bell that rings every sixteen seconds.
     */
    static void clockTower(RealmFeatures.Build b) {
        RealmStructures.Palette p = RealmStructures.Palette.of(b);
        shell(b, p, -2, -2, 2, 2, 12);
        for (int y = 0; y < 12; y++) {
            b.set(1, y, 1, s(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.NORTH));
        }
        b.set(1, 12, 1, s(Blocks.AIR));
        // the counting line runs along x on the roof
        int y = 13;
        b.set(-3, y, -1, gate(b, ModRegistry.CLOCK, Direction.EAST).setValue(GateBlock.DELAY, 10));
        b.set(-2, y, -1, gate(b, ModRegistry.T_FLIP_FLOP, Direction.EAST));
        b.set(-1, y, -1, s(Realm.CHISELED_REALMSTONE_BRICKS.get()));
        b.set(0, y, -1, gate(b, ModRegistry.T_FLIP_FLOP, Direction.EAST));
        b.set(1, y, -1, s(Realm.CHISELED_REALMSTONE_BRICKS.get()));
        b.set(2, y, -1, gate(b, ModRegistry.T_FLIP_FLOP, Direction.EAST));
        b.set(3, y, -1, s(Realm.CHISELED_REALMSTONE_BRICKS.get()));
        b.set(-1, y + 1, -1, s(ModRegistry.INSTANT_LAMP.get()));
        b.set(1, y + 1, -1, s(ModRegistry.INSTANT_LAMP.get()));
        b.set(3, y + 1, -1, s(Blocks.BELL).setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.NORTH));
        b.set(3, y, 0, s(ModRegistry.INSTANT_LAMP.get()));
        b.set(-3, y - 1, -1, p.bricks());
        b.set(3, y - 1, -1, p.bricks());
        b.set(3, y - 1, 0, p.bricks());
    }

    // ================================================================================================ salute battery
    /**
     * Three Firework Launchers round a Sequencer. A Player Detector feeds a NOT gate into the back of a Clock, so the
     * clock is paused until a visitor comes near; then the sequencer walks the launchers and they fire in turn.
     */
    static void saluteBattery(RealmFeatures.Build b) {
        RealmStructures.Palette p = RealmStructures.Palette.of(b);
        b.foundation(-3, -3, 3, 4, p.bricks(), p.bricks(), 6);
        for (int x = -3; x <= 3; x++) {
            b.set(x, 0, -3, p.bricks());
            b.set(x, 0, 4, p.bricks());
        }
        b.set(0, 0, -2, s(ModRegistry.PLAYER_DETECTOR.get()));
        b.set(0, 0, -1, gate(b, ModRegistry.NOT_GATE, Direction.SOUTH));
        b.set(0, 0, 0, gate(b, ModRegistry.CLOCK, Direction.SOUTH).setValue(GateBlock.DELAY, 8));
        b.set(0, 0, 1, gate(b, ModRegistry.SEQUENCER, Direction.SOUTH));
        // facing +z: left is +x, front is +z, right is -x
        b.set(1, 0, 1, machine(ModRegistry.FIREWORK_LAUNCHER.get(), Direction.UP));
        b.set(0, 0, 2, machine(ModRegistry.FIREWORK_LAUNCHER.get(), Direction.UP));
        b.set(-1, 0, 1, machine(ModRegistry.FIREWORK_LAUNCHER.get(), Direction.UP));
        for (int x : new int[]{-3, 3}) {
            for (int yy = 1; yy <= 4; yy++) {
                b.set(x, yy, 0, p.metal());
            }
            b.set(x, 5, 0, s(Blocks.LIGHTNING_ROD));
        }
    }

    // ================================================================================================ lockhouse
    /**
     * A combination lock. Three levers sit on blocks: the first two feed an XOR gate, whose output block and the third
     * lever's block feed an AND gate. Only (first XOR second) AND third powers the block under the lamp and turns the
     * Phantom Block below it passable: the way down into the vault.
     */
    static void lockhouse(RealmFeatures.Build b) {
        RealmStructures.Palette p = RealmStructures.Palette.of(b);
        shell(b, p, -3, -3, 4, 4, 5);
        BlockState block = s(Realm.CHISELED_REALMSTONE_BRICKS.get());
        // levers on their blocks
        b.set(-1, 0, 0, block);
        b.set(1, 0, 0, block);
        b.set(2, 0, 1, block);
        b.set(-1, 1, 0, b.lever(Direction.NORTH));
        b.set(1, 1, 0, b.lever(Direction.NORTH));
        b.set(2, 1, 1, b.lever(Direction.NORTH));
        // XOR (facing +z: left = +x = second lever, right = -x = first lever) into a block
        b.set(0, 0, 0, gate(b, ModRegistry.XOR_GATE, Direction.SOUTH));
        b.set(0, 0, 1, block);
        // AND (left = +x = third lever, right = -x = the XOR's block) into the block over the hatch
        b.set(1, 0, 1, gate(b, ModRegistry.AND_GATE, Direction.SOUTH));
        b.set(1, 0, 2, block);
        b.set(1, 0, 3, s(ModRegistry.INSTANT_LAMP.get()));
        b.set(1, -1, 2, s(ModRegistry.PHANTOM_BLOCK.get()));
        // the vault under the floor
        for (int x = -1; x <= 3; x++) {
            for (int z = 0; z <= 4; z++) {
                for (int yy = -6; yy <= -2; yy++) {
                    boolean wall = x == -1 || x == 3 || z == 0 || z == 4 || yy == -6;
                    b.set(x, yy, z, wall ? p.bricks() : s(Blocks.AIR));
                }
            }
        }
        // a ladder right under the hatch, against a pillar
        for (int yy = -5; yy <= -2; yy++) {
            b.set(1, yy, 3, p.bricks());
            b.set(1, yy, 2, s(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.NORTH));
        }
        b.chest(2, -5, 1, Direction.WEST, "realm_relic");
        b.set(0, -6, 1, s(Blocks.REDSTONE_BLOCK));
        b.set(0, -5, 1, s(ModRegistry.INSTANT_LAMP.get()).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
    }
}
