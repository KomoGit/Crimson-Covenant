package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/// Black Blood: the hunters' brew of nether wart and vampire blood. Whoever strikes the drinker in close combat is
/// hurt in turn, like Thorns, but every time: 3 damage, 6 with the stronger brew. Hunters drink it going into a fight;
/// vampires can't stomach it.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class BlackBloodEffect extends MobEffect {
    public static final float RETALIATION = 3.0F;

    public BlackBloodEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x1A0A0E);
    }

    /// A vampire can't bring themselves to drink it
    @SubscribeEvent
    static void vampiresRefuse(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && Vampirism.isVampire(player) && containsBlackBlood(event.getItem())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.crimsoncovenant.black_blood.vampire_refuses").withStyle(ChatFormatting.DARK_RED));
                serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 0.6F, 0.6F);
            }
        }
    }

    public static boolean containsBlackBlood(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.is(ModEffects.BLACK_BLOOD)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    static void retaliate(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        // A Thorned Shell (upgraded Armor of Ancient) hits back the same way
        MobEffectInstance blackBlood = victim.hasEffect(ModEffects.BLACK_BLOOD) ? victim.getEffect(ModEffects.BLACK_BLOOD) : victim.getEffect(ModEffects.THORNED_SHELL);
        // Melee only (the attacker struck by hand), and never thorns answering thorns
        if (blackBlood == null || !(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker)
                || source.getDirectEntity() != attacker || attacker == victim || source.is(DamageTypes.THORNS)) {
            return;
        }
        if (attacker.hurtServer(level, level.damageSources().thorns(victim), RETALIATION * (blackBlood.getAmplifier() + 1))) {
            level.sendParticles(new DustParticleOptions(0x150508, 1.4F), attacker.getX(), attacker.getY(0.6), attacker.getZ(), 15, 0.3, 0.4, 0.3, 0.0);
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.THORNS_HIT, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
    }
}
