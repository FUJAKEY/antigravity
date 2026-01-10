package com.antigravity.mod.capability;

import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provider for the Sanity capability.
 * Attaches the capability to entities (Players).
 */
public class SanityProvider implements ICapabilitySerializable<INBT> {

    @CapabilityInject(ISanity.class)
    public static Capability<ISanity> SANITY_CAPABILITY = null;

    private LazyOptional<ISanity> instance = LazyOptional.of(SanityImplementation::new);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return cap == SANITY_CAPABILITY ? instance.cast() : LazyOptional.empty();
    }

    @Override
    public INBT serializeNBT() {
        return SANITY_CAPABILITY.getStorage().writeNBT(SANITY_CAPABILITY, instance.orElseThrow(() -> new IllegalArgumentException("LazyOptional must not be empty!")), null);
    }

    @Override
    public void deserializeNBT(INBT nbt) {
        SANITY_CAPABILITY.getStorage().readNBT(SANITY_CAPABILITY, instance.orElseThrow(() -> new IllegalArgumentException("LazyOptional must not be empty!")), null, nbt);
    }
}
