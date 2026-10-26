package com.zestudio.crimsoncovenant.vampirism;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.entity.BloodBat;
import com.zestudio.crimsoncovenant.entity.BloodOrb;

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
/// It refills slowly on its own, but only up to 3 drops; past that, and much faster, by killing living things: like a
/// vampire mob feeding, the victim's blood streams into the player. Undead have no blood to give.
/// Older vampires hold more, and more of it comes back on its own (see VampireAge).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class BloodPool {
    /// One point every this many ticks: 3 drops come back in a minute
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

    public static int max(Player player) {
        return VampireAge.maxBlood(player);
    }

    public static void set(Player player, int blood) {
        int clamped = Mth.clamp(blood, 0, max(player));
        if (clamped != get(player)) {
            player.setData(ModAttachments.BLOOD, clamped);
        }
    }

    public static void add(Player player, int blood) {
        set(player, get(player) + blood);
    }

    /// Called every tick for vampire players. A Silver Wound stops the blood coming back.
    public static void tick(ServerPlayer player) {
        if (player.tickCount % REGEN_INTERVAL == 0 && get(player) < VampireAge.naturalBlood(player) && !player.hasEffect(ModEffects.SILVER_WOUND)) {
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
        // getEntity() is the attacker even for projectiles (the shooter), so skill kills count too: all but the Blood
        // Parade's (its orb's and its bats'), which give back health and more bats, never blood
        if (event.getSource().getEntity() instanceof ServerPlayer player && player != victim && Vampirism.isVampire(player)
                && Vampirism.hasLivingBlood(victim) && !(event.getSource().getDirectEntity() instanceof BloodOrb)
                && !(event.getSource().getDirectEntity() instanceof BloodBat) && victim.level() instanceof ServerLevel level) {
            add(player, bloodFrom(victim));
            player.heal(KILL_HEAL);
            Vampire.streamBlood(level, victim, player);
        }
    }
}
