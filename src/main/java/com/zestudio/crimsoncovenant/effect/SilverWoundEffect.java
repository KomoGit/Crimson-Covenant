package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModEffects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/// From a silvered weapon (see item.Silvering): the wound won't close. Nothing heals while it lasts, and a vampire
/// player's blood stops coming back (see BloodPool).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class SilverWoundEffect extends MobEffect {
    public SilverWoundEffect() {
        super(MobEffectCategory.HARMFUL, 0xB4BAC6);
    }

    @SubscribeEvent
    static void keepWoundOpen(LivingHealEvent event) {
        if (event.getEntity().hasEffect(ModEffects.SILVER_WOUND)) {
            event.setCanceled(true);
        }
    }
}
