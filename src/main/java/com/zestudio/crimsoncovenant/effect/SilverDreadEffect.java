package com.zestudio.crimsoncovenant.effect;

import java.util.Comparator;
import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.skill.NightOfTerror;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/// From the Potion of Silver, made to be thrown: undead it touches (bosses aside) drop everything and flee from where
/// it landed for as long as it lasts, the same way they run from a Night of Terror. A vampire player can't be made to
/// run, but is weakened instead. The living don't notice it.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class SilverDreadEffect extends MobEffect {
    /// How far from a creature to look for the potion or cloud that touched it
    private static final double SOURCE_RANGE = 6.0;

    public SilverDreadEffect() {
        super(MobEffectCategory.HARMFUL, 0xC8CCD4);
    }

    @SubscribeEvent
    static void scatterUndead(MobEffectEvent.Added event) {
        MobEffectInstance dread = event.getEffectInstance();
        LivingEntity entity = event.getEntity();
        if (!dread.is(ModEffects.SILVER_DREAD) || !(entity.level() instanceof ServerLevel level) || !Vampirism.isUndead(entity)) {
            return;
        }
        if (entity instanceof Mob mob && !mob.is(Tags.EntityTypes.BOSSES)) {
            NightOfTerror.terrify(level, mob, splashPoint(level, entity), dread.getDuration());
        } else if (entity instanceof Player) {
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dread.getDuration(), dread.getAmplifier()));
        }
    }

    /// Where the silver landed: the thrown potion (still there while it splashes) or lingering cloud nearest the
    /// creature, or just where the creature stands
    static Vec3 splashPoint(ServerLevel level, LivingEntity entity) {
        List<Entity> sources = level.getEntities(entity, entity.getBoundingBox().inflate(SOURCE_RANGE),
                candidate -> candidate instanceof AbstractThrownPotion || candidate instanceof AreaEffectCloud);
        return sources.stream().min(Comparator.comparingDouble(entity::distanceToSqr)).map(Entity::position).orElse(entity.position());
    }
}
