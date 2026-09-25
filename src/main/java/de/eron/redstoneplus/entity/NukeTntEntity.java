package de.eron.redstoneplus.entity;

import de.eron.redstoneplus.registry.ModRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;

/** A primed Uranium Nuke: a very long fuse and a huge explosion. */
public class NukeTntEntity extends PrimedTnt {
    public static final float POWER = 16.0F;
    public static final int FUSE = 160;

    public NukeTntEntity(EntityType<? extends PrimedTnt> type, Level level) {
        super(type, level);
        this.setFuse(FUSE);
        this.setBlockState(ModRegistry.URANIUM_NUKE.get().defaultBlockState());
    }

    @Override
    protected void explode() {
        if (this.level() instanceof ServerLevel level) {
            level.explode(this, this.getX(), this.getY(0.0625), this.getZ(), POWER, true, Level.ExplosionInteraction.TNT);
        }
    }
}
