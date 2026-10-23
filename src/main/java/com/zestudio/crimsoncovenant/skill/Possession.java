package com.zestudio.crimsoncovenant.skill;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.PossessedForm;
import com.zestudio.crimsoncovenant.registry.ModAttachments.PossessionState;
import com.zestudio.crimsoncovenant.registry.ModTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// The Possess skill: the vampire becomes the creature they're looking at for 10 seconds. The creature leaves the
/// world while it's possessed and the vampire takes its place: its size, its speed (and flight, if it flies), its
/// way of attacking on the attack button (see CreatureAbilities). Everyone sees the creature
/// where the vampire is (see client.PossessionRenderer), and blows aimed at the vampire hurt the creature instead.
///
/// When the time is up the creature steps back into the world where the vampire stands, as it was, minus any
/// damage it took. If the damage kills it, it dies there (with its loot) and the vampire is unharmed.
/// Master vampires, master hunters, bosses and minibosses can't be possessed (crimsoncovenant:cannot_be_possessed).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Possession {
    public static final int DURATION = 200;
    private static final double REACH = 20.0;
    /// A creature's movement speed attribute, squared, is roughly how fast it walks; a player's speed is ~0.098
    private static final double PLAYER_WALK_SPEED = 0.098;

    private static final Identifier SCALE_ID = CrimsonCovenant.id("possession.scale");
    private static final Identifier SPEED_ID = CrimsonCovenant.id("possession.speed");
    private static final Identifier FLIGHT_ID = CrimsonCovenant.id("possession.flight");

    private Possession() {}

    static boolean tryPossess(ServerPlayer player) {
        if (isPossessing(player)) {
            BloodSkills.refuse(player, Component.translatable("message.crimsoncovenant.skill.already_active"));
            return false;
        }
        Mob target = lookedAt(player, Mob.class, REACH);
        if (target == null) {
            BloodSkills.refuse(player, Component.translatable("message.crimsoncovenant.skill.nothing_to_possess"));
            return false;
        }
        if (!canBePossessed(target)) {
            BloodSkills.refuse(player, Component.translatable("message.crimsoncovenant.skill.cannot_possess", target.getDisplayName()));
            return false;
        }
        start(player, target);
        return true;
    }

    public static boolean canBePossessed(Mob mob) {
        return mob.isAlive() && !mob.isNoAi() && !mob.isVehicle() && !mob.isPassenger() && !mob.is(ModTags.Entities.CANNOT_BE_POSSESSED);
    }

    /// The closest creature of this kind in the player's line of sight
    static <T extends LivingEntity> @Nullable T lookedAt(ServerPlayer player, Class<T> kind, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        AABB searchBox = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        return player.level().getEntitiesOfClass(kind, searchBox, entity -> entity != player
                        && entity.getBoundingBox().inflate(0.3).clip(eye, end).isPresent() && player.hasLineOfSight(entity))
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(eye))).orElse(null);
    }

    public static boolean isPossessing(Player player) {
        return player.getData(ModAttachments.POSSESSED_FORM).isPresent();
    }

    public static Optional<EntityType<?>> possessedType(Player player) {
        return player.getData(ModAttachments.POSSESSED_FORM).flatMap(form -> BuiltInRegistries.ENTITY_TYPE.getOptional(form.type()));
    }

    /// The vampire steps into the creature: it leaves the world (saved on the vampire) and the vampire takes its shape
    public static void start(ServerPlayer player, Mob mob) {
        ServerLevel level = player.level();
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        mob.saveWithoutId(saved);
        AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        float attackDamage = attack != null ? (float) attack.getValue() : 1.0F;

        level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY(0.6), mob.getZ(), 25, 0.3, 0.5, 0.3, 0.02);
        player.teleportTo(level, mob.getX(), mob.getY(), mob.getZ(), Set.of(), mob.getYRot(), player.getXRot(), false);
        player.setData(ModAttachments.POSSESSED_FORM, Optional.of(new PossessedForm(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), saved.buildResult())));
        player.setData(ModAttachments.POSSESSION_STATE, new PossessionState(DURATION, mob.getHealth(), attackDamage, 0, 0));
        takeShape(player, mob);
        mob.discard();
        BloodSkills.playSound(player, SoundEvents.SOUL_ESCAPE.value(), 0.6F);
    }

    private static void takeShape(ServerPlayer player, Mob mob) {
        double scale = Mth.clamp(mob.getBbHeight() / player.getType().getHeight(), 0.25, 2.5);
        player.getAttribute(Attributes.SCALE).addTransientModifier(new AttributeModifier(SCALE_ID, scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        double creatureSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double speed = Mth.clamp(creatureSpeed * creatureSpeed / PLAYER_WALK_SPEED, 0.5, 1.5);
        player.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(new AttributeModifier(SPEED_ID, speed - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (flies(mob)) {
            player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).addTransientModifier(new AttributeModifier(FLIGHT_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        // Hidden: no shadow or name tag giving the vampire away, and creatures sense them like any invisible player
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DURATION + 5, 0, false, false, false));
        if (mob.canBreatheUnderwater()) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, DURATION + 5, 0, false, false, false));
        }
        if (mob.fireImmune()) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, DURATION + 5, 0, false, false, false));
        }
    }

    private static void dropShape(ServerPlayer player) {
        player.getAttribute(Attributes.SCALE).removeModifier(SCALE_ID);
        player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_ID);
        player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).removeModifier(FLIGHT_ID);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.WATER_BREATHING);
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
    }

    private static boolean flies(Mob mob) {
        return mob.isNoGravity() || mob.getNavigation() instanceof FlyingPathNavigation || mob instanceof Bat || mob instanceof Ghast
                || mob instanceof Phantom || mob instanceof Ghost;
    }

    /// Ends whatever possession the player has going: the creature comes back where they stand. True if there was one.
    public static boolean endPossessionBy(ServerPlayer player) {
        return end(player, true, null);
    }

    /// Ends the possession. The creature comes back unless it was used up (a creeper that blew itself up), and if
    /// it was killed it comes back only to die.
    static boolean end(ServerPlayer player, boolean creatureReturns, @Nullable DamageSource killedBy) {
        Optional<PossessedForm> form = player.getData(ModAttachments.POSSESSED_FORM);
        if (form.isEmpty()) {
            return false;
        }
        PossessionState state = player.getData(ModAttachments.POSSESSION_STATE);
        player.setData(ModAttachments.POSSESSED_FORM, Optional.empty());
        player.setData(ModAttachments.POSSESSION_STATE, PossessionState.NONE);
        player.setData(ModAttachments.CREEPER_FUSE, 0);
        player.setData(ModAttachments.POSSESSED_CHARGE, 0);
        dropShape(player);
        ServerLevel level = player.level();
        if (creatureReturns || killedBy != null) {
            Entity creature = BuiltInRegistries.ENTITY_TYPE.getOptional(form.get().type())
                    .flatMap(type -> EntityType.create(type, TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), form.get().data()), level, EntitySpawnReason.LOAD))
                    .orElse(null);
            if (creature != null) {
                creature.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                creature.setDeltaMovement(Vec3.ZERO);
                if (creature instanceof LivingEntity living) {
                    living.setHealth(Math.max(state.health(), 1.0F));
                }
                level.addFreshEntity(creature);
                if (killedBy != null) {
                    creature.hurtServer(level, killedBy, Float.MAX_VALUE);
                    if (creature.isAlive()) {
                        creature.kill(level);
                    }
                }
            }
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.6), player.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
        return true;
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tickPossession(player);
        }
    }

    /// Counts the possession down (and any lit creeper fuse), and ends it when the time is up
    public static void tickPossession(ServerPlayer player) {
        if (!isPossessing(player)) {
            return;
        }
        CreatureAbilities.tick(player);
        // The fuse may have just blown the creeper, and the possession with it
        if (!isPossessing(player)) {
            return;
        }
        PossessionState state = player.getData(ModAttachments.POSSESSION_STATE);
        if (state.ticksLeft() <= 1 || !player.isAlive()) {
            end(player, true, null);
            return;
        }
        player.setData(ModAttachments.POSSESSION_STATE, new PossessionState(state.ticksLeft() - 1, state.health(), state.attackDamage(),
                Math.max(0, state.abilityCooldown() - 1), Math.max(0, state.attackCooldown() - 1)));
        if (player.tickCount % 10 == 0) {
            Component name = possessedType(player).map(EntityType::getDescription).orElse(Component.empty());
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.possess.status", name, Mth.ceil(state.health()),
                    Mth.ceil(state.ticksLeft() / 20.0F)).withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    /// Blows aimed at the vampire hurt the creature they've become. If it dies, so does the possession.
    @SubscribeEvent
    static void creatureTakesTheBlow(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isPossessing(player) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        event.setCanceled(true);
        PossessionState state = player.getData(ModAttachments.POSSESSION_STATE);
        float health = state.health() - event.getAmount();
        if (health <= 0.0F) {
            end(player, false, event.getSource());
            return;
        }
        player.setData(ModAttachments.POSSESSION_STATE, new PossessionState(state.ticksLeft(), health, state.attackDamage(), state.abilityCooldown(), state.attackCooldown()));
        BloodSkills.playSound(player, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.8F);
    }

    /// A vampire killed by something nothing can stop (the void, /kill) leaves the creature behind
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player, true, null);
        }
    }

    /// A possessed creature attacks the creature's way, through the attack button (see CreatureAbilities): the
    /// client sends that instead of a normal swing. A swing that gets through anyway is turned into a bite.
    @SubscribeEvent
    static void attackAsTheCreature(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isPossessing(player)) {
            return;
        }
        event.setCanceled(true);
        if (event.getTarget() instanceof LivingEntity target) {
            possessedType(player).ifPresent(type -> CreatureAbilities.bite(type, player, target));
        }
    }

    // The creature isn't lost if the vampire leaves the game, changes dimension, or the server stopped mid-possession

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player, true, null);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player, true, null);
        }
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player, true, null);
        }
    }
}
