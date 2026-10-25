package com.zestudio.crimsoncovenant.effect;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// The Potion of Ephemeral (ectoplasm, boiled down): the drinker becomes a ghost. They float, and drift through
/// walls, floors and ceilings like a spectator, seen by others as a faint shape. When it wears off inside a wall,
/// they're pushed out to the nearest open space rather than left to suffocate.
/// Nothing can lay a hand on a ghost: blows from creatures and players miss, and arrows and other projectiles fly
/// straight through. Only what hurts ghosts (holy fire) still does, along with harm nobody dealt, like a fall.
///
/// Players only: the potion can't be thrown. Walking through walls itself happens in PlayerMixin, which turns off
/// the player's collisions right where vanilla decides whether a spectator has them.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class EphemeralEffect extends MobEffect {
    /// How far to look for open space when the effect ends inside a wall
    private static final int ESCAPE_RANGE = 8;

    public EphemeralEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xBFE8F0);
        this.addAttributeModifier(NeoForgeMod.CREATIVE_FLIGHT, CrimsonCovenant.id("effect.ephemeral"), 1.0, AttributeModifier.Operation.ADD_VALUE);
    }

    public static boolean isGhost(Player player) {
        return !player.isSpectator() && player.hasEffect(ModEffects.EPHEMERAL);
    }

    /// Called from PlayerMixin every tick, on both sides, just after vanilla sets up a spectator's collisions
    public static void tickGhostBody(Player player) {
        if (!isGhost(player)) {
            return;
        }
        player.noPhysics = true;
        player.setOnGround(false);
        // Always afloat: a ghost that stopped flying would sink through the floor
        if (!player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
    }

    @SubscribeEvent
    static void untouchable(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (event.getEntity() instanceof Player player && isGhost(player) && source.getEntity() != null && source.getEntity() != player
                && !source.is(ModTags.DamageTypes.HURTS_GHOSTS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void passThrough(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof Player player && isGhost(player)
                && event.getProjectile().getOwner() != player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void tickGhost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean ghost = isGhost(player);
        boolean wasGhost = player.getData(ModAttachments.GHOSTLY);
        if (ghost != wasGhost) {
            player.setData(ModAttachments.GHOSTLY, ghost);
            if (!ghost) {
                becomeSolid(player);
            }
        }
        if (ghost && player.tickCount % 4 == 0) {
            player.level().sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY(0.5), player.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
        }
    }

    /// Solid again: out of any wall, and gently down to the ground
    private static void becomeSolid(ServerPlayer player) {
        ServerLevel level = player.level();
        if (!player.isSpectator() && !level.noCollision(player, player.getBoundingBox())) {
            Vec3 open = findOpenSpace(player);
            if (open != null) {
                player.teleportTo(open.x, open.y, open.z);
            }
        }
        SoftLanding.begin(player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    // The nearest spot the player fits, closest first
    private static @Nullable Vec3 findOpenSpace(ServerPlayer player) {
        ServerLevel level = player.level();
        for (BlockPos pos : BlockPos.withinManhattan(player.blockPosition(), ESCAPE_RANGE)) {
            Vec3 feet = Vec3.atBottomCenterOf(pos);
            AABB box = player.getDimensions(player.getPose()).makeBoundingBox(feet);
            if (level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos) && level.noCollision(player, box)) {
                return feet;
            }
        }
        return null;
    }
}
