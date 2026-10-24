package com.zestudio.crimsoncovenant.mixin;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.zestudio.crimsoncovenant.network.BloodMoonPayload;

import net.minecraft.client.renderer.SkyRenderer;

/// A blood moon rises red: the moon's colour is tinted blood red while one is up
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    private static final float BLOOD_GREEN = 0.18F;
    private static final float BLOOD_BLUE = 0.14F;

    @ModifyArg(method = "renderMoon", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),
            index = 1)
    private Vector4f crimsoncovenant$bloodRedMoon(Vector4f colour) {
        if (BloodMoonPayload.clientBloodMoon) {
            return new Vector4f(colour.x(), colour.y() * BLOOD_GREEN, colour.z() * BLOOD_BLUE, colour.w());
        }
        return colour;
    }
}
