package de.eron.redstoneplus.entity;

import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla only sends up to 3.9 blocks per tick of velocity to the client, so a 100 blocks/tick arrow would be
 * drawn crawling along. For arrows from the Arrow Shooter the server sends the real position every tick for
 * the first second, so on screen they jump straight to where they really are, which looks like teleporting.
 */
public final class FastArrows {
    private static final int SYNC_TICKS = 20;
    private static final List<Tracked> TRACKED = new ArrayList<>();

    private record Tracked(Entity entity, long until) {
    }

    private FastArrows() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(FastArrows::onLevelTick);
    }

    public static void track(ServerLevel level, Entity entity) {
        TRACKED.add(new Tracked(entity, level.getGameTime() + SYNC_TICKS));
    }

    private static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || TRACKED.isEmpty()) {
            return;
        }
        TRACKED.removeIf(t -> {
            if (t.entity().level() != level) {
                return false;
            }
            if (t.entity().isRemoved() || level.getGameTime() > t.until()) {
                return true;
            }
            level.getChunkSource().broadcast(t.entity(), new ClientboundTeleportEntityPacket(t.entity()));
            return false;
        });
    }
}
