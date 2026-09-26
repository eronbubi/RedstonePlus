package de.eron.redstoneplus.content;

import de.eron.redstoneplus.entity.ModMobs;
import de.eron.redstoneplus.item.ModItems;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/** Decorative blocks, mob drops, food and the eight mobs with their spawn eggs. */
public final class Extras {
    private Extras() {
    }

    // ---------- decorative blocks ----------
    public static RegistryObject<Block> VOID_STONE;
    public static RegistryObject<Block> VOID_STONE_BRICKS;
    public static RegistryObject<Block> POLISHED_VOID_STONE;
    public static RegistryObject<Block> VOID_CRYSTAL;
    public static RegistryObject<Block> RUBY_BRICKS;
    public static RegistryObject<Block> SAPPHIRE_BRICKS;
    public static RegistryObject<Block> TITANIUM_PLATING;
    public static RegistryObject<Block> COBALT_BRICKS;

    // ---------- items ----------
    public static RegistryObject<Item> VOID_PEARL;
    public static RegistryObject<Item> CRYSTAL_SILK;
    public static RegistryObject<Item> URANIUM_FLESH;
    public static RegistryObject<Item> MAGMA_BONE;
    public static RegistryObject<Item> CHARGED_GUNPOWDER;
    public static RegistryObject<Item> EMBER_PORK;
    public static RegistryObject<Item> COOKED_EMBER_PORK;
    public static RegistryObject<Item> RUBY_APPLE;
    public static RegistryObject<Item> MYTHRIL_APPLE;
    public static RegistryObject<Item> VOID_CRYSTAL_SHARD;

    // ---------- mobs ----------
    public static final RegistryObject<EntityType<ModMobs.UraniumZombie>> URANIUM_ZOMBIE = mob("uranium_zombie",
            () -> EntityType.Builder.<ModMobs.UraniumZombie>of(ModMobs.UraniumZombie::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<ModMobs.RedstoneCreeper>> REDSTONE_CREEPER = mob("redstone_creeper",
            () -> EntityType.Builder.<ModMobs.RedstoneCreeper>of(ModMobs.RedstoneCreeper::new, MobCategory.MONSTER).sized(0.6F, 1.7F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<ModMobs.CrystalSpider>> CRYSTAL_SPIDER = mob("crystal_spider",
            () -> EntityType.Builder.<ModMobs.CrystalSpider>of(ModMobs.CrystalSpider::new, MobCategory.MONSTER).sized(1.4F, 0.9F).eyeHeight(0.65F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<ModMobs.MagmaSkeleton>> MAGMA_SKELETON = mob("magma_skeleton",
            () -> EntityType.Builder.<ModMobs.MagmaSkeleton>of(ModMobs.MagmaSkeleton::new, MobCategory.MONSTER).fireImmune().sized(0.6F, 1.99F).eyeHeight(1.74F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<ModMobs.RubySlime>> RUBY_SLIME = mob("ruby_slime",
            () -> EntityType.Builder.<ModMobs.RubySlime>of(ModMobs.RubySlime::new, MobCategory.MONSTER).sized(0.52F, 0.52F).eyeHeight(0.325F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<ModMobs.VoidEnderman>> VOID_ENDERMAN = mob("void_enderman",
            () -> EntityType.Builder.<ModMobs.VoidEnderman>of(ModMobs.VoidEnderman::new, MobCategory.MONSTER).sized(0.6F, 2.9F).eyeHeight(2.55F).clientTrackingRange(8));
    public static final RegistryObject<EntityType<ModMobs.EmberPig>> EMBER_PIG = mob("ember_pig",
            () -> EntityType.Builder.<ModMobs.EmberPig>of(ModMobs.EmberPig::new, MobCategory.CREATURE).fireImmune().sized(0.9F, 0.9F).clientTrackingRange(10));
    public static final RegistryObject<EntityType<ModMobs.RedstoneGolem>> REDSTONE_GOLEM = mob("redstone_golem",
            () -> EntityType.Builder.<ModMobs.RedstoneGolem>of(ModMobs.RedstoneGolem::new, MobCategory.CREATURE).sized(1.4F, 2.7F).clientTrackingRange(10));

    private static <T extends Mob> RegistryObject<EntityType<T>> mob(String name, Supplier<EntityType.Builder<T>> builder) {
        return ModRegistry.ENTITIES.register(name, () -> builder.get().build(name));
    }

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0F, 8.0F).requiresCorrectToolForDrops();
    }

    private static RegistryObject<Item> food(String name, FoodProperties food, Rarity rarity) {
        return ModRegistry.item(name, ModItems.DescribedItem::new, () -> new Item.Properties().food(food).rarity(rarity));
    }

    private static void egg(String name, Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight) {
        ModRegistry.item(name + "_spawn_egg", p -> new ForgeSpawnEggItem(type, background, highlight, p), Item.Properties::new);
    }

    /** Registers everything. Called once from {@link ModRegistry#register}. */
    public static void init(IEventBus modBus) {
        VOID_STONE = ModRegistry.block("void_stone", Block::new, () -> stone(MapColor.COLOR_PURPLE));
        VOID_STONE_BRICKS = ModRegistry.block("void_stone_bricks", Block::new, () -> stone(MapColor.COLOR_PURPLE));
        POLISHED_VOID_STONE = ModRegistry.block("polished_void_stone", Block::new, () -> stone(MapColor.COLOR_PURPLE));
        VOID_CRYSTAL = ModRegistry.block("void_crystal", Block::new, () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA)
                .strength(1.5F).sound(SoundType.AMETHYST).lightLevel(s -> 10).requiresCorrectToolForDrops());
        RUBY_BRICKS = ModRegistry.block("ruby_bricks", Block::new, () -> stone(MapColor.COLOR_RED));
        SAPPHIRE_BRICKS = ModRegistry.block("sapphire_bricks", Block::new, () -> stone(MapColor.COLOR_BLUE));
        TITANIUM_PLATING = ModRegistry.block("titanium_plating", Block::new, () -> stone(MapColor.METAL).sound(SoundType.METAL));
        COBALT_BRICKS = ModRegistry.block("cobalt_bricks", Block::new, () -> stone(MapColor.COLOR_LIGHT_BLUE).sound(SoundType.DEEPSLATE_BRICKS));

        VOID_PEARL = ModRegistry.item("void_pearl", EnderpearlItem::new, () -> new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
        CRYSTAL_SILK = ModRegistry.item("crystal_silk", ModItems.DescribedItem::new, Item.Properties::new);
        URANIUM_FLESH = food("uranium_flesh", new FoodProperties.Builder().nutrition(4).saturationModifier(0.1F)
                .effect(new MobEffectInstance(MobEffects.POISON, 200, 1), 0.8F).build(), Rarity.COMMON);
        MAGMA_BONE = ModRegistry.item("magma_bone", ModItems.DescribedItem::new, Item.Properties::new);
        CHARGED_GUNPOWDER = ModRegistry.item("charged_gunpowder", ModItems.DescribedItem::new, Item.Properties::new);
        EMBER_PORK = food("ember_pork", new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build(), Rarity.COMMON);
        COOKED_EMBER_PORK = food("cooked_ember_pork", new FoodProperties.Builder().nutrition(9).saturationModifier(0.9F)
                .effect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0), 1.0F).build(), Rarity.COMMON);
        RUBY_APPLE = food("ruby_apple", new FoodProperties.Builder().nutrition(4).saturationModifier(1.2F).alwaysEdible()
                .effect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 1), 1.0F)
                .effect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1), 1.0F).build(), Rarity.RARE);
        MYTHRIL_APPLE = food("mythril_apple", new FoodProperties.Builder().nutrition(4).saturationModifier(1.2F).alwaysEdible()
                .effect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3), 1.0F)
                .effect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1200, 1), 1.0F).build(), Rarity.EPIC);
        VOID_CRYSTAL_SHARD = ModRegistry.item("void_crystal_shard", ModItems.DescribedItem::new, Item.Properties::new);

        egg("uranium_zombie", URANIUM_ZOMBIE, 0x2f6b15, 0x7dff3a);
        egg("redstone_creeper", REDSTONE_CREEPER, 0x8a1010, 0xff4a3a);
        egg("crystal_spider", CRYSTAL_SPIDER, 0x1a4a5a, 0x80ffff);
        egg("magma_skeleton", MAGMA_SKELETON, 0x6a2a10, 0xffa040);
        egg("ruby_slime", RUBY_SLIME, 0xb01a2a, 0xff8090);
        egg("void_enderman", VOID_ENDERMAN, 0x2a1040, 0xc070ff);
        egg("ember_pig", EMBER_PIG, 0xd06020, 0xffd060);
        egg("redstone_golem", REDSTONE_GOLEM, 0x9a2a2a, 0xe0e0e0);

        modBus.addListener(Extras::attributes);
        modBus.addListener(Extras::spawnPlacements);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(URANIUM_ZOMBIE.get(), ModMobs.UraniumZombie.attributes().build());
        event.put(REDSTONE_CREEPER.get(), Creeper.createAttributes().build());
        event.put(CRYSTAL_SPIDER.get(), ModMobs.CrystalSpider.attributes().build());
        event.put(MAGMA_SKELETON.get(), Skeleton.createAttributes().build());
        event.put(RUBY_SLIME.get(), Monster.createMonsterAttributes().build());
        event.put(VOID_ENDERMAN.get(), ModMobs.VoidEnderman.attributes().build());
        event.put(EMBER_PIG.get(), Pig.createAttributes().build());
        event.put(REDSTONE_GOLEM.get(), ModMobs.RedstoneGolem.attributes().build());
    }

    private static void spawnPlacements(SpawnPlacementRegisterEvent event) {
        var op = SpawnPlacementRegisterEvent.Operation.REPLACE;
        var ground = SpawnPlacementTypes.ON_GROUND;
        var height = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        event.register(URANIUM_ZOMBIE.get(), ground, height, Monster::checkMonsterSpawnRules, op);
        event.register(REDSTONE_CREEPER.get(), ground, height, Monster::checkMonsterSpawnRules, op);
        event.register(CRYSTAL_SPIDER.get(), ground, height, Monster::checkMonsterSpawnRules, op);
        event.register(MAGMA_SKELETON.get(), ground, height, Monster::checkMonsterSpawnRules, op);
        event.register(RUBY_SLIME.get(), ground, height, Mob::checkMobSpawnRules, op);
        event.register(VOID_ENDERMAN.get(), ground, height, Monster::checkMonsterSpawnRules, op);
        event.register(EMBER_PIG.get(), ground, height, Animal::checkAnimalSpawnRules, op);
        event.register(REDSTONE_GOLEM.get(), ground, height, Mob::checkMobSpawnRules, op);
    }
}
