package de.eron.redstoneplus.client;

import de.eron.redstoneplus.RedstonePlus;
import de.eron.redstoneplus.content.Extras;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** The new mobs use the vanilla renderers with their own textures. */
final class MobRenderers {
    private MobRenderers() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(RedstonePlus.MODID, "textures/entity/" + name + ".png");
    }

    static void register(EntityRenderersEvent.RegisterRenderers event) {
        ResourceLocation zombie = tex("uranium_zombie");
        event.registerEntityRenderer(Extras.URANIUM_ZOMBIE.get(), ctx -> new ZombieRenderer(ctx) {
            @Override
            public ResourceLocation getTextureLocation(Zombie entity) {
                return zombie;
            }
        });
        ResourceLocation creeper = tex("redstone_creeper");
        event.registerEntityRenderer(Extras.REDSTONE_CREEPER.get(), ctx -> new CreeperRenderer(ctx) {
            @Override
            public ResourceLocation getTextureLocation(Creeper entity) {
                return creeper;
            }
        });
        ResourceLocation spider = tex("crystal_spider");
        event.registerEntityRenderer(Extras.CRYSTAL_SPIDER.get(), ctx -> new SpiderRenderer<Spider>(ctx) {
            @Override
            public ResourceLocation getTextureLocation(Spider entity) {
                return spider;
            }
        });
        ResourceLocation skeleton = tex("magma_skeleton");
        event.registerEntityRenderer(Extras.MAGMA_SKELETON.get(), ctx -> new SkeletonRenderer(ctx) {
            @Override
            public ResourceLocation getTextureLocation(AbstractSkeleton entity) {
                return skeleton;
            }
        });
        ResourceLocation slime = tex("ruby_slime");
        event.registerEntityRenderer(Extras.RUBY_SLIME.get(), ctx -> new SlimeRenderer(ctx) {
            @Override
            public ResourceLocation getTextureLocation(Slime entity) {
                return slime;
            }
        });
        ResourceLocation enderman = tex("void_enderman");
        event.registerEntityRenderer(Extras.VOID_ENDERMAN.get(), ctx -> new EndermanRenderer(ctx) {
            @Override
            public ResourceLocation getTextureLocation(EnderMan entity) {
                return enderman;
            }
        });
        ResourceLocation pig = tex("ember_pig");
        event.registerEntityRenderer(Extras.EMBER_PIG.get(), ctx -> new PigRenderer(ctx) {
            @Override
            public ResourceLocation getTextureLocation(Pig entity) {
                return pig;
            }
        });
        ResourceLocation golem = tex("redstone_golem");
        event.registerEntityRenderer(Extras.REDSTONE_GOLEM.get(), ctx -> new IronGolemRenderer(ctx) {
            @Override
            public ResourceLocation getTextureLocation(IronGolem entity) {
                return golem;
            }
        });
    }
}
