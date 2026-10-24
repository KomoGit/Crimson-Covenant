package com.zestudio.crimsoncovenant.registry;

import com.mojang.serialization.Codec;
import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.IronMaidenBlockEntity;

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

    /// Set on a blood skill a Blood Altar has upgraded (see skill.SkillUpgrade), and on a Blood Spear conjured by
    /// an upgraded Blood Spear skill
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SKILL_UPGRADED = DATA_COMPONENTS.registerComponentType("skill_upgraded",
            builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /// Set on a weapon silvered at the smithing table (see item.Silvering)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SILVERED = DATA_COMPONENTS.registerComponentType("silvered",
            builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /// The villager held captive in an Iron Maiden, carried with it (see block.IronMaidenBlockEntity)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<IronMaidenBlockEntity.Captive>> CAPTIVE = DATA_COMPONENTS.registerComponentType("captive",
            builder -> builder.persistent(IronMaidenBlockEntity.Captive.CODEC).networkSynchronized(IronMaidenBlockEntity.Captive.STREAM_CODEC));

    private ModDataComponents() {}
}
