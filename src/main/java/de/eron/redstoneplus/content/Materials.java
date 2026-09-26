package de.eron.redstoneplus.content;

import de.eron.redstoneplus.RedstonePlus;
import de.eron.redstoneplus.item.ModItems;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every ore material of the mod. Each one gets its ore blocks, raw item / gem, storage blocks, the five tools
 * and a full armor set, all registered from the numbers below.
 */
public enum Materials {
    //          kind         uses  speed dmg  ench  incorrect-for tag                    armor b,l,c,h     tough kb    dur  colour
    RUBY(Kind.GEM, 1200, 8.5F, 3.5F, 15, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, new int[]{3, 6, 8, 3}, 1.5F, 0.0F, 30, MapColor.COLOR_RED),
    SAPPHIRE(Kind.GEM, 1400, 8.0F, 3.0F, 18, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, new int[]{3, 6, 8, 3}, 2.0F, 0.0F, 32, MapColor.COLOR_BLUE),
    TITANIUM(Kind.METAL, 1800, 7.5F, 3.0F, 12, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, new int[]{3, 6, 8, 3}, 2.5F, 0.05F, 34, MapColor.METAL),
    COBALT(Kind.METAL, 1000, 10.0F, 2.5F, 20, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, new int[]{2, 5, 7, 2}, 1.0F, 0.0F, 26, MapColor.COLOR_LIGHT_BLUE),
    MYTHRIL(Kind.METAL, 2400, 9.0F, 4.0F, 22, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, new int[]{3, 6, 8, 3}, 3.0F, 0.1F, 38, MapColor.COLOR_CYAN),
    VOIDIUM(Kind.END_METAL, 2800, 10.0F, 4.5F, 20, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, new int[]{3, 7, 9, 3}, 3.5F, 0.12F, 40, MapColor.COLOR_PURPLE),
    /** Uranium already has its ore, shard and block; it only gets tools and armor, repaired with Enriched Uranium. */
    URANIUM(Kind.TOOLS_ONLY, 2000, 9.5F, 5.0F, 16, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, new int[]{3, 7, 8, 3}, 3.0F, 0.1F, 36, MapColor.COLOR_LIGHT_GREEN);

    public enum Kind { GEM, METAL, END_METAL, TOOLS_ONLY }

    public final Kind kind;
    private final int uses;
    private final float speed;
    private final float damage;
    private final int enchantment;
    private final TagKey<Block> incorrect;
    private final int[] armor;
    private final float toughness;
    private final float knockback;
    private final int durability;
    private final MapColor color;

    public RegistryObject<Block> ore;
    public RegistryObject<Block> deepslateOre;
    public RegistryObject<Block> block;
    public RegistryObject<Block> rawBlock;
    public RegistryObject<Item> raw;
    /** The ingot, or the gem itself for gem materials. */
    public RegistryObject<Item> main;
    public final Map<String, RegistryObject<Item>> gear = new java.util.LinkedHashMap<>();

    Materials(Kind kind, int uses, float speed, float damage, int enchantment, TagKey<Block> incorrect, int[] armor,
              float toughness, float knockback, int durability, MapColor color) {
        this.kind = kind;
        this.uses = uses;
        this.speed = speed;
        this.damage = damage;
        this.enchantment = enchantment;
        this.incorrect = incorrect;
        this.armor = armor;
        this.toughness = toughness;
        this.knockback = knockback;
        this.durability = durability;
        this.color = color;
    }

    public String id() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    private Item repairItem() {
        return this == URANIUM ? ModRegistry.ENRICHED_URANIUM.get() : this.main.get();
    }

    private Tier tier() {
        Materials self = this;
        return new Tier() {
            @Override
            public int getUses() {
                return self.uses;
            }

            @Override
            public float getSpeed() {
                return self.speed;
            }

            @Override
            public float getAttackDamageBonus() {
                return self.damage;
            }

            @Override
            public TagKey<Block> getIncorrectBlocksForDrops() {
                return self.incorrect;
            }

            @Override
            public int getEnchantmentValue() {
                return self.enchantment;
            }

            @Override
            public Ingredient getRepairIngredient() {
                return Ingredient.of(self.repairItem());
            }
        };
    }

    private Holder<ArmorMaterial> armorMaterial() {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.BOOTS, this.armor[0]);
        defense.put(ArmorItem.Type.LEGGINGS, this.armor[1]);
        defense.put(ArmorItem.Type.CHESTPLATE, this.armor[2]);
        defense.put(ArmorItem.Type.HELMET, this.armor[3]);
        defense.put(ArmorItem.Type.BODY, this.armor[2]);
        Supplier<Ingredient> repair = () -> Ingredient.of(this.repairItem());
        ArmorMaterial material = new ArmorMaterial(defense, this.enchantment, SoundEvents.ARMOR_EQUIP_DIAMOND, repair,
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(RedstonePlus.MODID, this.id()))),
                this.toughness, this.knockback);
        return Holder.direct(material);
    }

    private BlockBehaviour.Properties oreProps(boolean deepslate) {
        return BlockBehaviour.Properties.of().mapColor(deepslate ? MapColor.DEEPSLATE : MapColor.STONE)
                .strength(deepslate ? 4.5F : 3.0F, 3.0F).requiresCorrectToolForDrops()
                .sound(deepslate ? SoundType.DEEPSLATE : SoundType.STONE);
    }

    private void registerAll() {
        String id = this.id();
        UniformInt xp = this.kind == Kind.GEM ? UniformInt.of(3, 7) : UniformInt.of(0, 2);
        switch (this.kind) {
            case GEM, METAL -> {
                this.ore = ModRegistry.block(id + "_ore", p -> new DropExperienceBlock(xp, p), () -> this.oreProps(false));
                this.deepslateOre = ModRegistry.block("deepslate_" + id + "_ore", p -> new DropExperienceBlock(xp, p), () -> this.oreProps(true));
            }
            case END_METAL -> this.ore = ModRegistry.block(id + "_ore", p -> new DropExperienceBlock(xp, p),
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(4.0F, 9.0F).requiresCorrectToolForDrops());
            case TOOLS_ONLY -> {
            }
        }
        if (this.kind == Kind.GEM) {
            this.main = ModRegistry.item(id, ModItems.DescribedItem::new, Item.Properties::new);
        } else if (this.kind != Kind.TOOLS_ONLY) {
            this.raw = ModRegistry.item("raw_" + id, ModItems.DescribedItem::new, Item.Properties::new);
            this.main = ModRegistry.item(id + "_ingot", ModItems.DescribedItem::new, Item.Properties::new);
        }
        if (this.kind != Kind.TOOLS_ONLY) {
            this.block = ModRegistry.block(id + "_block", Block::new,
                    () -> BlockBehaviour.Properties.of().mapColor(this.color).strength(5.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL));
            if (this.kind != Kind.GEM) {
                this.rawBlock = ModRegistry.block("raw_" + id + "_block", Block::new,
                        () -> BlockBehaviour.Properties.of().mapColor(this.color).strength(5.0F, 6.0F).requiresCorrectToolForDrops());
            }
        }

        Tier tier = this.tier();
        this.gear.put("sword", ModRegistry.item(id + "_sword",
                p -> new SwordItem(tier, p.attributes(SwordItem.createAttributes(tier, 3, -2.4F))), Item.Properties::new));
        this.gear.put("pickaxe", ModRegistry.item(id + "_pickaxe",
                p -> new PickaxeItem(tier, p.attributes(DiggerItem.createAttributes(tier, 1.0F, -2.8F))), Item.Properties::new));
        this.gear.put("axe", ModRegistry.item(id + "_axe",
                p -> new AxeItem(tier, p.attributes(DiggerItem.createAttributes(tier, 5.0F, -3.0F))), Item.Properties::new));
        this.gear.put("shovel", ModRegistry.item(id + "_shovel",
                p -> new ShovelItem(tier, p.attributes(DiggerItem.createAttributes(tier, 1.5F, -3.0F))), Item.Properties::new));
        this.gear.put("hoe", ModRegistry.item(id + "_hoe",
                p -> new HoeItem(tier, p.attributes(DiggerItem.createAttributes(tier, -3.0F, 0.0F))), Item.Properties::new));

        Holder<ArmorMaterial> armorMaterial = this.armorMaterial();
        for (ArmorItem.Type type : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS}) {
            this.gear.put(type.getName(), ModRegistry.item(id + "_" + type.getName(),
                    p -> new ArmorItem(armorMaterial, type, p.durability(type.getDurability(this.durability))), Item.Properties::new));
        }
    }

    /** Registers every material. Called once from {@link ModRegistry#register}. */
    public static void init() {
        for (Materials m : values()) {
            m.registerAll();
        }
    }
}
