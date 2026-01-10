package com.antigravity.mod.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;

/**
 * Handles saving and loading of the Sanity capability data to NBT.
 */
public class SanityStorage implements Capability.IStorage<ISanity> {

    @Nullable
    @Override
    public INBT writeNBT(Capability<ISanity> capability, ISanity instance, Direction side) {
        CompoundNBT tag = new CompoundNBT();
        tag.putFloat("sanity", instance.getSanity());
        tag.putBoolean("isInsane", instance.isInsane());
        return tag;
    }

    @Override
    public void readNBT(Capability<ISanity> capability, ISanity instance, Direction side, INBT nbt) {
        if (!(nbt instanceof CompoundNBT)) {
            return;
        }
        CompoundNBT tag = (CompoundNBT) nbt;
        instance.setSanity(tag.getFloat("sanity"));
        instance.setInsane(tag.getBoolean("isInsane"));
    }
}
