package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.RedstonePlus;
import de.eron.redstoneplus.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Redstone Realm: a dimension with six biomes, each with its own mechanical creature, a redstone trap
 * (trigger and response) and the tool that counters it. Everything of the realm is registered here and
 * shown in its own creative tab.
 */
public final class Realm {
    private static final String MODID = RedstonePlus.MODID;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    private static final List<RegistryObject<? extends Item>> TAB_ORDER = new ArrayList<>();

    public static final ResourceKey<Level> REALM = ResourceKey.create(Registries.DIMENSION, id("redstone_realm"));

    // ---------- getting there ----------
    public static final RegistryObject<Block> REALM_GATE = block("realm_gate", RealmGateBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.LODESTONE).lightLevel(s -> s.getValue(RealmGateBlock.LIT) ? 12 : 3));

    // ---------- terrain ----------
    public static final RegistryObject<Block> KARST_LIMESTONE = block("karst_limestone", Block::new, () -> rock(MapColor.SAND));
    public static final RegistryObject<Block> LICHEN_KARST = block("lichen_karst", Block::new, () -> rock(MapColor.COLOR_ORANGE));
    public static final RegistryObject<Block> KARST_BRICKS = block("karst_bricks", Block::new, () -> rock(MapColor.SAND));
    public static final RegistryObject<Block> RUSTED_SOIL = block("rusted_soil", Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).strength(0.6F).sound(SoundType.GRAVEL));
    public static final RegistryObject<Block> SLAG = block("slag", Block::new, () -> rock(MapColor.COLOR_BLACK).sound(SoundType.TUFF));
    public static final RegistryObject<Block> RESONANT_CRYSTAL = block("resonant_crystal", AmethystBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.5F).sound(SoundType.AMETHYST)
                    .lightLevel(s -> 12).requiresCorrectToolForDrops());
    public static final RegistryObject<Block> SULFUR_CRUST = block("sulfur_crust", Block::new,
            () -> rock(MapColor.COLOR_YELLOW).sound(SoundType.NETHERRACK));
    public static final RegistryObject<Block> BRIAR_THORNS = block("briar_thorns", RealmBlocks.BriarThorns::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().noOcclusion()
                    .sound(SoundType.SWEET_BERRY_BUSH).pushReaction(PushReaction.DESTROY));

    // ---------- triggers ----------
    public static final RegistryObject<Block> TRIPPER_RAIL = block("tripper_rail", RealmBlocks.TripperRail::new,
            () -> BlockBehaviour.Properties.of().noCollission().strength(0.7F).sound(SoundType.METAL));

    // ---------- traps (responses) ----------
    public static final RegistryObject<Block> CRUSHER = block("crusher", TrapBlock.Crusher::new, () -> trap(MapColor.SAND));
    public static final RegistryObject<Block> HAZARD_SWITCH = block("hazard_switch", TrapBlock.HazardSwitch::new, () -> trap(MapColor.TERRACOTTA_RED));
    public static final RegistryObject<Block> LOCKDOWN_GATE = block("lockdown_gate", TrapBlock.LockdownGate::new,
            () -> trap(MapColor.METAL).noOcclusion().isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final RegistryObject<Block> FLOODGATE = block("floodgate", TrapBlock.Floodgate::new, () -> trap(MapColor.WARPED_STEM));
    public static final RegistryObject<Block> KILN_TURRET = block("kiln_turret", TrapBlock.KilnTurret::new,
            () -> trap(MapColor.COLOR_BLACK).lightLevel(s -> s.getValue(TrapBlock.FIRING) ? 13 : 0));
    public static final RegistryObject<Block> VOLLEY_LAUNCHER = block("volley_launcher", TrapBlock.VolleyLauncher::new, () -> trap(MapColor.PLANT));

    // ---------- counters ----------
    public static final RegistryObject<Item> REALM_COG = item("realm_cog", ModItems.DescribedItem::new, Item.Properties::new);
    public static final RegistryObject<Item> PISTON_BRACE = item("piston_brace", RealmItems.PistonBrace::new, () -> new Item.Properties().stacksTo(16));
    public static final RegistryObject<Item> PULSE_INJECTOR = item("pulse_injector", RealmItems.PulseInjector::new,
            () -> new Item.Properties().durability(64).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Block> DECOY_BEACON = block("decoy_beacon", RealmBlocks.DecoyBeacon::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(2.0F).sound(SoundType.COPPER).lightLevel(s -> 10));
    public static final RegistryObject<Item> REPEATER_WRENCH = item("repeater_wrench", RealmItems.RepeaterWrench::new, () -> new Item.Properties().stacksTo(1));
    public static final RegistryObject<Item> SIGNAL_JAMMER = item("signal_jammer", RealmItems.SignalJammer::new,
            () -> new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> INSULATED_CUTTERS = item("insulated_cutters", RealmItems.InsulatedCutters::new,
            () -> new Item.Properties().durability(238).component(net.minecraft.core.component.DataComponents.TOOL,
                    net.minecraft.world.item.ShearsItem.createToolProperties()));

    public static final RegistryObject<BlockEntityType<RealmBlocks.DecoyBeaconEntity>> DECOY_BEACON_BE = BLOCK_ENTITIES.register("decoy_beacon",
            () -> BlockEntityType.Builder.of(RealmBlocks.DecoyBeaconEntity::new, DECOY_BEACON.get()).build(null));

    // ---------- the six constructs ----------
    public static final RegistryObject<EntityType<Constructs.KarstColossus>> KARST_COLOSSUS = mob("karst_colossus",
            () -> EntityType.Builder.of(Constructs.KarstColossus::new, MobCategory.MONSTER).sized(1.9F, 3.1F).eyeHeight(2.7F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<Constructs.SwitchbackCrawler>> SWITCHBACK_CRAWLER = mob("switchback_crawler",
            () -> EntityType.Builder.of(Constructs.SwitchbackCrawler::new, MobCategory.MONSTER).sized(1.3F, 0.9F).eyeHeight(0.6F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<Constructs.BellStalker>> BELL_STALKER = mob("bell_stalker",
            () -> EntityType.Builder.of(Constructs.BellStalker::new, MobCategory.MONSTER).sized(1.1F, 3.6F).eyeHeight(3.2F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<Constructs.SluiceChainjaw>> SLUICE_CHAINJAW = mob("sluice_chainjaw",
            () -> EntityType.Builder.of(Constructs.SluiceChainjaw::new, MobCategory.MONSTER).sized(1.5F, 1.1F).eyeHeight(0.8F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<Constructs.KilnBrute>> KILN_BRUTE = mob("kiln_brute",
            () -> EntityType.Builder.of(Constructs.KilnBrute::new, MobCategory.MONSTER).fireImmune().sized(1.8F, 2.8F).eyeHeight(2.2F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<Constructs.SpoolWeaver>> SPOOL_WEAVER = mob("spool_weaver",
            () -> EntityType.Builder.of(Constructs.SpoolWeaver::new, MobCategory.MONSTER).sized(1.5F, 1.0F).eyeHeight(0.7F).clientTrackingRange(10));

    // ---------- the Machine-Bound (realm versions of the mod's creatures, with their own bodies) ----------
    public static final RegistryObject<EntityType<MachineBound.LeakingCell>> LEAKING_CELL = mob("leaking_cell",
            () -> EntityType.Builder.<MachineBound.LeakingCell>of(MachineBound.LeakingCell::new, MobCategory.MONSTER).sized(0.9F, 2.1F).eyeHeight(1.8F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<MachineBound.DetonatorHusk>> DETONATOR_HUSK = mob("detonator_husk",
            () -> EntityType.Builder.<MachineBound.DetonatorHusk>of(MachineBound.DetonatorHusk::new, MobCategory.MONSTER).sized(0.9F, 1.9F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<MachineBound.TripwireBrood>> TRIPWIRE_BROOD = mob("tripwire_brood",
            () -> EntityType.Builder.<MachineBound.TripwireBrood>of(MachineBound.TripwireBrood::new, MobCategory.MONSTER).sized(1.5F, 1.0F).eyeHeight(0.65F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<MachineBound.Kilnbound>> KILNBOUND = mob("kilnbound",
            () -> EntityType.Builder.<MachineBound.Kilnbound>of(MachineBound.Kilnbound::new, MobCategory.MONSTER).fireImmune().sized(0.8F, 2.4F).eyeHeight(2.1F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<MachineBound.LivingCapacitor>> LIVING_CAPACITOR = mob("living_capacitor",
            () -> EntityType.Builder.<MachineBound.LivingCapacitor>of(MachineBound.LivingCapacitor::new, MobCategory.MONSTER).sized(0.52F, 0.52F).eyeHeight(0.325F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<MachineBound.RelayStrider>> RELAY_STRIDER = mob("relay_strider",
            () -> EntityType.Builder.<MachineBound.RelayStrider>of(MachineBound.RelayStrider::new, MobCategory.MONSTER).sized(0.7F, 3.0F).eyeHeight(2.6F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<MachineBound.BellowsHog>> BELLOWS_HOG = mob("bellows_hog",
            () -> EntityType.Builder.<MachineBound.BellowsHog>of(MachineBound.BellowsHog::new, MobCategory.CREATURE).fireImmune().sized(1.1F, 1.1F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<MachineBound.FleshPress>> FLESH_PRESS = mob("flesh_press",
            () -> EntityType.Builder.<MachineBound.FleshPress>of(MachineBound.FleshPress::new, MobCategory.CREATURE).sized(1.8F, 2.9F).clientTrackingRange(10));

    // ---------- world generation ----------
    public static final RegistryObject<Feature<RealmFeatures.SpireConfig>> SPIRE = FEATURES.register("spire", RealmFeatures.Spire::new);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CRUSHER_PASSAGE = site("crusher_passage", RealmFeatures.Kind.CRUSHER_PASSAGE);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SWITCHYARD_JUNCTION = site("switchyard_junction", RealmFeatures.Kind.SWITCHYARD_JUNCTION);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> RESONANCE_GATEHOUSE = site("resonance_gatehouse", RealmFeatures.Kind.RESONANCE_GATEHOUSE);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SLUICE_BRIDGE = site("sluice_bridge", RealmFeatures.Kind.SLUICE_BRIDGE);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> KILN_BRIDGE = site("kiln_bridge", RealmFeatures.Kind.KILN_BRIDGE);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> BRIAR_AMBUSH = site("briar_ambush", RealmFeatures.Kind.BRIAR_AMBUSH);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CIRCUIT_WORKSHOP = site("circuit_workshop", RealmFeatures.Kind.CIRCUIT_WORKSHOP);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> RAIL_LINE = site("rail_line", RealmFeatures.Kind.RAIL_LINE);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("redstone_realm", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.redstoneplus.redstone_realm"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> REALM_GATE.get().asItem().getDefaultInstance())
            .displayItems((params, output) -> TAB_ORDER.forEach(item -> output.accept(item.get())))
            .build());

    static {
        egg("karst_colossus", KARST_COLOSSUS, 0x8a7a60, 0xff3020);
        egg("switchback_crawler", SWITCHBACK_CRAWLER, 0x8a4a20, 0xffa030);
        egg("bell_stalker", BELL_STALKER, 0xb06a30, 0x40e0ff);
        egg("sluice_chainjaw", SLUICE_CHAINJAW, 0x3a8a70, 0xe02020);
        egg("kiln_brute", KILN_BRUTE, 0x2a2622, 0xff8a20);
        egg("spool_weaver", SPOOL_WEAVER, 0x5a7a60, 0xe8e8e8);
        egg("leaking_cell", LEAKING_CELL, 0x5a6a2a, 0x7dff3a);
        egg("detonator_husk", DETONATOR_HUSK, 0xa05a5a, 0xff2020);
        egg("tripwire_brood", TRIPWIRE_BROOD, 0x5a6a80, 0x9ad8ff);
        egg("kilnbound", KILNBOUND, 0x6a3a1a, 0xff8a20);
        egg("living_capacitor", LIVING_CAPACITOR, 0xc02a4a, 0x8a8a8a);
        egg("relay_strider", RELAY_STRIDER, 0x2a1a3a, 0xc060ff);
        egg("bellows_hog", BELLOWS_HOG, 0xd08a80, 0xff7a20);
        egg("flesh_press", FLESH_PRESS, 0xa06060, 0xe02020);
    }

    private Realm() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static BlockBehaviour.Properties rock(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
                .instrument(NoteBlockInstrument.BASEDRUM);
    }

    private static BlockBehaviour.Properties trap(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.5F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    private static RegistryObject<Block> block(String name, Function<BlockBehaviour.Properties, ? extends Block> factory,
                                                Supplier<BlockBehaviour.Properties> properties) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> factory.apply(properties.get()));
        TAB_ORDER.add(ITEMS.register(name, () -> new ModItems.DescribedBlockItem(block.get(), new Item.Properties())));
        return block;
    }

    private static RegistryObject<Item> item(String name, Function<Item.Properties, ? extends Item> factory, Supplier<Item.Properties> properties) {
        RegistryObject<Item> item = ITEMS.register(name, () -> factory.apply(properties.get()));
        TAB_ORDER.add(item);
        return item;
    }

    private static <T extends Mob> RegistryObject<EntityType<T>> mob(String name, Supplier<EntityType.Builder<T>> builder) {
        return ENTITIES.register(name, () -> builder.get().build(name));
    }

    private static void egg(String name, Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight) {
        item(name + "_spawn_egg", p -> new ForgeSpawnEggItem(type, background, highlight, p), Item.Properties::new);
    }

    private static RegistryObject<Feature<NoneFeatureConfiguration>> site(String name, RealmFeatures.Kind kind) {
        return FEATURES.register(name, () -> new RealmFeatures.Site(kind));
    }

    /** Called once from the mod constructor. */
    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        FEATURES.register(modBus);
        TABS.register(modBus);
        RealmSounds.init(modBus);
        modBus.addListener(Realm::attributes);
        modBus.addListener(Realm::spawnPlacements);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            de.eron.redstoneplus.realm.client.RealmClient.init(modBus);
        }
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(KARST_COLOSSUS.get(), Constructs.KarstColossus.attributes().build());
        event.put(SWITCHBACK_CRAWLER.get(), Constructs.SwitchbackCrawler.attributes().build());
        event.put(BELL_STALKER.get(), Constructs.BellStalker.attributes().build());
        event.put(SLUICE_CHAINJAW.get(), Constructs.SluiceChainjaw.attributes().build());
        event.put(KILN_BRUTE.get(), Constructs.KilnBrute.attributes().build());
        event.put(SPOOL_WEAVER.get(), Constructs.SpoolWeaver.attributes().build());
        event.put(LEAKING_CELL.get(), MachineBound.LeakingCell.attributes().build());
        event.put(DETONATOR_HUSK.get(), MachineBound.DetonatorHusk.attributes().build());
        event.put(TRIPWIRE_BROOD.get(), MachineBound.TripwireBrood.attributes().build());
        event.put(KILNBOUND.get(), MachineBound.Kilnbound.attributes().build());
        event.put(LIVING_CAPACITOR.get(), Monster.createMonsterAttributes().build());
        event.put(RELAY_STRIDER.get(), MachineBound.RelayStrider.attributes().build());
        event.put(BELLOWS_HOG.get(), MachineBound.BellowsHog.attributes().build());
        event.put(FLESH_PRESS.get(), MachineBound.FleshPress.attributes().build());
    }

    private static void spawnPlacements(SpawnPlacementRegisterEvent event) {
        var op = SpawnPlacementRegisterEvent.Operation.REPLACE;
        var ground = SpawnPlacementTypes.ON_GROUND;
        var height = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        // constructs are machines: they do not care about light, only the biome's spawn costs limit them
        event.register(KARST_COLOSSUS.get(), ground, height, Monster::checkAnyLightMonsterSpawnRules, op);
        event.register(SWITCHBACK_CRAWLER.get(), ground, height, Monster::checkAnyLightMonsterSpawnRules, op);
        event.register(BELL_STALKER.get(), ground, height, Monster::checkAnyLightMonsterSpawnRules, op);
        event.register(SLUICE_CHAINJAW.get(), ground, height, Monster::checkAnyLightMonsterSpawnRules, op);
        event.register(KILN_BRUTE.get(), ground, height, Monster::checkAnyLightMonsterSpawnRules, op);
        event.register(SPOOL_WEAVER.get(), ground, height, Monster::checkAnyLightMonsterSpawnRules, op);
        // the Machine-Bound keep the habits of what they were: monsters come out in the dark
        event.register(LEAKING_CELL.get(), ground, height, Monster::checkMonsterSpawnRules, op);
        event.register(DETONATOR_HUSK.get(), ground, height, Monster::checkMonsterSpawnRules, op);
        event.register(TRIPWIRE_BROOD.get(), ground, height, Monster::checkMonsterSpawnRules, op);
        event.register(KILNBOUND.get(), ground, height, Monster::checkMonsterSpawnRules, op);
        event.register(LIVING_CAPACITOR.get(), ground, height, Mob::checkMobSpawnRules, op);
        event.register(RELAY_STRIDER.get(), ground, height, Monster::checkMonsterSpawnRules, op);
        event.register(BELLOWS_HOG.get(), ground, height, Mob::checkMobSpawnRules, op);
        event.register(FLESH_PRESS.get(), ground, height, Mob::checkMobSpawnRules, op);
    }
}
