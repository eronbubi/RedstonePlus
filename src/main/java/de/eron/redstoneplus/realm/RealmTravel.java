package de.eron.redstoneplus.realm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/** Moving players between their world and the Redstone Realm. */
public final class RealmTravel {
    private static final String RETURN = "redstoneplus_realm_return";
    private static final String VISITED = "redstoneplus_realm_visited";
    private static final int GUIDE_PAGES = 14;

    private RealmTravel() {
    }

    public static void travel(ServerPlayer player) {
        MinecraftServer server = player.server;
        player.stopRiding();
        CompoundTag data = player.getPersistentData();
        if (player.level().dimension() == Realm.REALM) {
            ServerLevel target = server.overworld();
            BlockPos back = null;
            if (data.contains(RETURN)) {
                CompoundTag ret = data.getCompound(RETURN);
                ServerLevel saved = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(ret.getString("dim"))));
                if (saved != null) {
                    target = saved;
                    back = BlockPos.of(ret.getLong("pos"));
                }
            }
            if (back == null) {
                BlockPos spawn = target.getSharedSpawnPos();
                back = target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            }
            player.teleportTo(target, back.getX() + 0.5, back.getY(), back.getZ() + 0.5, player.getYRot(), player.getXRot());
            arrived(player, target);
            player.displayClientMessage(Component.translatable("message.redstoneplus.realm_leave"), true);
            return;
        }
        ServerLevel realm = server.getLevel(Realm.REALM);
        if (realm == null) {
            player.displayClientMessage(Component.translatable("message.redstoneplus.realm_missing"), false);
            return;
        }
        CompoundTag ret = new CompoundTag();
        ret.putString("dim", player.level().dimension().location().toString());
        ret.putLong("pos", player.blockPosition().asLong());
        data.put(RETURN, ret);

        BlockPos arrival = arrival(realm, player.getBlockX(), player.getBlockZ());
        player.teleportTo(realm, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, 180.0F, 0.0F);
        arrived(player, realm);
        if (!data.getBoolean(VISITED)) {
            data.putBoolean(VISITED, true);
            player.getInventory().add(guideBook());
            player.displayClientMessage(Component.translatable("message.redstoneplus.realm_first"), false);
        } else {
            player.displayClientMessage(Component.translatable("message.redstoneplus.realm_enter"), true);
        }
    }

    private static void arrived(ServerPlayer player, Level level) {
        level.playSound(null, player.blockPosition(), RealmSounds.REALM_TRAVEL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Where a player lands. If a return gate is already close, next to it; otherwise the arrival station is built
     * there: a platform with the return gate, a starter chest and every example circuit.
     */
    static BlockPos arrival(ServerLevel realm, int x, int z) {
        realm.getChunk(x >> 4, z >> 4);
        BlockPos existing = findGate(realm, x, z);
        if (existing != null) {
            return existing.offset(-2, 0, 0);
        }
        int y = realm.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        y = Math.max(y, realm.getSeaLevel() + 1);
        y = Math.min(y, realm.getMaxBuildHeight() - 12);
        BlockPos origin = new BlockPos(x, y, z);
        RealmFeatures.Build b = new RealmFeatures.Build(realm, origin, Rotation.NONE, realm.getRandom(), false);
        station(b);
        return origin.offset(0, 0, 3);
    }

    private static BlockPos findGate(ServerLevel realm, int x, int z) {
        int surface = Math.max(realm.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), realm.getSeaLevel() + 1);
        for (BlockPos pos : BlockPos.betweenClosed(x - 16, surface - 24, z - 16, x + 16, surface + 24, z + 16)) {
            if (realm.getBlockState(pos).is(Realm.REALM_GATE.get())) {
                return pos.immutable();
            }
        }
        return null;
    }

    /** The arrival station: platform, return gate, starter chest and all example circuits with their signs. */
    static void station(RealmFeatures.Build b) {
        BlockState floor = Realm.KARST_BRICKS.get().defaultBlockState();
        BlockState edge = Realm.LICHEN_KARST.get().defaultBlockState();
        b.foundation(-13, -10, 13, 5, floor, Realm.KARST_LIMESTONE.get().defaultBlockState(), 5);
        for (int x = -13; x <= 13; x++) {
            b.set(x, -1, -10, edge);
            b.set(x, -1, 5, edge);
        }
        for (int z = -10; z <= 5; z++) {
            b.set(-13, -1, z, edge);
            b.set(13, -1, z, edge);
        }
        // the return gate sits on a redstone block, so it is always powered
        b.set(2, -1, 3, Blocks.REDSTONE_BLOCK);
        b.set(2, 0, 3, Realm.REALM_GATE.get());
        b.sign(3, 0, 4, Direction.SOUTH, "station_gate");
        b.chest(-2, 0, 3, Direction.SOUTH, "realm_arrival");
        b.sign(-3, 0, 4, Direction.SOUTH, "station_kit");
        // lamp posts on the corners, lit by player detectors
        for (int x : new int[]{-12, 12}) {
            for (int z : new int[]{-9, 4}) {
                b.set(x, 0, z, Blocks.DARK_OAK_FENCE);
                b.set(x, 1, z, de.eron.redstoneplus.registry.ModRegistry.PLAYER_DETECTOR.get());
                b.set(x, 2, z, de.eron.redstoneplus.registry.ModRegistry.INSTANT_LAMP.get());
            }
        }
        Exhibits.station(b);
    }

    /** "Redstone Realm Field Guide": pages are translation keys, so it reads in the player's language. */
    static ItemStack guideBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 0; i < GUIDE_PAGES; i++) {
            pages.add(Filterable.passThrough(Component.translatable("book.redstoneplus.realm_guide." + i)));
        }
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Redstone Realm"), "RedstonePlus", 0, pages, true));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("book.redstoneplus.realm_guide.title"));
        return book;
    }
}
