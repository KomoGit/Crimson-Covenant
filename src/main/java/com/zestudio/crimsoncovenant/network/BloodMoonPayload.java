package com.zestudio.crimsoncovenant.network;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/// Tells clients whether a blood moon is up, so they can tint the sky red
public record BloodMoonPayload(boolean active) implements CustomPacketPayload {
    public static final Type<BloodMoonPayload> TYPE = new Type<>(CrimsonCovenant.id("blood_moon"));
    public static final StreamCodec<ByteBuf, BloodMoonPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(BloodMoonPayload::new, BloodMoonPayload::active);

    /// Set on the client when a payload arrives; read by the client's fog colouring
    public static volatile boolean clientBloodMoon = false;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = CrimsonCovenant.MODID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientBloodMoon = payload.active());
        }
    }
}
