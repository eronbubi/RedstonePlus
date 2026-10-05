package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.RedstonePlus;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Original sounds of the realm creatures (synthesized by tools/gen_realm_sounds.py).
 * Every creature has ambient, hurt, death, step and ability sounds: entity.redstoneplus.NAME.KIND.
 */
public final class RealmSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RedstonePlus.MODID);

    public record Set(RegistryObject<SoundEvent> ambient, RegistryObject<SoundEvent> hurt, RegistryObject<SoundEvent> death,
                      RegistryObject<SoundEvent> step, RegistryObject<SoundEvent> ability) {
    }

    public static final Set KARST_COLOSSUS = set("karst_colossus");
    public static final Set SWITCHBACK_CRAWLER = set("switchback_crawler");
    public static final Set BELL_STALKER = set("bell_stalker");
    public static final Set SLUICE_CHAINJAW = set("sluice_chainjaw");
    public static final Set KILN_BRUTE = set("kiln_brute");
    public static final Set SPOOL_WEAVER = set("spool_weaver");
    public static final Set LEAKING_CELL = set("leaking_cell");
    public static final Set DETONATOR_HUSK = set("detonator_husk");
    public static final Set TRIPWIRE_BROOD = set("tripwire_brood");
    public static final Set KILNBOUND = set("kilnbound");
    public static final Set LIVING_CAPACITOR = set("living_capacitor");
    public static final Set RELAY_STRIDER = set("relay_strider");
    public static final Set BELLOWS_HOG = set("bellows_hog");
    public static final Set FLESH_PRESS = set("flesh_press");
    public static final Set SPARK_MITE = set("spark_mite");
    public static final Set LAMP_MOTH = set("lamp_moth");
    public static final Set SCRAP_JACKAL = set("scrap_jackal");
    public static final Set TRACKWRIGHT = set("trackwright");
    public static final Set WIREWRAITH = set("wirewraith");
    public static final Set MAW_ENGINE = set("maw_engine");
    public static final Set ECHO_FORCE = set("echo_force");
    public static final Set ECHO_SIGNAL = set("echo_signal");
    public static final Set ECHO_RESONANCE = set("echo_resonance");
    public static final Set ECHO_HEAT = set("echo_heat");
    public static final Set ECHO_FLOW = set("echo_flow");
    public static final Set OVERTOLL = set("the_overtoll");

    public static final RegistryObject<SoundEvent> REALM_TRAVEL = sound("block.realm_gate.travel");
    /** The toll of the Great Bell in the sky. */
    public static final RegistryObject<SoundEvent> GREAT_BELL = sound("ambient.great_bell");
    /** The light cycle: its engine (looped and pitched with speed by the client), and materialising / dissolving. */
    public static final RegistryObject<SoundEvent> CYCLE_ENGINE = sound("entity.light_cycle.engine");
    public static final RegistryObject<SoundEvent> CYCLE_REZ = sound("entity.light_cycle.rez");
    public static final RegistryObject<SoundEvent> CYCLE_DEREZ = sound("entity.light_cycle.derez");
    /** A Trackwright pressing a lightline tile into the ground. */
    public static final RegistryObject<SoundEvent> LIGHTLINE_LAY = sound("block.lightline.lay");
    /** An Echo wakes from its seal. */
    public static final RegistryObject<SoundEvent> ECHO_AWAKEN = sound("event.echo_awaken");
    /** One of the giant chains breaks. */
    public static final RegistryObject<SoundEvent> CHAIN_BREAK = sound("event.chain_break");
    /** The Overtoll has fallen and the realm is free. */
    public static final RegistryObject<SoundEvent> REALM_FREED = sound("event.realm_freed");
    /** The realm's heartbeat: the artery pulses (see RealmSky and ArteryBounds). */
    public static final RegistryObject<SoundEvent> HEARTBEAT = sound("ambient.heartbeat");
    /** The realm's music (three tracks, one picked at random). */
    public static final RegistryObject<SoundEvent> MUSIC = sound("music.realm");

    private RealmSounds() {
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Realm.id(name)));
    }

    private static Set set(String mob) {
        String p = "entity." + mob + ".";
        return new Set(sound(p + "ambient"), sound(p + "hurt"), sound(p + "death"), sound(p + "step"), sound(p + "ability"));
    }

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
