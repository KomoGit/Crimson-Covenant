package com.zestudio.crimsoncovenant.skill;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.effect.BloodFlightEffect;
import com.zestudio.crimsoncovenant.entity.BloodOrb;
import com.zestudio.crimsoncovenant.entity.BloodSpear;
import com.zestudio.crimsoncovenant.entity.HemorrhageZone;
import com.zestudio.crimsoncovenant.entity.SwarmBat;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.item.EphemeralSwordItem;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.DashState;
import com.zestudio.crimsoncovenant.registry.ModAttachments.ShadowAnchor;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/// Casting the skills a player wears (see SkillSlots), and what each one does
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class BloodSkills {
    // Dash: 10 blocks, covered at 2 blocks a tick
    private static final int DASH_TICKS = 5;
    private static final double DASH_SPEED = 2.0;

    private static final int ANCIENT_ARMOR_DURATION = 200;
    /// One full Minecraft day
    private static final int DAYWALKER_DURATION = 24000;
    private static final float SPEAR_SPEED = 2.8F;

    // Bat Swarm: the Master Vampire's swarm, sent the way the player is looking
    private static final double SWARM_RANGE = 24.0;

    private static final int DEFY_DEATH_DURATION = 100;
    public static final int DEFY_DEATH_COST_PER_HIT = 10;

    public static final double SHADOW_STEP_RANGE = 100.0;
    private static final int STALKERS_GAZE_DURATION = 600;

    private BloodSkills() {}

    /// Casts the skill worn in this slot, if the player can: pays its blood and starts its cooldown
    public static boolean tryCast(ServerPlayer player, int slot) {
        ItemStack stack = SkillSlots.get(player, slot);
        if (!(stack.getItem() instanceof SkillItem skillItem)) {
            refuse(player, Component.translatable("message.crimsoncovenant.skill.empty_slot"));
            return false;
        }
        if (player.getCooldowns().isOnCooldown(stack)) {
            return false;
        }
        BloodSkill skill = skillItem.skill();
        int cost = costFor(skill, player);
        String refusal = whyCantCast(skill, player, cost);
        if (refusal != null) {
            refuse(player, Component.translatable(refusal, BloodPool.get(player), cost));
            return false;
        }
        int spent = skill.spendsAllBlood() ? BloodPool.get(player) : cost;
        if (!cast(skill, player, spent)) {
            return false;
        }
        BloodPool.set(player, BloodPool.get(player) - spent);
        player.getCooldowns().addCooldown(stack, skill.cooldown());
        return true;
    }

    /// Blood the skill would cost right now. Shadow Step's anchor is placed for free.
    public static int costFor(BloodSkill skill, Player player) {
        return skill == BloodSkill.SHADOW_STEP && placesAnchor(player) ? 0 : skill.cost();
    }

    /// The message key explaining why the player can't cast this right now, or null if they can
    public static @Nullable String whyCantCast(BloodSkill skill, Player player, int cost) {
        if (!Vampirism.isVampire(player)) {
            return "message.crimsoncovenant.skill.not_a_vampire";
        }
        Holder<MobEffect> effect = lastingEffect(skill);
        if (effect != null && player.hasEffect(effect)) {
            return "message.crimsoncovenant.skill.already_active";
        }
        if (BloodPool.get(player) < cost) {
            return "message.crimsoncovenant.skill.not_enough_blood";
        }
        return null;
    }

    /// The effect a skill leaves on the caster while it lasts, if any
    private static @Nullable Holder<MobEffect> lastingEffect(BloodSkill skill) {
        return switch (skill) {
            case FLIGHT -> ModEffects.BLOOD_FLIGHT;
            case ARMOR_OF_ANCIENT -> ModEffects.ANCIENT_ARMOR;
            case DAYWALKER -> ModEffects.DAYWALKER;
            case DEFY_DEATH -> ModEffects.DEFY_DEATH;
            case STALKERS_GAZE -> ModEffects.STALKERS_GAZE;
            default -> null;
        };
    }

    /// Does what the skill does. False if it couldn't (and nothing should be paid), having told the player why.
    public static boolean cast(BloodSkill skill, ServerPlayer player, int bloodSpent) {
        ServerLevel level = player.level();
        switch (skill) {
            case FLIGHT -> {
                player.addEffect(new MobEffectInstance(ModEffects.BLOOD_FLIGHT, BloodFlightEffect.DURATION));
                if (player.onGround()) {
                    // Off the ground first: the game drops a flying player out of flight as soon as they touch it
                    player.setDeltaMovement(player.getDeltaMovement().x, 0.6, player.getDeltaMovement().z);
                    player.syncVelocity = true;
                }
                BloodFlightEffect.startFlying(player);
                burst(level, player, 30);
                playSound(player, SoundEvents.PHANTOM_FLAP, 0.7F);
            }
            case DASH -> {
                Vec3 direction = player.getLookAngle();
                if (player.onGround() && direction.y < 0.0) {
                    direction = new Vec3(direction.x, 0.0, direction.z);
                }
                if (direction.lengthSqr() < 1.0E-4) {
                    direction = Vec3.directionFromRotation(0.0F, player.getYRot());
                }
                player.setData(ModAttachments.DASH, new DashState(direction.normalize().scale(DASH_SPEED), DASH_TICKS));
                playSound(player, SoundEvents.BREEZE_JUMP, 1.4F);
            }
            case ARMOR_OF_ANCIENT -> {
                player.addEffect(new MobEffectInstance(ModEffects.ANCIENT_ARMOR, ANCIENT_ARMOR_DURATION));
                burst(level, player, 50);
                playSound(player, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.6F);
            }
            case BLOOD_SPEAR -> {
                BloodSpear spear = new BloodSpear(level, player);
                spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, SPEAR_SPEED, 0.5F);
                level.addFreshEntity(spear);
                playSound(player, SoundEvents.TRIDENT_THROW.value(), 0.7F);
            }
            case DAYWALKER -> {
                player.addEffect(new MobEffectInstance(ModEffects.DAYWALKER, DAYWALKER_DURATION));
                player.clearFire();
                burst(level, player, 30);
                playSound(player, SoundEvents.BEACON_POWER_SELECT, 0.6F);
            }
            case BLOOD_PARADE -> {
                Vec3 direction = player.getLookAngle();
                BloodOrb orb = new BloodOrb(level, player, direction, bloodSpent);
                orb.setPos(player.getX() + direction.x, player.getEyeY() - 0.3 + direction.y, player.getZ() + direction.z);
                level.addFreshEntity(orb);
                playSound(player, SoundEvents.WARDEN_SONIC_CHARGE, 1.3F);
            }
            case BAT_SWARM -> {
                Vec3 direction = player.getLookAngle();
                SwarmBat.launchSwarm(level, player, player.getEyePosition().add(direction), direction, SWARM_RANGE);
                playSound(player, SoundEvents.BAT_TAKEOFF, 0.7F);
            }
            case NIGHT_OF_TERROR -> {
                NightOfTerror.unleash(level, player);
            }
            case DEFY_DEATH -> {
                player.addEffect(new MobEffectInstance(ModEffects.DEFY_DEATH, DEFY_DEATH_DURATION));
                burst(level, player, 30);
                playSound(player, SoundEvents.WITHER_SPAWN, 1.6F);
            }
            case POSSESS -> {
                return Possession.tryPossess(player);
            }
            case SHADOW_STEP -> {
                return shadowStep(player);
            }
            case EPHEMERAL_SWORD -> {
                return conjureSword(player);
            }
            case BOILING_HEMORRHAGE -> {
                // On the ground the vampire is looking at (up to 16 blocks away), or at their feet
                Vec3 eye = player.getEyePosition();
                BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(16.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                Vec3 at = hit.getType() == HitResult.Type.MISS ? player.position() : Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
                level.addFreshEntity(HemorrhageZone.create(level, player, at.x, at.y, at.z));
                playSound(player, SoundEvents.LAVA_AMBIENT, 0.6F);
            }
            case DEATH_MARCH -> {
                return DeathMarch.tryCast(player);
            }
            case STALKERS_GAZE -> {
                player.addEffect(new MobEffectInstance(ModEffects.STALKERS_GAZE, STALKERS_GAZE_DURATION));
                playSound(player, SoundEvents.WARDEN_HEARTBEAT, 0.6F);
            }
        }
        return true;
    }

    // ---- Dash ----

    /// Called every tick for every player: moves a dash along
    public static void tickDash(ServerPlayer player) {
        DashState dash = player.getData(ModAttachments.DASH);
        if (dash.ticksLeft() <= 0) {
            return;
        }
        int ticksLeft = dash.ticksLeft() - 1;
        // Ease off on the last tick so the player doesn't slide on past the 10 blocks
        player.setDeltaMovement(ticksLeft == 0 ? dash.velocity().scale(0.2) : dash.velocity());
        player.syncVelocity = true;
        player.resetFallDistance();
        player.level().sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.2F), player.getX(), player.getY(0.5), player.getZ(), 6, 0.25, 0.4, 0.25, 0.0);
        player.setData(ModAttachments.DASH, ticksLeft == 0 ? DashState.NONE : new DashState(dash.velocity(), ticksLeft));
    }

    // ---- Shadow Step ----

    /// With no anchor (or one in another dimension), or while sneaking, the skill places the anchor
    private static boolean placesAnchor(Player player) {
        Optional<ShadowAnchor> anchor = player.getData(ModAttachments.SHADOW_ANCHOR);
        return player.isShiftKeyDown() || anchor.isEmpty() || anchor.get().dimension() != player.level().dimension();
    }

    private static boolean shadowStep(ServerPlayer player) {
        ServerLevel level = player.level();
        if (placesAnchor(player)) {
            player.setData(ModAttachments.SHADOW_ANCHOR, Optional.of(new ShadowAnchor(level.dimension(), player.position())));
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.1, player.getZ(), 40, 0.4, 0.1, 0.4, 0.02);
            playSound(player, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 1.4F);
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.skill.anchor_placed").withStyle(ChatFormatting.DARK_PURPLE));
            return true;
        }
        Vec3 anchor = player.getData(ModAttachments.SHADOW_ANCHOR).orElseThrow().position();
        double distance = anchor.distanceTo(player.position());
        if (distance > SHADOW_STEP_RANGE) {
            refuse(player, Component.translatable("message.crimsoncovenant.skill.anchor_too_far", (int) distance, (int) SHADOW_STEP_RANGE));
            return false;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.5), player.getZ(), 30, 0.3, 0.6, 0.3, 0.02);
        player.teleportTo(anchor.x, anchor.y, anchor.z);
        // An anchor is good for one step: the next cast places a new one
        player.setData(ModAttachments.SHADOW_ANCHOR, Optional.empty());
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, anchor.x, anchor.y + 1.0, anchor.z, 40, 0.3, 0.6, 0.3, 0.05);
        playSound(player, SoundEvents.ENDERMAN_TELEPORT, 0.6F);
        return true;
    }

    /// Every half second, shows the player where their anchor is (only they can see it)
    public static void showAnchor(ServerPlayer player) {
        if (player.tickCount % 10 != 0) {
            return;
        }
        player.getData(ModAttachments.SHADOW_ANCHOR).ifPresent(anchor -> {
            if (anchor.dimension() == player.level().dimension() && anchor.position().distanceTo(player.position()) <= SHADOW_STEP_RANGE) {
                Vec3 at = anchor.position();
                player.level().sendParticles(player, ParticleTypes.REVERSE_PORTAL, true, false, at.x, at.y + 0.2, at.z, 6, 0.25, 0.1, 0.25, 0.01);
            }
        });
    }

    // ---- Ephemeral Sword ----

    /// A fresh sword in hand (or the inventory, if the hand is busy). Casting again replaces the old one.
    private static boolean conjureSword(ServerPlayer player) {
        EphemeralSwordItem.removeAll(player);
        ItemStack sword = EphemeralSwordItem.create(player.level().getGameTime());
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        } else if (!player.getInventory().add(sword)) {
            refuse(player, Component.translatable("message.crimsoncovenant.skill.no_room"));
            return false;
        }
        burst(player.level(), player, 20);
        playSound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F);
        return true;
    }

    // ---- Defy Death ----

    /// A deadly blow while Defy Death is up leaves the vampire on half a heart instead, for 10 blood. Out of blood,
    /// death takes them after all. Nothing defies /kill or the void.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void defyDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasEffect(ModEffects.DEFY_DEATH)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) && BloodPool.get(player) >= DEFY_DEATH_COST_PER_HIT) {
            event.setCanceled(true);
            player.setHealth(1.0F);
            BloodPool.set(player, BloodPool.get(player) - DEFY_DEATH_COST_PER_HIT);
            burst(player.level(), player, 40);
            playSound(player, SoundEvents.TOTEM_USE, 1.4F);
        }
    }

    // ---- Holy Water ----

    /// Holy Water washes every active blood skill off a vampire player
    public static void dispel(ServerPlayer player) {
        boolean dispelled = player.removeEffect(ModEffects.BLOOD_FLIGHT);
        dispelled |= player.removeEffect(ModEffects.ANCIENT_ARMOR);
        dispelled |= player.removeEffect(ModEffects.DAYWALKER);
        dispelled |= player.removeEffect(ModEffects.DEFY_DEATH);
        dispelled |= player.removeEffect(ModEffects.STALKERS_GAZE);
        dispelled |= Possession.endPossessionBy(player);
        if (player.getData(ModAttachments.DASH).ticksLeft() > 0) {
            player.setData(ModAttachments.DASH, DashState.NONE);
            dispelled = true;
        }
        if (dispelled) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.skill.dispelled").withStyle(ChatFormatting.AQUA));
            playSound(player, SoundEvents.FIRE_EXTINGUISH, 1.2F);
        }
    }

    static void refuse(ServerPlayer player, Component message) {
        player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.DARK_RED));
    }

    static void burst(ServerLevel level, Player player, int count) {
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.5F), player.getX(), player.getY(0.5), player.getZ(), count, 0.4, 0.6, 0.4, 0.0);
    }

    static void playSound(Player player, SoundEvent sound, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, pitch);
    }
}
