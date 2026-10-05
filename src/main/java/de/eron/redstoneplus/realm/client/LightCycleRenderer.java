package de.eron.redstoneplus.realm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.eron.redstoneplus.realm.LightCycle;
import de.eron.redstoneplus.realm.Realm;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the light cycle from its Blender model (realm_models/light_cycle.json): the wheels turn with the distance it
 * covers, it leans into turns, its light band and rims glow full bright, and behind it hangs the light wall: a ribbon of
 * red light along the path of the last two seconds, fading towards its tail.
 */
public class LightCycleRenderer extends EntityRenderer<LightCycle> {
    static final ModelLayerLocation LAYER = new ModelLayerLocation(Realm.id("light_cycle"), "main");
    private static final ResourceLocation TEXTURE = Realm.id("textures/entity/realm/light_cycle.png");
    private static final ResourceLocation GLOW = Realm.id("textures/entity/realm/light_cycle_glow.png");

    private final ModelPart root;
    private final List<ModelPart> parts = new ArrayList<>();
    private final ModelPart wheelFront;
    private final ModelPart wheelBack;
    private final ModelPart fin;

    public LightCycleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.root = context.bakeLayer(LAYER);
        this.root.getAllParts().forEach(this.parts::add);
        ModelPart body = this.root.getChild("body");
        this.wheelFront = body.getChild("wheel_front");
        this.wheelBack = body.getChild("wheel_back");
        this.fin = body.getChild("fin");
        this.shadowRadius = 0.6F;
    }

    @Override
    public boolean shouldRender(LightCycle cycle, Frustum frustum, double x, double y, double z) {
        return !cycle.trail.isEmpty() ? cycle.distanceToSqr(x, y, z) < 160 * 160 : super.shouldRender(cycle, frustum, x, y, z);
    }

    @Override
    public void render(LightCycle cycle, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        this.trail(cycle, partialTick, pose, buffers);
        pose.pushPose();
        float yaw = Mth.rotLerp(partialTick, cycle.yRotO, cycle.getYRot());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, cycle.leanO, cycle.lean)));
        // model space: 16 pixels a block, y down, ground at 24
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);
        this.parts.forEach(ModelPart::resetPose);
        float spin = Mth.lerp(partialTick, cycle.wheelAngleO, cycle.wheelAngle) * Mth.DEG_TO_RAD;
        this.wheelFront.xRot = spin;
        this.wheelBack.xRot = spin;
        float pulse = 1.0F + 0.08F * Mth.sin((cycle.tickCount + partialTick) * 0.3F);
        this.fin.yScale = pulse;
        this.fin.zScale = pulse;
        this.root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        this.root.render(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(cycle, entityYaw, partialTick, pose, buffers, light);
    }

    /** The light wall: one quad from each remembered position to the next, both sides, fading with age. */
    private void trail(LightCycle cycle, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        if (cycle.trail.size() < 2) {
            return;
        }
        double ox = Mth.lerp(partialTick, cycle.xo, cycle.getX());
        double oy = Mth.lerp(partialTick, cycle.yo, cycle.getY());
        double oz = Mth.lerp(partialTick, cycle.zo, cycle.getZ());
        List<Vec3> points = new ArrayList<>(cycle.trail);
        points.add(new Vec3(ox, oy, oz));
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int n = points.size();
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            if (a.distanceToSqr(b) > 9.0 * 9.0) {
                continue; // a jump (rebuilt cycle, teleport): no wall across it
            }
            int alphaA = (int) (200.0F * i / n);
            int alphaB = (int) (200.0F * (i + 1) / n);
            float ax = (float) (a.x - ox);
            float ay = (float) (a.y - oy);
            float az = (float) (a.z - oz);
            float bx = (float) (b.x - ox);
            float by = (float) (b.y - oy);
            float bz = (float) (b.z - oz);
            float low = 0.08F;
            float high = 0.95F;
            vc.addVertex(m, ax, ay + low, az).setColor(255, 40, 20, alphaA);
            vc.addVertex(m, bx, by + low, bz).setColor(255, 40, 20, alphaB);
            vc.addVertex(m, bx, by + high, bz).setColor(255, 120, 60, alphaB / 3);
            vc.addVertex(m, ax, ay + high, az).setColor(255, 120, 60, alphaA / 3);
            vc.addVertex(m, ax, ay + high, az).setColor(255, 120, 60, alphaA / 3);
            vc.addVertex(m, bx, by + high, bz).setColor(255, 120, 60, alphaB / 3);
            vc.addVertex(m, bx, by + low, bz).setColor(255, 40, 20, alphaB);
            vc.addVertex(m, ax, ay + low, az).setColor(255, 40, 20, alphaA);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(LightCycle cycle) {
        return TEXTURE;
    }
}
