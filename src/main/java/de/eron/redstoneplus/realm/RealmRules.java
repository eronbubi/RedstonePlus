package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Concordance: the rules the realm still keeps. Its creatures leave visitors alone until they break them.
 * <ol>
 *     <li>Do not strike first.</li>
 *     <li>Do not break what the Wirewrights built.</li>
 *     <li>Be still when the Great Bell tolls.</li>
 * </ol>
 * A bar at the top of the screen counts the broken rules while you are in the realm. From {@link #CONDEMNED} on, every
 * creature hunts you. Three rites set things right: relight a dead lamp with redstone (one rule), ring a bell while the
 * Great Bell tolls (two rules), or lay an Etched Plate on Bell Bronze (all of them). The realm forgets the dead.
 */
public final class RealmRules {
    public static final int MAX = 5;
    public static final int CONDEMNED = 3;
    private static final String BROKEN = "redstoneplus_rules_broken";
    private static final String PROVOKED = "redstoneplus_provoked_until";
    private static final String COOLDOWN = "redstoneplus_rule_cooldown";
    private static final String ANSWERED = "redstoneplus_bell_answered";

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    /** Where each player stood when the Great Bell last tolled. */
    private static final Map<UUID, Vec3> AT_TOLL = new HashMap<>();

    private RealmRules() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(RealmRules::changeTarget);
        MinecraftForge.EVENT_BUS.addListener(RealmRules::attack);
        MinecraftForge.EVENT_BUS.addListener(RealmRules::breakBlock);
        MinecraftForge.EVENT_BUS.addListener(RealmRules::rite);
        MinecraftForge.EVENT_BUS.addListener(RealmRules::playerTick);
        MinecraftForge.EVENT_BUS.addListener(RealmRules::logout);
    }

    static boolean inRealm(Level level) {
        return level.dimension().equals(Realm.REALM);
    }

    public static int broken(Player player) {
        return player.getPersistentData().getInt(BROKEN);
    }

    private static boolean playing(Player player) {
        return !player.isCreative() && !player.isSpectator();
    }

    /** A creature of the realm: anything this mod registers for it. */
    static boolean realmCreature(Entity entity) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null || !key.getNamespace().equals(Realm.id("x").getNamespace())) {
            return false;
        }
        for (var entry : Realm.ENTITIES.getEntries()) {
            if (entry.getId().equals(key)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================================================ the rules
    private static void breakRule(ServerPlayer player, String rule) {
        long now = player.level().getGameTime();
        if (player.getPersistentData().getLong(COOLDOWN) > now) {
            return; // one breach counts once
        }
        player.getPersistentData().putLong(COOLDOWN, now + 60);
        int before = broken(player);
        int after = Math.min(MAX, before + 1);
        player.getPersistentData().putInt(BROKEN, after);
        player.level().playSound(null, player.blockPosition(), RealmSounds.GREAT_BELL.get(), SoundSource.AMBIENT, 0.6F, 1.6F);
        player.displayClientMessage(Component.translatable("rules.redstoneplus." + rule).withStyle(ChatFormatting.RED), true);
        if (before < CONDEMNED && after >= CONDEMNED) {
            player.level().playSound(null, player.blockPosition(), RealmSounds.GREAT_BELL.get(), SoundSource.AMBIENT, 2.0F, 0.6F);
        }
        updateBar(player);
    }

    private static void forgive(ServerPlayer player, int count) {
        int after = Math.max(0, broken(player) - count);
        player.getPersistentData().putInt(BROKEN, after);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 30, 0.6, 0.8, 0.6, 0.05);
        updateBar(player);
    }

    /** Realm creatures only take a player as their target if the player has broken the rules or just provoked them. */
    private static void changeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof Player player) || !inRealm(event.getEntity().level()) || !realmCreature(event.getEntity())) {
            return;
        }
        if (broken(player) >= CONDEMNED || player.getPersistentData().getLong(PROVOKED) > player.level().getGameTime()
                || event.getEntity().getLastHurtByMob() == player) {
            return;
        }
        event.setCanceled(true);
    }

    /** Rule 1: do not strike first. Striking also sets the creatures around you on you for a while. */
    private static void attack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !playing(player) || !inRealm(player.level())
                || !realmCreature(event.getEntity())) {
            return;
        }
        boolean defending = event.getEntity() instanceof Mob mob && mob.getTarget() == player;
        player.getPersistentData().putLong(PROVOKED, player.level().getGameTime() + 200);
        if (!defending) {
            breakRule(player, "strike_first");
        }
    }

    private static final Set<String> BUILT = Set.of("realmstone_bricks", "cracked_realmstone_bricks", "chiseled_realmstone_bricks",
            "wirewright_tiles", "bell_bronze", "rust_plating", "karst_bricks", "crusher", "hazard_switch", "lockdown_gate", "floodgate",
            "kiln_turret", "volley_launcher", "tripper_rail");

    /** Rule 2: do not break what the Wirewrights built. */
    private static void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !playing(player) || !(event.getLevel() instanceof Level level) || !inRealm(level)) {
            return;
        }
        BlockState state = event.getState();
        var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        boolean built = key != null && key.getNamespace().equals("redstoneplus") && BUILT.contains(key.getPath());
        if (built || state.is(ModRegistry.INSTANT_LAMP.get()) || state.is(Blocks.BELL)) {
            breakRule(player, "break_built");
        }
    }

    /** Rule 3: be still when the Great Bell tolls. Called by {@link RealmBell} on every toll. */
    static void toll(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (playing(player)) {
                AT_TOLL.put(player.getUUID(), player.position());
            }
        }
    }

    // ================================================================================================ the rites
    private static void rite(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inRealm(player.level())) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack held = event.getItemStack();
        // relight a dead lamp with redstone: one rule forgiven
        if (state.is(ModRegistry.INSTANT_LAMP.get()) && !state.getValue(SimpleBlocks.Lamp.LIT) && held.is(Items.REDSTONE)) {
            level.setBlock(pos, state.setValue(SimpleBlocks.Lamp.LIT, true), 2);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.2);
            if (!player.isCreative()) {
                held.shrink(1);
            }
            if (broken(player) > 0) {
                forgive(player, 1);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // answer the Great Bell: ring a bell while it tolls, two rules forgiven (once per toll)
        if (state.getBlock() instanceof BellBlock) {
            long sinceToll = Math.floorMod(level.getGameTime(), RealmBell.INTERVAL);
            long toll = level.getGameTime() - sinceToll;
            if (sinceToll <= 100 && player.getPersistentData().getLong(ANSWERED) != toll && broken(player) > 0) {
                player.getPersistentData().putLong(ANSWERED, toll);
                forgive(player, 2);
            }
            return;
        }
        // an Etched Plate laid on Bell Bronze: the realm forgives everything
        if (state.is(Realm.BELL_BRONZE.get()) && held.getItem().getDescriptionId().contains("etched_plate")) {
            if (broken(player) > 0) {
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                forgive(player, MAX);
                level.sendParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    // ================================================================================================ the bar
    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        Vec3 atToll = AT_TOLL.get(player.getUUID());
        if (atToll != null) {
            long since = Math.floorMod(player.level().getGameTime(), RealmBell.INTERVAL);
            if (!inRealm(player.level()) || since > 60) {
                AT_TOLL.remove(player.getUUID());
            } else if (player.position().distanceToSqr(atToll) > 2.0 * 2.0) {
                AT_TOLL.remove(player.getUUID());
                breakRule(player, "toll_still");
            }
        }
        if (player.tickCount % 20 == 0) {
            updateBar(player);
        }
    }

    private static void updateBar(ServerPlayer player) {
        ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(),
                id -> new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_6));
        if (!inRealm(player.level()) || player.isSpectator()) {
            bar.removePlayer(player);
            return;
        }
        int n = broken(player);
        String key = n == 0 ? "rules.redstoneplus.bar.harmony" : n >= CONDEMNED ? "rules.redstoneplus.bar.condemned" : "rules.redstoneplus.bar.broken";
        bar.setName(Component.translatable(key, n, MAX));
        bar.setColor(n == 0 ? BossEvent.BossBarColor.WHITE : n >= CONDEMNED ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        bar.setProgress(1.0F - n / (float) MAX);
        bar.addPlayer(player);
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerBossEvent bar = BARS.remove(event.getEntity().getUUID());
        if (bar != null && event.getEntity() instanceof ServerPlayer player) {
            bar.removePlayer(player);
        }
        AT_TOLL.remove(event.getEntity().getUUID());
    }

    /** True if the realm's creatures will hunt this player on sight. */
    public static boolean condemned(LivingEntity entity) {
        return entity instanceof Player player && broken(player) >= CONDEMNED;
    }
}
