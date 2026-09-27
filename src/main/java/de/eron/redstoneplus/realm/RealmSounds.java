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

    public static final RegistryObject<SoundEvent> REALM_TRAVEL = sound("block.realm_gate.travel");

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
