package com.zestudio.crimsoncovenant.item;

import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.skill.Possession;
import com.zestudio.crimsoncovenant.world.WerewolfSpawner;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// The Werewolf Belt, made from Werewolf Hide and worn in the leggings slot. On a full-moon or Blood Moon night its
/// wearer can turn into a werewolf with the transform key (see client.ModKeyMappings) and back again with the same key:
/// Strength III, Speed II and Jump Boost III for as long as they stay turned. It's the player's own body still, with
/// their own health, hands and inventory, only drawn as the beast (see client.renderer.PossessionRenderer). The beast
/// leaves them at dawn, when the belt comes off, or if they leave the overworld (or the game), or die.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class WerewolfBelt {
    public static final int STRENGTH_AMPLIFIER = 2;
    public static final int SPEED_AMPLIFIER = 1;
    public static final int JUMP_AMPLIFIER = 2;
    private static final int EFFECT_REFRESH = 20;
    private static final int EFFECT_TICKS = 50;
    private static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, CrimsonCovenant.id("werewolf_belt"));

    private WerewolfBelt() {}

    /// Worn, not fought in: it takes no damage and gives no armor
    public static Item.Properties properties(Item.Properties properties) {
        return properties.stacksTo(1).rarity(Rarity.RARE)
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

    /// The transform key: turns the player, or turns them back. Returns whether anything changed.
    public static boolean toggle(ServerPlayer player) {
        if (isTurned(player)) {
            turnBack(player);
            return true;
        }
        if (!isWearing(player)) {
            refuse(player, "message.crimsoncovenant.werewolf_belt.not_worn");
            return false;
        }
        if (!moonIsUp(player.level())) {
            refuse(player, "message.crimsoncovenant.werewolf_belt.no_moon");
            return false;
        }
        if (Possession.isPossessing(player)) {
            refuse(player, "message.crimsoncovenant.skill.already_active");
            return false;
        }
        player.setData(ModAttachments.WEREWOLF_FORM, true);
        giveEffects(player);
        changeShape(player);
        return true;
    }

    private static void refuse(ServerPlayer player, String message) {
        player.sendOverlayMessage(Component.translatable(message).withStyle(ChatFormatting.GRAY));
    }

    public static void turnBack(ServerPlayer player) {
        if (!isTurned(player)) {
            return;
        }
        player.setData(ModAttachments.WEREWOLF_FORM, false);
        takeEffect(player, MobEffects.STRENGTH, STRENGTH_AMPLIFIER);
        takeEffect(player, MobEffects.SPEED, SPEED_AMPLIFIER);
        takeEffect(player, MobEffects.JUMP_BOOST, JUMP_AMPLIFIER);
        changeShape(player);
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

    /// Keeps a turned player's effects up, and ends it when the belt comes off or the moon sets
    public static void tickForm(ServerPlayer player) {
        if (!isTurned(player)) {
            return;
        }
        boolean worn = isWearing(player);
        if (!worn || !moonIsUp(player.level())) {
            turnBack(player);
            refuse(player, worn ? "message.crimsoncovenant.werewolf_belt.moon_set" : "message.crimsoncovenant.werewolf_belt.taken_off");
        } else if (player.tickCount % EFFECT_REFRESH == 0) {
            giveEffects(player);
        }
    }

    // The form isn't saved, so it's over for good if the player leaves, and a corpse falls as a player
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
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
