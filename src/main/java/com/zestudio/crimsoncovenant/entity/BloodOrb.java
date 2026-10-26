package com.zestudio.crimsoncovenant.entity;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.skill.Enthrall;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/// The Blood Parade skill: a big ball of blood that drifts slowly ahead for 8 seconds, straight through creatures,
/// bouncing off any wall it meets. Twice a second it lashes out with bolts of blood at up to three hostile creatures
/// within 8 blocks that it can see. Each bolt hurts, and the blood it draws heals the caster. Healing past full health
/// becomes overheal: extra hearts that last as long as the orb does, and fade 10 seconds after it's gone. Each
/// creature can only feed the overheal once per orb, however often it's hit.
///
/// The more blood went into it, the harder the bolts hit (a player's puts in what the skill cost them). Whatever a bolt
/// kills sets a Blood Bat loose on the next hostile nearby (see BloodBat).
public class BloodOrb extends Fireball {
    public static final int LIFETIME = 160;
    private static final double SPEED = 0.15;
    public static final int BOLT_INTERVAL = 10;
    private static final double BOLT_RANGE = 8.0;
    private static final int BOLTS_PER_VOLLEY = 3;
    private static final float BASE_BOLT_DAMAGE = 2.2F;
    /// The 40 blood a player puts in makes every bolt hit for about 3.1
    private static final float BOLT_DAMAGE_PER_BLOOD = 0.022F;
    public static final int OVERHEAL_DURATION = 200;

    private int bloodSpent;
    /// Creatures that have already fed this orb's overheal
    private final Set<UUID> overhealedFrom = new HashSet<>();

    public BloodOrb(EntityType<? extends BloodOrb> type, Level level) {
        super(type, level);
        // Drifts at a steady pace rather than speeding up like a fireball
        this.accelerationPower = 0.0;
    }

    public BloodOrb(Level level, LivingEntity owner, Vec3 direction, int bloodSpent) {
        super(ModEntities.BLOOD_ORB.get(), owner, direction, level);
        this.accelerationPower = 0.0;
        this.setDeltaMovement(direction.normalize().scale(SPEED));
        this.bloodSpent = bloodSpent;
        this.setItem(new ItemStack(ModItems.BLOOD_ORB.get()));
    }

    public float boltDamage() {
        return BASE_BOLT_DAMAGE + this.bloodSpent * BOLT_DAMAGE_PER_BLOOD;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive()) {
            return;
        }
        // Droplets spraying off it
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 1.8F), this.getX(), this.getY(0.5), this.getZ(), 3, 0.35, 0.35, 0.35, 0.0);
        level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 0.6F), this.getX(), this.getY(0.5), this.getZ(), 4, 0.9, 0.9, 0.9, 0.0);
        if (this.tickCount % BOLT_INTERVAL == 0) {
            this.lashOut(level);
        }
        if (this.tickCount >= LIFETIME) {
            level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 2.5F), this.getX(), this.getY(0.5), this.getZ(), 40, 0.6, 0.6, 0.6, 0.0);
            this.playSound(SoundEvents.HONEY_BLOCK_BREAK, 1.0F, 0.5F);
            this.discard();
        }
    }

    /// One volley: a bolt at each of the nearest hostiles it can see
    private void lashOut(ServerLevel level) {
        LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
        Vec3 centre = this.position().add(0.0, this.getBbHeight() * 0.5, 0.0);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(BOLT_RANGE),
                        entity -> isHostile(entity, owner) && entity.distanceToSqr(centre) <= BOLT_RANGE * BOLT_RANGE && this.canSee(level, centre, entity))
                .stream().sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(centre))).limit(BOLTS_PER_VOLLEY).toList();
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = this.damageSources().source(ModDamageTypes.BLOOD_MAGIC, this, owner == null ? this : owner);
        for (LivingEntity target : targets) {
            drawBolt(level, centre, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
            float before = target.getHealth();
            // Every volley lands in full, however recently the last one did
            target.damageCooldownTime = 0;
            boolean hurt = target.hurtServer(level, source, this.boltDamage());
            if (hurt && !target.isAlive()) {
                BloodBat.chain(level, owner, target, BloodBat.FIRST_RANGE);
            }
            if (hurt && owner != null && owner.isAlive()) {
                boolean firstOverheal = !this.overhealedFrom.contains(target.getUUID());
                // The overheal holds while the orb lasts, and its 10 seconds start once the orb is gone
                int overhealTicks = LIFETIME - this.tickCount + OVERHEAL_DURATION;
                if (drinkFromBolt(owner, Math.max(0.0F, before - target.getHealth()), firstOverheal, overhealTicks)) {
                    this.overhealedFrom.add(target.getUUID());
                }
            }
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 0.8F, 0.5F);
    }

    /// What the vampire skills (Blood Parade, Afterimage Strike, Crimson Lightning) lash at: monsters, hunters (the
    /// vampires' sworn enemies, even when they aren't after the caster this moment), and anything else out to get the
    /// caster. Never players, the caster's pets, thralls or allies.
    public static boolean isHostile(LivingEntity entity, @Nullable LivingEntity owner) {
        if (entity == owner || !entity.isAlive() || entity instanceof ArmorStand || entity instanceof Player) {
            return false;
        }
        if (owner != null && (entity.isAlliedTo(owner) || (entity instanceof OwnableEntity pet && pet.getOwner() == owner) || Enthrall.isThrallOf(entity, owner))) {
            return false;
        }
        return entity instanceof Enemy || entity instanceof Hunter || (entity instanceof Mob mob && owner != null && mob.getTarget() == owner);
    }

    private boolean canSee(ServerLevel level, Vec3 from, LivingEntity target) {
        Vec3 to = target.getEyePosition();
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    // A thin streak of blood from the orb to its target
    private static void drawBolt(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int points = Math.max(2, (int) (step.length() / 0.35));
        for (int i = 0; i <= points; i++) {
            Vec3 point = from.add(step.scale((double) i / points));
            level.sendParticles(new DustParticleOptions(Vampire.BLOOD_COLOR, 0.7F), point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /// The blood a bolt draws heals the caster. Past full health it becomes overheal (if `mayOverheal`), held for
    /// `overhealTicks` (see ModEffects.OVERHEAL, which makes room for it). Returns whether it added overheal.
    public static boolean drinkFromBolt(LivingEntity caster, float drawn, boolean mayOverheal, int overhealTicks) {
        if (drawn <= 0.0F) {
            return false;
        }
        float healed = Math.min(drawn, caster.getMaxHealth() - caster.getHealth());
        if (healed > 0.0F) {
            caster.heal(healed);
        }
        float excess = drawn - healed;
        if (excess <= 0.0F || !mayOverheal) {
            return false;
        }
        caster.addEffect(new MobEffectInstance(ModEffects.OVERHEAL, overhealTicks, 0, false, false, true));
        caster.setAbsorptionAmount(Math.min(caster.getAbsorptionAmount() + excess, caster.getMaxAbsorption()));
        return true;
    }

    // It drifts through creatures, and bounces off walls
    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        if (hitResult instanceof BlockHitResult blockHit) {
            Vec3 movement = this.getDeltaMovement();
            this.setDeltaMovement(switch (blockHit.getDirection().getAxis()) {
                case X -> new Vec3(-movement.x, movement.y, movement.z);
                case Y -> new Vec3(movement.x, -movement.y, movement.z);
                case Z -> new Vec3(movement.x, movement.y, -movement.z);
            });
            this.needsSync = true;
            this.playSound(SoundEvents.SLIME_SQUISH, 1.0F, 0.6F);
        }
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected float getLiquidInertia() {
        return 1.0F;
    }

    // Blood doesn't set things alight, and can't be batted back like a ghast's fireball
    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected @Nullable ParticleOptions getTrailParticle() {
        return null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BloodSpent", this.bloodSpent);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.bloodSpent = input.getIntOr("BloodSpent", 0);
        this.accelerationPower = 0.0;
    }
}
