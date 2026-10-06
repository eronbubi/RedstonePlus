package de.eron.redstoneplus.realm.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import de.eron.redstoneplus.realm.Realm;
import de.eron.redstoneplus.realm.RealmPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws the cables of {@link RealmPower}'s lines: three dark cables slung between the insulators of each pair of pylons,
 * sagging in the middle, with pulses of red power running along them from pylon to pylon down the whole line, brighter
 * on every heartbeat.
 */
public final class PowerLines {
    private PowerLines() {
    }

    private static final double RANGE = 320.0;
    private static final int PIECES = 14;
    /** Blocks between two pulses on a line, and how fast they run (blocks per tick). */
    private static final double GAP = 40.0;
    private static final double SPEED = 0.7;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PowerLines::render);
    }

    /** The y of a pylon's crown, read from the loaded world (the centre insulator is the highest block there), or -1. */
    private static int crown(ClientLevel level, RealmPower.Pylon p) {
        if (!level.hasChunk(p.x() >> 4, p.z() >> 4)) {
            return -1;
        }
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, p.x(), p.z());
        return level.getBlockState(new net.minecraft.core.BlockPos(p.x(), top - 1, p.z())).is(net.minecraft.world.level.block.Blocks.LIGHTNING_ROD)
                ? top - 2 : -1;
    }

    /** The point a cable hangs from: -1 = the right-hand arm, 0 = the middle, 1 = the left-hand arm. */
    private static Vec3 insulator(RealmPower.Pylon p, int crown, int side) {
        int off = side * p.leftSign() * RealmPower.ARM;
        return new Vec3(p.x() + 0.5 + (p.armAxis() == 0 ? off : 0), crown + 1.85, p.z() + 0.5 + (p.armAxis() == 1 ? off : 0));
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !level.dimension().equals(Realm.REALM)) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        float time = level.getGameTime() + event.getPartialTick();
        float beat = RealmSky.heartbeat(level, event.getPartialTick());
        Matrix4f pose = new Matrix4f(event.getPoseStack());
        // the cables in view: from, to, span length, distance along the line where the span starts
        java.util.List<Object[]> spans = new java.util.ArrayList<>();
        for (List<RealmPower.Pylon> line : RealmPower.LINES) {
            double run = 0; // distance along the line, so pulses carry on from span to span
            for (int i = 0; i + 1 < line.size(); i++) {
                RealmPower.Pylon a = line.get(i);
                RealmPower.Pylon b = line.get(i + 1);
                double span = a == null || b == null ? RealmPower.SPACING : Math.hypot(b.x() - a.x(), b.z() - a.z());
                if (a != null && b != null
                        && Math.min(cam.distanceToSqr(a.x(), cam.y, a.z()), cam.distanceToSqr(b.x(), cam.y, b.z())) < RANGE * RANGE) {
                    int ca = crown(level, a);
                    int cb = crown(level, b);
                    if (ca >= 0 && cb >= 0) {
                        for (int side = -1; side <= 1; side++) {
                            spans.add(new Object[]{insulator(a, ca, side).subtract(cam), insulator(b, cb, side).subtract(cam), span, run + side * 9});
                        }
                    }
                }
                run += span;
            }
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer cable = buffers.getBuffer(RenderType.debugQuads());
        for (Object[] sp : spans) {
            span(cable, pose, (Vec3) sp[0], (Vec3) sp[1], (double) sp[2]);
        }
        buffers.endBatch(RenderType.debugQuads());
        // the power itself, in additive light
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        for (Object[] sp : spans) {
            pulses(glow, pose, (Vec3) sp[0], (Vec3) sp[1], (double) sp[2], (double) sp[3], time, beat);
        }
        buffers.endBatch(RenderType.lightning());
    }

    /** A point on the sagging cable, f of the way from a to b. */
    private static Vec3 sag(Vec3 a, Vec3 b, double span, double f) {
        return a.lerp(b, f).add(0, -span * 0.05 * 4 * f * (1 - f), 0);
    }

    private static void span(VertexConsumer vc, Matrix4f pose, Vec3 a, Vec3 b, double span) {
        for (int k = 0; k < PIECES; k++) {
            Vec3 p0 = sag(a, b, span, k / (double) PIECES);
            Vec3 p1 = sag(a, b, span, (k + 1) / (double) PIECES);
            ribbon(vc, pose, p0, p1, 0.07F, 46, 16, 14, 255);
        }
    }

    private static void pulses(VertexConsumer vc, Matrix4f pose, Vec3 a, Vec3 b, double span, double run, float time, float beat) {
        double head = time * SPEED;
        for (int k = 0; k < PIECES; k++) {
            double f0 = k / (double) PIECES;
            double f1 = (k + 1) / (double) PIECES;
            double s = run + span * (f0 + f1) / 2;
            // distance (in blocks) to the nearest pulse behind this point
            double d = ((head - s) % GAP + GAP) % GAP;
            double bright = Math.exp(-d / 2.5) + 0.12 + 0.25 * beat;
            int alpha = (int) Math.min(255, 255 * bright);
            ribbon(vc, pose, sag(a, b, span, f0), sag(a, b, span, f1), 0.11F, 255, 60, 30, alpha);
        }
    }

    /** A flat band from a to b, turned to face the camera, from both sides. */
    private static void ribbon(VertexConsumer vc, Matrix4f pose, Vec3 a, Vec3 b, float halfWidth, int r, int g, int bl, int alpha) {
        Vector3f along = new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z));
        Vector3f mid = new Vector3f((float) (a.x + b.x) / 2, (float) (a.y + b.y) / 2, (float) (a.z + b.z) / 2);
        Vector3f side = new Vector3f(along).cross(mid);
        if (side.lengthSquared() < 1.0E-8F) {
            return;
        }
        side.normalize(halfWidth * Math.max(1.0F, mid.length() * 0.012F));
        vc.addVertex(pose, (float) a.x - side.x, (float) a.y - side.y, (float) a.z - side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) a.x + side.x, (float) a.y + side.y, (float) a.z + side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) b.x + side.x, (float) b.y + side.y, (float) b.z + side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) b.x - side.x, (float) b.y - side.y, (float) b.z - side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) b.x - side.x, (float) b.y - side.y, (float) b.z - side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) b.x + side.x, (float) b.y + side.y, (float) b.z + side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) a.x + side.x, (float) a.y + side.y, (float) a.z + side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) a.x - side.x, (float) a.y - side.y, (float) a.z - side.z).setColor(r, g, bl, alpha);
    }
}
