package de.eron.redstoneplus;

import de.eron.redstoneplus.client.ClientSetup;
import de.eron.redstoneplus.entity.FastArrows;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(RedstonePlus.MODID)
public final class RedstonePlus {
    public static final String MODID = "redstoneplus";

    public RedstonePlus(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModRegistry.register(modBus);
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(de.eron.redstoneplus.world.ModBiomes::setup));
        FastArrows.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }
}
