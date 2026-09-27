package de.eron.redstoneplus.realm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.eron.redstoneplus.RedstonePlus;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Slime;

/**
 * Renders any realm creature with its {@link RealmModel}, its texture and a glow layer
 * (eyes, redstone, furnace grills) that stays bright in the dark.
 */
public class RealmRenderer<T extends Mob> extends MobRenderer<T, RealmModel<T>> {
    private final ResourceLocation texture;
    private final float scale;

    public RealmRenderer(EntityRendererProvider.Context context, String name, ModelLayerLocation layer) {
        super(context, new RealmModel<>(context.bakeLayer(layer), name), RealmModel.spec(name).shadow());
        this.texture = ResourceLocation.fromNamespaceAndPath(RedstonePlus.MODID, "textures/entity/realm/" + name + ".png");
        this.scale = RealmModel.spec(name).scale();
        RenderType glow = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(RedstonePlus.MODID, "textures/entity/realm/" + name + "_glow.png"));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return glow;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return this.texture;
    }

    @Override
    protected void scale(T entity, PoseStack pose, float partialTick) {
        float s = this.scale;
        if (entity instanceof Slime slime) {
            s *= slime.getSize();
            this.shadowRadius = 0.25F * slime.getSize();
        }
        if (entity instanceof Creeper creeper) {
            // same swelling as the vanilla creeper before it blows
            float swell = creeper.getSwelling(partialTick);
            float wobble = 1.0F + Mth.sin(swell * 100.0F) * swell * 0.01F;
            swell = Mth.clamp(swell, 0.0F, 1.0F);
            swell = swell * swell * swell * swell;
            pose.scale((1.0F + swell * 0.4F) * wobble * s, (1.0F + swell * 0.1F) / wobble * s, (1.0F + swell * 0.4F) * wobble * s);
            return;
        }
        pose.scale(s, s, s);
    }

    @Override
    protected float getWhiteOverlayProgress(T entity, float partialTick) {
        if (entity instanceof Creeper creeper) {
            float swell = creeper.getSwelling(partialTick);
            return (int) (swell * 10.0F) % 2 == 0 ? 0.0F : Mth.clamp(swell, 0.5F, 1.0F);
        }
        return 0.0F;
    }
}
