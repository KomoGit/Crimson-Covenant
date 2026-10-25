package com.zestudio.crimsoncovenant.vampirism;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Ghost;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/// What the night's creatures make of an Ancient vampire (30 days and more, see VampireAge):
/// - ghosts never go after it;
/// - zombies and skeletons take it for one of their own, and leave it be, unless it strikes them first (one it's
///   struck will fight it);
/// - spiders are its allies: they won't turn on it unless it turns on them, and those nearby join its fights, going
///   after whatever it strikes and whatever strikes it.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class AncientKinship {
    /// How far off spiders come to an Ancient's aid
    private static final double SPIDER_RANGE = 16.0;

    private AncientKinship() {}

    /// Whether `mob` leaves this Ancient vampire alone
    public static boolean spares(Mob mob, Player ancient) {
        if (!VampireAge.isAncient(ancient)) {
            return false;
        }
        if (mob instanceof Ghost) {
            return true;
        }
        boolean kin = mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof Spider;
        // Kin, unless the vampire struck it
        return kin && mob.getLastHurtByMob() != ancient;
    }

    @SubscribeEvent
    static void spareTheAncient(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getNewAboutToBeSetTarget() instanceof Player player && spares(mob, player)) {
            event.setCanceled(true);
        }
    }

    // Spiders join an Ancient's fights: against whatever it strikes, and whatever strikes it
    @SubscribeEvent
    static void spidersJoinTheFight(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != victim && VampireAge.isAncient(attacker)) {
            callSpiders(attacker, victim);
        } else if (victim instanceof ServerPlayer ancient && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != ancient
                && VampireAge.isAncient(ancient)) {
            callSpiders(ancient, attacker);
        }
    }

    /// Sends the spiders near `ancient` after `foe` (never after another spider, nor after the Ancient)
    public static void callSpiders(Player ancient, LivingEntity foe) {
        if (foe instanceof Spider || !foe.isAlive()) {
            return;
        }
        for (Spider spider : ancient.level().getEntitiesOfClass(Spider.class, ancient.getBoundingBox().inflate(SPIDER_RANGE),
                spider -> spider.isAlive() && spider.getTarget() != foe && spider.getLastHurtByMob() != ancient)) {
            spider.setTarget(foe);
        }
    }
}
