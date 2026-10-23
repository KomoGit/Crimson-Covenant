package com.zestudio.crimsoncovenant.vampirism;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/// A vampire player's blood: the "mana" blood skills are paid with, shown as a bar of drops where the hunger bar
/// would be (vampires don't hunger).
/// It refills slowly on its own, and much faster by killing living things: like a vampire mob feeding, the
/// victim's blood streams into the player. Undead have no blood to give.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class BloodPool {
    public static final int MAX_BLOOD = 100;
    /// One point every this many ticks: an empty bar refills in a little over 3 minutes
    private static final int REGEN_INTERVAL = 40;
    private static final float BLOOD_PER_VICTIM_HEALTH = 0.5F;
    private static final int MIN_KILL_BLOOD = 2;
    private static final int MAX_KILL_BLOOD = 25;
    /// Every kill also heals half a heart
    public static final float KILL_HEAL = 1.0F;

    private BloodPool() {}

    public static int get(Player player) {
        return player.getData(ModAttachments.BLOOD);
    }

    public static void set(Player player, int blood) {
        int clamped = Mth.clamp(blood, 0, MAX_BLOOD);
        if (clamped != get(player)) {
            player.setData(ModAttachments.BLOOD, clamped);
        }
    }

    public static void add(Player player, int blood) {
        set(player, get(player) + blood);
    }

    /// Called every tick for vampire players. A Silver Wound stops the blood coming back.
    public static void tick(ServerPlayer player) {
        if (player.tickCount % REGEN_INTERVAL == 0 && !player.hasEffect(ModEffects.SILVER_WOUND)) {
            add(player, 1);
        }
    }

    /// How much blood a kill is worth: half the victim's max health, so a zombie gives 10 and a chicken 2
    public static int bloodFrom(LivingEntity victim) {
        return Mth.clamp(Math.round(victim.getMaxHealth() * BLOOD_PER_VICTIM_HEALTH), MIN_KILL_BLOOD, MAX_KILL_BLOOD);
    }

    @SubscribeEvent
    static void onKill(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        // getEntity() is the attacker even for projectiles (the shooter), so skill kills count too
        if (event.getSource().getEntity() instanceof ServerPlayer player && player != victim && Vampirism.isVampire(player)
                && Vampirism.hasLivingBlood(victim) && victim.level() instanceof ServerLevel level) {
            add(player, bloodFrom(victim));
            player.heal(KILL_HEAL);
            Vampire.streamBlood(level, victim, player);
        }
    }
}
