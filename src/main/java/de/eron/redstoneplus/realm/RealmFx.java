package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.RedstonePlus;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The realm's own particles and small helpers that place effects on a creature's body.
 * Offsets are in blocks relative to the body: side (+ = the creature's left), up, forward.
 */
public final class RealmFx {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, RedstonePlus.MODID);

    /** Short bright redstone spark that flies off and falls. */
    public static final RegistryObject<SimpleParticleType> SPARK = PARTICLES.register("realm_spark", () -> new SimpleParticleType(true));
    /** Soft white puff that rises and grows. */
    public static final RegistryObject<SimpleParticleType> STEAM = PARTICLES.register("realm_steam", () -> new SimpleParticleType(true));
    /** Glowing ember that floats up and flickers. */
    public static final RegistryObject<SimpleParticleType> EMBER = PARTICLES.register("realm_ember", () -> new SimpleParticleType(true));
    /** Cyan ring of sound that expands. */
    public static final RegistryObject<SimpleParticleType> RESONANCE = PARTICLES.register("realm_resonance", () -> new SimpleParticleType(true));
    /** Green glowing droplet. */
    public static final RegistryObject<SimpleParticleType> DRIP = PARTICLES.register("realm_drip", () -> new SimpleParticleType(true));

    private RealmFx() {
    }

    public static void init(IEventBus modBus) {
        PARTICLES.register(modBus);
    }

    /** World position of a point on the entity's body. */
    public static Vec3 on(LivingEntity e, double side, double up, double forward) {
        float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        // forward is -sin, cos in Minecraft's yaw convention; left is cos, sin
        double x = e.getX() - sin * forward + cos * side;
        double z = e.getZ() + cos * forward + sin * side;
        return new Vec3(x, e.getY() + up, z);
    }

    /** Client side: one particle on the body, with a small velocity. */
    public static void emit(LivingEntity e, ParticleOptions p, double side, double up, double forward, double vx, double vy, double vz) {
        Level level = e.level();
        if (level.isClientSide()) {
            Vec3 at = on(e, side, up, forward);
            level.addParticle(p, at.x, at.y, at.z, vx, vy, vz);
        }
    }

    /** Server side: particles from a point on the body for everyone to see. */
    public static void burst(LivingEntity e, ParticleOptions p, double side, double up, double forward, int count, double spread, double speed) {
        if (e.level() instanceof ServerLevel server) {
            Vec3 at = on(e, side, up, forward);
            server.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
        }
    }

    /** Server side: a flat ring of particles around a point, like a shockwave. */
    public static void ring(ServerLevel level, Vec3 center, double radius, ParticleOptions p, int points) {
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            level.sendParticles(p, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1,
                    Math.cos(a) * 0.1, 0.02, Math.sin(a) * 0.1, 0.05);
        }
    }

    /** Server side: a line of particles between two points (arcs, wires, beams). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double step) {
        int n = Math.max(2, (int) (from.distanceTo(to) / step));
        for (int i = 0; i <= n; i++) {
            Vec3 at = from.lerp(to, i / (double) n);
            level.sendParticles(p, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    public static boolean chance(Entity e, int oneIn) {
        return e.getRandom().nextInt(oneIn) == 0;
    }
}
