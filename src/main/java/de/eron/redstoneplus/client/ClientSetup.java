package de.eron.redstoneplus.client;

import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) ->
                event.enqueueWork(() -> MenuScreens.register(ModRegistry.SUPER_PISTON_MENU.get(), SuperPistonScreen::new)));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(ModRegistry.FROZEN_TNT.get(), TntRenderer::new);
            event.registerEntityRenderer(ModRegistry.NUKE_TNT.get(), TntRenderer::new);
        });
    }
}
