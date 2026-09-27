package de.eron.redstoneplus.realm.client;

import de.eron.redstoneplus.realm.Realm;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Client registration for the realm: one model layer and renderer per creature. */
public final class RealmClient {
    private static final Map<String, Supplier<? extends EntityType<? extends Mob>>> CREATURES = new LinkedHashMap<>();

    private RealmClient() {
    }

    private static void creature(String name, RegistryObject<? extends EntityType<? extends Mob>> type) {
        CREATURES.put(name, type);
    }

    public static void init(IEventBus modBus) {
        creature("karst_colossus", Realm.KARST_COLOSSUS);
        creature("switchback_crawler", Realm.SWITCHBACK_CRAWLER);
        creature("bell_stalker", Realm.BELL_STALKER);
        creature("sluice_chainjaw", Realm.SLUICE_CHAINJAW);
        creature("kiln_brute", Realm.KILN_BRUTE);
        creature("spool_weaver", Realm.SPOOL_WEAVER);
        creature("leaking_cell", Realm.LEAKING_CELL);
        creature("detonator_husk", Realm.DETONATOR_HUSK);
        creature("tripwire_brood", Realm.TRIPWIRE_BROOD);
        creature("kilnbound", Realm.KILNBOUND);
        creature("living_capacitor", Realm.LIVING_CAPACITOR);
        creature("relay_strider", Realm.RELAY_STRIDER);
        creature("bellows_hog", Realm.BELLOWS_HOG);
        creature("flesh_press", Realm.FLESH_PRESS);

        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) ->
                CREATURES.keySet().forEach(name -> event.registerLayerDefinition(layer(name), () -> RealmModel.layer(name))));
        modBus.addListener(RealmClient::renderers);
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Realm.id(name), "main");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        CREATURES.forEach((name, type) -> event.registerEntityRenderer((EntityType) type.get(),
                ctx -> new RealmRenderer<>(ctx, name, layer(name))));
    }
}
