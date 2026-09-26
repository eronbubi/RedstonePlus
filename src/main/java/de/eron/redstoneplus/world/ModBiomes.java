package de.eron.redstoneplus.world;

import com.mojang.datafixers.util.Pair;
import de.eron.redstoneplus.RedstonePlus;
import de.eron.redstoneplus.content.Extras;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.api.EndBiomeRegistry;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

import java.util.function.Consumer;

/**
 * Three new Overworld biomes and two new End biomes, placed with TerraBlender.
 * The Overworld ones live in their own region that swaps plains, forests and hills for the new biomes.
 */
public final class ModBiomes {
    public static final ResourceKey<Biome> REDSTONE_FIELDS = key("redstone_fields");
    public static final ResourceKey<Biome> CRYSTAL_FOREST = key("crystal_forest");
    public static final ResourceKey<Biome> TITAN_HIGHLANDS = key("titan_highlands");
    public static final ResourceKey<Biome> VOID_WASTES = key("void_wastes");
    public static final ResourceKey<Biome> CRYSTAL_SPIRES = key("crystal_spires");

    private ModBiomes() {
    }

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(RedstonePlus.MODID, name));
    }

    private static class OverworldRegion extends Region {
        OverworldRegion() {
            super(ResourceLocation.fromNamespaceAndPath(RedstonePlus.MODID, "overworld"), RegionType.OVERWORLD, 4);
        }

        @Override
        public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
            this.addModifiedVanillaOverworldBiomes(mapper, builder -> {
                builder.replaceBiome(Biomes.PLAINS, REDSTONE_FIELDS);
                builder.replaceBiome(Biomes.SUNFLOWER_PLAINS, REDSTONE_FIELDS);
                builder.replaceBiome(Biomes.FOREST, CRYSTAL_FOREST);
                builder.replaceBiome(Biomes.BIRCH_FOREST, CRYSTAL_FOREST);
                builder.replaceBiome(Biomes.WINDSWEPT_HILLS, TITAN_HIGHLANDS);
                builder.replaceBiome(Biomes.MEADOW, TITAN_HIGHLANDS);
            });
        }
    }

    /** Runs in common setup (enqueueWork). */
    public static void setup() {
        Regions.register(new OverworldRegion());
        EndBiomeRegistry.registerMidlandsBiome(VOID_WASTES, 8);
        EndBiomeRegistry.registerEdgeBiome(VOID_WASTES, 6);
        EndBiomeRegistry.registerHighlandsBiome(CRYSTAL_SPIRES, 8);

        // Void Wastes are covered in Void Stone instead of End Stone
        SurfaceRules.RuleSource voidFloor = SurfaceRules.ifTrue(SurfaceRules.isBiome(VOID_WASTES),
                SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(0, true, 2, net.minecraft.world.level.levelgen.placement.CaveSurface.FLOOR),
                        SurfaceRules.state(Extras.VOID_STONE.get().defaultBlockState())));
        SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.END, RedstonePlus.MODID, voidFloor);

        // Titan Highlands: bare stone and gravel on the peaks
        SurfaceRules.RuleSource titan = SurfaceRules.ifTrue(SurfaceRules.isBiome(TITAN_HIGHLANDS),
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                        SurfaceRules.ifTrue(SurfaceRules.noiseCondition(net.minecraft.world.level.levelgen.Noises.SURFACE, 0.1),
                                SurfaceRules.state(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()))));
        SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, RedstonePlus.MODID, titan);
    }
}
