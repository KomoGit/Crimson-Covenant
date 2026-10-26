package com.zestudio.crimsoncovenant.vampirism;

import com.zestudio.crimsoncovenant.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/// How long a player has been a vampire, in Minecraft days since they turned: the older, the more blood they hold
/// and the more of it comes back on its own (see BloodPool). Counted from the world's game time, so /time set
/// doesn't age anyone. Shown on the Blood Skills screen.
public enum VampireAge {
    /// Under 10 days: 100 blood, 3 drops back on their own
    FLEDGLING("fledgling", 0, 100, 30, ChatFormatting.GRAY),
    /// 10 days: 10% more blood, 2.5% more of the bar comes back
    NEO("neo", 10, 110, 36, ChatFormatting.AQUA),
    /// 20 days: 50% more blood, 10% more of the bar comes back
    UNUSUAL("unusual", 20, 150, 60, ChatFormatting.GOLD),
    /// 30 days: double the blood, 15% more of the bar comes back
    ANCIENT("ancient", 30, 200, 90, ChatFormatting.DARK_RED);

    private static final long DAY = 24000L;
    /// TURNED_AT for a player who never turned
    public static final long NEVER = -1L;

    private final String name;
    private final int days;
    private final int maxBlood;
    private final int naturalMax;
    private final ChatFormatting colour;

    VampireAge(String name, int days, int maxBlood, int naturalMax, ChatFormatting colour) {
        this.name = name;
        this.days = days;
        this.maxBlood = maxBlood;
        this.naturalMax = naturalMax;
        this.colour = colour;
    }

    public Component displayName() {
        return Component.translatable("vampire_age.crimsoncovenant." + this.name).withStyle(this.colour);
    }

    public int maxBlood() {
        return this.maxBlood;
    }

    /// Blood stops coming back on its own here; the rest has to be fed for
    public int naturalMax() {
        return this.naturalMax;
    }

    /// Days since the player turned, 0 if they never have (or turned before ages were kept: see Vampirism.tickVampire)
    public static long daysTurned(Player player) {
        long turnedAt = player.getData(ModAttachments.TURNED_AT);
        return turnedAt == NEVER ? 0 : (player.level().getGameTime() - turnedAt) / DAY;
    }

    public static VampireAge of(Player player) {
        long days = daysTurned(player);
        VampireAge age = FLEDGLING;
        for (VampireAge each : values()) {
            if (days >= each.days) {
                age = each;
            }
        }
        return age;
    }
}
