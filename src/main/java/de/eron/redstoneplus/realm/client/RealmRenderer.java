package de.eron.redstoneplus.realm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.eron.redstoneplus.RedstonePlus;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
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
        this.addLayer(new GlowLayer<>(this, ResourceLocation.fromNamespaceAndPath(RedstonePlus.MODID, "textures/entity/realm/" + name + "_glow.png")));
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

    /**
     * Full-bright layer for eyes, redstone and fire. It breathes slowly, and flares up while the creature's
     * special move plays.
     */
    static class GlowLayer<T extends Mob> extends net.minecraft.client.renderer.entity.layers.RenderLayer<T, RealmModel<T>> {
        private final RenderType type;

        GlowLayer(net.minecraft.client.renderer.entity.RenderLayerParent<T, RealmModel<T>> parent, ResourceLocation texture) {
            super(parent);
            this.type = RenderType.eyes(texture);
        }

        @Override
        public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, T entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
            float pulse = 0.78F + 0.22F * Mth.sin(age * 0.12F + entity.getId());
            if (entity instanceof de.eron.redstoneplus.realm.RealmAnimated animated) {
                float since = age - animated.abilityStart();
                if (since >= 0 && since < 20) {
                    pulse = 1.0F;
                }
            }
            if (entity.hurtTime > 0) {
                pulse = 1.0F;
            }
            int color = net.minecraft.util.FastColor.ARGB32.colorFromFloat(1.0F, pulse, pulse, pulse);
            this.getParentModel().renderToBuffer(pose, buffers.getBuffer(this.type), 0xF00000,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, color);
        }
    }
}
