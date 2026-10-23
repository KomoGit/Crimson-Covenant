package com.zestudio.crimsoncovenant.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zestudio.crimsoncovenant.entity.BloodOrb;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// Afterimage Strike (upgraded Dash). Every tick of the dash leaves an afterimage where the player is, lingering as
/// long as the ones drawn on screen (see client.renderer.DashAfterimages). Each afterimage slashes every hostile it
/// touches, once: a creature the dash runs straight through is caught by several of them, one it only brushes past
/// by one. A creature's slashes land one at a time, SLASH_INTERVAL apart, each getting past its hurt immunity.
public final class AfterimageStrike {
    public static final float DAMAGE = 5.0F;
    /// 0.2 seconds between one creature's slashes
    public static final int SLASH_INTERVAL = 4;
    public static final int LINGER_TICKS = 8;
    /// How far an afterimage reaches sideways from its middle, besides the victim's own width
    private static final double REACH = 1.8;

    /// A player's afterimages, and the slashes they still owe each creature (by entity id)
    public static final class Trail {
        private final List<Afterimage> afterimages = new ArrayList<>();
        private final Map<Integer, OwedSlashes> owed = new HashMap<>();
    }

    private record Afterimage(AABB box, Vec3 middle, long formedAt, Set<Integer> struck) {}

    private static final class OwedSlashes {
        private int left;
        private long next;

        private OwedSlashes(long next) {
            this.next = next;
        }
    }

    private AfterimageStrike() {}

    /// Called on every tick of an Afterimage Strike dash
    public static void leaveAfterimage(ServerPlayer player) {
        player.getData(ModAttachments.DASH_AFTERIMAGES).afterimages.add(
                new Afterimage(player.getBoundingBox(), player.position(), player.level().getGameTime(), new HashSet<>()));
    }

    /// Called every tick for every player: afterimages catch whoever they touch, and owed slashes land
    public static void tick(ServerPlayer player) {
        if (!player.hasData(ModAttachments.DASH_AFTERIMAGES)) {
            return;
        }
        Trail trail = player.getData(ModAttachments.DASH_AFTERIMAGES);
        ServerLevel level = player.level();
        long now = level.getGameTime();
        trail.afterimages.removeIf(image -> now - image.formedAt() > LINGER_TICKS);
        for (Afterimage image : trail.afterimages) {
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, image.box().inflate(REACH, 0.0, REACH),
                    entity -> BloodOrb.isHostile(entity, player) && touches(image, entity))) {
                if (image.struck().add(victim.getId())) {
                    trail.owed.computeIfAbsent(victim.getId(), id -> new OwedSlashes(now)).left++;
                }
            }
        }
        Iterator<Map.Entry<Integer, OwedSlashes>> owed = trail.owed.entrySet().iterator();
        while (owed.hasNext()) {
            Map.Entry<Integer, OwedSlashes> entry = owed.next();
            OwedSlashes slashes = entry.getValue();
            if (now < slashes.next) {
                continue;
            }
            // Kept until its 0.2 seconds are up even with nothing owed, so a slash from a newer afterimage still waits
            if (slashes.left > 0 && level.getEntity(entry.getKey()) instanceof LivingEntity victim && victim.isAlive()) {
                slash(level, player, victim);
                slashes.left--;
                slashes.next = now + SLASH_INTERVAL;
            } else {
                owed.remove();
            }
        }
        if (trail.afterimages.isEmpty() && trail.owed.isEmpty()) {
            player.removeData(ModAttachments.DASH_AFTERIMAGES);
        }
    }

    // Measured flat, from the afterimage's middle: the closer the dash passes, the more afterimages reach
    private static boolean touches(Afterimage image, LivingEntity victim) {
        double reach = REACH + victim.getBbWidth() * 0.5;
        double dx = victim.getX() - image.middle().x;
        double dz = victim.getZ() - image.middle().z;
        return dx * dx + dz * dz <= reach * reach;
    }

    private static void slash(ServerLevel level, ServerPlayer player, LivingEntity victim) {
        victim.damageCooldownTime = 0;
        victim.hurtServer(level, level.damageSources().source(ModDamageTypes.BLOOD_MAGIC, player), DAMAGE);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.6), victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.4F), victim.getX(), victim.getY(0.6), victim.getZ(), 10, 0.2, 0.3, 0.2, 0.0);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);
    }
}
