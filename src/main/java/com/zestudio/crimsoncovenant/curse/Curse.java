package com.zestudio.crimsoncovenant.curse;

import com.mojang.serialization.Codec;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.registry.ModTags;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/// The hunters' curses: old, bound things laid on a weapon at a Curseforge (see CurseforgeMenu), and unleashed with
/// the curse key for a short while before they need time to stir again (see Curses). Each comes in three levels, each
/// asking a higher hunter rank of whoever would unleash it.
public enum Curse implements StringRepresentable {
    /// A ghostly hand takes up a copy of the weapon and fights beside its owner
    SPIRIT_HAND("spirit_hand", Weapons.MELEE, 300, 100, 1200),
    /// Arrows mark what they hit as banished: for a while it can't hurt anyone, or heal
    BANISH("banish", Weapons.RANGED, 300, 0, 900),
    /// Hits chain the target where it stands
    ENCHAIN("enchain", Weapons.EITHER, 300, 0, 900),
    /// Arrows seek out their targets
    TRACING_SHOT("tracing_shot", Weapons.RANGED, 300, 100, 800),
    /// What the weapon kills rises again to fight for its killer a while
    ENTHRALL("enthrall", Weapons.EITHER, 300, 0, 1200),
    /// Kills feed the wielder; failing to kill enough before it ends, they wither
    GLUTTONY("gluttony", Weapons.MELEE, 400, 200, 900),
    /// A gale sweeps out from the wielder, and from every blow they land
    GUST("gust", Weapons.MELEE, 200, 0, 600),
    /// The next arrow loosed becomes a pair of spirit wyrms, coiling round each other as they tear straight on through
    /// walls and everything in their way (see SpiritWyrms)
    DRAGONSTRIKE("dragonstrike", Weapons.RANGED, 200, 0, 1800);

    public static final int MAX_LEVEL = 3;
    public static final Codec<Curse> CODEC = StringRepresentable.fromEnum(Curse::values);
    public static final StreamCodec<ByteBuf, Curse> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Curse::ordinal);
    /// The curses' colour: a cold, ghostly blue
    public static final int COLOUR = 0x8FE3FF;

    /// What a curse can be laid on
    public enum Weapons {
        MELEE, RANGED, EITHER;

        public boolean fits(ItemStack weapon) {
            boolean melee = weapon.is(ModTags.Items.CURSEABLE_MELEE);
            boolean ranged = weapon.is(ModTags.Items.CURSEABLE_RANGED);
            return switch (this) {
                case MELEE -> melee;
                case RANGED -> ranged;
                case EITHER -> melee || ranged;
            };
        }
    }

    private final String name;
    private final Weapons weapons;
    private final int duration;
    private final int durationPerLevel;
    private final int cooldown;

    Curse(String name, Weapons weapons, int duration, int durationPerLevel, int cooldown) {
        this.name = name;
        this.weapons = weapons;
        this.duration = duration;
        this.durationPerLevel = durationPerLevel;
        this.cooldown = cooldown;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public Weapons weapons() {
        return this.weapons;
    }

    public boolean fits(ItemStack weapon) {
        return this.weapons.fits(weapon);
    }

    /// How long it stays unleashed, in ticks
    public int duration(int level) {
        return this.duration + (level - 1) * this.durationPerLevel;
    }

    /// How long before it can be unleashed again, in ticks: a little shorter at each level
    public int cooldown(int level) {
        return this.cooldown - (level - 1) * this.cooldown / 6;
    }

    /// The hunter rank needed to unleash it at this level: Initiate, then Slayer, then Grand Hunter
    public static int rankFor(int level) {
        return switch (Math.max(1, Math.min(level, MAX_LEVEL))) {
            case 1 -> HunterRanks.INITIATE;
            case 2 -> HunterRanks.SLAYER;
            default -> HunterRanks.GRAND_HUNTER;
        };
    }

    /// Its proper name, "Revenant's Grip"
    public Component title() {
        return Component.translatable("curse.crimsoncovenant." + this.name);
    }

    /// What it does, plainly: "Spirit Hand"
    public Component plainName() {
        return Component.translatable("curse.crimsoncovenant." + this.name + ".plain");
    }

    public Component description() {
        return Component.translatable("curse.crimsoncovenant." + this.name + ".description");
    }

    /// "Revenant's Grip II", in the curses' colour
    public Component titleAt(int level) {
        return Component.translatable("curse.crimsoncovenant.level", this.title(), Component.translatable("enchantment.level." + level))
                .withColor(COLOUR);
    }

    public Component weaponsName() {
        return Component.translatable("curse.crimsoncovenant.weapons." + this.weapons.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY);
    }
}
