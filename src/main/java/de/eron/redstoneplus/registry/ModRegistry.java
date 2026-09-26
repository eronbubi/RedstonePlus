package de.eron.redstoneplus.registry;

import de.eron.redstoneplus.RedstonePlus;
import de.eron.redstoneplus.block.EntityTeleporterBlock;
import de.eron.redstoneplus.block.FieldBlocks;
import de.eron.redstoneplus.block.InventoryMachineBlock;
import de.eron.redstoneplus.block.Machines;
import de.eron.redstoneplus.block.NewMachines;
import de.eron.redstoneplus.block.Sensors;
import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.block.Wireless;
import de.eron.redstoneplus.block.entity.EntityTeleporterBlockEntity;
import de.eron.redstoneplus.block.entity.InventoryMachineBlockEntity;
import de.eron.redstoneplus.block.gate.Gates;
import de.eron.redstoneplus.block.piston.SuperPistonBlock;
import de.eron.redstoneplus.block.piston.SuperPistonBlockEntity;
import de.eron.redstoneplus.block.piston.SuperPistonParts;
import de.eron.redstoneplus.menu.SuperPistonMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import de.eron.redstoneplus.entity.DrillEntity;
import de.eron.redstoneplus.entity.FrozenTntEntity;
import de.eron.redstoneplus.entity.NukeTntEntity;
import de.eron.redstoneplus.item.ModItems;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ModRegistry {
    private static final String MODID = RedstonePlus.MODID;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Everything in creative tab order. */
    private static final List<RegistryObject<? extends Item>> TAB_ORDER = new ArrayList<>();

    // ---------- data components ----------
    public static final RegistryObject<DataComponentType<GlobalPos>> LINK = COMPONENTS.register("link",
            () -> DataComponentType.<GlobalPos>builder().persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC).build());
    public static final RegistryObject<DataComponentType<Integer>> CHANNEL = COMPONENTS.register("channel",
            () -> DataComponentType.<Integer>builder().persistent(ExtraCodecs.intRange(0, 15)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    // ---------- block property presets ----------
    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
                .instrument(NoteBlockInstrument.BASEDRUM);
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    /** Metal blocks whose model does not fill the whole block. */
    private static BlockBehaviour.Properties shaped() {
        return metal().noOcclusion();
    }

    private static BlockBehaviour.Properties piston() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> !s.getValue(BlockStateProperties.EXTENDED))
                .isViewBlocking((s, l, p) -> !s.getValue(BlockStateProperties.EXTENDED));
    }

    private static BlockBehaviour.Properties gate() {
        return BlockBehaviour.Properties.of().instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY);
    }

    // ---------- the requested core items ----------
    public static final RegistryObject<Block> TNTER = block("tnter", p -> new Machines.Tnter(p, false), ModRegistry::stone);
    public static final RegistryObject<Block> FREEZE_TNTER = block("freeze_tnter", p -> new Machines.Tnter(p, true), ModRegistry::stone);
    public static final RegistryObject<Block> ACTIVATOR_NET = block("activator_net", SimpleBlocks.ActivatorNet::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollission().noOcclusion().strength(0.3F)
                    .sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY));
    public static final RegistryObject<Block> ENTITY_TELEPORTER = block("entity_teleporter", EntityTeleporterBlock::new,
            () -> shaped().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).lightLevel(s -> 7));
    public static final RegistryObject<Item> REMOTE_DETONATOR = item("remote_detonator", ModItems.RemoteDetonator::new,
            () -> new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Block> NUCLEAR_REPEATER = block("nuclear_repeater", Gates.NuclearRepeater::new, ModRegistry::gate);
    public static final RegistryObject<Block> ARROW_SHOOTER = block("arrow_shooter", p -> new InventoryMachineBlock(p, true), ModRegistry::stone);
    public static final RegistryObject<Block> URANIUM_ORE = block("uranium_ore",
            p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(4.0F, 9.0F).requiresCorrectToolForDrops().lightLevel(s -> 4));
    public static final RegistryObject<Item> URANIUM_SHARD = item("uranium_shard", ModItems.DescribedItem::new, Item.Properties::new);

    // ---------- materials ----------
    public static final RegistryObject<Item> ENRICHED_URANIUM = item("enriched_uranium", ModItems.DescribedItem::new,
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> REDSTONE_CIRCUIT = item("redstone_circuit", ModItems.DescribedItem::new, Item.Properties::new);
    public static final RegistryObject<Block> URANIUM_BLOCK = block("uranium_block", SimpleBlocks.SignalSource::new,
            () -> metal().mapColor(MapColor.COLOR_LIGHT_GREEN).lightLevel(s -> 12));

    // ---------- logic gates ----------
    public static final RegistryObject<Block> AND_GATE = logic("and_gate", Gates.Logic.AND);
    public static final RegistryObject<Block> OR_GATE = logic("or_gate", Gates.Logic.OR);
    public static final RegistryObject<Block> XOR_GATE = logic("xor_gate", Gates.Logic.XOR);
    public static final RegistryObject<Block> NOT_GATE = logic("not_gate", Gates.Logic.NOT);
    public static final RegistryObject<Block> NAND_GATE = logic("nand_gate", Gates.Logic.NAND);
    public static final RegistryObject<Block> NOR_GATE = logic("nor_gate", Gates.Logic.NOR);
    public static final RegistryObject<Block> XNOR_GATE = logic("xnor_gate", Gates.Logic.XNOR);
    public static final RegistryObject<Block> AMPLIFIER = logic("amplifier", Gates.Logic.AMPLIFIER);
    public static final RegistryObject<Block> T_FLIP_FLOP = block("t_flip_flop", Gates.FlipFlop::new, ModRegistry::gate);
    public static final RegistryObject<Block> RS_LATCH = block("rs_latch", Gates.RsLatch::new, ModRegistry::gate);
    public static final RegistryObject<Block> PULSE_LIMITER = block("pulse_limiter", Gates.PulseLimiter::new, ModRegistry::gate);
    public static final RegistryObject<Block> PULSE_EXTENDER = block("pulse_extender", Gates.PulseExtender::new, ModRegistry::gate);
    public static final RegistryObject<Block> DELAY_BLOCK = block("delay_block", Gates.Delay::new, ModRegistry::gate);
    public static final RegistryObject<Block> RANDOMIZER = block("randomizer", Gates.Randomizer::new, ModRegistry::gate);
    public static final RegistryObject<Block> COUNTER = block("counter", Gates.Counter::new, ModRegistry::gate);
    public static final RegistryObject<Block> SEQUENCER = block("sequencer", Gates.Sequencer::new, ModRegistry::gate);
    public static final RegistryObject<Block> REDSTONE_CROSSING = block("redstone_crossing", Gates.Crossing::new, ModRegistry::gate);
    public static final RegistryObject<Block> CLOCK = block("clock", Gates.Clock::new, ModRegistry::gate);

    // ---------- sources ----------
    public static final RegistryObject<Block> VARIABLE_SOURCE = block("variable_source", SimpleBlocks.VariableSource::new,
            () -> stone().mapColor(MapColor.FIRE));
    public static final RegistryObject<Block> REINFORCED_REDSTONE_BLOCK = block("reinforced_redstone_block", SimpleBlocks.SignalSource::new,
            () -> metal().mapColor(MapColor.FIRE).strength(25.0F, 1200.0F));

    // ---------- wireless ----------
    public static final RegistryObject<Block> WIRELESS_TRANSMITTER = block("wireless_transmitter", Wireless.Transmitter::new, ModRegistry::shaped);
    public static final RegistryObject<Block> WIRELESS_RECEIVER = block("wireless_receiver", Wireless.Receiver::new, ModRegistry::shaped);
    public static final RegistryObject<Item> REDSTONE_REMOTE = item("redstone_remote", ModItems.RedstoneRemote::new,
            () -> new Item.Properties().stacksTo(1));

    // ---------- sensors ----------
    public static final RegistryObject<Block> PLAYER_DETECTOR = block("player_detector", Sensors.PlayerDetector::new, ModRegistry::shaped);
    public static final RegistryObject<Block> MOB_DETECTOR = block("mob_detector", Sensors.MobDetector::new, ModRegistry::shaped);
    public static final RegistryObject<Block> WEATHER_SENSOR = block("weather_sensor", Sensors.WeatherSensor::new, ModRegistry::shaped);
    public static final RegistryObject<Block> NIGHT_SENSOR = block("night_sensor", Sensors.NightSensor::new, ModRegistry::shaped);
    public static final RegistryObject<Block> LASER_SENSOR = block("laser_sensor", Sensors.LaserSensor::new, ModRegistry::metal);

    // ---------- machines ----------
    public static final RegistryObject<Block> BLOCK_BREAKER = block("block_breaker", Machines.BlockBreaker::new, ModRegistry::stone);
    public static final RegistryObject<Block> BLOCK_PLACER = block("block_placer", p -> new InventoryMachineBlock(p, false), ModRegistry::stone);
    public static final RegistryObject<Block> LIGHTNING_CALLER = block("lightning_caller", Machines.LightningCaller::new, ModRegistry::metal);
    public static final RegistryObject<Block> LAUNCH_PAD = block("launch_pad", FieldBlocks.LaunchPad::new, ModRegistry::shaped);
    public static final RegistryObject<Block> FAN = block("fan", Machines.Fan::new, ModRegistry::metal);
    public static final RegistryObject<Block> ITEM_MAGNET = block("item_magnet", FieldBlocks.ItemMagnet::new, ModRegistry::shaped);
    public static final RegistryObject<Block> FIRE_STARTER = block("fire_starter", Machines.FireStarter::new, ModRegistry::stone);
    public static final RegistryObject<Block> SPIKE_BLOCK = block("spike_block", FieldBlocks.Spikes::new, ModRegistry::shaped);
    public static final RegistryObject<Block> PHANTOM_BLOCK = block("phantom_block", SimpleBlocks.Phantom::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.0F).noOcclusion().sound(SoundType.AMETHYST)
                    .isViewBlocking((s, l, p) -> !s.getValue(BlockStateProperties.POWERED))
                    .isSuffocating((s, l, p) -> !s.getValue(BlockStateProperties.POWERED)));
    public static final RegistryObject<Block> CONVEYOR_BELT = block("conveyor_belt", SimpleBlocks.Conveyor::new, ModRegistry::shaped);
    public static final RegistryObject<Block> TNT_CANNON = block("tnt_cannon", Machines.TntCannon::new, ModRegistry::shaped);
    public static final RegistryObject<Block> URANIUM_NUKE = block("uranium_nuke", SimpleBlocks.UraniumNuke::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).instabreak().noOcclusion().sound(SoundType.GRASS).lightLevel(s -> 6));
    public static final RegistryObject<Block> FIREBALL_LAUNCHER = block("fireball_launcher", Machines.FireballLauncher::new, ModRegistry::shaped);
    public static final RegistryObject<Block> CROP_HARVESTER = block("crop_harvester", Machines.CropHarvester::new, ModRegistry::stone);
    public static final RegistryObject<Block> ALARM_SIREN = block("alarm_siren", FieldBlocks.AlarmSiren::new, ModRegistry::shaped);

    // ---------- lamps, display, traps ----------
    public static final RegistryObject<Block> INSTANT_LAMP = block("instant_lamp", p -> new SimpleBlocks.Lamp(p, false),
            () -> BlockBehaviour.Properties.of().strength(0.3F).sound(SoundType.GLASS)
                    .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 15 : 0));
    public static final RegistryObject<Block> INVERTED_LAMP = block("inverted_lamp", p -> new SimpleBlocks.Lamp(p, true),
            () -> BlockBehaviour.Properties.of().strength(0.3F).sound(SoundType.GLASS)
                    .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 15 : 0));
    public static final RegistryObject<Block> SIGNAL_DISPLAY = block("signal_display", SimpleBlocks.SignalDisplay::new,
            () -> metal().lightLevel(s -> s.getValue(BlockStateProperties.POWER) > 0 ? 5 : 0));
    public static final RegistryObject<Block> LANDMINE = block("landmine", SimpleBlocks.Landmine::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY));
    public static final RegistryObject<Block> ANTI_GRAVITY_FIELD = block("anti_gravity_field", FieldBlocks.AntiGravity::new, ModRegistry::shaped);

    // ---------- the 20 newer items ----------
    public static final RegistryObject<Block> EDGE_DETECTOR = block("edge_detector", Gates.EdgeDetector::new, ModRegistry::gate);
    public static final RegistryObject<Block> ANALOG_INVERTER = block("analog_inverter", p -> new Gates.AnalogGate(p, Gates.Analog.INVERT), ModRegistry::gate);
    public static final RegistryObject<Block> SIGNAL_ADDER = block("signal_adder", p -> new Gates.AnalogGate(p, Gates.Analog.ADD), ModRegistry::gate);
    public static final RegistryObject<Block> SIGNAL_SUBTRACTOR = block("signal_subtractor", p -> new Gates.AnalogGate(p, Gates.Analog.SUBTRACT), ModRegistry::gate);
    public static final RegistryObject<Block> ITEM_DETECTOR = block("item_detector", Sensors.ItemDetector::new, ModRegistry::shaped);
    public static final RegistryObject<Block> LIGHT_SENSOR = block("light_sensor", Sensors.LightSensor::new, ModRegistry::shaped);
    public static final RegistryObject<Block> BLOCK_DETECTOR = block("block_detector", Sensors.BlockDetector::new, ModRegistry::metal);
    public static final RegistryObject<Block> ENTITY_COUNTER = block("entity_counter", Sensors.EntityCounter::new, ModRegistry::shaped);
    public static final RegistryObject<Block> ICE_MAKER = block("ice_maker", NewMachines.IceMaker::new, ModRegistry::metal);
    public static final RegistryObject<Block> WATER_PUMP = block("water_pump", NewMachines.WaterPump::new, ModRegistry::metal);
    public static final RegistryObject<Block> FIREWORK_LAUNCHER = block("firework_launcher", NewMachines.FireworkLauncher::new, ModRegistry::shaped);
    public static final RegistryObject<Block> SNOWBALL_TURRET = block("snowball_turret", NewMachines.SnowballTurret::new, ModRegistry::shaped);
    public static final RegistryObject<Block> ANVIL_DROPPER = block("anvil_dropper", NewMachines.AnvilDropper::new, ModRegistry::metal);
    public static final RegistryObject<Block> CLUSTER_TNTER = block("cluster_tnter", NewMachines.ClusterTnter::new, ModRegistry::stone);
    public static final RegistryObject<Block> TNT_RAIN = block("tnt_rain", NewMachines.TntRain::new, ModRegistry::stone);
    public static final RegistryObject<Block> BLOCK_SWAPPER = block("block_swapper", NewMachines.BlockSwapper::new, ModRegistry::metal);
    public static final RegistryObject<Block> HEAL_PAD = block("heal_pad", NewMachines.HealPad::new, ModRegistry::shaped);
    public static final RegistryObject<Block> SPEED_PAD = block("speed_pad", NewMachines.SpeedPad::new, ModRegistry::shaped);
    public static final RegistryObject<Block> SMOKE_EMITTER = block("smoke_emitter", NewMachines.SmokeEmitter::new, ModRegistry::shaped);
    public static final RegistryObject<Item> TNT_ACTIVATOR = item("tnt_activator", ModItems.TntActivator::new, () -> new Item.Properties().stacksTo(1));

    // ---------- Drill ----------
    public static final RegistryObject<Item> DRILL = item("drill", ModItems.DrillItem::new, () -> new Item.Properties().stacksTo(1));
    /** Model-only blocks the drill renderer draws; they have no item and are never placed. */
    public static final RegistryObject<Block> DRILL_BODY_MODEL = BLOCKS.register("drill_body_model",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().noLootTable()));
    public static final RegistryObject<Block> DRILL_BIT_MODEL = BLOCKS.register("drill_bit_model",
            () -> new Block(BlockBehaviour.Properties.of().noOcclusion().noLootTable()));

    // ---------- Super Piston ----------
    public static final RegistryObject<Block> SUPER_PISTON = block("super_piston", p -> new SuperPistonBlock(p, false), ModRegistry::piston);
    public static final RegistryObject<Block> STICKY_SUPER_PISTON = block("sticky_super_piston", p -> new SuperPistonBlock(p, true), ModRegistry::piston);
    public static final RegistryObject<Block> SUPER_PISTON_HEAD = BLOCKS.register("super_piston_head", () -> new SuperPistonParts.Head(
            BlockBehaviour.Properties.of().strength(1.5F).noLootTable().noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> SUPER_PISTON_ARM = BLOCKS.register("super_piston_arm", () -> new SuperPistonParts.Arm(
            BlockBehaviour.Properties.of().strength(1.5F).noLootTable().noOcclusion().pushReaction(PushReaction.BLOCK)));

    // ---------- tools ----------
    public static final RegistryObject<Item> REDSTONE_WRENCH = item("redstone_wrench", ModItems.Wrench::new, () -> new Item.Properties().stacksTo(1));
    public static final RegistryObject<Item> MULTIMETER = item("multimeter", ModItems.Multimeter::new, () -> new Item.Properties().stacksTo(1));

    // ---------- block entities ----------
    public static final RegistryObject<BlockEntityType<EntityTeleporterBlockEntity>> ENTITY_TELEPORTER_BE = BLOCK_ENTITIES.register("entity_teleporter",
            () -> BlockEntityType.Builder.of(EntityTeleporterBlockEntity::new, ENTITY_TELEPORTER.get()).build(null));
    public static final RegistryObject<BlockEntityType<InventoryMachineBlockEntity>> INVENTORY_MACHINE_BE = BLOCK_ENTITIES.register("inventory_machine",
            () -> BlockEntityType.Builder.of(InventoryMachineBlockEntity::new, ARROW_SHOOTER.get(), BLOCK_PLACER.get()).build(null));

    public static final RegistryObject<BlockEntityType<SuperPistonBlockEntity>> SUPER_PISTON_BE = BLOCK_ENTITIES.register("super_piston",
            () -> BlockEntityType.Builder.of(SuperPistonBlockEntity::new, SUPER_PISTON.get(), STICKY_SUPER_PISTON.get()).build(null));

    // ---------- menus ----------
    public static final RegistryObject<MenuType<SuperPistonMenu>> SUPER_PISTON_MENU = MENUS.register("super_piston",
            () -> new MenuType<>(SuperPistonMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // ---------- entities ----------
    public static final RegistryObject<EntityType<FrozenTntEntity>> FROZEN_TNT = ENTITIES.register("frozen_tnt",
            () -> EntityType.Builder.<FrozenTntEntity>of(FrozenTntEntity::new, MobCategory.MISC).fireImmune()
                    .sized(0.98F, 0.98F).eyeHeight(0.15F).clientTrackingRange(10).updateInterval(10).build("frozen_tnt"));
    public static final RegistryObject<EntityType<NukeTntEntity>> NUKE_TNT = ENTITIES.register("nuke_tnt",
            () -> EntityType.Builder.<NukeTntEntity>of(NukeTntEntity::new, MobCategory.MISC).fireImmune()
                    .sized(0.98F, 0.98F).eyeHeight(0.15F).clientTrackingRange(16).updateInterval(10).build("nuke_tnt"));

    public static final RegistryObject<EntityType<DrillEntity>> DRILL_ENTITY = ENTITIES.register("drill",
            () -> EntityType.Builder.<DrillEntity>of(DrillEntity::new, MobCategory.MISC).sized(1.2F, 1.0F)
                    .passengerAttachments(0.55F).clientTrackingRange(10).updateInterval(1).build("drill"));

    // ---------- creative tab ----------
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("redstoneplus", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.redstoneplus"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> TNTER.get().asItem().getDefaultInstance())
            .displayItems((params, output) -> TAB_ORDER.forEach(item -> output.accept(item.get())))
            .build());

    private ModRegistry() {
    }

    private static RegistryObject<Block> logic(String name, Gates.Logic logic) {
        return block(name, p -> new Gates.LogicGate(p, logic), ModRegistry::gate);
    }

    private static RegistryObject<Block> block(String name, Function<BlockBehaviour.Properties, ? extends Block> factory,
                                               Supplier<BlockBehaviour.Properties> properties) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> factory.apply(properties.get()));
        RegistryObject<Item> item = ITEMS.register(name, () -> new ModItems.DescribedBlockItem(block.get(),
                new Item.Properties()));
        TAB_ORDER.add(item);
        return block;
    }

    private static RegistryObject<Item> item(String name, Function<Item.Properties, ? extends Item> factory, Supplier<Item.Properties> properties) {
        RegistryObject<Item> item = ITEMS.register(name, () -> factory.apply(properties.get()));
        TAB_ORDER.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
    }
}
