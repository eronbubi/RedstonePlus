package de.eron.redstoneplus.realm;

import de.eron.redstoneplus.block.SimpleBlocks;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * The story of the realm.
 * <p>
 * When the Great Bell tore free on the Overtoll, its five voices broke away from it: the <b>Echoes</b>, godlike beings of
 * redstone energy, each holding one power of the realm (force, signal, resonance, heat and flow) and one of the great
 * chains that keep the Bell tolling over a world that cannot move on. Each Echo keeps a sanctum (see {@link Sanctums});
 * a giant chain rises from it into the sky, to the Bell. Defeat an Echo and its chain breaks: in the world, and in the sky.
 * <p>
 * With all five chains broken, the cores of the Echoes forge the Heart of the Five. Laid on the bronze in the crater of
 * a Foundry City's empty Cradle, it calls the Bell down: the Overtoll, the last fight. When it falls, the realm is freed.
 * A red sun rises where the Bell hung, the light grows warm and bright, the creatures bloom and leave you in peace, and
 * around every visitor the land heals: blight turns to heather, the giant chains fall, dead lamps light, broken bricks
 * mend and dawn lilies grow. It stays red, and its past stays in it, but it is on its way to being a redstone society again.
 * <p>
 * The story is kept for the whole world, and sent to every client so the sky, the light and the creatures look right.
 */
public final class RealmStory extends SavedData {
    private static final String NAME = "redstoneplus_realm_story";

    /** The five Echoes, the powers they hold, and the colour of their light. */
    public enum Echo {
        FORCE(0xFF3A1A), SIGNAL(0xFF1A10), RESONANCE(0xFFB040), HEAT(0xFF7A10), FLOW(0xFF4A20);

        public final int color;

        Echo(int color) {
            this.color = color;
        }

        public String id() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }

        public int bit() {
            return 1 << this.ordinal();
        }
    }

    public static final int ALL = (1 << Echo.values().length) - 1;

    private int conquered;
    private boolean healed;
    private long healedAt;
    /** Sanctums whose chain has already come down, so it is not built again. */
    private final Set<Long> silentSeats = new HashSet<>();

    private RealmStory() {
    }

    private static RealmStory load(CompoundTag tag, HolderLookup.Provider registries) {
        RealmStory story = new RealmStory();
        story.conquered = tag.getInt("conquered");
        story.healed = tag.getBoolean("healed");
        story.healedAt = tag.getLong("healed_at");
        for (long l : tag.getLongArray("silent_seats")) {
            story.silentSeats.add(l);
        }
        return story;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("conquered", this.conquered);
        tag.putBoolean("healed", this.healed);
        tag.putLong("healed_at", this.healedAt);
        tag.put("silent_seats", new LongArrayTag(this.silentSeats.stream().mapToLong(Long::longValue).toArray()));
        return tag;
    }

    /** The story of this world (kept with the Overworld's data, so it exists whether the realm is loaded or not). */
    public static RealmStory of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RealmStory::new, RealmStory::load, null), NAME);
    }

    // ============================================================================================ asking the story
    /** Client side: what the server last told us. */
    private static int clientConquered;
    private static boolean clientHealed;

    public static int conquered(@Nullable Level level) {
        if (level instanceof ServerLevel server) {
            return of(server.getServer()).conquered;
        }
        return level == null ? 0 : clientConquered;
    }

    public static boolean conquered(@Nullable Level level, Echo echo) {
        return (conquered(level) & echo.bit()) != 0;
    }

    /** True once the Overtoll has fallen and the realm heals. */
    public static boolean healed(@Nullable Level level) {
        if (level instanceof ServerLevel server) {
            return of(server.getServer()).healed;
        }
        return level != null && clientHealed;
    }

    /** True on the client once the realm heals (for renderers that have no level at hand). */
    public static boolean clientHealed() {
        return clientHealed;
    }

    public static int clientConquered() {
        return clientConquered;
    }

    /*
     * World generation runs on worker threads, so it does not read the saved data itself: it reads this copy, made when
     * the server starts and kept up to date as the story moves on.
     */
    private static final Set<Long> SILENT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile boolean healedNow;
    @Nullable
    private static volatile MinecraftServer snapshotOf;

    private static void snapshot(MinecraftServer server) {
        if (snapshotOf == server) {
            return;
        }
        synchronized (RealmStory.class) {
            if (snapshotOf != server) {
                RealmStory story = of(server);
                SILENT.clear();
                SILENT.addAll(story.silentSeats);
                healedNow = story.healed;
                snapshotOf = server;
            }
        }
    }

    /** True if the chain of the sanctum at this seat has come down (safe to ask from world generation). */
    static boolean seatSilent(MinecraftServer server, BlockPos seat) {
        snapshot(server);
        return healedNow || SILENT.contains(BlockPos.asLong(seat.getX(), 0, seat.getZ()));
    }

    /** True once the realm is free (safe to ask from world generation): no new chains are built then. */
    static boolean healedForWorldgen(MinecraftServer server) {
        snapshot(server);
        return healedNow;
    }

    // ============================================================================================ telling the story
    /** An Echo has fallen at its seat: its chain breaks, everybody in the realm hears it, and the story moves on. */
    static void conquer(ServerLevel level, Echo echo, BlockPos seat) {
        MinecraftServer server = level.getServer();
        RealmStory story = of(server);
        boolean first = (story.conquered & echo.bit()) == 0;
        story.conquered |= echo.bit();
        story.silentSeats.add(BlockPos.asLong(seat.getX(), 0, seat.getZ()));
        SILENT.add(BlockPos.asLong(seat.getX(), 0, seat.getZ()));
        story.setDirty();
        Sanctums.breakChain(level, seat);
        int left = Echo.values().length - Integer.bitCount(story.conquered);
        for (ServerPlayer p : level.players()) {
            p.playNotifySound(RealmSounds.CHAIN_BREAK.get(), SoundSource.AMBIENT, 2.0F, 0.8F);
            title(p, Component.translatable("story.redstoneplus.chain_broken.title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("story.redstoneplus.echo." + echo.id()).withStyle(ChatFormatting.RED));
            if (first) {
                p.sendSystemMessage(Component.translatable(left == 0 ? "story.redstoneplus.all_broken" : "story.redstoneplus.chains_left", left)
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        sync(server);
    }

    /** The Overtoll has fallen: the realm is free. */
    static void victory(ServerLevel level) {
        MinecraftServer server = level.getServer();
        RealmStory story = of(server);
        if (story.healed) {
            return;
        }
        story.healed = true;
        story.conquered = ALL;
        story.healedAt = level.getGameTime();
        healedNow = true;
        story.setDirty();
        RealmRules.peace(level);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() == level) {
                p.playNotifySound(RealmSounds.REALM_FREED.get(), SoundSource.MUSIC, 1.0F, 1.0F);
                title(p, Component.translatable("story.redstoneplus.freed.title").withStyle(ChatFormatting.GOLD),
                        Component.translatable("story.redstoneplus.freed.subtitle").withStyle(ChatFormatting.YELLOW));
            }
            p.sendSystemMessage(Component.translatable("story.redstoneplus.freed.message").withStyle(ChatFormatting.GOLD));
        }
        sync(server);
    }

    private static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 30));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    // ============================================================================================ the healing
    /**
     * Once the realm is free it heals, around every visitor: each tick a few columns near each player in the realm mend.
     * Blight turns to heather, rubble and slag to clay and turf, cracked bricks mend, dead lamps light, the giant chains
     * fall link by link, the quarantine gates open, and dawn lilies and heather grow on the open ground.
     */
    private static void heal(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int i = 0; i < 24; i++) {
            int x = player.getBlockX() + random.nextInt(97) - 48;
            int z = player.getBlockZ() + random.nextInt(97) - 48;
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos topPos = new BlockPos(x, top, z);
            if (level.getBlockState(topPos).is(Realm.CHAIN_LINK.get())) {
                Sanctums.dropChainColumn(level, x, z, top);
                continue;
            }
            for (int y = top + 1; y >= top - 2; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState healedState = healedState(level, pos, level.getBlockState(pos), random);
                if (healedState != null) {
                    level.setBlock(pos, healedState, Block.UPDATE_ALL);
                    if (random.nextInt(3) == 0) {
                        level.sendParticles(ParticleTypes.WAX_ON, x + 0.5, y + 1.1, z + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
                    }
                }
            }
        }
    }

    /** What a block becomes as the realm heals, or null if it stays as it is. */
    @Nullable
    private static BlockState healedState(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.is(Realm.BLIGHT_CRUST.get()) || state.is(Realm.RUSTED_SOIL.get())) {
            return Realm.HEATHER_TURF.get().defaultBlockState();
        }
        if (state.is(Realm.SLAG.get())) {
            return Realm.RED_CLAY.get().defaultBlockState();
        }
        if (state.is(Realm.CRACKED_REALMSTONE_BRICKS.get())) {
            return Realm.REALMSTONE_BRICKS.get().defaultBlockState();
        }
        if (state.is(ModRegistry.INSTANT_LAMP.get()) && !state.getValue(SimpleBlocks.Lamp.LIT)) {
            return state.setValue(SimpleBlocks.Lamp.LIT, true);
        }
        if (state.is(Realm.BRIAR_THORNS.get())) {
            return Realm.CRIMSON_HEATHER.get().defaultBlockState();
        }
        if (state.is(Realm.LOCKDOWN_GATE.get()) && level.getBiome(pos).is(SealedReach.BIOME) || state.is(Realm.CHAIN_LINK.get())) {
            return Blocks.AIR.defaultBlockState();
        }
        if (state.isAir() && random.nextInt(5) == 0) {
            BlockState below = level.getBlockState(pos.below());
            if (below.is(Realm.HEATHER_TURF.get()) || below.is(Realm.GROVE_MOSS.get()) || below.is(Realm.RED_CLAY.get())
                    || below.is(Realm.CANAL_MOSS.get()) || below.is(Realm.ROOT_SOIL.get()) || below.is(Realm.BRIAR_SOIL.get())) {
                return random.nextInt(3) == 0 ? Realm.CRIMSON_HEATHER.get().defaultBlockState() : Realm.DAWN_LILY.get().defaultBlockState();
            }
        }
        return null;
    }

    private static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !level.dimension().equals(Realm.REALM)) {
            return;
        }
        if (!of(level.getServer()).healed) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                heal(level, player, level.getRandom());
            }
        }
    }

    // ============================================================================================ telling the clients
    private static final SimpleChannel CHANNEL = ChannelBuilder.named(Realm.id("story")).networkProtocolVersion(1)
            .optional().simpleChannel();

    /** What a client needs to know of the story: which chains are broken, and whether the realm is free. */
    public record StoryPacket(int conquered, boolean healed) {
        static void encode(StoryPacket packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.conquered);
            buf.writeBoolean(packet.healed);
        }

        static StoryPacket decode(FriendlyByteBuf buf) {
            return new StoryPacket(buf.readVarInt(), buf.readBoolean());
        }
    }

    static {
        CHANNEL.messageBuilder(StoryPacket.class, 0).encoder(StoryPacket::encode).decoder(StoryPacket::decode)
                .consumerMainThread((packet, context) -> {
                    clientConquered = packet.conquered();
                    clientHealed = packet.healed();
                }).add();
    }

    private static void sync(MinecraftServer server) {
        RealmStory story = of(server);
        CHANNEL.send(new StoryPacket(story.conquered, story.healed), PacketDistributor.ALL.noArg());
    }

    private static void sync(ServerPlayer player) {
        RealmStory story = of(player.getServer());
        CHANNEL.send(new StoryPacket(story.conquered, story.healed), PacketDistributor.PLAYER.with(player));
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(RealmStory::levelTick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent event) -> snapshot(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event) -> {
            snapshotOf = null;
            SILENT.clear();
            healedNow = false;
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer p) {
                sync(p);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer p) {
                sync(p);
            }
        });
    }

    /** Called by the client when it leaves a world: it forgets that world's story. */
    public static void clientForget() {
        clientConquered = 0;
        clientHealed = false;
    }
}
