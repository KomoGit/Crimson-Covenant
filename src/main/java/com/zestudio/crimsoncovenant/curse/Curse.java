package com.zestudio.crimsoncovenant.curse;

import com.mojang.serialization.Codec;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.item.WhipItem;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/// The hunters' curses: old, bound things laid on a weapon at a Curseforge (see CurseforgeMenu). Most are unleashed with
/// the curse key for a short while before they need time to stir again (see Curses); a few never sleep, and are at
/// work whenever the weapon is (see `alwaysAwake`). Each comes in three levels, each asking a higher hunter rank of
/// whoever would use it.
public enum Curse implements StringRepresentable {
    /// Always awake: held, the weapon is taken up by a ghostly hand that fights beside its owner (see SpiritHand)
    SPIRIT_HAND("spirit_hand", Weapons.MELEE),
    /// Arrows mark what they hit as banished: for a while it can't hurt anyone, or heal
    BANISH("banish", Weapons.RANGED, 300, 0, 900),
    /// Hits chain the target where it stands
    ENCHAIN("enchain", Weapons.EITHER, 300, 0, 900),
    /// Always awake: whatever its arrows strike is marked, and every arrow after hunts the mark down while it lives
    TRACING_SHOT("tracing_shot", Weapons.RANGED),
    /// What the weapon kills rises again to fight for its killer a while
    ENTHRALL("enthrall", Weapons.EITHER, 300, 0, 1200),
    /// Always awake: kills feed the wielder, but wielded too long unfed, it gnaws at them
    GLUTTONY("gluttony", Weapons.MELEE),
    /// A gale sweeps out from the wielder, and from every blow they land
    GUST("gust", Weapons.MELEE, 200, 0, 600),
    /// The next arrow loosed becomes a pair of spirit wyrms, coiling round each other as they tear straight on through
    /// walls and everything in their way (see SpiritWyrms)
    DRAGONSTRIKE("dragonstrike", Weapons.RANGED, 200, 0, 1800),
    /// Whips only: the lash comes down on the ground, and a quake rolls out before the wielder, throwing down everything
    /// standing in its way (see Earthshatter). It's spent at once.
    EARTHSHATTER("earthshatter", Weapons.WHIP, 1, 0, 600),
    /// Whips only, always awake: right-clicked at a block, the lash hooks round it and hauls its wielder in (see Grapple)
    GRAPPLE("grapple", Weapons.WHIP);

    public static final int MAX_LEVEL = 3;
    public static final Codec<Curse> CODEC = StringRepresentable.fromEnum(Curse::values);
    public static final StreamCodec<ByteBuf, Curse> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Curse::ordinal);
    /// The curses' colour: a cold, ghostly blue
    public static final int COLOUR = 0x8FE3FF;

    /// What a curse can be laid on
    public enum Weapons {
        MELEE, RANGED, EITHER, WHIP;

        public boolean fits(ItemStack weapon) {
            boolean melee = weapon.is(ModTags.Items.CURSEABLE_MELEE);
            boolean ranged = weapon.is(ModTags.Items.CURSEABLE_RANGED);
            return switch (this) {
                case MELEE -> melee;
                case RANGED -> ranged;
                case EITHER -> melee || ranged;
                case WHIP -> WhipItem.isWhip(weapon);
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

    // One that never sleeps: nothing to unleash, nothing to wait for
    Curse(String name, Weapons weapons) {
        this(name, weapons, 0, 0, 0);
    }

    /// At work whenever the weapon is, for a hunter of high enough rank: the curse key does nothing for it
    public boolean alwaysAwake() {
        return this.duration == 0;
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

    /// How many souls it takes to lay on a plain weapon at a Haunted Anvil (see CurseforgeMenu.soulCost)
    public int souls() {
        return switch (this) {
            case TRACING_SHOT -> 5;
            case SPIRIT_HAND, ENTHRALL, DRAGONSTRIKE, EARTHSHATTER -> 4;
            case BANISH, ENCHAIN, GLUTTONY, GUST, GRAPPLE -> 3;
        };
    }

    /// How long it stays unleashed, in ticks
    public int duration(int level) {
        return this.duration + (level - 1) * this.durationPerLevel;
    }

    /// How long before it can be unleashed again, in ticks: a little shorter at each level
    public int cooldown(int level) {
        return this.cooldown - (level - 1) * this.cooldown / 6;
    }

    /// The hunter rank needed to use it at this level: Initiate, then Slayer, then Grand Hunter
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
