package de.eron.redstoneplus.realm.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import de.eron.redstoneplus.realm.Realm;
import de.eron.redstoneplus.realm.RealmBell;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;

/**
 * The realm's sky: where the sun should be hangs the Great Bell, the Concordance, torn from its cradle. It sways,
 * throws slow beams of red light, and every {@link RealmBell#INTERVAL} ticks it tolls: the beams flare, the halo
 * swells, and a deep bong rolls over the land.
 */
public final class RealmSky extends DimensionSpecialEffects {
    private static final ResourceLocation BELL = Realm.id("textures/environment/great_bell.png");
    private static final ResourceLocation HALO = Realm.id("textures/environment/great_bell_halo.png");

    public RealmSky() {
        super(192.0F, true, SkyType.NORMAL, false, false);
    }

    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Realm.id("redstone_realm"), new RealmSky());
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return color.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    /** 1 right at a toll, fading to 0 over four seconds. */
    static float toll(ClientLevel level, float partialTick) {
        float since = Math.floorMod(level.getGameTime(), RealmBell.INTERVAL) + partialTick;
        return Math.max(0.0F, 1.0F - since / 80.0F);
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Camera camera, Matrix4f modelView, boolean foggy, Runnable setupFog) {
        setupFog.run();
        if (foggy) {
            return true;
        }
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        Vec3 sky = level.getSkyColor(camera.getPosition(), partialTick);
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor((float) sky.x, (float) sky.y, (float) sky.z, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        disc(pose.last().pose(), 16.0F);
        RenderSystem.enableBlend();

        float clear = 1.0F - level.getRainLevel(partialTick);
        float pulse = toll(level, partialTick);
        float time = ticks + partialTick;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        float angle = level.getTimeOfDay(partialTick) * 360.0F;
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        if (Mth.sin(angle * Mth.DEG_TO_RAD) < 0.0F) {
            // keep the crown pointing to the top of the sky while it rises, as it does while it sets
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
        // the bell swings gently, and hard when it tolls
        float swing = 5.0F * Mth.sin(time * 0.03F) + 22.0F * pulse * Mth.sin(time * 0.25F);
        pose.mulPose(Axis.YP.rotationDegrees(swing));
        Matrix4f m = pose.last().pose();

        // halo and beams: additive light
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, HALO);
        RenderSystem.setShaderColor(1.0F, 0.45F, 0.3F, clear * (0.55F + 0.45F * pulse));
        quad(m, 100.0F, 55.0F + 45.0F * pulse);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        beams(m, time, pulse, clear);

        // the bell itself
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BELL);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clear);
        quad(m, 98.0F, 26.0F);
        pose.popPose();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        return true;
    }

    private static void disc(Matrix4f m, float y) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        float r = Math.signum(y) * 512.0F;
        b.addVertex(m, 0.0F, y, 0.0F);
        for (int i = -180; i <= 180; i += 45) {
            b.addVertex(m, r * Mth.cos(i * Mth.DEG_TO_RAD), y, 512.0F * Mth.sin(i * Mth.DEG_TO_RAD));
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void quad(Matrix4f m, float y, float s) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, -s, y, -s).setUv(0.0F, 0.0F);
        b.addVertex(m, s, y, -s).setUv(1.0F, 0.0F);
        b.addVertex(m, s, y, s).setUv(1.0F, 1.0F);
        b.addVertex(m, -s, y, s).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** Slowly turning rays of red light around the bell; they flare outwards when it tolls. */
    private static void beams(Matrix4f m, float time, float pulse, float clear) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int rays = 12;
        for (int i = 0; i < rays; i++) {
            float a = i * Mth.TWO_PI / rays + time * 0.0015F * (i % 2 == 0 ? 1 : -1);
            float len = (110.0F + 60.0F * Mth.sin(time * 0.01F + i * 1.7F)) * (1.0F + 0.8F * pulse);
            float width = 0.07F + 0.03F * Mth.sin(time * 0.02F + i);
            float alpha = clear * (0.22F + 0.5F * pulse) * (0.6F + 0.4F * Mth.sin(time * 0.015F + i * 2.3F));
            int core = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255);
            b.addVertex(m, 0.0F, 100.0F, 0.0F).setColor(255, 90, 60, core);
            b.addVertex(m, len * Mth.cos(a - width), 100.0F, len * Mth.sin(a - width)).setColor(255, 40, 20, 0);
            b.addVertex(m, len * Mth.cos(a + width), 100.0F, len * Mth.sin(a + width)).setColor(255, 40, 20, 0);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** The bong: heard everywhere in the realm, timed by the world clock so every player hears it together. */
    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.player == null || mc.isPaused()
                || !mc.level.dimension().equals(Realm.REALM)) {
            return;
        }
        if (Math.floorMod(mc.level.getGameTime(), RealmBell.INTERVAL) == 0) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 30, mc.player.getZ(), RealmBell.TOLL.get(), SoundSource.AMBIENT,
                    3.0F, 1.0F, false);
        }
    }
}
