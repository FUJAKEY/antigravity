package com.antigravity.mod.network;

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
 */
public class SanitySyncPacket {

    private final float sanity;

    public SanitySyncPacket(float sanity) {
        this.sanity = sanity;
    }

    public static void encode(SanitySyncPacket msg, PacketBuffer buf) {
        buf.writeFloat(msg.sanity);
    }

    public static SanitySyncPacket decode(PacketBuffer buf) {
        return new SanitySyncPacket(buf.readFloat());
    }

    public static void handle(SanitySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Execute on client thread
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                handleClient(msg);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(SanitySyncPacket msg) {
        PlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            player.getCapability(SanityProvider.SANITY_CAPABILITY).ifPresent(cap -> {
                cap.setSanity(msg.sanity);
                // Maybe trigger some visual flash if sanity dropped significantly?
            });
        }
    }
}
