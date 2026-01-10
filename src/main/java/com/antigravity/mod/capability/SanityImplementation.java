package com.antigravity.mod.capability;

import net.minecraft.util.math.MathHelper;

/**
 * Default implementation of the {@link ISanity} interface.
 * Handles the logic for maintaining sanity values within the valid range.
 * This class is designed to be easily extensible for future mechanics.
 */
public class SanityImplementation implements ISanity {

    private float sanity;
    private boolean isInsane;

    // Default constructor initializes sanity to max
    public SanityImplementation() {
        this.sanity = 100.0f;
        this.isInsane = false;
    }

    @Override
    public float getSanity() {
        return this.sanity;
    }

    @Override
    public void setSanity(float sanity) {
        // Clamp sanity between 0 and 100
        this.sanity = MathHelper.clamp(sanity, 0.0f, 100.0f);
        checkInsanity();
    }

    @Override
    public void increaseSanity(float amount) {
        setSanity(this.sanity + amount);
    }

    @Override
    public void decreaseSanity(float amount) {
        setSanity(this.sanity - amount);
    }
    
    @Override
    public void copyFrom(ISanity other) {
        this.sanity = other.getSanity();
        this.isInsane = other.isInsane();
    }

    @Override
    public boolean isInsane() {
        return this.isInsane;
    }

    @Override
    public void setInsane(boolean insane) {
        this.isInsane = insane;
    }
    
    /**
     * Internal method to check if the player should be considered 'insane'.
     * Currently thresholds at 30% sanity.
     */
    private void checkInsanity() {
        if (this.sanity < 30.0f) {
            this.isInsane = true;
        } else {
            this.isInsane = false;
        }
    }
}
