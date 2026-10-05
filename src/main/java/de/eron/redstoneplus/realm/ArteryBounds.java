package de.eron.redstoneplus.realm;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * The limits of the realm. The artery floats over a void; beyond its rims there is nothing but the Blood Below.
 * <ul>
 *     <li>Who falls into the Blood Below is not left to drown in it: after a few seconds the artery's pulse carries them
 *     back up onto the nearest land, burnt and dazed.</li>
 *     <li>Who flies out over the void is pushed back by the realm's pulse once they are far from any land, and brought back
 *     if they go further still.</li>
 * </ul>
 */
public final class ArteryBounds {
    /** Beyond this many blocks from the land the void pushes back. */
    private static final double PUSH = 280.0;
    /** Beyond this, you are brought back. */
    private static final double RETURN = 560.0;
    private static final String IN_BLOOD = "redstoneplus_in_blood";

    private ArteryBounds() {
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(ArteryBounds::playerTick);
    }

    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.isSpectator()
                || !player.level().dimension().equals(Realm.REALM)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        var data = player.getPersistentData();
        // the Blood Below
        if (player.getY() < ArteryWorldgen.BLOOD_TOP + 4 && !player.getAbilities().flying) {
            int t = data.getInt(IN_BLOOD) + 1;
            data.putInt(IN_BLOOD, t);
            if (t == 1) {
                player.displayClientMessage(Component.translatable("artery.redstoneplus.blood").withStyle(ChatFormatting.RED), true);
            }
            if (t >= 60) {
                data.remove(IN_BLOOD);
                carryBack(level, player, "artery.redstoneplus.carried_back");
            }
            return;
        }
        data.remove(IN_BLOOD);
        if (player.tickCount % 10 != 0 || player.isCreative()) {
            return;
        }
        // out over the void
        if (Artery.s(player.getX(), player.getZ()) > -0.05) {
            return;
        }
        double out = Artery.distanceOutside(player.getX(), player.getZ());
        if (out > RETURN) {
            carryBack(level, player, "artery.redstoneplus.returned");
        } else if (out > PUSH) {
            double[] land = Artery.nearestLand(player.getX(), player.getZ());
            Vec3 back = new Vec3(land[0] - player.getX(), 0.0, land[1] - player.getZ()).normalize().scale(0.9 + (out - PUSH) / 200.0);
            player.push(back.x, 0.15, back.z);
            player.hurtMarked = true;
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 1, player.getZ(), 30, 1.0, 1.0, 1.0, 0.02);
            player.displayClientMessage(Component.translatable("artery.redstoneplus.edge").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Puts a player down on the land nearest to them, on its surface. */
    private static void carryBack(ServerLevel level, ServerPlayer player, String message) {
        double[] land = Artery.nearestLand(player.getX(), player.getZ());
        int x = (int) Math.floor(land[0]);
        int z = (int) Math.floor(land[1]);
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= ArteryWorldgen.BLOOD_TOP + 2) {
            y = 120;
        }
        player.teleportTo(level, x + 0.5, y + 0.5, z + 0.5, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.clearFire();
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1));
        level.playSound(null, BlockPos.containing(player.position()), RealmSounds.HEARTBEAT.get(), SoundSource.PLAYERS, 2.0F, 0.7F);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1, player.getZ(), 30, 0.6, 0.8, 0.6, 0.0);
        player.displayClientMessage(Component.translatable(message).withStyle(ChatFormatting.RED), false);
    }
}
