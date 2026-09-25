package de.eron.redstoneplus.block.entity;

import de.eron.redstoneplus.block.EntityTeleporterBlock;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.HolderLookup;
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
import java.util.List;

/**
 * Swallows every non-player entity that enters the block space above it and stores it exactly as it was
 * (full entity data: fuse time, health, items, motion, passengers...). Remote Detonators pull them out again
 * in the order they were stored.
 */
public class EntityTeleporterBlockEntity extends BlockEntity {
    private final ArrayDeque<CompoundTag> stored = new ArrayDeque<>();

    public EntityTeleporterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ENTITY_TELEPORTER_BE.get(), pos, state);
    }

    public int storedCount() {
        return this.stored.size();
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
        for (Entity entity : found) {
            CompoundTag tag = new CompoundTag();
            if (!entity.save(tag)) {
                continue;
            }
            be.stored.addLast(tag);
            serverLevel.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 0.5, entity.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
            entity.getSelfAndPassengers().toList().forEach(Entity::discard);
        }
        level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.4F, 1.5F);
        be.changed();
    }

    private static boolean containsPlayer(Entity entity) {
        return entity.getSelfAndPassengers().anyMatch(e -> e instanceof Player);
    }

    /** Spawns the oldest stored entity with its feet at {@code at}. Returns false if nothing is stored. */
    public boolean releaseNext(ServerLevel target, Vec3 at) {
        CompoundTag tag = this.stored.pollFirst();
        if (tag == null) {
            return false;
        }
        Entity entity = EntityType.loadEntityRecursive(tag, target, e -> {
            e.moveTo(at.x, at.y, at.z, e.getYRot(), e.getXRot());
            return e;
        });
        this.changed();
        if (entity == null) {
            return true;
        }
        target.addFreshEntityWithPassengers(entity);
        target.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.5, at.z, 30, 0.4, 0.4, 0.4, 0.1);
        target.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 0.8F);
        return true;
    }

    private void changed() {
        this.setChanged();
        if (this.level != null) {
            BlockState state = this.getBlockState();
            boolean hasAny = !this.stored.isEmpty();
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
        ListTag list = new ListTag();
        list.addAll(this.stored);
        tag.put("entities", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.stored.clear();
        ListTag list = tag.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            this.stored.addLast(list.getCompound(i));
        }
    }

    @Nullable
    public static EntityTeleporterBlockEntity at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof EntityTeleporterBlockEntity be ? be : null;
    }
}
