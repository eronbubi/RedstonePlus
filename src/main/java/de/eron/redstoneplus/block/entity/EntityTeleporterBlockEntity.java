package de.eron.redstoneplus.block.entity;

import de.eron.redstoneplus.block.EntityTeleporterBlock;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Swallows every non-player entity that enters the block space above it and stores it exactly as it was
 * (full entity data: fuse time, health, items, motion, passengers...).
 * Entities that arrive at (almost) the same time form one group: everything that comes in within
 * {@link #GROUP_WINDOW} ticks of the previous arrival. A Remote Detonator releases one whole group at once,
 * oldest group first, keeping the positions of the entities relative to each other.
 */
public class EntityTeleporterBlockEntity extends BlockEntity {
    /** Arrivals closer together than this (in ticks) count as "at the same time". */
    public static final int GROUP_WINDOW = 10;

    /** One stored entity: its full data and where it was relative to the capture point. */
    private record Stored(CompoundTag data, double dx, double dy, double dz) {
    }

    private final ArrayDeque<List<Stored>> groups = new ArrayDeque<>();
    private long lastCapture = Long.MIN_VALUE;

    public EntityTeleporterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ENTITY_TELEPORTER_BE.get(), pos, state);
    }

    public int storedCount() {
        return this.groups.stream().mapToInt(List::size).sum();
    }

    public int groupCount() {
        return this.groups.size();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EntityTeleporterBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB above = new AABB(pos.above());
        List<Entity> found = level.getEntities((Entity) null, above, e -> e.isAlive() && e.getVehicle() == null && !containsPlayer(e));
        if (found.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<Stored> group;
        if (be.groups.isEmpty() || now - be.lastCapture > GROUP_WINDOW) {
            group = new ArrayList<>();
            be.groups.addLast(group);
        } else {
            group = be.groups.peekLast();
        }
        be.lastCapture = now;
        Vec3 origin = Vec3.atBottomCenterOf(pos.above());
        for (Entity entity : found) {
            CompoundTag tag = new CompoundTag();
            if (!entity.save(tag)) {
                continue;
            }
            group.add(new Stored(tag, entity.getX() - origin.x, entity.getY() - origin.y, entity.getZ() - origin.z));
            serverLevel.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 0.5, entity.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
            entity.getSelfAndPassengers().toList().forEach(Entity::discard);
        }
        level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.4F, 1.5F);
        be.changed();
    }

    private static boolean containsPlayer(Entity entity) {
        return entity.getSelfAndPassengers().anyMatch(e -> e instanceof Player);
    }

    /**
     * Spawns the oldest stored group with its capture point at {@code at}. Every entity keeps its offset to the
     * others. Returns the number of entities released (0 if nothing is stored).
     */
    public int releaseNext(ServerLevel target, Vec3 at) {
        List<Stored> group = this.groups.pollFirst();
        if (group == null) {
            return 0;
        }
        if (this.groups.isEmpty()) {
            // the next arrival starts a fresh group even if it comes right away
            this.lastCapture = Long.MIN_VALUE;
        }
        int released = 0;
        for (Stored stored : group) {
            double x = at.x + stored.dx(), y = at.y + stored.dy(), z = at.z + stored.dz();
            Entity entity = EntityType.loadEntityRecursive(stored.data(), target, e -> {
                e.moveTo(x, y, z, e.getYRot(), e.getXRot());
                return e;
            });
            if (entity != null) {
                target.addFreshEntityWithPassengers(entity);
                released++;
            }
        }
        this.changed();
        target.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.5, at.z, 30, 0.4, 0.4, 0.4, 0.1);
        target.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 0.8F);
        return released;
    }

    private void changed() {
        this.setChanged();
        if (this.level != null) {
            BlockState state = this.getBlockState();
            boolean hasAny = !this.groups.isEmpty();
            if (state.getValue(EntityTeleporterBlock.POWERED) != hasAny) {
                this.level.setBlock(this.worldPosition, state.setValue(EntityTeleporterBlock.POWERED, hasAny), Block.UPDATE_ALL);
            } else {
                this.level.updateNeighbourForOutputSignal(this.worldPosition, state.getBlock());
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag groupList = new ListTag();
        for (List<Stored> group : this.groups) {
            ListTag entries = new ListTag();
            for (Stored s : group) {
                CompoundTag entry = new CompoundTag();
                entry.put("entity", s.data());
                entry.putDouble("dx", s.dx());
                entry.putDouble("dy", s.dy());
                entry.putDouble("dz", s.dz());
                entries.add(entry);
            }
            CompoundTag g = new CompoundTag();
            g.put("entities", entries);
            groupList.add(g);
        }
        tag.put("groups", groupList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.groups.clear();
        ListTag groupList = tag.getList("groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < groupList.size(); i++) {
            ListTag entries = groupList.getCompound(i).getList("entities", Tag.TAG_COMPOUND);
            List<Stored> group = new ArrayList<>();
            for (int k = 0; k < entries.size(); k++) {
                CompoundTag e = entries.getCompound(k);
                group.add(new Stored(e.getCompound("entity"), e.getDouble("dx"), e.getDouble("dy"), e.getDouble("dz")));
            }
            this.groups.addLast(group);
        }
        // teleporters saved by 1.2.2 and older: every entity was stored on its own
        ListTag old = tag.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < old.size(); i++) {
            List<Stored> single = new ArrayList<>();
            single.add(new Stored(old.getCompound(i), 0, 0, 0));
            this.groups.addLast(single);
        }
    }

    @Nullable
    public static EntityTeleporterBlockEntity at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof EntityTeleporterBlockEntity be ? be : null;
    }
}
