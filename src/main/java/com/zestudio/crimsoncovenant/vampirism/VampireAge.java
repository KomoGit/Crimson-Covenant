package com.zestudio.crimsoncovenant.vampirism;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.skill.BloodSkill;
import com.zestudio.crimsoncovenant.skill.BloodSkills;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/// A vampire's age: the longer one survives, the stronger it grows, a tier for every ten days (twenty minutes of play
/// each), up to the third at day 30.
///
/// | Days  | Blood skills | Blood held | Comes back alone | Max health | Always on                      | And                                          |
/// |-------|--------------|------------|------------------|------------|--------------------------------|----------------------------------------------|
/// | 0-9   | full cost    | 100        | up to 30         | +0         | (Speed I, Strength I by night) |                                              |
/// | 10-19 | 5% cheaper   | 110        | up to 36         | +4         | Speed I                        |                                              |
/// | 20-29 | 10% cheaper  | 150        | up to 60         | +7         | Speed I, Haste I, Strength I   |                                              |
/// | 30+   | 20% cheaper  | 200        | up to 90         | +10        | Speed II, Haste I, Strength II | Last Stand: at half health, the Armor of the |
/// |       |              |            |                  |            |                                | Ancient and Regeneration rise up unbidden    |
///
/// Past what comes back on its own (see BloodPool), blood has to be fed for.
///
/// Days are counted in game ticks since the turning, so sleeping through the night doesn't age a vampire faster.
/// Dying sets one back a tier (ten days, from the cap); being cured starts the count over.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class VampireAge {
    public static final int TICKS_PER_DAY = 24000;
    public static final int DAYS_PER_TIER = 10;
    public static final int MAX_DAYS = 30;
    public static final int MAX_TIER = MAX_DAYS / DAYS_PER_TIER;
    /// What each tier does to blood skill costs, and adds to max health
    private static final float[] COST_MULTIPLIER = {1.0F, 0.95F, 0.90F, 0.80F};
    private static final double[] EXTRA_HEALTH = {0.0, 4.0, 7.0, 10.0};
    /// How much blood each tier holds, and how much of it comes back on its own
    private static final int[] MAX_BLOOD = {100, 110, 150, 200};
    private static final int[] NATURAL_BLOOD = {30, 36, 60, 90};
    /// Amplifiers for the always-on effects at each tier, -1 for none
    private static final int[] SPEED = {-1, 0, 0, 1};
    private static final int[] HASTE = {-1, -1, 0, 0};
    private static final int[] STRENGTH = {-1, -1, 0, 1};
    /// The effects outlast the refresh, so they never flicker
    private static final int EFFECT_TICKS = 40;
    private static final Identifier HEALTH_ID = CrimsonCovenant.id("vampire_age.health");
    /// Last Stand: how long the Regeneration lasts, how strong, and how long before it can rise again
    private static final int LAST_STAND_REGENERATION_TICKS = 200;
    private static final int LAST_STAND_REGENERATION = 1;
    public static final int LAST_STAND_COOLDOWN = 2400;
    /// Not started: the vampire's age begins counting at its next tick
    public static final long NOT_STARTED = Long.MIN_VALUE;

    private VampireAge() {}

    /// The turning: the count begins
    public static void start(Player player) {
        player.setData(ModAttachments.VAMPIRE_SINCE, player.level().getGameTime());
        player.setData(ModAttachments.VAMPIRE_DAYS, 0);
    }

    /// Cured: no age at all, and none of its strength
    public static void clear(Player player) {
        player.removeData(ModAttachments.VAMPIRE_SINCE);
        player.setData(ModAttachments.VAMPIRE_DAYS, 0);
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(HEALTH_ID);
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        }
    }

    /// Days survived as a vampire, up to the cap. On the client, as last sent by the server.
    public static int days(Player player) {
        long since = player.getData(ModAttachments.VAMPIRE_SINCE);
        if (player.level().isClientSide() || since == NOT_STARTED) {
            return player.getData(ModAttachments.VAMPIRE_DAYS);
        }
        return (int) Math.min(MAX_DAYS, Math.max(0L, (player.level().getGameTime() - since) / TICKS_PER_DAY));
    }

    /// Sets the vampire's age to `days` days (for the age command and tests)
    public static void setDays(Player player, int days) {
        player.setData(ModAttachments.VAMPIRE_SINCE, player.level().getGameTime() - (long) Math.clamp(days, 0, MAX_DAYS) * TICKS_PER_DAY);
        player.setData(ModAttachments.VAMPIRE_DAYS, days(player));
        // A younger vampire holds less: anything past that is lost
        BloodPool.set(player, BloodPool.get(player));
    }

    public static int tier(Player player) {
        return tierAt(days(player));
    }

    public static int tierAt(int days) {
        return Math.min(MAX_TIER, Math.max(0, days) / DAYS_PER_TIER);
    }

    /// What a vampire of this tier is called: Neo (day 10), Unusual (day 20), Ancient (day 30). A fledgling (tier 0)
    /// has no name yet.
    public static Component tierName(int tier) {
        return Component.translatable("vampire_age.crimsoncovenant.tier." + tier);
    }

    /// A vampire of 30 days or more
    public static boolean isAncient(Player player) {
        return Vampirism.isVampire(player) && tier(player) >= MAX_TIER;
    }

    /// What a blood skill (or a blood charge, or a blow turned by Defy Death) costs a vampire of this age: a share of
    /// the full cost, rounded up, never less than 1 for something that costs anything
    public static int discounted(int cost, Player player) {
        if (cost <= 0) {
            return cost;
        }
        return Math.max(1, (int) Math.ceil(cost * COST_MULTIPLIER[tier(player)] - 1.0E-4));
    }

    /// The most blood a vampire of this age can hold. On the client, as of the age last sent by the server.
    public static int maxBlood(Player player) {
        return MAX_BLOOD[tier(player)];
    }

    /// Blood stops coming back on its own here; the rest has to be fed for
    public static int naturalBlood(Player player) {
        return NATURAL_BLOOD[tier(player)];
    }

    public static double extraHealth(int tier) {
        return EXTRA_HEALTH[tier];
    }

    /// Every second for a living vampire: keeps the count (and its client's copy) up to date, announces a new tier, and
    /// keeps the tier's strength on them
    public static void tick(ServerPlayer player) {
        if (player.getData(ModAttachments.VAMPIRE_SINCE) == NOT_STARTED) {
            // A vampire from before vampires aged: its count begins now
            start(player);
        }
        int days = days(player);
        int known = player.getData(ModAttachments.VAMPIRE_DAYS);
        if (days != known) {
            player.setData(ModAttachments.VAMPIRE_DAYS, days);
            if (tierAt(days) > tierAt(known)) {
                player.sendSystemMessage(Component.translatable("message.crimsoncovenant.vampire_age.grows." + tierAt(days), tierName(tierAt(days))).withStyle(ChatFormatting.DARK_RED));
                player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4F, 1.6F);
            }
        }
        int tier = tierAt(days);
        applyHealth(player, tier);
        addEffect(player, MobEffects.SPEED, SPEED[tier]);
        addEffect(player, MobEffects.HASTE, HASTE[tier]);
        addEffect(player, MobEffects.STRENGTH, STRENGTH[tier]);
    }

    private static void addEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
        if (amplifier >= 0) {
            player.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, amplifier, true, false, true));
        }
    }

    // The extra health, as a modifier put back every second (it isn't saved), changed only when the tier changes
    private static void applyHealth(ServerPlayer player, int tier) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        double extra = EXTRA_HEALTH[tier];
        AttributeModifier current = health.getModifier(HEALTH_ID);
        if (current != null && current.amount() == extra) {
            return;
        }
        health.removeModifier(HEALTH_ID);
        if (extra > 0.0) {
            health.addTransientModifier(new AttributeModifier(HEALTH_ID, extra, AttributeModifier.Operation.ADD_VALUE));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /// Death costs a vampire a tier: ten days off its age (from the cap, for an old one)
    public static void onDeath(Player player) {
        setDays(player, Math.max(0, days(player) - DAYS_PER_TIER));
    }

    /// Last Stand: an old vampire (day 30 on) brought to half health or less has the Armor of the Ancient rise up over
    /// it unbidden, and its wounds start to close. It can't happen again for two minutes.
    @SubscribeEvent
    static void lastStand(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive() && Vampirism.isVampire(player)) {
            tryLastStand(player);
        }
    }

    /// Returns whether the Last Stand rose
    public static boolean tryLastStand(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (tier(player) < MAX_TIER || player.getHealth() > player.getMaxHealth() * 0.5F || player.hasEffect(ModEffects.ANCIENT_ARMOR)
                || now < player.getData(ModAttachments.LAST_STAND_READY_AT)) {
            return false;
        }
        player.setData(ModAttachments.LAST_STAND_READY_AT, now + LAST_STAND_COOLDOWN);
        BloodSkills.cast(BloodSkill.ARMOR_OF_ANCIENT, player, 0, false);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, LAST_STAND_REGENERATION_TICKS, LAST_STAND_REGENERATION));
        player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.vampire_age.last_stand").withStyle(ChatFormatting.DARK_RED));
        return true;
    }
}
