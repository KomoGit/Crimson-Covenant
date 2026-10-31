package com.zestudio.crimsoncovenant.item;

import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.entity.Werewolf;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.skill.MoonPowers;
import com.zestudio.crimsoncovenant.skill.Possession;
import com.zestudio.crimsoncovenant.skill.SkillKeeper;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.world.WerewolfSpawner;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// The Werewolf Belt, made from Werewolf Hide and worn in the leggings slot. Whoever wears it on a full-moon or Blood Moon
/// night turns into a werewolf, like it or not, unless they're a vampire: Strength III, Speed II, Jump Boost III, ten extra
/// hearts, blows that land as if with Knockback II, and eyes higher off the ground (the body stays a player's size, so
/// doorways still fit). They don't hunger: their moonbars and four powers take its place (see skill.MoonPowers). Vampire
/// blood has no hold on them: they can drink it, and a vampire's kill won't infect them, nor cost them the belt, though it
/// costs the belt some of its wear. It's mended with Werewolf Hide. It's the
/// player's own body still, with their own hands and inventory, only drawn as the beast (see
/// client.renderer.PossessionRenderer). The beast leaves them at dawn, when the belt comes off, or if they leave the
/// overworld (or the game), die, or turn vampire. Changing shape leaves them as badly hurt as they were, so slipping the
/// belt off and on heals nothing.
///
/// Skeletons won't stand against a werewolf: they run, and won't take one for a target. Werewolves take it for one of
/// their own, unless it strikes them. Hunters hunt it on sight, even their own sworn brothers (see Hunter.huntsPlayer),
/// and leave off when it turns back, unless it struck them.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class WerewolfBelt {
    public static final int STRENGTH_AMPLIFIER = 2;
    public static final int SPEED_AMPLIFIER = 1;
    public static final int JUMP_AMPLIFIER = 2;
    /// What the Knockback enchantment adds at level II
    public static final double EXTRA_KNOCKBACK = 2.0;
    /// Enough for a player to stand as tall as a true werewolf, at 40
    public static final double EXTRA_HEALTH = 20.0;
    /// How near a werewolf has to come before skeletons run
    public static final float SKELETON_FEAR_RANGE = 12.0F;
    /// A werewolf's eyes are this much higher, for the camera: about where the drawn beast's are
    public static final float EYE_HEIGHT_SCALE = 1.2F;
    public static final int DURABILITY = 100;
    /// What a vampire's kill costs the belt: four of them and it's gone
    public static final int VAMPIRE_KILL_DAMAGE = 25;
    /// One short of full, so a werewolf can still eat, for raw flesh (see skill.MoonPowers), but it's near enough to sprint
    /// and heal on
    private static final int WEREWOLF_FOOD = 19;
    private static final int EFFECT_REFRESH = 20;
    private static final int EFFECT_TICKS = 50;
    /// How far off skeletons give up on, and hunters forget, a player who changes shape
    private static final double CALL_OFF_RANGE = 32.0;
    private static final Identifier KNOCKBACK_ID = CrimsonCovenant.id("werewolf_belt.knockback");
    private static final Identifier HEALTH_ID = CrimsonCovenant.id("werewolf_belt.health");
    private static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, CrimsonCovenant.id("werewolf_belt"));

    private WerewolfBelt() {}

    /// Worn, not fought in: blows don't wear it and it gives no armor
    public static Item.Properties properties(Item.Properties properties) {
        return properties.durability(DURABILITY).repairable(ModItems.WEREWOLF_HIDE.get()).rarity(Rarity.RARE)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).setAsset(ASSET).setDamageOnHurt(false).build())
                .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.crimsoncovenant.werewolf_belt.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))));
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.WEREWOLF_BELT.get());
    }

    public static boolean isTurned(Player player) {
        return player.getData(ModAttachments.WEREWOLF_FORM);
    }

    /// A full moon or a Blood Moon overhead, for those under the overworld's sky
    public static boolean moonIsUp(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD && WerewolfSpawner.isWerewolfNight(level);
    }

    /// Whether the moon has this player: the belt on, the moon up, and no vampire's blood to stand in its way
    public static boolean answersTheMoon(ServerPlayer player) {
        return isWearing(player) && !Vampirism.isVampire(player) && moonIsUp(player.level());
    }

    private static void turn(ServerPlayer player) {
        player.setData(ModAttachments.WEREWOLF_FORM, true);
        MoonPowers.set(player, 0);
        player.refreshDimensions();
        player.getAttribute(Attributes.ATTACK_KNOCKBACK).addTransientModifier(new AttributeModifier(KNOCKBACK_ID, EXTRA_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE));
        float share = player.getHealth() / player.getMaxHealth();
        player.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(HEALTH_ID, EXTRA_HEALTH, AttributeModifier.Operation.ADD_VALUE));
        player.setHealth(share * player.getMaxHealth());
        giveEffects(player);
        changeShape(player);
        tell(player, "message.crimsoncovenant.werewolf_belt.turned");
        // Any skeleton already after them thinks better of it, and any werewolf knows them for kin
        player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(CALL_OFF_RANGE), mob -> mob.getTarget() == player
                && (mob instanceof AbstractSkeleton || (mob instanceof Werewolf && mob.getLastHurtByMob() != player))).forEach(mob -> mob.setTarget(null));
    }

    public static void turnBack(ServerPlayer player) {
        if (!isTurned(player)) {
            return;
        }
        player.setData(ModAttachments.WEREWOLF_FORM, false);
        player.refreshDimensions();
        player.removeEffect(ModEffects.ECLIPSE_HIDE);
        player.getAttribute(Attributes.ATTACK_KNOCKBACK).removeModifier(KNOCKBACK_ID);
        float share = player.getHealth() / player.getMaxHealth();
        player.getAttribute(Attributes.MAX_HEALTH).removeModifier(HEALTH_ID);
        player.setHealth(share * player.getMaxHealth());
        takeEffect(player, MobEffects.STRENGTH, STRENGTH_AMPLIFIER);
        takeEffect(player, MobEffects.SPEED, SPEED_AMPLIFIER);
        takeEffect(player, MobEffects.JUMP_BOOST, JUMP_AMPLIFIER);
        changeShape(player);
        // The beast is gone, and hunters have no quarrel with the player, unless there's another reason
        if (!Hunter.huntsPlayer(player)) {
            player.level().getEntitiesOfClass(Hunter.class, player.getBoundingBox().inflate(CALL_OFF_RANGE),
                    hunter -> hunter.getTarget() == player && hunter.getLastHurtByMob() != player).forEach(hunter -> hunter.setTarget(null));
        }
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendOverlayMessage(Component.translatable(message).withStyle(ChatFormatting.GRAY));
    }

    private static void changeShape(ServerPlayer player) {
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.6), player.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound().value(),
                SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    private static void giveEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, EFFECT_TICKS, STRENGTH_AMPLIFIER, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, EFFECT_TICKS, SPEED_AMPLIFIER, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, EFFECT_TICKS, JUMP_AMPLIFIER, false, false, true));
    }

    // Only the belt's own: a potion of the same kind, with more time left to run, stays
    private static void takeEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance != null && instance.getAmplifier() == amplifier && instance.getDuration() <= EFFECT_TICKS) {
            player.removeEffect(effect);
        }
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tickForm(player);
        }
    }

    /// Turns the player when the moon has them (a possession has to run its course first), keeps their effects up, and
    /// turns them back when it lets them go
    public static void tickForm(ServerPlayer player) {
        if (!isTurned(player)) {
            if (answersTheMoon(player) && !Possession.isPossessing(player)) {
                turn(player);
            }
        } else if (!answersTheMoon(player)) {
            turnBack(player);
            tell(player, Vampirism.isVampire(player) ? "message.crimsoncovenant.werewolf_belt.vampire"
                    : isWearing(player) ? "message.crimsoncovenant.werewolf_belt.moon_set" : "message.crimsoncovenant.werewolf_belt.taken_off");
        } else {
            // No hunger: food stays up (so they can sprint and heal) but without saturation, as for a vampire
            player.getFoodData().setFoodLevel(WEREWOLF_FOOD);
            player.getFoodData().setSaturation(0.0F);
            if (player.tickCount % EFFECT_REFRESH == 0) {
                giveEffects(player);
            }
        }
    }

    // Higher eyes, on both sides: the client runs this again when it hears they've turned (see PossessionRenderer)
    @SubscribeEvent
    static void standTall(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player && player.hasData(ModAttachments.WEREWOLF_FORM) && isTurned(player)) {
            EntityDimensions size = event.getNewSize();
            event.setNewSize(size.withEyeHeight(size.eyeHeight() * EYE_HEIGHT_SCALE));
        }
    }

    // ---- Skeletons ----

    /// Whether `mob` is afraid of this player
    public static boolean fears(Mob mob, Player player) {
        return mob instanceof AbstractSkeleton && isTurned(player);
    }

    @SubscribeEvent
    static void addFleeGoal(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof AbstractSkeleton skeleton) {
            skeleton.goalSelector.addGoal(1, new FleeWerewolfGoal(skeleton));
        }
    }

    // Afraid of them, a skeleton won't pick them for a target, nor turn on them when struck
    @SubscribeEvent
    static void skeletonsDareNot(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getNewAboutToBeSetTarget() instanceof Player player && fears(mob, player)) {
            event.setCanceled(true);
        }
    }

    /// Runs from a werewolf player, as from a wolf but faster and sooner. Every skeleton has this, so it only looks about
    /// now and then.
    static final class FleeWerewolfGoal extends AvoidEntityGoal<Player> {
        FleeWerewolfGoal(AbstractSkeleton skeleton) {
            super(skeleton, Player.class, SKELETON_FEAR_RANGE, 1.2, 1.5, target -> target instanceof Player player && isTurned(player));
        }

        @Override
        public boolean canUse() {
            return this.mob.getRandom().nextInt(reducedTickDelay(5)) == 0 && super.canUse();
        }
    }

    // ---- Ends ----

    /// Killed by a vampire as a werewolf, the belt stays with them for a price (the rest of the death is in Vampirism)
    private static void keepBelt(ServerPlayer player) {
        ItemStack belt = player.getItemBySlot(EquipmentSlot.LEGS).copy();
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        belt.setDamageValue(belt.getDamageValue() + VAMPIRE_KILL_DAMAGE);
        if (belt.getDamageValue() < belt.getMaxDamage()) {
            SkillKeeper.keep(player, belt);
        }
    }

    private static boolean isVampire(DamageSource source) {
        return source.getEntity() instanceof Vampire || (source.getEntity() instanceof Player player && Vampirism.isVampire(player));
    }

    // The form isn't saved, so it's over if the player leaves, and a corpse falls as a player. Last, so that the rest of
    // the death still sees a werewolf.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isTurned(player) && isWearing(player) && isVampire(event.getSource())) {
                keepBelt(player);
            }
            turnBack(player);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            turnBack(player);
        }
    }
}
