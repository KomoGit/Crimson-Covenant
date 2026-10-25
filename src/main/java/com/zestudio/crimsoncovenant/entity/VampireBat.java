package com.zestudio.crimsoncovenant.entity;

import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/// A hostile, undead bat. It flutters around like a normal bat until it finds prey, then dives at it and
/// bites, healing a little with every bite. Burns in sunlight. Master Vampires summon them, and they roost in caves
/// (see ModEntities.checkVampireBatSpawnRules).
public class VampireBat extends Bat implements Enemy {
    private static final int BITE_COOLDOWN = 20;
    private static final float BITE_HEAL = 1.0F;
    /// The speeds it steers for when diving at prey, across and up or down: a little faster than a vanilla bat flies
    private static final double CHASE_SPEED = 0.45;
    private static final double CHASE_CLIMB = 0.525;

    private int biteCooldown = 0;

    public VampireBat(EntityType<? extends VampireBat> type, Level level) {
        super(type, level);
        // Vanilla bats spawn hanging upside down, these spawn ready to hunt
        this.setResting(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Bat.createAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        // Same prey as vampires. Movement is handled in customServerAiStep, like vanilla bats.
        // (Retaliation is in hurtServer, since HurtByTargetGoal only works for walking mobs.)
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
                (target, level) -> !(target instanceof Player player && Vampirism.isVampire(player))));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
                (target, level) -> target.is(ModTags.Entities.VAMPIRE_PREY)));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            // No prey: wander and roost like a normal bat
            super.customServerAiStep(level);
            return;
        }

        this.setResting(false);
        if (this.biteCooldown > 0) {
            this.biteCooldown--;
        }

        // Steer towards the target's chest using the same flight model as vanilla bats, a little faster
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.6) - this.getY();
        double dz = target.getZ() - this.getZ();
        Vec3 movement = this.getDeltaMovement();
        Vec3 newMovement = movement.add(
                (Math.signum(dx) * CHASE_SPEED - movement.x) * 0.15, (Math.signum(dy) * CHASE_CLIMB - movement.y) * 0.15,
                (Math.signum(dz) * CHASE_SPEED - movement.z) * 0.15);
        this.setDeltaMovement(newMovement);
        float yRotD = (float) (Mth.atan2(newMovement.z, newMovement.x) * 180.0F / (float) Math.PI) - 90.0F;
        this.setYRot(this.getYRot() + Mth.wrapDegrees(yRotD - this.getYRot()));
        this.zza = 0.5F;

        if (this.biteCooldown <= 0 && this.isWithinMeleeAttackRange(target)) {
            this.biteCooldown = BITE_COOLDOWN;
            this.doHurtTarget(level, target);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean bit = super.doHurtTarget(level, target);
        if (bit) {
            if (target instanceof LivingEntity living && Vampirism.hasLivingBlood(living)) {
                this.heal(BITE_HEAL);
            }
            level.sendParticles(new DustParticleOptions(0x9B0A14, 0.8F), target.getX(), target.getY(0.6), target.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
        }
        return bit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && source.getEntity() instanceof LivingEntity attacker && attacker != this && !(attacker instanceof Vampire)
                && !Vampire.isFamiliar(attacker) && !(attacker instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            this.setTarget(attacker);
        }
        return hurt;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.7F;
    }
}
