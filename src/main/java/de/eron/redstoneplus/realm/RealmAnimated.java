package de.eron.redstoneplus.realm;

/**
 * A realm creature whose special move is animated. The server announces the move with
 * {@link #ABILITY_EVENT}; the client stores the tick it started and the model plays the "ability" clip from there.
 */
public interface RealmAnimated {
    byte ABILITY_EVENT = 71;

    /** tickCount when the special move last started (client side), or a large negative number. */
    int abilityStart();

    /** 0..1 to drive the ability clip directly (e.g. a creeper's fuse), or below 0 to use {@link #abilityStart()}. */
    default float abilityOverride(float partialTick) {
        return -1.0F;
    }
}
