package de.eron.redstoneplus.realm.client;

import de.eron.redstoneplus.realm.RealmFx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

/** Client behaviour of the realm particles: all full-bright, animated through their sprite frames. */
final class RealmParticles {
    private RealmParticles() {
    }

    static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(RealmFx.SPARK.get(), sprites -> new Provider(sprites, Kind.SPARK));
        event.registerSpriteSet(RealmFx.STEAM.get(), sprites -> new Provider(sprites, Kind.STEAM));
        event.registerSpriteSet(RealmFx.EMBER.get(), sprites -> new Provider(sprites, Kind.EMBER));
        event.registerSpriteSet(RealmFx.RESONANCE.get(), sprites -> new Provider(sprites, Kind.RESONANCE));
        event.registerSpriteSet(RealmFx.DRIP.get(), sprites -> new Provider(sprites, Kind.DRIP));
    }

    enum Kind {
        SPARK, STEAM, EMBER, RESONANCE, DRIP
    }

    record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new FxParticle(level, x, y, z, dx, dy, dz, this.sprites, this.kind);
        }
    }

    static class FxParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final Kind kind;
        private final float baseSize;

        FxParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, Kind kind) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.kind = kind;
            this.xd = dx + (this.random.nextDouble() - 0.5) * 0.02;
            this.yd = dy + (this.random.nextDouble() - 0.5) * 0.02;
            this.zd = dz + (this.random.nextDouble() - 0.5) * 0.02;
            switch (kind) {
                case SPARK -> {
                    this.lifetime = 6 + this.random.nextInt(8);
                    this.gravity = 0.6F;
                    this.friction = 0.9F;
                    this.quadSize = 0.06F + this.random.nextFloat() * 0.04F;
                    this.hasPhysics = true;
                }
                case STEAM -> {
                    this.lifetime = 25 + this.random.nextInt(20);
                    this.gravity = -0.03F;
                    this.friction = 0.92F;
                    this.quadSize = 0.2F + this.random.nextFloat() * 0.15F;
                    this.alpha = 0.7F;
                    this.hasPhysics = false;
                }
                case EMBER -> {
                    this.lifetime = 20 + this.random.nextInt(25);
                    this.gravity = -0.02F;
                    this.friction = 0.95F;
                    this.quadSize = 0.05F + this.random.nextFloat() * 0.05F;
                }
                case RESONANCE -> {
                    this.lifetime = 18;
                    this.gravity = 0.0F;
                    this.friction = 0.8F;
                    this.quadSize = 0.2F;
                    this.hasPhysics = false;
                }
                case DRIP -> {
                    this.lifetime = 20 + this.random.nextInt(15);
                    this.gravity = 0.5F;
                    this.friction = 0.98F;
                    this.quadSize = 0.05F + this.random.nextFloat() * 0.03F;
                    this.hasPhysics = true;
                }
            }
            this.baseSize = this.quadSize;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.setSpriteFromAge(this.sprites);
            float life = this.age / (float) this.lifetime;
            switch (this.kind) {
                case STEAM -> {
                    this.quadSize = this.baseSize * (1.0F + life * 2.5F);
                    this.alpha = 0.7F * (1.0F - life);
                }
                case RESONANCE -> {
                    this.quadSize = this.baseSize * (1.0F + life * 6.0F);
                    this.alpha = 1.0F - life;
                }
                case EMBER -> this.alpha = 0.6F + 0.4F * Mth.sin(this.age * 0.8F);
                case SPARK, DRIP -> this.alpha = 1.0F - life * life;
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return this.kind == Kind.STEAM ? super.getLightColor(partialTick) : 0xF000F0;
        }
    }
}
