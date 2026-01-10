package com.antigravity.mod.capability;

import net.minecraft.nbt.CompoundNBT;

/**
 * Interface for the Sanity Capability.
 * Defines methods to get and set sanity levels.
 * The sanity level ranges from 0.0 (Insane) to 100.0 (Sane).
 */
public interface ISanity {
    /**
     * @return The current sanity level.
     */
    float getSanity();

    /**
     * Sets the sanity level.
     * @param sanity New sanity value.
     */
    void setSanity(float sanity);

    /**
     * Increases sanity by the specified amount.
     * @param amount Amount to add.
     */
    void increaseSanity(float amount);

    /**
     * Decreases sanity by the specified amount.
     * @param amount Amount to subtract.
     */
    void decreaseSanity(float amount);

    /**
     * Copies data from another ISanity instance.
     * Useful for syncing or persistence.
     * @param other The source instance.
     */
    void copyFrom(ISanity other);
    
    // Add more complex methods here if needed for advanced logic
    boolean isInsane();
    
    void setInsane(boolean insane);
}
