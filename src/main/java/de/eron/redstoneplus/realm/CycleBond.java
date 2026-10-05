package de.eron.redstoneplus.realm;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bond between a rider and their light cycle.
 * <p>
 * The first time a player enters the realm, a light cycle materialises beside them and they get its Cycle Key. From then
 * on the realm makes sure the cycle is always there: every second it checks that the rider's cycle exists, is in the
 * same world and is within reach; if it is not (left behind, unloaded, fallen out of the world, broken), it rebuilds it
 * beside the rider a few seconds later. There is only ever one: a cycle whose rider has since been given another one
 * dissolves by itself. The key recalls the cycle on demand (and puts you on it), or with sneak toggles the automatic
 * return. Sneak-hitting your own cycle puts it away (the automatic return stays off until the key brings it back).
 * <p>
 * The bond is kept under Forge's PlayerPersisted tag, so it survives death.
 */
public final class CycleBond {
    private static final String PERSISTED = "PlayerPersisted";
    private static final String DATA = "redstoneplus_cycle";
    /** Seconds a cycle may be missing before it is rebuilt. */
    private static final int LOST_SECONDS = 4;
    private static final double REACH = 64.0;

    private static final Map<UUID, Integer> LOST = new HashMap<>();

    private CycleBond() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(CycleBond::playerTick);
        MinecraftForge.EVENT_BUS.addListener(CycleBond::changedDimension);
        MinecraftForge.EVENT_BUS.addListener(CycleBond::login);
        MinecraftForge.EVENT_BUS.addListener(CycleBond::logout);
        MinecraftForge.EVENT_BUS.addListener(CycleBond::riderSafety);
    }

    private static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(PERSISTED);
        if (!root.contains(PERSISTED)) {
            root.put(PERSISTED, persisted);
        }
        CompoundTag data = persisted.getCompound(DATA);
        if (!persisted.contains(DATA)) {
            persisted.put(DATA, data);
        }
        return data;
    }

    @Nullable
    public static UUID bonded(Player player) {
        CompoundTag data = data(player);
        return data.hasUUID("cycle") ? data.getUUID("cycle") : null;
    }

    /** False after the rider put the cycle away (sneak-hit or sneak-use of the key). */
    public static boolean autoReturn(Player player) {
        return !data(player).getBoolean("manual");
    }

    private static void bind(ServerPlayer player, LightCycle cycle) {
        cycle.setOwner(player.getUUID());
        data(player).putUUID("cycle", cycle.getUUID());
    }

    /** An unowned cycle (from a spawn command, or an old world) becomes the cycle of whoever rides it first. */
    static void claim(ServerPlayer player, LightCycle cycle) {
        LightCycle old = find(player);
        if (old != null && old != cycle) {
            derez(old);
        }
        bind(player, cycle);
    }

    @Nullable
    private static LightCycle find(ServerPlayer player) {
        UUID id = bonded(player);
        if (id == null) {
            return null;
        }
        Entity e = player.serverLevel().getEntity(id);
        return e instanceof LightCycle cycle && cycle.isAlive() ? cycle : null;
    }

    // ============================================================================================ rez and derez
    private static final DustParticleOptions LIGHT = new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.08F), 1.4F);

    /** The cycle dissolves into light. */
    static void derez(LightCycle cycle) {
        if (cycle.level() instanceof ServerLevel level) {
            level.sendParticles(LIGHT, cycle.getX(), cycle.getY() + 0.5, cycle.getZ(), 40, 0.6, 0.4, 0.6, 0.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cycle.getX(), cycle.getY() + 0.5, cycle.getZ(), 20, 0.5, 0.3, 0.5, 0.2);
            level.playSound(null, cycle.getX(), cycle.getY(), cycle.getZ(), RealmSounds.CYCLE_DEREZ.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        cycle.ejectPassengers();
        cycle.discard();
    }

    /**
     * Builds the rider's cycle beside them (the old one, wherever it is, dissolves) and optionally puts them on it.
     * Returns the new cycle, or null if there is no room.
     */
    @Nullable
    static LightCycle rez(ServerPlayer player, boolean mount) {
        ServerLevel level = player.serverLevel();
        LightCycle old = find(player);
        if (old != null) {
            derez(old);
        }
        LightCycle cycle = Realm.LIGHT_CYCLE.get().create(level);
        if (cycle == null) {
            return null;
        }
        Vec3 spot = spot(player, cycle);
        cycle.moveTo(spot.x, spot.y, spot.z, player.getYRot(), 0.0F);
        bind(player, cycle);
        level.addFreshEntity(cycle);
        level.sendParticles(LIGHT, spot.x, spot.y + 0.5, spot.z, 60, 0.7, 0.5, 0.7, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 0.5, spot.z, 20, 0.6, 0.4, 0.6, 0.05);
        level.playSound(null, spot.x, spot.y, spot.z, RealmSounds.CYCLE_REZ.get(), SoundSource.NEUTRAL, 1.2F, 1.0F);
        data(player).putBoolean("manual", false);
        if (mount) {
            player.startRiding(cycle);
        }
        return cycle;
    }

    /** Room for a cycle beside the player: to the side they are not looking at, else where they stand. */
    private static Vec3 spot(ServerPlayer player, LightCycle cycle) {
        Vec3 base = player.position();
        for (float side : new float[]{90.0F, -90.0F, 180.0F, 0.0F}) {
            Vec3 at = base.add(Vec3.directionFromRotation(0.0F, player.getYRot() + side).scale(1.6));
            BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
            if (player.level().noCollision(cycle, cycle.getType().getDimensions().makeBoundingBox(at))
                    && player.level().getBlockState(below).isSolid()) {
                return at;
            }
        }
        return base;
    }

    /** The rider sneak-hit their own cycle: it is put away until the key calls it. */
    static void park(ServerPlayer player, LightCycle cycle) {
        derez(cycle);
        data(player).putBoolean("manual", true);
        player.displayClientMessage(Component.translatable("message.redstoneplus.cycle_parked").withStyle(ChatFormatting.GOLD), true);
    }

    // ============================================================================================ the checks
    /** Every 40 ticks on each cycle: a cycle its rider no longer owns (they got a new one) dissolves. */
    static void checkCycle(ServerLevel level, LightCycle cycle) {
        UUID owner = cycle.owner();
        if (owner == null || cycle.isVehicle()) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null && !cycle.getUUID().equals(bonded(player))) {
            derez(cycle);
        }
    }

    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 7) {
            return;
        }
        if (!RealmRules.inRealm(player.level()) || player.isSpectator() || !player.isAlive()) {
            LOST.remove(player.getUUID());
            return;
        }
        CompoundTag data = data(player);
        if (!data.getBoolean("given")) {
            welcome(player);
            return;
        }
        if (player.getVehicle() instanceof LightCycle riding) {
            if (!riding.getUUID().equals(bonded(player)) && player.getUUID().equals(riding.owner())) {
                bind(player, riding);
            }
            LOST.remove(player.getUUID());
            return;
        }
        if (!autoReturn(player) || player.isPassenger()) {
            LOST.remove(player.getUUID());
            return;
        }
        LightCycle cycle = find(player);
        boolean present = cycle != null && cycle.level() == player.level() && cycle.distanceToSqr(player) < REACH * REACH
                && !cycle.level().getBlockState(cycle.blockPosition()).isSuffocating(cycle.level(), cycle.blockPosition());
        if (present) {
            LOST.remove(player.getUUID());
            return;
        }
        int seconds = LOST.merge(player.getUUID(), 1, Integer::sum);
        if (seconds >= LOST_SECONDS) {
            LOST.remove(player.getUUID());
            if (rez(player, false) != null) {
                player.displayClientMessage(Component.translatable("message.redstoneplus.cycle_returned").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /** The first visit: a cycle materialises, the key goes to the player, and a few lines tell them how to ride. */
    private static void welcome(ServerPlayer player) {
        CompoundTag data = data(player);
        data.putBoolean("given", true);
        ItemStack key = new ItemStack(Realm.CYCLE_KEY.get());
        if (!player.getInventory().add(key)) {
            player.drop(key, false);
        }
        rez(player, false);
        player.sendSystemMessage(Component.translatable("message.redstoneplus.cycle_welcome.0").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.redstoneplus.cycle_welcome.1").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.translatable("message.redstoneplus.cycle_welcome.2").withStyle(ChatFormatting.YELLOW));
    }

    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOST.remove(player.getUUID());
        }
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        LOST.remove(event.getEntity().getUUID());
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOST.remove(event.getEntity().getUUID());
    }

    /** A rider on the grav-lift never suffocates in the rock it glides past, and the cycle takes the fall for them. */
    private static void riderSafety(LivingAttackEvent event) {
        if (event.getEntity().getVehicle() instanceof LightCycle
                && (event.getSource().is(DamageTypes.IN_WALL) || event.getSource().is(DamageTypes.FALL) || event.getSource().is(DamageTypes.FLY_INTO_WALL))) {
            event.setCanceled(true);
        }
    }

    // ============================================================================================ the key
    /** The Cycle Key: use to call your light cycle and ride it; sneak-use to switch its automatic return on or off. */
    public static class CycleKey extends Item {
        public CycleKey(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(player instanceof ServerPlayer sp)) {
                return InteractionResultHolder.success(stack);
            }
            if (player.isSecondaryUseActive()) {
                boolean auto = !autoReturn(player);
                data(player).putBoolean("manual", !auto);
                player.displayClientMessage(Component.translatable(auto ? "message.redstoneplus.cycle_auto_on" : "message.redstoneplus.cycle_auto_off")
                        .withStyle(ChatFormatting.GOLD), true);
                return InteractionResultHolder.success(stack);
            }
            if (player.getVehicle() instanceof LightCycle) {
                return InteractionResultHolder.pass(stack);
            }
            data(player).putBoolean("given", true);
            LightCycle cycle = find(sp);
            if (cycle != null && cycle.level() == level && cycle.distanceToSqr(player) < 8 * 8 && !cycle.isVehicle()) {
                player.startRiding(cycle);
            } else {
                rez(sp, true);
            }
            player.getCooldowns().addCooldown(this, 30);
            return InteractionResultHolder.success(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            for (int i = 0; i < 3; i++) {
                tooltip.add(Component.translatable("item.redstoneplus.cycle_key.tip." + i).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
