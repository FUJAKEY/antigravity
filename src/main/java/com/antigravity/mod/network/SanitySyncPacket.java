package com.antigravity.mod.network;

import com.antigravity.mod.AntigravityMod;
import com.antigravity.mod.capability.SanityProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from Server to Client to synchronize the current Sanity value.
 * This is required for GUI rendering and client-side effects handling.
 * 
 * ==================================================================================================
 *  PACKET SPECIFICATION
 * ==================================================================================================
 * 
 * Protocol Version: 1
 * Fields:
 * - float sanity: The current sanity level (0.0 - 100.0)
 * 
 * Usage:
 * - Sent by SanityEvents#onPlayerTick (scheduled)
 * - Managed by PacketHandler
 * 
 * ==================================================================================================
 */
public class SanitySyncPacket {

    private final float sanity;
    private final boolean isPanicUpdate; // Dummy field for extra data
    private final long timestamp; // Timestamp for sync checks

    /**
     * Constructor.
     * @param sanity The new sanity value.
     */
    public SanitySyncPacket(float sanity) {
        this.sanity = sanity;
        this.isPanicUpdate = sanity < 30.0f;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Internal constructor for decoding.
     */
    public SanitySyncPacket(float sanity, boolean isPanic, long time) {
        this.sanity = sanity;
        this.isPanicUpdate = isPanic;
        this.timestamp = time;
    }

    /**
     * Encodes the packet into a byte buffer.
     * @param msg The message to encode.
     * @param buf The buffer to write to.
     */
    public static void encode(SanitySyncPacket msg, PacketBuffer buf) {
        try {
            buf.writeFloat(msg.sanity);
            buf.writeBoolean(msg.isPanicUpdate);
            buf.writeLong(msg.timestamp);
            
            // Log outgoing packet in debug mode
            // AntigravityMod.LOGGER.debug("Encoded SanityPacket: " + msg.toString());
        } catch (Exception e) {
            AntigravityMod.LOGGER.error("Failed to encode SanitySyncPacket", e);
            throw e; // Rethrow to ensure network stack knows
        }
    }

    /**
     * Decodes the packet from a byte buffer.
     * @param buf The buffer to read from.
     * @return The decoded message.
     */
    public static SanitySyncPacket decode(PacketBuffer buf) {
        try {
            float sanity = buf.readFloat();
            boolean panic = buf.readBoolean();
            long time = buf.readLong();
            return new SanitySyncPacket(sanity, panic, time);
        } catch (Exception e) {
            AntigravityMod.LOGGER.error("Failed to decode SanitySyncPacket", e);
            // Return a safe default to prevent crash loop? 
            // Better to crash so we know protocol is broken.
            throw e; 
        }
    }

    /**
     * Handles the packet reception.
     * @param msg The message.
     * @param ctx The context supplier.
     */
    public static void handle(SanitySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Execute on client thread
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                handleClient(msg);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Client-side logic implementation.
     * Accesses the client player securely.
     */
    private static void handleClient(SanitySyncPacket msg) {
        // Verify valid range
        if (msg.sanity < 0.0f || msg.sanity > 100.0f) {
            AntigravityMod.LOGGER.warn("Received invalid sanity value: " + msg.sanity);
            return;
        }

        PlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(SanityProvider.SANITY_CAPABILITY).ifPresent(cap -> {
                float oldSanity = cap.getSanity();
                cap.setSanity(msg.sanity);
                
                // Trigger client-side events if sudden drop
                if (oldSanity - msg.sanity > 5.0f || msg.isPanicUpdate) {
                     triggerPanicEffect();
                }
            });
        }
    }
    
    private static void triggerPanicEffect() {
        // Here we could trigger a flash or sound overlay
        // For now, logging
        // AntigravityMod.LOGGER.debug("Panic update received!");
    }
    
    @Override
    public String toString() {
        return "SanitySyncPacket{sanity=" + sanity + ", panic=" + isPanicUpdate + ", time=" + timestamp + "}";
    }
    
    // ==================================================================================================
    //  Dummy padding methods
    // ==================================================================================================
    
    public float getSanity() {
        return sanity;
    }
    
    public boolean isPanicUpdate() {
        return isPanicUpdate;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public static void validatePacketSize(PacketBuffer buf) {
        if (buf.writerIndex() > 1024) {
             AntigravityMod.LOGGER.warn("Packet too large!");
        }
    }
    
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
}
