package com.zestudio.crimsoncovenant.effect;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.MiasmaCloud;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.InstantaneousMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/// The Potion of Cleanse, brewed from ferns (or bought from a Plague Doctor): it cures the Plague and the Miasma's
/// sickness, and the nausea and blindness that come with it. Thrown, it also clears away any Miasma where it lands
/// and keeps it off that spot for a while (see MiasmaCloud). Drunk (not splashed), it also cures a vampire's curse
/// that hasn't yet taken (Sanguine Vampiris).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class CleanseEffect extends InstantaneousMobEffect {
    public CleanseEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x9FE0B0);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        cleanse(entity);
        return true;
    }

    @Override
    public void applyInstantaneousEffect(ServerLevel level, @Nullable Entity source, @Nullable Entity owner, LivingEntity entity, int amplifier, double scale) {
        cleanse(entity);
    }

    public static void cleanse(LivingEntity entity) {
        boolean miasma = entity.removeEffect(ModEffects.MIASMA);
        entity.removeEffect(ModEffects.PLAGUE);
        if (miasma) {
            entity.removeEffect(MobEffects.NAUSEA);
            entity.removeEffect(MobEffects.BLINDNESS);
        }
    }

    public static boolean isCleanse(PotionContents contents) {
        for (var effect : contents.getAllEffects()) {
            if (effect.is(ModEffects.CLEANSE)) {
                return true;
            }
        }
        return false;
    }

    // Only a drunk Cleanse cures the curse: splashes, clouds and arrows don't
    @SubscribeEvent
    static void cureCurse(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide() && event.getItem().is(Items.POTION)
                && Vampirism.isInfected(player) && !Vampirism.isVampire(player)
                && isCleanse(event.getItem().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, PotionContents.EMPTY))) {
            Vampirism.cure(player);
        }
    }

    // A thrown Cleanse clears the Miasma where it bursts
    @SubscribeEvent
    static void clearMiasma(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof AbstractThrownPotion potion && potion.level() instanceof ServerLevel level
                && isCleanse(potion.getItem().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, PotionContents.EMPTY))) {
            MiasmaCloud.cleanseAround(level, event.getRayTraceResult().getLocation());
        }
    }
}
