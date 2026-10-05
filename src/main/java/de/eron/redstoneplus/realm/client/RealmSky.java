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
import de.eron.redstoneplus.realm.RealmStory;
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
import org.joml.Vector3f;

/**
 * The realm's sky: where the sun should be hangs the Great Bell, the Concordance, torn from its cradle. It sways,
 * throws slow beams of red light, and every {@link RealmBell#INTERVAL} ticks it tolls: the beams flare, the halo
 * swells, and a deep bong rolls over the land. Five giant chains run from the horizon up to it, one for each Echo
 * (see {@link RealmStory}); the chain of a fallen Echo hangs broken.
 * <p>
 * Once the realm is freed the Bell and its chains are gone: a red sun rises in their place, ringed with the bronze light
 * of the Bell's lip, and the light, the fog and the sky grow warm and bright.
 */
public final class RealmSky extends DimensionSpecialEffects {
    private static final ResourceLocation BELL = Realm.id("textures/environment/great_bell.png");
    private static final ResourceLocation HALO = Realm.id("textures/environment/great_bell_halo.png");
    private static final ResourceLocation CHAIN = Realm.id("textures/environment/sky_chain.png");
    private static final ResourceLocation SUN = Realm.id("textures/environment/red_sun.png");
    /** The warm light of the freed realm, that fog and sky are drawn towards. */
    private static final Vec3 DAWN = new Vec3(1.0, 0.62, 0.42);

    public RealmSky() {
        super(192.0F, true, SkyType.NORMAL, false, false);
    }

    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Realm.id("redstone_realm"), new RealmSky());
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        Vec3 fog = color.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
        if (RealmStory.clientHealed()) {
            fog = fog.lerp(DAWN.scale(brightness * 0.85 + 0.15), 0.4);
        }
        // every heartbeat flushes the haze a little redder
        var level = Minecraft.getInstance().level;
        if (level != null) {
            float beat = heartbeat(level, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            fog = fog.add(0.06 * beat, 0.0, 0.0).multiply(1.0 + 0.05 * beat, 1.0, 1.0);
        }
        return fog;
    }

    /** The freed realm's light: warmer and a little brighter everywhere, never quite black. */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX,
                                     int pixelY, Vector3f colors) {
        if (RealmStory.clientHealed()) {
            colors.set(Math.min(1.0F, colors.x * 1.08F + 0.06F), Math.min(1.0F, colors.y * 1.06F + 0.04F), Math.min(1.0F, colors.z * 1.02F + 0.025F));
        }
        // the light swells with each heartbeat, warmest in the reds
        float beat = heartbeat(level, partialTicks);
        colors.set(Math.min(1.0F, colors.x * (1.0F + 0.07F * beat)), Math.min(1.0F, colors.y * (1.0F + 0.03F * beat)), colors.z);
    }

    /** The Sealed Reach lies under a fog that never lifts; the rest of the realm is clear. */
    @Override
    public boolean isFoggyAt(int x, int z) {
        var level = Minecraft.getInstance().level;
        return level != null && !RealmStory.clientHealed()
                && level.getBiome(new net.minecraft.core.BlockPos(x, level.getSeaLevel(), z)).is(de.eron.redstoneplus.realm.SealedReach.BIOME);
    }

    /** The artery's heartbeat: one double beat every {@link #HEART_PERIOD} ticks. */
    static final int HEART_PERIOD = 100;

    /** How strongly the realm's heart is beating right now: 1 on a beat, falling off fast; a smaller second beat follows. */
    static float heartbeat(ClientLevel level, float partialTick) {
        float t = Math.floorMod(level.getGameTime(), HEART_PERIOD) + partialTick;
        float lub = (float) Math.exp(-t / 3.0);
        float dub = t >= 8 ? 0.6F * (float) Math.exp(-(t - 8) / 3.0) : 0.0F;
        return Math.min(1.0F, lub + dub);
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
        boolean healed = RealmStory.clientHealed();
        Vec3 sky = level.getSkyColor(camera.getPosition(), partialTick);
        if (healed) {
            sky = sky.lerp(DAWN.scale(0.4 + 0.6 * level.getSkyDarken(partialTick)), 0.35);
        }
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor((float) sky.x, (float) sky.y, (float) sky.z, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        disc(pose.last().pose(), 16.0F);
        RenderSystem.enableBlend();

        float clear = 1.0F - level.getRainLevel(partialTick);
        float pulse = healed ? 0.0F : toll(level, partialTick);
        float time = ticks + partialTick;
        float angle = level.getTimeOfDay(partialTick) * 360.0F;
        if (!healed) {
            // the chains, drawn first so the Bell hangs in front of where they meet it
            Vector3f bell = new Matrix4f().rotate(Axis.YP.rotationDegrees(-90.0F)).rotate(Axis.XP.rotationDegrees(angle))
                    .transformPosition(new Vector3f(0.0F, 100.0F, 0.0F));
            RenderSystem.defaultBlendFunc();
            chains(pose.last().pose(), bell, time, pulse, clear);
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        if (Mth.sin(angle * Mth.DEG_TO_RAD) < 0.0F) {
            // keep the crown pointing to the top of the sky while it rises, as it does while it sets
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
        if (healed) {
            sun(pose.last().pose(), time, clear);
            pose.popPose();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            return true;
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

    /** The red sun of the freed realm: a corona of warm light, slow soft rays, and the disc with its ring of bronze. */
    private static void sun(Matrix4f m, float time, float clear) {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, HALO);
        float breathe = 0.5F + 0.5F * Mth.sin(time * 0.01F);
        RenderSystem.setShaderColor(1.0F, 0.42F, 0.2F, clear * (0.6F + 0.15F * breathe));
        quad(m, 100.0F, 70.0F + 6.0F * breathe);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        beams(m, time, 0.0F, clear * 0.45F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SUN);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clear);
        quad(m, 99.0F, 34.0F);
    }

    /**
     * The five chains, from anchors spread round the horizon up to the Bell. A broken chain is a stub hanging from the Bell
     * and a length rising from the horizon that ends in the air. They shake when the Bell tolls.
     */
    private static void chains(Matrix4f m, Vector3f bell, float time, float pulse, float clear) {
        int conquered = RealmStory.clientConquered();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, CHAIN);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clear);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < RealmStory.Echo.values().length; i++) {
            double a = i * Mth.TWO_PI / 5.0 + 0.4;
            Vector3f anchor = new Vector3f((float) Math.cos(a) * 320.0F, -30.0F, (float) Math.sin(a) * 320.0F);
            float shake = pulse * 4.0F * Mth.sin(time * 0.7F + i * 1.3F);
            Vector3f top = new Vector3f(bell).add(shake, 0.0F, -shake);
            if ((conquered & (1 << i)) == 0) {
                strip(b, m, anchor, top, 3.5F);
            } else {
                Vector3f stub = new Vector3f(top).lerp(anchor, 0.1F).add(0.0F, -6.0F, 0.0F);
                strip(b, m, top, stub, 3.5F);
                strip(b, m, anchor, new Vector3f(anchor).lerp(top, 0.42F + 0.06F * (i % 3)), 3.5F);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** A flat band of chain from {@code a} to {@code b}, turned to face the camera (at the origin). */
    private static void strip(BufferBuilder buf, Matrix4f m, Vector3f a, Vector3f b, float halfWidth) {
        Vector3f along = new Vector3f(b).sub(a);
        float length = along.length();
        Vector3f mid = new Vector3f(a).add(b).mul(0.5F);
        Vector3f side = new Vector3f(along).cross(mid);
        if (side.lengthSquared() < 1.0E-6F) {
            return;
        }
        side.normalize(halfWidth);
        // the texture holds four links, each as long as the band is wide
        float v = length / (halfWidth * 2.0F * 4.0F);
        buf.addVertex(m, a.x - side.x, a.y - side.y, a.z - side.z).setUv(0.0F, 0.0F);
        buf.addVertex(m, a.x + side.x, a.y + side.y, a.z + side.z).setUv(1.0F, 0.0F);
        buf.addVertex(m, b.x + side.x, b.y + side.y, b.z + side.z).setUv(1.0F, v);
        buf.addVertex(m, b.x - side.x, b.y - side.y, b.z - side.z).setUv(0.0F, v);
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

    private static net.minecraft.sounds.Music realmMusic;
    private static int musicWait = 100;
    private static boolean wasInRealm;

    /**
     * The realm's music. Vanilla plays its creative music over biome music in creative mode, so in the realm the mod
     * runs the music itself: a track soon after arriving, then another every half minute to two minutes after one ends.
     */
    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.isPaused()) {
            return;
        }
        boolean inRealm = mc.level != null && mc.player != null && mc.level.dimension().equals(Realm.REALM);
        if (!inRealm) {
            wasInRealm = false;
            return;
        }
        if (!wasInRealm) {
            wasInRealm = true;
            musicWait = 100;
        }
        // the heartbeat: felt more than heard, louder the nearer you are to the Blood Below
        if (Math.floorMod(mc.level.getGameTime(), HEART_PERIOD) == 0) {
            float depth = (float) Math.max(0.0, Math.min(1.0, (110.0 - mc.player.getY()) / 160.0));
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forAmbientAddition(
                    de.eron.redstoneplus.realm.RealmSounds.HEARTBEAT.get()));
            if (depth > 0.5) {
                mc.player.playSound(de.eron.redstoneplus.realm.RealmSounds.HEARTBEAT.get(), 0.6F * depth, 0.8F);
            }
        }
        if (realmMusic == null) {
            realmMusic = new net.minecraft.sounds.Music(de.eron.redstoneplus.realm.RealmSounds.MUSIC.getHolder().orElseThrow(), 600, 2400, true);
        }
        var music = mc.getMusicManager();
        if (music.isPlayingMusic(realmMusic)) {
            return;
        }
        music.stopPlaying(); // no overworld or creative music in the realm
        if (--musicWait <= 0) {
            music.startPlaying(realmMusic);
            musicWait = 600 + mc.level.getRandom().nextInt(1800);
        }
    }
}
