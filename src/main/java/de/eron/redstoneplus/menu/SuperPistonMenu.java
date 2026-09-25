package de.eron.redstoneplus.menu;

import de.eron.redstoneplus.block.piston.SuperPistonBlock;
import de.eron.redstoneplus.block.piston.SuperPistonBlockEntity;
import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Slot-less menu for the Super Piston slider. The range travels to the client through a data slot, and the
 * slider sends the chosen range back as a menu button click (button id = range), so no custom packet is needed.
 */
public class SuperPistonMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final ContainerLevelAccess access;
    @Nullable
    private final SuperPistonBlockEntity piston;

    /** Client side. */
    public SuperPistonMenu(int id, Inventory inventory) {
        super(ModRegistry.SUPER_PISTON_MENU.get(), id);
        this.data = new SimpleContainerData(1);
        this.access = ContainerLevelAccess.NULL;
        this.piston = null;
        this.addDataSlots(this.data);
    }

    /** Server side. */
    public SuperPistonMenu(int id, Inventory inventory, SuperPistonBlockEntity piston, ContainerLevelAccess access) {
        super(ModRegistry.SUPER_PISTON_MENU.get(), id);
        this.piston = piston;
        this.access = access;
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return piston.range();
            }

            @Override
            public void set(int index, int value) {
                piston.setRange(value);
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
        this.addDataSlots(this.data);
    }

    public int range() {
        return Math.max(1, this.data.get(0));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 1 || id > SuperPistonBlock.MAX_RANGE) {
            return false;
        }
        this.data.set(0, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.piston == null || (!this.piston.isRemoved()
                && player.distanceToSqr(this.piston.getBlockPos().getCenter()) <= 64.0);
    }
}
