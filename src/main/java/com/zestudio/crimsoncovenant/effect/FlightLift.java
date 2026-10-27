package com.zestudio.crimsoncovenant.effect;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.Lift;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.skill.Enthrall;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// A vampire on the wing (Flight, or gliding in the Crimson Shroud) who flies through a creature snatches it up: they
/// hold it up in front of them wherever they fly until they right-click (see drop), land or lose their wings, then it
/// falls from however high they took it. While their hands are full they can't Blood Charge. One creature at a time,
/// and never their own pets or thralls. Some things are too big or too heavy to carry (the
/// cannot_be_lifted tag: the Warden, Iron Golems, the Wither, the Ender Dragon and gargoyles).
///
/// The server holds it where it should be each tick, and every client holds it there again once the carrier has moved
/// (see client.LiftedHold), so it keeps up with them instead of trailing behind.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class FlightLift {
    /// How far from a held creature its carrier can ever be
    private static final double GRAB_RANGE = 8.0;
    /// How close a vampire has to pass to a creature to snatch it up, beyond touching it
    private static final double REACH = 0.4;
    /// How long a creature that's been let go can't be snatched up again
    public static final int REGRAB_TICKS = 30;
    /// How long after a drop a death from the fall still counts as the vampire's kill
    public static final int DROP_CREDIT_TICKS = 200;
    /// How high the top of a held creature is, as a share of the vampire's height
    private static final double HOLD_HEIGHT = 0.7;
    /// The gap between the vampire and a held creature
    private static final double HOLD_GAP = 0.7;
    /// For a player carried off, who has to be pulled along instead: how much of the way to where they should be they
    /// cover each tick, and no faster than this (blocks a tick)
    private static final double PULL = 0.6;
    private static final double MAX_SPEED = 2.0;

    private FlightLift() {}

    /// On the wing: in Flight, or gliding in the Crimson Shroud, and off the ground
    public static boolean isOnTheWing(Player player) {
        return !player.onGround() && (player.hasEffect(ModEffects.BLOOD_FLIGHT) || (CrimsonShroudEffect.isShrouded(player) && player.isFallFlying()));
    }

    public static boolean canLift(LivingEntity target, Player carrier) {
        return target != carrier && target.isAlive() && !target.is(ModTags.Entities.CANNOT_BE_LIFTED) && !isLifted(target)
                && !target.hasPassenger(carrier) && !carrier.hasPassenger(target) && !(target instanceof ArmorStand)
                && !(target instanceof Player player && (player.isSpectator() || player.isCreative()))
                && !(target instanceof OwnableEntity pet && carrier.equals(pet.getOwner())) && !Enthrall.isThrallOf(target, carrier)
                && target.getData(ModAttachments.DROPPED) + REGRAB_TICKS <= target.level().getGameTime() && !isCarrying(carrier);
    }

    public static boolean isLifted(LivingEntity entity) {
        return entity.getData(ModAttachments.LIFTED).isPresent();
    }

    public static boolean isCarriedBy(LivingEntity entity, Player carrier) {
        return entity.getData(ModAttachments.LIFTED).filter(lift -> lift.carrier().equals(carrier.getUUID())).isPresent();
    }

    /// What `carrier` holds, if anything (it's never far from them)
    public static Optional<LivingEntity> carried(Player carrier) {
        return carrier.level().getEntitiesOfClass(LivingEntity.class, carrier.getBoundingBox().inflate(GRAB_RANGE), entity -> isCarriedBy(entity, carrier))
                .stream().findFirst();
    }

    /// Whether `carrier` has their hands full
    public static boolean isCarrying(Player carrier) {
        return carried(carrier).isPresent();
    }

    /// Lets go of whatever `carrier` holds, the right click of a vampire with their hands full. Returns whether they
    /// held anything.
    public static boolean drop(Player carrier) {
        Optional<LivingEntity> held = carried(carrier);
        held.ifPresent(FlightLift::letGo);
        return held.isPresent();
    }

    // It falls from here, and the fall's counted from here. If the fall kills it, it's the vampire's kill.
    private static void letGo(LivingEntity held) {
        Optional<UUID> carrier = held.getData(ModAttachments.LIFTED).map(Lift::carrier);
        held.setData(ModAttachments.LIFTED, Optional.empty());
        held.setData(ModAttachments.DROPPED, held.level().getGameTime());
        held.setData(ModAttachments.DROPPED_BY, carrier);
        carrier.ifPresent(player -> held.setLastHurtByPlayer(player, DROP_CREDIT_TICKS));
    }

    /// The vampire whose drop killed `victim`, if `source` is the fall from it: they let go of it within the last
    /// DROP_CREDIT_TICKS
    public static @Nullable Player droppedBy(LivingEntity victim, DamageSource source) {
        if (!source.is(DamageTypeTags.IS_FALL) || victim.level().getGameTime() - victim.getData(ModAttachments.DROPPED) > DROP_CREDIT_TICKS) {
            return null;
        }
        return victim.getData(ModAttachments.DROPPED_BY).map(victim.level()::getPlayerByUUID).orElse(null);
    }

    /// Carries `target` off in front of `carrier`, if it can be. Returns whether it was.
    public static boolean lift(LivingEntity target, Player carrier) {
        if (!canLift(target, carrier) || !(target.level() instanceof ServerLevel level)) {
            return false;
        }
        if (target.isPassenger()) {
            target.stopRiding();
        }
        target.setData(ModAttachments.LIFTED, Optional.of(new Lift(carrier.getUUID())));
        // No charging on with their hands full
        carrier.removeData(ModAttachments.BLOOD_CHARGE);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.0F, 0.8F);
        return true;
    }

    /// Where a creature held by a vampire at `carrier` (their feet), facing `yaw`, has its feet: just in front of them,
    /// held up with its top at their chest, so they can see what they've caught
    public static Vec3 holdPoint(Vec3 carrier, float yaw, Player holder, LivingEntity held) {
        double ahead = holder.getBbWidth() / 2.0 + held.getBbWidth() / 2.0 + HOLD_GAP;
        float radians = yaw * Mth.DEG_TO_RAD;
        return carrier.add(-Mth.sin(radians) * ahead, holder.getBbHeight() * HOLD_HEIGHT - held.getBbHeight(), Mth.cos(radians) * ahead);
    }

    /// Puts a held creature where it should be, facing its carrier: `old` for where the carrier was at the start of the
    /// tick too (so it's drawn moving with them), as each client does
    public static void hold(LivingEntity held, Player carrier, boolean old) {
        Vec3 at = holdPoint(carrier.position(), carrier.getYRot(), carrier, held);
        float facing = Mth.wrapDegrees(carrier.getYRot() + 180.0F);
        if (old) {
            Vec3 was = holdPoint(new Vec3(carrier.xOld, carrier.yOld, carrier.zOld), carrier.yRotO, carrier, held);
            held.xo = held.xOld = was.x;
            held.yo = held.yOld = was.y;
            held.zo = held.zOld = was.z;
            held.yRotO = held.yBodyRotO = held.yHeadRotO = Mth.wrapDegrees(carrier.yRotO + 180.0F);
        }
        held.setPos(at);
        held.setYRot(facing);
        held.yBodyRot = facing;
        held.yHeadRot = facing;
        held.setDeltaMovement(Vec3.ZERO);
    }

    /// Snatches up the nearest creature `carrier` has flown through since last tick, if they're on the wing and can.
    /// Returns whether they did.
    public static boolean snatch(Player carrier) {
        if (!isOnTheWing(carrier) || !(carrier.level() instanceof ServerLevel level) || isCarrying(carrier)) {
            return false;
        }
        // All the way from where they were, or a vampire flying flat out would pass clean over things
        AABB swept = carrier.getBoundingBox().expandTowards(carrier.xOld - carrier.getX(), carrier.yOld - carrier.getY(), carrier.zOld - carrier.getZ())
                .inflate(REACH);
        return level.getEntitiesOfClass(LivingEntity.class, swept, target -> canLift(target, carrier)).stream()
                .min(Comparator.comparingDouble(target -> target.distanceToSqr(carrier)))
                .map(target -> lift(target, carrier))
                .orElse(false);
    }

    @SubscribeEvent
    static void flyThrough(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) {
            snatch(event.getEntity());
        }
    }

    @SubscribeEvent
    static void carry(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Lift> lift = target.getData(ModAttachments.LIFTED);
        if (lift.isEmpty()) {
            return;
        }
        Entity found = level.getEntity(lift.get().carrier());
        if (!(found instanceof Player carrier) || !carrier.isAlive() || !target.isAlive() || !isOnTheWing(carrier) || carrier.level() != level) {
            letGo(target);
            return;
        }
        if (target instanceof Player) {
            // A player's client says where they are, so they're pulled along rather than put there
            Vec3 pull = holdPoint(carrier.position(), carrier.getYRot(), carrier, target).subtract(target.position()).scale(PULL);
            if (pull.length() > MAX_SPEED) {
                pull = pull.normalize().scale(MAX_SPEED);
            }
            target.setDeltaMovement(pull);
            target.syncVelocity = true;
        } else {
            hold(target, carrier, false);
            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
            }
        }
        target.resetFallDistance();
    }
}
