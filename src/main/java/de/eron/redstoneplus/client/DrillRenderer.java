package de.eron.redstoneplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.eron.redstoneplus.entity.DrillEntity;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Draws the drill from two block models: the body with tracks and cabin, and the cone shaped bit in front,
 * which spins while the drill is boring.
 */
public class DrillRenderer extends EntityRenderer<DrillEntity> {
    /** The bit rotates around this point (in block pixels / 16): the centre of the cone's base. */
    private static final float AXIS_X = 0.5F;
    private static final float AXIS_Y = 7.0F / 16.0F;

    /** The block models are drawn at this size: the drill is two blocks wide and high. */
    private static final float SCALE = 2.0F;

    private final BlockRenderDispatcher blocks;

    public DrillRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 1.2F;
    }

    @Override
    public void render(DrillEntity drill, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        // the block models face north (-Z); an entity with yaw 0 faces south (+Z)
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, drill.yRotO, drill.getYRot())));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5F, 0.0F, -0.5F);
        this.blocks.renderSingleBlock(ModRegistry.DRILL_BODY_MODEL.get().defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);

        float spin = Mth.lerp(partialTick, drill.bitAngleO, drill.bitAngle);
        pose.translate(AXIS_X, AXIS_Y, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
        pose.translate(-AXIS_X, -AXIS_Y, 0.0F);
        this.blocks.renderSingleBlock(ModRegistry.DRILL_BIT_MODEL.get().defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(drill, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DrillEntity drill) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
