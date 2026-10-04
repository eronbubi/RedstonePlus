package de.eron.redstoneplus.realm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Great Bell that hangs in the realm's sky. It tolls every {@link #INTERVAL} ticks; the sky renderer shows it and
 * plays the bong. Down on the ground the toll shakes the old machines: the traps around every player fire once,
 * as if the bell had reminded them of orders they no longer understand.
 */
public final class RealmBell {
    /** One toll a minute. */
    public static final int INTERVAL = 1200;
    public static final RegistryObject<SoundEvent> TOLL = RealmSounds.GREAT_BELL;

    private RealmBell() {
    }

    public static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !level.dimension().equals(Realm.REALM)) {
            return;
        }
        if (Math.floorMod(level.getGameTime(), INTERVAL) != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            RealmMechanics.pulseTraps(level, player.blockPosition(), 16);
        }
    }
}
