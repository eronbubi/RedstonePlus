package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
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
import net.minecraft.world.phys.AABB;
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
 * A bar at the top of the screen counts the broken rules while you are in the realm, and warns before the Great Bell
 * tolls. From {@link #CONDEMNED} on, every creature hunts you. Three rites set things right: relight a dead lamp with
 * redstone (one rule; each lamp only once, and not more than once a minute), ring a bell while the Great Bell tolls (two
 * rules), or lay an Etched Plate on Bell Bronze (all of them). Atoning calls off the creatures that were hunting you.
 * The realm forgets the dead: dying clears the count (coming back from the End does not).
 * <p>
 * The things walled into the Sealed Reach never kept the Concordance: they hunt everyone, and striking them is no crime.
 */
public final class RealmRules {
    public static final int MAX = 5;
    public static final int CONDEMNED = 3;
    /** Kept under Forge's PlayerPersisted tag, so it survives respawning and the trip back from the End. */
    private static final String PERSISTED = "PlayerPersisted";
    private static final String DATA = "redstoneplus_concordance";
    private static final String BROKEN = "broken";
    private static final String ANSWERED = "bell_answered";
    private static final String RELIT = "relit_lamps";
    private static final String RELIT_AT = "relit_at";
    private static final String WELCOMED = "welcomed";
    /** Older versions kept the count straight in the player's data. */
    private static final String LEGACY_BROKEN = "redstoneplus_rules_broken";
    private static final String PROVOKED = "redstoneplus_provoked_until";
    private static final String COOLDOWN = "redstoneplus_rule_cooldown";
    /** How many relit lamps a player's record remembers. */
    private static final int RELIT_MEMORY = 48;
    private static final int RELIT_COOLDOWN = 1200;
    /** Ticks before a toll during which the bar warns. */
    private static final int TOLL_WARNING = 100;
    /** Ticks after a toll during which moving breaks the third rule. */
    static final int TOLL_STILL = 60;

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
        MinecraftForge.EVENT_BUS.addListener(RealmRules::respawn);
        MinecraftForge.EVENT_BUS.addListener(RealmRules::changedDimension);
    }

    static boolean inRealm(Level level) {
        return level.dimension().equals(Realm.REALM);
    }

    /** This player's record with the Concordance (created on first use, carried over from older versions). */
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
        if (root.contains(LEGACY_BROKEN)) {
            data.putInt(BROKEN, Math.max(data.getInt(BROKEN), root.getInt(LEGACY_BROKEN)));
            root.remove(LEGACY_BROKEN);
        }
        return data;
    }

    public static int broken(Player player) {
        return data(player).getInt(BROKEN);
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

    /** The things walled into the Sealed Reach: the Concordance does not bind them, nor protect them. */
    static boolean lawless(Entity entity) {
        return entity instanceof SealedReach.Lawless;
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
        data(player).putInt(BROKEN, after);
        player.level().playSound(null, player.blockPosition(), RealmSounds.GREAT_BELL.get(), SoundSource.AMBIENT, 0.6F, 1.6F);
        player.displayClientMessage(Component.translatable("rules.redstoneplus." + rule).withStyle(ChatFormatting.RED), true);
        if (before < CONDEMNED && after >= CONDEMNED) {
            player.level().playSound(null, player.blockPosition(), RealmSounds.GREAT_BELL.get(), SoundSource.AMBIENT, 2.0F, 0.6F);
            player.sendSystemMessage(Component.translatable("rules.redstoneplus.condemned").withStyle(ChatFormatting.DARK_RED));
        }
        updateBar(player);
    }

    private static void forgive(ServerPlayer player, int count) {
        int before = broken(player);
        int after = Math.max(0, before - count);
        data(player).putInt(BROKEN, after);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 30, 0.6, 0.8, 0.6, 0.05);
        player.displayClientMessage(Component.translatable(after == 0 ? "rules.redstoneplus.forgiven_all" : "rules.redstoneplus.forgiven", after, MAX)
                .withStyle(ChatFormatting.GOLD), true);
        // whoever was only hunting because of the broken rules lets go
        player.getPersistentData().remove(PROVOKED);
        if (after < CONDEMNED) {
            calm(player);
        }
        updateBar(player);
    }

    /** The realm is freed: every creature of the realm that was hunting someone lets go. */
    static void peace(ServerLevel level) {
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob mob && realmCreature(mob) && !(mob instanceof Echoes.EchoBoss) && mob.getTarget() instanceof Player) {
                mob.setTarget(null);
                mob.setLastHurtByMob(null);
                mob.getNavigation().stop();
            }
        }
    }

    /** Realm creatures around the player that were hunting them stop, unless the player is still fighting them. */
    private static void calm(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(64.0);
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, area, m -> m.getTarget() == player && realmCreature(m) && !lawless(m))) {
            if (mob.getLastHurtByMob() == player && mob.tickCount - mob.getLastHurtByMobTimestamp() < 100) {
                continue;
            }
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
            mob.getNavigation().stop();
        }
    }

    /** Realm creatures only take a player as their target if the player has broken the rules or just provoked them. */
    private static void changeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof Player player) || !inRealm(event.getEntity().level()) || !realmCreature(event.getEntity())
                || event.getEntity() instanceof Echoes.EchoBoss || event.getEntity().getTags().contains(Echoes.CALLED)) {
            return;
        }
        if (!RealmStory.healed(player.level()) && lawless(event.getEntity())) {
            return;
        }
        if (RealmStory.healed(player.level())) {
            // the freed realm is at peace: its creatures only ever turn on someone who is hurting them right now
            LivingEntity self = event.getEntity();
            if (self.getLastHurtByMob() != player || self.tickCount - self.getLastHurtByMobTimestamp() > 100) {
                event.setCanceled(true);
            }
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
                || !realmCreature(event.getEntity()) || lawless(event.getEntity()) || RealmStory.healed(player.level())) {
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
            "kiln_turret", "volley_launcher", "tripper_rail", "lightline", "grid_beacon", "quarantine_plating");

    /** True for the blocks the second rule protects. */
    public static boolean built(BlockState state) {
        var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        boolean ours = key != null && key.getNamespace().equals("redstoneplus") && BUILT.contains(key.getPath());
        return ours || state.is(ModRegistry.INSTANT_LAMP.get()) || state.is(Blocks.BELL);
    }

    /** Rule 2: do not break what the Wirewrights built. */
    private static void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !playing(player) || !(event.getLevel() instanceof Level level) || !inRealm(level)
                || RealmStory.healed(level)) {
            return;
        }
        if (built(event.getState())) {
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

    /** Ticks until the Great Bell next tolls (0 on the toll itself). */
    public static int ticksToToll(long gameTime) {
        return (int) Math.floorMod(-gameTime, (long) RealmBell.INTERVAL);
    }

    /** Ticks since the Great Bell last tolled. */
    public static int ticksSinceToll(long gameTime) {
        return (int) Math.floorMod(gameTime, (long) RealmBell.INTERVAL);
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
        // relight a dead lamp with redstone: one rule forgiven (each lamp once, at most once a minute)
        if (state.is(ModRegistry.INSTANT_LAMP.get()) && !state.getValue(SimpleBlocks.Lamp.LIT) && held.is(Items.REDSTONE)) {
            level.setBlock(pos, state.setValue(SimpleBlocks.Lamp.LIT, true), 2);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.2);
            if (!player.isCreative()) {
                held.shrink(1);
            }
            if (broken(player) > 0) {
                if (relitBefore(player, pos)) {
                    player.displayClientMessage(Component.translatable("rules.redstoneplus.lamp_known").withStyle(ChatFormatting.GRAY), true);
                } else if (data(player).getLong(RELIT_AT) > level.getGameTime()) {
                    player.displayClientMessage(Component.translatable("rules.redstoneplus.lamp_wait").withStyle(ChatFormatting.GRAY), true);
                } else {
                    rememberRelit(player, pos, level.getGameTime());
                    forgive(player, 1);
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // answer the Great Bell: ring a bell while it tolls, two rules forgiven (once per toll)
        if (state.getBlock() instanceof BellBlock) {
            long sinceToll = ticksSinceToll(level.getGameTime());
            long toll = level.getGameTime() - sinceToll;
            if (sinceToll <= 100 && data(player).getLong(ANSWERED) != toll && broken(player) > 0) {
                data(player).putLong(ANSWERED, toll);
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

    private static boolean relitBefore(Player player, BlockPos pos) {
        long key = pos.asLong();
        for (long l : data(player).getLongArray(RELIT)) {
            if (l == key) {
                return true;
            }
        }
        return false;
    }

    private static void rememberRelit(Player player, BlockPos pos, long now) {
        CompoundTag data = data(player);
        long[] old = data.getLongArray(RELIT);
        int keep = Math.min(old.length, RELIT_MEMORY - 1);
        long[] list = new long[keep + 1];
        System.arraycopy(old, old.length - keep, list, 0, keep);
        list[keep] = pos.asLong();
        data.put(RELIT, new LongArrayTag(list));
        data.putLong(RELIT_AT, now + RELIT_COOLDOWN);
    }

    // ================================================================================================ the bar
    private static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        Vec3 atToll = AT_TOLL.get(player.getUUID());
        if (atToll != null) {
            long since = ticksSinceToll(player.level().getGameTime());
            if (!inRealm(player.level()) || since > TOLL_STILL || !player.isAlive()) {
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
        if (RealmStory.healed(player.level())) {
            // the Concordance rests: no rules, no tolls, nobody hunted
            bar.setName(Component.translatable("story.redstoneplus.bar.healed"));
            bar.setColor(BossEvent.BossBarColor.YELLOW);
            bar.setProgress(1.0F);
            bar.addPlayer(player);
            return;
        }
        int n = broken(player);
        long time = player.level().getGameTime();
        int toToll = ticksToToll(time);
        int sinceToll = ticksSinceToll(time);
        Component name;
        if (sinceToll <= TOLL_STILL) {
            name = Component.translatable("rules.redstoneplus.bar.tolling");
        } else if (toToll <= TOLL_WARNING) {
            name = Component.translatable("rules.redstoneplus.bar.toll_soon", (toToll + 19) / 20);
        } else {
            String key = n == 0 ? "rules.redstoneplus.bar.harmony" : n >= CONDEMNED ? "rules.redstoneplus.bar.condemned" : "rules.redstoneplus.bar.broken";
            name = Component.translatable(key, n, MAX);
        }
        bar.setName(name);
        bar.setColor(n == 0 ? BossEvent.BossBarColor.WHITE : n >= CONDEMNED ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        bar.setProgress(1.0F - n / (float) MAX);
        bar.addPlayer(player);
        CompoundTag data = data(player);
        if (!data.getBoolean(WELCOMED) && playing(player)) {
            data.putBoolean(WELCOMED, true);
            player.sendSystemMessage(Component.translatable("rules.redstoneplus.welcome").withStyle(ChatFormatting.GOLD));
        }
    }

    /** A new player object after death or the End: the old one leaves the bar, and the dead are forgiven. */
    private static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerBossEvent bar = BARS.get(player.getUUID());
        if (bar != null) {
            bar.removeAllPlayers(); // the old player object would otherwise keep the bar on screen
        }
        AT_TOLL.remove(player.getUUID());
        if (!event.isEndConquered()) {
            data(player).putInt(BROKEN, 0); // the realm forgets the dead
            player.getPersistentData().remove(PROVOKED);
        }
        updateBar(player);
    }

    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AT_TOLL.remove(player.getUUID());
            updateBar(player);
        }
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerBossEvent bar = BARS.remove(event.getEntity().getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
        }
        AT_TOLL.remove(event.getEntity().getUUID());
    }

    /** True if the realm's creatures will hunt this player on sight. */
    public static boolean condemned(LivingEntity entity) {
        return entity instanceof Player player && broken(player) >= CONDEMNED;
    }
}
