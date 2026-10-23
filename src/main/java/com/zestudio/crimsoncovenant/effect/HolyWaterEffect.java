package com.zestudio.crimsoncovenant.effect;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.skill.BloodSkills;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.InstantaneousMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/// Holy Water: hurts undead creatures (and vampire players), strips away their Resistance and sets them alight
/// with Holy Flame, which slows them and which water can't put out. Ghosts are destroyed instantly, and vampire
/// players lose any blood skills they have active.
/// Everything else is unaffected.
/// Splash potions scale the effect down with distance; lingering clouds re-apply it every second.
public class HolyWaterEffect extends InstantaneousMobEffect {
    private static final float BURN_SECONDS = 8.0F;
    private static final int BASE_DAMAGE = 3;

    public HolyWaterEffect() {
        super(MobEffectCategory.NEUTRAL, 0xBFE8FF);
    }

    public static boolean isAffected(LivingEntity entity) {
        return entity.is(EntityTypeTags.UNDEAD) || (entity instanceof Player player && Vampirism.isVampire(player));
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        this.burn(level, null, null, entity, amplifier, 1.0);
        return true;
    }

    @Override
    public void applyInstantaneousEffect(ServerLevel level, @Nullable Entity source, @Nullable Entity owner, LivingEntity entity, int amplifier, double scale) {
        this.burn(level, source, owner, entity, amplifier, scale);
    }

    private void burn(ServerLevel level, @Nullable Entity source, @Nullable Entity owner, LivingEntity entity, int amplifier, double scale) {
        if (!isAffected(entity)) {
            return;
        }
        DamageSource holyFire = entity.damageSources().source(ModDamageTypes.HOLY_FIRE, source, owner);
        // Ghosts are banished outright
        if (entity instanceof Ghost) {
            entity.hurtServer(level, holyFire, entity.getMaxHealth() * 10.0F);
            return;
        }
        if (entity instanceof ServerPlayer player) {
            BloodSkills.dispel(player);
        }
        entity.removeEffect(MobEffects.RESISTANCE);
        // Hidden effect particles: the burning look comes from Holy Flame itself
        entity.addEffect(new MobEffectInstance(ModEffects.HOLY_FLAME, Math.max(20, (int) (BURN_SECONDS * 20 * scale)), amplifier, false, false, true), owner);
        int damage = (int) (scale * (BASE_DAMAGE << amplifier) + 0.5);
        entity.hurtServer(level, holyFire, damage);
    }
}
