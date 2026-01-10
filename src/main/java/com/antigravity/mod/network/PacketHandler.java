package com.antigravity.mod.network;

import com.antigravity.mod.AntigravityMod;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/**
 * Handles the registration of network packets for the Antigravity mod.
 * Uses Forge's SimpleChannel for easy packet management.
 */
public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(AntigravityMod.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void init() {
        // Register packets here
        INSTANCE.registerMessage(id++, SanitySyncPacket.class, SanitySyncPacket::encode, SanitySyncPacket::decode, SanitySyncPacket::handle);
    }
}
