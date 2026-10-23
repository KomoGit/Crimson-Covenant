package com.zestudio.crimsoncovenant.registry;

import com.mojang.serialization.Codec;
import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/// Extra data carried on item stacks
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CrimsonCovenant.MODID);

    /// The game time an Ephemeral Sword fades away at
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> EXPIRES_AT = DATA_COMPONENTS.registerComponentType("expires_at",
            builder -> builder.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    private ModDataComponents() {}
}
