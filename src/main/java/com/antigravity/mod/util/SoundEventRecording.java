package com.antigravity.mod.util;

import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;

public class SoundEventRecording {
    public SoundEvent sound;
    public BlockPos pos;
    public float volume;
    public long timestamp;

    public SoundEventRecording(SoundEvent s, BlockPos p, float v, long t) {
        this.sound = s;
        this.pos = p;
        this.volume = v;
        this.timestamp = t;
    }
}
