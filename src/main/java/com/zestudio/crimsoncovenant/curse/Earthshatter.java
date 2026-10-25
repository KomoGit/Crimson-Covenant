package com.zestudio.crimsoncovenant.curse;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.WhipLash;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.skill.Enthrall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/// Gravequake (the Earthshatter haunting, whips only), in the manner of Reinhardt's: the lash comes down hard on the
/// ground before its wielder, and a quake rolls out from it in a wide arc, throwing down everything standing in its way.
/// The fallen lie flat for a few seconds (see effect.KnockedDownEffect), unable to move or land a blow, then get up.
/// Whatever's off the ground (flying, leaping, falling) rides it out, as does anything out of the wielder's sight.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Earthshatter {
    /// How long the fallen stay down
    public static final int KNOCKDOWN_TICKS = 60;
    /// How wide the arc is: the cosine of its half-angle (about 55 degrees either side of the wielder's aim)
    private static final double ARC = 0.57;
    /// Where the lash comes down, ahead of the wielder
    private static final double LASH_LANDS = 3.0;

    private Earthshatter() {}

    /// How far the quake rolls out, in blocks
    public static double range(int level) {
        return 6.0 + 2.0 * level;
    }

    /// What the quake does to those it throws down
    public static float damage(int level) {
        return 1.0F + level;
    }

    /// The lash comes down, and the quake rolls out. Returns how many it threw down.
    public static int quake(ServerLevel level, Player player, int curseLevel) {
        Vec3 look = player.getLookAngle().horizontal();
        look = look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()).horizontal() : look.normalize();
        double range = range(curseLevel);
        Vec3 feet = player.position();
        DamageSource quake = level.damageSources().indirectMagic(player, player);
        int thrown = 0;
        for (LivingEntity creature : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range, 2.0, range),
                entity -> standsInTheWay(player, entity))) {
            Vec3 toward = creature.position().subtract(feet).horizontal();
            if (toward.length() > range || toward.length() < 1.0E-3 || toward.normalize().dot(look) < ARC || !player.hasLineOfSight(creature)) {
                continue;
            }
            creature.hurtServer(level, quake, damage(curseLevel));
            knockDown(creature, player);
            thrown++;
        }
        // The lash, brought down on the ground ahead
        Vec3 lands = feet.add(look.scale(LASH_LANDS)).add(0.0, 0.1, 0.0);
        WhipLash.crack(player, WhipLash.Kind.SHOW, lands, WhipLash.LASH_TICKS);
        show(level, player, look, range);
        return thrown;
    }

    /// Whether the quake can throw this creature down: anything but the wielder and their own (thralls and pets), as long
    /// as it's standing on the ground
    private static boolean standsInTheWay(Player player, LivingEntity entity) {
        if (entity == player || !entity.isAlive() || entity.isSpectator() || !entity.onGround() || Enthrall.isThrallOf(entity, player)) {
            return false;
        }
        if (entity instanceof OwnableEntity pet && player.equals(pet.getOwner())) {
            return false;
        }
        return !(entity instanceof Player other && other.isCreative());
    }

    /// Throws the creature down for KNOCKDOWN_TICKS: it falls flat, and gets up again when they're up
    public static void knockDown(LivingEntity creature, Entity by) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        creature.addEffect(new MobEffectInstance(ModEffects.KNOCKED_DOWN, KNOCKDOWN_TICKS, 0, false, false, true), by);
        creature.setData(ModAttachments.KNOCKED_DOWN_UNTIL, level.getGameTime() + KNOCKDOWN_TICKS);
        creature.setDeltaMovement(0.0, Math.min(creature.getDeltaMovement().y, 0.0), 0.0);
        creature.syncVelocity = true;
        level.playSound(null, creature.getX(), creature.getY(), creature.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    public static boolean isDown(LivingEntity creature) {
        return creature.hasEffect(ModEffects.KNOCKED_DOWN);
    }

    /// How far round its standing box a fallen creature can be struck: it's drawn lying flat, its body stretched out
    /// along the ground to one side, so a blow aimed at that should land (see mixin.EntityMixin). 0 for one on its feet.
    public static float lyingReach(Entity entity) {
        if (!(entity instanceof LivingEntity creature) || creature.getData(ModAttachments.KNOCKED_DOWN_UNTIL) == 0L) {
            return 0.0F;
        }
        return Math.max(0.0F, creature.getBbHeight() - creature.getBbWidth() * 0.5F);
    }

    // The ground cracking open along the arc, rolling outward, with a deep crash and a ghostly wail under it
    private static void show(ServerLevel level, Player player, Vec3 look, double range) {
        Vec3 feet = player.position();
        double halfAngle = Math.acos(ARC);
        for (double distance = 1.5; distance <= range; distance += 1.0) {
            int across = Math.max(2, (int) (distance * halfAngle * 1.2));
            for (int i = -across; i <= across; i++) {
                double angle = halfAngle * i / across;
                Vec3 heading = new Vec3(look.x * Math.cos(angle) - look.z * Math.sin(angle), 0.0, look.x * Math.sin(angle) + look.z * Math.cos(angle));
                Vec3 at = feet.add(heading.scale(distance));
                BlockPos ground = BlockPos.containing(at.x, feet.y - 0.5, at.z);
                BlockState state = level.getBlockState(ground);
                if (state.isAir()) {
                    continue;
                }
                level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, state), at.x, feet.y + 0.1, at.z, 3, 0.2, 0.0, 0.2, 0.0);
                if (level.getRandom().nextInt(4) == 0) {
                    level.sendParticles(ParticleTypes.SOUL, at.x, feet.y + 0.2, at.z, 1, 0.1, 0.1, 0.1, 0.02);
                }
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.5F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    // The fallen can't land a blow (nor loose an arrow, nor blow up) until they're up again
    @SubscribeEvent
    static void theFallenCantFight(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && isDown(attacker)) {
            event.setCanceled(true);
        }
    }
}
