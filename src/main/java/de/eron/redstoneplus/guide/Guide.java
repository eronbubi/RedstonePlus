package de.eron.redstoneplus.guide;

import de.eron.redstoneplus.RedstonePlus;
import de.eron.redstoneplus.realm.Realm;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Predicate;

/**
 * The RedstonePlus Guide: a book that explains the whole mod. Its pages are built when it is opened, from the
 * items, blocks and creatures that are registered and their descriptions, so new content shows up by itself.
 * The chapter texts are translation keys (guide.redstoneplus.*) written by tools/gen_guide.py.
 */
public final class Guide {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RedstonePlus.MODID);
    public static final RegistryObject<Item> GUIDE = ITEMS.register("guide_book", () -> new GuideItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private static final String GIVEN = "redstoneplus_guide_given";

    /** A chapter: its id (for texts) and which item ids it takes. Items no chapter takes land in "other". */
    public record Chapter(String id, Predicate<String> takes) {
    }

    private static final String[] MATERIALS = {"ruby", "sapphire", "titanium", "cobalt", "mythril", "voidium"};
    private static final String[] GEAR = {"_sword", "_pickaxe", "_axe", "_shovel", "_hoe", "_helmet", "_chestplate", "_leggings", "_boots"};

    public static final List<Chapter> CHAPTERS = List.of(
            new Chapter("core", in("tnter", "freeze_tnter", "activator_net", "entity_teleporter", "remote_detonator", "nuclear_repeater",
                    "arrow_shooter", "uranium_ore", "uranium_shard", "enriched_uranium", "redstone_circuit", "uranium_block")),
            new Chapter("logic", id -> id.endsWith("_gate") || in("amplifier", "t_flip_flop", "rs_latch", "pulse_limiter", "pulse_extender",
                    "delay_block", "randomizer", "counter", "sequencer", "redstone_crossing", "clock", "edge_detector", "analog_inverter",
                    "signal_adder", "signal_subtractor").test(id)),
            new Chapter("sources", in("variable_source", "reinforced_redstone_block", "wireless_transmitter", "wireless_receiver", "redstone_remote")),
            new Chapter("sensors", id -> !id.equals("item_detector") && id.endsWith("_detector") || in("item_detector", "weather_sensor", "night_sensor",
                    "laser_sensor", "light_sensor", "entity_counter").test(id)),
            new Chapter("machines", in("block_breaker", "block_placer", "lightning_caller", "launch_pad", "fan", "item_magnet", "fire_starter",
                    "spike_block", "phantom_block", "conveyor_belt", "tnt_cannon", "uranium_nuke", "fireball_launcher", "crop_harvester",
                    "alarm_siren", "anti_gravity_field", "ice_maker", "water_pump", "firework_launcher", "snowball_turret", "anvil_dropper",
                    "cluster_tnter", "tnt_rain", "block_swapper", "heal_pad", "speed_pad", "smoke_emitter")),
            new Chapter("display", in("instant_lamp", "inverted_lamp", "signal_display", "landmine")),
            new Chapter("tools", in("redstone_wrench", "multimeter", "tnt_activator", "drill", "super_piston", "sticky_super_piston")),
            new Chapter("ores", id -> {
                for (String m : MATERIALS) {
                    if (id.contains(m) && !id.endsWith("_bricks") && !id.endsWith("_plating") && !id.contains("apple")) {
                        return true;
                    }
                }
                if (id.startsWith("uranium_")) {
                    for (String g : GEAR) {
                        if (id.endsWith(g)) {
                            return true;
                        }
                    }
                }
                return false;
            }),
            new Chapter("extras", in("void_stone", "void_stone_bricks", "polished_void_stone", "void_crystal", "ruby_bricks", "sapphire_bricks",
                    "titanium_plating", "cobalt_bricks", "void_pearl", "crystal_silk", "uranium_flesh", "magma_bone", "charged_gunpowder",
                    "ember_pork", "cooked_ember_pork", "ruby_apple", "mythril_apple", "void_crystal_shard")),
            new Chapter("realm", id -> false),
            new Chapter("other", id -> false));

    private Guide() {
    }

    private static Predicate<String> in(String... ids) {
        List<String> list = List.of(ids);
        return list::contains;
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(Guide::creativeTabs);
        MinecraftForge.EVENT_BUS.addListener(Guide::firstJoin);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModRegistry.TAB.getKey() || event.getTabKey() == Realm.TAB.getKey()
                || event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(GUIDE.get());
        }
    }

    /** Every player gets the guide the first time they join a world. */
    private static void firstJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        // kept under PlayerPersisted: Forge drops the rest of a player's data on death, which handed out the book again
        net.minecraft.nbt.CompoundTag root = player.getPersistentData();
        net.minecraft.nbt.CompoundTag persisted = root.getCompound("PlayerPersisted");
        if (!persisted.getBoolean(GIVEN) && !root.getBoolean(GIVEN)) {
            player.getInventory().add(new ItemStack(GUIDE.get()));
        }
        persisted.putBoolean(GIVEN, true);
        root.put("PlayerPersisted", persisted);
    }

    public static class GuideItem extends Item {
        public GuideItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide()) {
                de.eron.redstoneplus.guide.client.GuideScreen.open();
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.redstoneplus.guide_book.desc").withStyle(ChatFormatting.GRAY));
        }
    }
}
