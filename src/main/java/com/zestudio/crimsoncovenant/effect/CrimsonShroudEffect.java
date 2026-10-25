package com.zestudio.crimsoncovenant.effect;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/// The Crimson Shroud (the upgraded Flight skill): for a minute the vampire grows a giant bat's wings that glide like
/// an elytra, over whatever armor they have on (jump in mid-air to spread them; see client.renderer.CrimsonShroudLayer
/// for how they're drawn). While gliding, a right click is a Blood Charge: five blood burst behind them to drive them
/// on, the way a firework drives an elytra (see skill.BloodSkills.bloodCharge). No fall hurts them while it lasts. It
/// ends when its minute is up, or when the vampire uses the skill again, and the vampire is caught as they near the
/// ground and drifts down gently (see SoftLanding).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class CrimsonShroudEffect extends MobEffect {
    /// One minute
    public static final int DURATION = 1200;

    public CrimsonShroudEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8A0B18);
        this.addAttributeModifier(NeoForgeMod.GLIDING_FLIGHT, CrimsonCovenant.id("effect.crimson_shroud"), 1.0, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return remainingDuration <= 1;
    }

    // The shroud's minute is up: a soft landing
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            end(player);
        }
        return true;
    }

    public static void end(Player player) {
        SoftLanding.begin(player);
    }

    public static boolean isShrouded(LivingEntity entity) {
        return entity.hasEffect(ModEffects.CRIMSON_SHROUD);
    }

    /// The old Man-Bat form made players half again as big. Effects' attribute modifiers are saved with the player,
    /// so anyone who logged out mid-form kept the size (and the wings) after the form was taken out of the game.
    /// They're cleared away on logging in.
    @SubscribeEvent
    static void shedTheOldBatForm(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.removeModifier(CrimsonCovenant.id("effect.man_bat.size"));
        }
        AttributeInstance gliding = player.getAttribute(NeoForgeMod.GLIDING_FLIGHT);
        if (gliding != null) {
            gliding.removeModifier(CrimsonCovenant.id("effect.man_bat.wings"));
        }
    }

    @SubscribeEvent
    static void noFallDamage(LivingFallEvent event) {
        if (isShrouded(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
