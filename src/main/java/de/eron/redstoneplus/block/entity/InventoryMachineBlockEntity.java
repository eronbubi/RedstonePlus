package de.eron.redstoneplus.block.entity;

import de.eron.redstoneplus.block.InventoryMachineBlock;
import de.eron.redstoneplus.block.MachineBlock;
import de.eron.redstoneplus.entity.FastArrows;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 3x3 inventory used by the Arrow Shooter and the Block Placer. */
public class InventoryMachineBlockEntity extends RandomizableContainerBlockEntity {
    public static final int ARROWS_PER_SALVO = 6;
    /** 100 blocks per tick = 2000 blocks per second: the arrow is basically there the moment it is fired. */
    public static final float ARROW_SPEED = 100.0F;

    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    /** Arrows still to fire in the running salvo of an Arrow Shooter. */
    private int salvoLeft;
    /** Game time of the last fired pair, so a salvo started by a block tick doesn't fire again in the same tick. */
    private long lastFired = Long.MIN_VALUE;

    public InventoryMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.INVENTORY_MACHINE_BE.get(), pos, state);
    }

    private boolean isArrowShooter() {
        return this.getBlockState().is(ModRegistry.ARROW_SHOOTER.get());
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new DispenserMenu(id, inventory, this);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (this.isArrowShooter()) {
            return stack.is(ItemTags.ARROWS) || stack.is(ModRegistry.URANIUM_SHARD.get());
        }
        return stack.getItem() instanceof BlockItem;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }
        this.salvoLeft = tag.getInt("salvo_left");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items, registries);
        }
        tag.putInt("salvo_left", this.salvoLeft);
    }

    private int findSlot(java.util.function.Predicate<ItemStack> filter) {
        for (int i = 0; i < this.items.size(); i++) {
            if (!this.items.get(i).isEmpty() && filter.test(this.items.get(i))) {
                return i;
            }
        }
        return -1;
    }

    // ---------- Block Placer ----------

    public void placeBlock(ServerLevel level, BlockPos pos, Direction facing) {
        int slot = this.findSlot(s -> s.getItem() instanceof BlockItem);
        if (slot < 0) {
            return;
        }
        ItemStack stack = this.items.get(slot);
        BlockPos front = pos.relative(facing);
        if (stack.getItem() instanceof BlockItem blockItem) {
            blockItem.place(new DirectionalPlaceContext(level, front, facing, stack, facing.getOpposite()));
            this.setChanged();
        }
    }

    // ---------- Arrow Shooter ----------

    /** Starts a salvo of six arrows. Costs one Uranium Shard. */
    public void startSalvo(ServerLevel level) {
        if (this.salvoLeft > 0) {
            return;
        }
        int shard = this.findSlot(s -> s.is(ModRegistry.URANIUM_SHARD.get()));
        if (shard < 0 || this.findSlot(s -> s.is(ItemTags.ARROWS)) < 0) {
            level.playSound(null, this.worldPosition, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0F, 1.2F);
            return;
        }
        this.items.get(shard).shrink(1);
        this.salvoLeft = ARROWS_PER_SALVO;
        this.setChanged();
        this.fireTick(level);
    }

    /**
     * Fires the next two arrows of the salvo. The game runs at 20 ticks per second, so "one arrow every half tick"
     * is done by spawning two arrows per tick and moving the first one half a tick of flight ahead. In the air the
     * six arrows end up spaced exactly as if they had been fired half a tick apart.
     */
    private void fireTick(ServerLevel level) {
        BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof InventoryMachineBlock)) {
            this.salvoLeft = 0;
            return;
        }
        this.lastFired = level.getGameTime();
        Direction facing = MachineBlock.facing(state);
        Vec3 spawn = MachineBlock.frontPoint(this.worldPosition, facing, 0.7);
        for (int half = 0; half < 2 && this.salvoLeft > 0; half++) {
            int slot = this.findSlot(s -> s.is(ItemTags.ARROWS) && s.getItem() instanceof ProjectileItem);
            if (slot < 0) {
                this.salvoLeft = 0;
                break;
            }
            ItemStack stack = this.items.get(slot);
            ItemStack single = stack.split(1);
            Projectile projectile = ((ProjectileItem) single.getItem()).asProjectile(level, spawn, single, facing);
            projectile.shoot(facing.getStepX(), facing.getStepY(), facing.getStepZ(), ARROW_SPEED, 0.0F);
            if (projectile instanceof AbstractArrow arrow) {
                arrow.pickup = AbstractArrow.Pickup.ALLOWED;
            }
            if (half == 0) {
                // this arrow was "fired" half a tick earlier than the second one, so it has already flown half a tick
                // (50 blocks at this speed). Only skip ahead when nothing is in the way, otherwise it would fly
                // through whatever it should have hit during that half tick.
                Vec3 from = projectile.position();
                Vec3 to = from.add(projectile.getDeltaMovement().scale(0.5));
                boolean blocked = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile))
                        .getType() != HitResult.Type.MISS
                        || ProjectileUtil.getEntityHitResult(level, projectile, from, to,
                        projectile.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0), e -> !e.isSpectator() && e.isPickable()) != null;
                if (!blocked) {
                    projectile.setPos(to);
                }
            }
            level.addFreshEntity(projectile);
            FastArrows.track(level, projectile);
            this.salvoLeft--;
        }
        level.playSound(null, this.worldPosition, SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 1.0F, 1.2F + level.getRandom().nextFloat() * 0.2F);
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InventoryMachineBlockEntity be) {
        if (be.salvoLeft > 0 && level instanceof ServerLevel serverLevel && level.getGameTime() != be.lastFired) {
            be.fireTick(serverLevel);
        }
    }
}
