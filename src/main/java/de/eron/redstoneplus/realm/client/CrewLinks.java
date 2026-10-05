package de.eron.redstoneplus.realm.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import de.eron.redstoneplus.realm.Crews;
import de.eron.redstoneplus.realm.Realm;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a crew looks like from outside (see {@link Crews}): thin links of light from its node to every member, a pulse
 * running down each link when the node sends an order and a fainter one running back when the member acknowledges, and
 * the members' animations kept in step. Engaged crews' links burn red and a lock-on line runs from the node to the target.
 */
public final class CrewLinks {
    private CrewLinks() {
    }

    private static List<Crews.CrewView> crews = List.of();
    private static final Map<Integer, Member> MEMBERS = new HashMap<>();
    private static long received;

    /** What the client knows of one creature's place in a crew. */
    record Member(Crews.CrewView crew, int latency, int role) {
    }

    static void init() {
        Crews.clientHandler = CrewLinks::accept;
        MinecraftForge.EVENT_BUS.addListener(CrewLinks::render);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
            crews = List.of();
            MEMBERS.clear();
        });
    }

    private static void accept(Crews.CrewPacket packet) {
        crews = packet.crews();
        MEMBERS.clear();
        for (Crews.CrewView c : crews) {
            MEMBERS.put(c.node(), new Member(c, 0, -1));
            for (int i = 0; i < c.members().length; i++) {
                MEMBERS.put(c.members()[i], new Member(c, c.latency()[i], c.role()[i]));
            }
        }
        received = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }

    private static boolean fresh() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getGameTime() - received < 40;
    }

    /** True if the creature is in a crew (its idle animation then runs on the crew's shared clock). */
    public static boolean inCrew(Entity e) {
        return fresh() && MEMBERS.containsKey(e.getId());
    }

    /**
     * Ticks since the last order reached this creature (0 the tick it arrives), or a large number. The model nods and the
     * glow flares while it is small.
     */
    public static float sinceOrder(Entity e, float partialTick) {
        Member m = MEMBERS.get(e.getId());
        if (m == null || !fresh()) {
            return 1000.0F;
        }
        return e.level().getGameTime() + partialTick - (m.crew().orderTick() + m.latency());
    }

    /** 0 member, 1 attack token, 2 repairing, -1 the node, or -2 not in a crew. */
    public static int role(Entity e) {
        Member m = MEMBERS.get(e.getId());
        return m == null || !fresh() ? -2 : m.role();
    }

    // ============================================================================================ drawing the links
    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !fresh()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(Realm.REALM)) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick();
        float time = mc.level.getGameTime() + partial;
        Matrix4f pose = new Matrix4f(event.getPoseStack());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        for (Crews.CrewView c : crews) {
            Entity node = mc.level.getEntity(c.node());
            if (node == null || c.members().length == 0) {
                continue; // a creature alone shows nothing
            }
            boolean engaged = c.state() == Crews.State.ENGAGE.ordinal();
            boolean working = c.state() == Crews.State.WORK.ordinal();
            Vec3 from = anchor(node, partial).subtract(cam);
            float since = time - c.orderTick();
            for (int i = 0; i < c.members().length; i++) {
                Entity m = mc.level.getEntity(c.members()[i]);
                if (m == null) {
                    continue;
                }
                Vec3 to = anchor(m, partial).subtract(cam);
                int r = engaged ? 255 : 255;
                int g = engaged ? 40 : working ? 150 : 90;
                int b = engaged ? 20 : working ? 50 : 40;
                int base = engaged ? 90 : 38;
                if (c.role()[i] == 2) {
                    g = 200;
                    b = 90; // repairing: a pale link
                }
                ribbon(vc, pose, from, to, 0.025F, r, g, b, base);
                // the order running down the link, and the acknowledgement coming back
                int lat = c.latency()[i];
                if (since >= 0 && since <= lat) {
                    pulse(vc, pose, from, to, since / lat, r, Math.min(255, g + 120), b + 60, 230);
                } else if (since > lat && since <= lat * 2) {
                    pulse(vc, pose, to, from, (since - lat) / lat, r, g, b, 120);
                }
                // the members holding a token show it: a short spike of light above them
                if (c.role()[i] == 1) {
                    Vec3 top = to.add(0, 0.9 + 0.15 * Mth.sin(time * 0.4F + i), 0);
                    ribbon(vc, pose, to, top, 0.05F, 255, 60, 30, 200);
                }
            }
            // the node's lock-on line to the target
            if (engaged && c.target() >= 0) {
                Entity target = mc.level.getEntity(c.target());
                if (target != null) {
                    Vec3 t = target.getPosition(partial).add(0, target.getBbHeight() * 0.6, 0).subtract(cam);
                    float flicker = 0.6F + 0.4F * Mth.sin(time * 0.9F);
                    ribbon(vc, pose, from, t, 0.018F, 255, 30, 10, (int) (110 * flicker));
                }
            }
            // the node itself: a slow beacon of its crew's heartbeat
            float beat = Math.max(0.0F, 1.0F - (time % 40) / 10.0F);
            ribbon(vc, pose, from, from.add(0, 0.5 + 0.5 * beat, 0), 0.06F, 255, engaged ? 40 : 160, 40, (int) (80 + 150 * beat));
        }
        buffers.endBatch(RenderType.lightning());
    }

    /** Where a link meets a creature: just above its back. */
    private static Vec3 anchor(Entity e, float partial) {
        return e.getPosition(partial).add(0, e.getBbHeight() * 0.85 + 0.2, 0);
    }

    /** A short bright stretch of the link, {@code f} of the way from a to b. */
    private static void pulse(VertexConsumer vc, Matrix4f pose, Vec3 a, Vec3 b, float f, int r, int g, int bl, int alpha) {
        double len = a.distanceTo(b);
        double half = Math.min(0.5, 0.6 / Math.max(0.1, len));
        Vec3 p0 = a.lerp(b, Math.max(0.0, f - half));
        Vec3 p1 = a.lerp(b, Math.min(1.0, f + half));
        ribbon(vc, pose, p0, p1, 0.07F, r, g, bl, alpha);
    }

    /** A flat band from a to b, turned to face the camera (at the origin of these coordinates). */
    private static void ribbon(VertexConsumer vc, Matrix4f pose, Vec3 a, Vec3 b, float halfWidth, int r, int g, int bl, int alpha) {
        Vector3f along = new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z));
        Vector3f mid = new Vector3f((float) (a.x + b.x) / 2, (float) (a.y + b.y) / 2, (float) (a.z + b.z) / 2);
        Vector3f side = new Vector3f(along).cross(mid);
        if (side.lengthSquared() < 1.0E-8F) {
            return;
        }
        side.normalize(halfWidth * Math.max(1.0F, mid.length() * 0.04F));
        vc.addVertex(pose, (float) a.x - side.x, (float) a.y - side.y, (float) a.z - side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) a.x + side.x, (float) a.y + side.y, (float) a.z + side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) b.x + side.x, (float) b.y + side.y, (float) b.z + side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) b.x - side.x, (float) b.y - side.y, (float) b.z - side.z).setColor(r, g, bl, alpha);
        // and the other way round, so it shows from either side
        vc.addVertex(pose, (float) b.x - side.x, (float) b.y - side.y, (float) b.z - side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) b.x + side.x, (float) b.y + side.y, (float) b.z + side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) a.x + side.x, (float) a.y + side.y, (float) a.z + side.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) a.x - side.x, (float) a.y - side.y, (float) a.z - side.z).setColor(r, g, bl, alpha);
    }
}
