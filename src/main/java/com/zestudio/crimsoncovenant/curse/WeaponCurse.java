package com.zestudio.crimsoncovenant.curse;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/// The curse laid on a weapon, and how deep: one level for each tome forged into it
public record WeaponCurse(Curse curse, int level) {
    public static final Codec<WeaponCurse> CODEC = RecordCodecBuilder.create(i -> i.group(
            Curse.CODEC.fieldOf("curse").forGetter(WeaponCurse::curse),
            Codec.intRange(1, Curse.MAX_LEVEL).fieldOf("level").forGetter(WeaponCurse::level))
            .apply(i, WeaponCurse::new));
    public static final StreamCodec<ByteBuf, WeaponCurse> STREAM_CODEC = StreamCodec.composite(
            Curse.STREAM_CODEC, WeaponCurse::curse,
            ByteBufCodecs.VAR_INT, WeaponCurse::level,
            WeaponCurse::new);

    public static @Nullable WeaponCurse of(ItemStack weapon) {
        return weapon.get(ModDataComponents.CURSE.get());
    }

    /// The level of `curse` on the weapon, or 0 if it doesn't carry that curse
    public static int levelOf(ItemStack weapon, Curse curse) {
        WeaponCurse laid = of(weapon);
        return laid != null && laid.curse() == curse ? laid.level() : 0;
    }
}
