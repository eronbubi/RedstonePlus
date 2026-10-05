package de.eron.redstoneplus.realm.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.eron.redstoneplus.realm.LightCycle;
import de.eron.redstoneplus.realm.RealmSounds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/** Client side of the light cycle: the Grid Map key, the boost key, the engine sound and the HUD. */
public final class CycleClient {
    public static final KeyMapping GRID_MAP = new KeyMapping("key.redstoneplus.grid_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M,
            "key.categories.redstoneplus");

    private static final Map<Integer, EngineSound> ENGINES = new HashMap<>();

    private CycleClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(GRID_MAP));
        // the HUD draws with the hotbar layers, under chat and titles
        modBus.addListener((AddGuiOverlayLayersEvent event) -> event.getLayeredDraw().add(ForgeLayeredDraw.PRE_SLEEP_STACK,
                de.eron.redstoneplus.realm.Realm.id("light_cycle_hud"), (graphics, delta) -> CycleHud.render(graphics, delta.getGameTimeDeltaPartialTick(true))));
        MinecraftForge.EVENT_BUS.addListener(CycleClient::clientTick);
    }

    private static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GridScan.tick(mc.level);
        while (GRID_MAP.consumeClick()) {
            if (mc.level != null && mc.screen == null) {
                mc.setScreen(new GridMapScreen());
            }
        }
        ENGINES.values().removeIf(EngineSound::isStopped);
    }

    /** The overdrive: hold jump while riding a lightline. */
    public static boolean boostHeld() {
        return Minecraft.getInstance().options.keyJump.isDown();
    }

    /** Keeps an engine sound running for a moving cycle (called every client tick while it moves). */
    public static void engine(LightCycle cycle, double moved) {
        EngineSound sound = ENGINES.get(cycle.getId());
        if (sound == null || sound.isStopped()) {
            sound = new EngineSound(cycle);
            ENGINES.put(cycle.getId(), sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
        sound.moved = moved;
        sound.idle = 0;
    }

    /** A looping hum that follows the cycle and rises in pitch with its speed. */
    static final class EngineSound extends AbstractTickableSoundInstance {
        private final LightCycle cycle;
        double moved;
        int idle;

        EngineSound(LightCycle cycle) {
            super(RealmSounds.CYCLE_ENGINE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.cycle = cycle;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.x = cycle.getX();
            this.y = cycle.getY();
            this.z = cycle.getZ();
        }

        @Override
        public void tick() {
            if (this.cycle.isRemoved() || ++this.idle > 20) {
                this.stop();
                return;
            }
            this.x = this.cycle.getX();
            this.y = this.cycle.getY();
            this.z = this.cycle.getZ();
            float speed = (float) Math.min(2.4, this.moved);
            this.pitch = 0.6F + speed * 0.65F;
            this.volume = Math.min(1.0F, 0.25F + speed * 0.5F);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
