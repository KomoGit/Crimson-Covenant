package com.zestudio.crimsoncovenant.effect;

import java.util.Comparator;
import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.skill.NightOfTerror;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/// From the Potion of Frenzy, brewed from a werewolf's tooth. Hostile creatures caught in it (hunters count, bosses
/// don't) turn on the nearest other hostile creature they can see, and those they bite bite back. Anything else
/// scatters from where it landed for as long as it lasts, the way creatures run from a Night of Terror. A drinker
/// gets the same as Strength and Speed of its level, as does anything else under it.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class FrenzyEffect extends MobEffect {
    /// How far off a frenzied creature will look for another to go for
    public static final double SEEK_RANGE = 16.0;
    private static final double DAMAGE_PER_LEVEL = 3.0;
    private static final double SPEED_PER_LEVEL = 0.2;
    private static final int SEEK_INTERVAL = 10;

    public FrenzyEffect() {
        super(MobEffectCategory.NEUTRAL, 0xB02A1E);
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE, CrimsonCovenant.id("effect.frenzy.strength"), DAMAGE_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, CrimsonCovenant.id("effect.frenzy.speed"), SPEED_PER_LEVEL, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    public static boolean isHostile(LivingEntity entity) {
        return entity instanceof Enemy || entity instanceof Hunter;
    }

    /// Whether a frenzied creature goes for this one: another hostile creature that could be hurt. Bosses are left be,
    /// and so are ghosts, which nothing but Holy Water touches.
    public static boolean isPrey(Mob frenzied, LivingEntity other) {
        return other != frenzied && other.isAlive() && isHostile(other) && !(other instanceof Ghost) && !other.is(Tags.EntityTypes.BOSSES) && !other.isInvulnerable();
    }

    @SubscribeEvent
    static void scatter(MobEffectEvent.Added event) {
        MobEffectInstance frenzy = event.getEffectInstance();
        if (frenzy.is(ModEffects.FRENZY) && event.getEntity() instanceof Mob mob && mob.level() instanceof ServerLevel level
                && !isHostile(mob) && !mob.is(Tags.EntityTypes.BOSSES)) {
            NightOfTerror.terrify(level, mob, SilverDreadEffect.splashPoint(level, mob), frenzy.getDuration());
        }
    }

    @SubscribeEvent
    static void addTargetGoal(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && isHostile(mob) && !mob.is(Tags.EntityTypes.BOSSES)) {
            mob.targetSelector.addGoal(-1, new FrenzyTargetGoal(mob));
        }
    }

    /// Comes before every other target. With nothing to turn on, the creature goes on as it was.
    static final class FrenzyTargetGoal extends Goal {
        private final Mob mob;
        private @Nullable LivingEntity prey;

        FrenzyTargetGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        private boolean isFrenzied() {
            return this.mob.hasEffect(ModEffects.FRENZY);
        }

        private @Nullable LivingEntity nearestPrey() {
            return this.mob.level().getEntitiesOfClass(LivingEntity.class, this.mob.getBoundingBox().inflate(SEEK_RANGE),
                            other -> isPrey(this.mob, other) && this.mob.getSensing().hasLineOfSight(other))
                    .stream().min(Comparator.comparingDouble(this.mob::distanceToSqr)).orElse(null);
        }

        @Override
        public boolean canUse() {
            if (!this.isFrenzied() || (this.mob.tickCount + this.mob.getId()) % SEEK_INTERVAL != 0) {
                return false;
            }
            this.prey = this.nearestPrey();
            return this.prey != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.isFrenzied() && this.prey != null && isPrey(this.mob, this.prey) && this.mob.distanceToSqr(this.prey) <= SEEK_RANGE * SEEK_RANGE * 4.0;
        }

        @Override
        public void start() {
            this.tick();
        }

        @Override
        public void tick() {
            if (this.prey != null && this.mob.getTarget() != this.prey) {
                this.mob.setTarget(this.prey);
                // Mobs run by a brain (piglins, hoglins...) attack from memories rather than goals
                this.mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, this.prey);
            }
        }

        @Override
        public void stop() {
            this.prey = null;
            this.mob.setTarget(null);
            this.mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
}
