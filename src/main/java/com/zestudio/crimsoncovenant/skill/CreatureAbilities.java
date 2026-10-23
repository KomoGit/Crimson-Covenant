package com.zestudio.crimsoncovenant.skill;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.PossessionState;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/// What a possessed creature does with the attack button, as the creature itself would:
/// - creatures that draw or charge (skeletons and their kin draw a bow, pillagers and hunters load a crossbow,
///   ghasts and blazes build up a fireball) charge while it's held, and let fly on release;
/// - creatures that just shoot (witches, snow golems, llamas, breezes, shulkers) shoot on the click;
/// - a creeper lights its fuse;
/// - everything else bites what the crosshair is on, or, clicking at nothing, uses its trick: an enderman
///   teleports where it looks, spiders and wolves pounce, slimes leap, iron golems slam the ground...
///
/// Projectiles belong to the vampire, so kills count as theirs.
public final class CreatureAbilities {
    /// A creeper's fuse: a second and a half, the same as a real one's
    public static final int CREEPER_FUSE = 30;
    /// Ticks to fully draw a bow, load a crossbow, or build up a fireball
    public static final int BOW_DRAW = 20;
    public static final int CROSSBOW_LOAD = 25;
    public static final int FIREBALL_CHARGE = 20;
    private static final int BITE_COOLDOWN = 10;
    private static final double BITE_REACH = 3.5;

    private enum Kind { BOW, CROSSBOW, FIREBALL, INSTANT_SHOT, CREEPER, MELEE }

    private CreatureAbilities() {}

    private static Kind kindOf(EntityType<?> type) {
        if (type == EntityTypes.SKELETON || type == EntityTypes.STRAY || type == EntityTypes.BOGGED) {
            return Kind.BOW;
        }
        if (type == EntityTypes.PILLAGER || type == ModEntities.HUNTER.get()) {
            return Kind.CROSSBOW;
        }
        if (type == EntityTypes.GHAST || type == EntityTypes.BLAZE) {
            return Kind.FIREBALL;
        }
        if (type == EntityTypes.WITCH || type == EntityTypes.SNOW_GOLEM || type == EntityTypes.LLAMA || type == EntityTypes.TRADER_LLAMA
                || type == EntityTypes.BREEZE || type == EntityTypes.SHULKER) {
            return Kind.INSTANT_SHOT;
        }
        if (type == EntityTypes.CREEPER) {
            return Kind.CREEPER;
        }
        return Kind.MELEE;
    }

    /// Whether this creature charges up while the attack button is held (a bow, a crossbow, a fireball)
    public static boolean charges(EntityType<?> type) {
        Kind kind = kindOf(type);
        return kind == Kind.BOW || kind == Kind.CROSSBOW || kind == Kind.FIREBALL;
    }

    /// The attack button went down. `target` is what the crosshair was on, if anything.
    public static void press(ServerPlayer player, @Nullable Entity target) {
        EntityType<?> type = Possession.possessedType(player).orElse(null);
        PossessionState state = player.getData(ModAttachments.POSSESSION_STATE);
        if (type == null || player.getData(ModAttachments.CREEPER_FUSE) > 0) {
            return;
        }
        switch (kindOf(type)) {
            case BOW, CROSSBOW, FIREBALL -> {
                if (state.abilityCooldown() == 0) {
                    player.setData(ModAttachments.POSSESSED_CHARGE, 1);
                    sound(player, type == EntityTypes.GHAST ? SoundEvents.GHAST_WARN : type == EntityTypes.BLAZE ? SoundEvents.BLAZE_AMBIENT
                            : kindOf(type) == Kind.CROSSBOW ? SoundEvents.CROSSBOW_LOADING_START.value() : SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F);
                }
            }
            case INSTANT_SHOT -> {
                if (state.abilityCooldown() == 0) {
                    setAbilityCooldown(player, shootInstantly(type, player));
                }
            }
            case CREEPER -> {
                // Light the fuse: it hisses and swells, then blows (see tick)
                player.setData(ModAttachments.CREEPER_FUSE, CREEPER_FUSE);
                sound(player, SoundEvents.CREEPER_PRIMED, 0.5F);
            }
            case MELEE -> {
                if (target instanceof LivingEntity living && living.isAlive() && player.distanceTo(living) <= BITE_REACH + living.getBbWidth()) {
                    bite(type, player, living);
                } else if (state.abilityCooldown() == 0) {
                    int cooldown = useTrick(type, player);
                    if (cooldown > 0) {
                        setAbilityCooldown(player, cooldown);
                    }
                }
            }
        }
    }

    /// The attack button came back up: a drawn bow, loaded crossbow or built-up fireball is let loose
    public static void release(ServerPlayer player) {
        int charge = player.getData(ModAttachments.POSSESSED_CHARGE);
        EntityType<?> type = Possession.possessedType(player).orElse(null);
        if (charge <= 0 || type == null) {
            return;
        }
        player.setData(ModAttachments.POSSESSED_CHARGE, 0);
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle();
        switch (kindOf(type)) {
            case BOW -> {
                float power = BowItem.getPowerForTime(charge);
                if (power < 0.1F) {
                    return;
                }
                Arrow arrow = arrow(player, type);
                arrow.setCritArrow(power >= 1.0F);
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);
                level.addFreshEntity(arrow);
                sound(player, SoundEvents.SKELETON_SHOOT, 1.0F);
                setAbilityCooldown(player, 5);
            }
            case CROSSBOW -> {
                // A crossbow only fires once it's fully loaded
                if (charge < CROSSBOW_LOAD) {
                    return;
                }
                Arrow bolt = arrow(player, type);
                bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.15F, 1.0F);
                level.addFreshEntity(bolt);
                sound(player, SoundEvents.CROSSBOW_SHOOT, 1.0F);
                setAbilityCooldown(player, 10);
            }
            case FIREBALL -> {
                if (charge < FIREBALL_CHARGE) {
                    return;
                }
                if (type == EntityTypes.GHAST) {
                    LargeFireball fireball = new LargeFireball(level, player, look, 1);
                    fireball.setPos(player.getEyePosition().add(look.scale(2.0)));
                    level.addFreshEntity(fireball);
                    sound(player, SoundEvents.GHAST_SHOOT, 1.0F);
                } else {
                    for (int i = 0; i < 3; i++) {
                        Vec3 spread = look.add(player.getRandom().triangle(0.0, 0.1), player.getRandom().triangle(0.0, 0.1), player.getRandom().triangle(0.0, 0.1));
                        SmallFireball fireball = new SmallFireball(level, player, spread.normalize());
                        fireball.setPos(player.getEyePosition().add(look));
                        level.addFreshEntity(fireball);
                    }
                    sound(player, SoundEvents.BLAZE_SHOOT, 1.0F);
                }
                setAbilityCooldown(player, 40);
            }
            default -> {
            }
        }
    }

    /// Called every tick while possessing: draws a held bow further, and burns down a lit creeper fuse
    static void tick(ServerPlayer player) {
        int charge = player.getData(ModAttachments.POSSESSED_CHARGE);
        if (charge > 0) {
            player.setData(ModAttachments.POSSESSED_CHARGE, Math.min(charge + 1, 72000));
            if (charge == CROSSBOW_LOAD && Possession.possessedType(player).map(type -> kindOf(type) == Kind.CROSSBOW).orElse(false)) {
                sound(player, SoundEvents.CROSSBOW_LOADING_END.value(), 1.0F);
            }
            if (player.tickCount % 4 == 0 && Possession.possessedType(player).map(type -> type == EntityTypes.BLAZE).orElse(false)) {
                player.level().sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.6), player.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
            }
        }
        int fuse = player.getData(ModAttachments.CREEPER_FUSE);
        if (fuse > 0) {
            player.setData(ModAttachments.CREEPER_FUSE, fuse - 1);
            if (fuse == 1) {
                explodeAsCreeper(player);
            }
        }
    }

    private static Arrow arrow(ServerPlayer player, EntityType<?> type) {
        Arrow arrow = new Arrow(player.level(), player, new ItemStack(Items.ARROW), null);
        if (type == EntityTypes.STRAY) {
            arrow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600));
        } else if (type == EntityTypes.BOGGED) {
            arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        }
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        return arrow;
    }

    /// Creatures that shoot the moment they decide to. Returns the cooldown, 0 if nothing was shot.
    private static int shootInstantly(EntityType<?> type, ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle();
        if (type == EntityTypes.WITCH) {
            ItemStack potion = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING);
            Projectile.spawnProjectileUsingShoot(ThrownSplashPotion::new, level, potion, player, look.x, look.y + 0.2, look.z, 0.75F, 2.0F);
            sound(player, SoundEvents.WITCH_THROW, 1.0F);
            return 40;
        }
        if (type == EntityTypes.SNOW_GOLEM) {
            Projectile.spawnProjectileUsingShoot(Snowball::new, level, new ItemStack(Items.SNOWBALL), player, look.x, look.y, look.z, 1.6F, 1.0F);
            sound(player, SoundEvents.SNOW_GOLEM_SHOOT, 1.0F);
            return 5;
        }
        if (type == EntityTypes.LLAMA || type == EntityTypes.TRADER_LLAMA) {
            LlamaSpit spit = new LlamaSpit(EntityTypes.LLAMA_SPIT, level);
            spit.setOwner(player);
            spit.setPos(player.getEyePosition().add(look.scale(0.5)));
            spit.shoot(look.x, look.y, look.z, 1.5F, 1.0F);
            level.addFreshEntity(spit);
            sound(player, SoundEvents.LLAMA_SPIT, 1.0F);
            return 20;
        }
        if (type == EntityTypes.BREEZE) {
            WindCharge charge = new WindCharge(player, level, player.getX(), player.getEyeY(), player.getZ());
            charge.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(charge);
            sound(player, SoundEvents.BREEZE_SHOOT, 1.0F);
            return 20;
        }
        if (type == EntityTypes.SHULKER) {
            LivingEntity target = Possession.lookedAt(player, LivingEntity.class, 32.0);
            if (target == null) {
                return 0;
            }
            level.addFreshEntity(new ShulkerBullet(level, player, target, Direction.Axis.Y));
            sound(player, SoundEvents.SHULKER_SHOOT, 1.0F);
            return 40;
        }
        return 0;
    }

    /// What a creature does when it attacks at nothing. Returns the cooldown, 0 if it did nothing.
    private static int useTrick(EntityType<?> type, ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle();
        if (type == EntityTypes.ENDERMAN) {
            return teleportAsEnderman(player) ? 20 : 0;
        }
        if (type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER || type == EntityTypes.WOLF || type == EntityTypes.CAT
                || type == EntityTypes.OCELOT || type == EntityTypes.FOX || type == ModEntities.WEREWOLF.get()) {
            // Pounce
            player.setDeltaMovement(look.x * 1.2, 0.5, look.z * 1.2);
            player.syncVelocity = true;
            sound(player, SoundEvents.SPIDER_AMBIENT, 1.4F);
            return 30;
        }
        if (type == EntityTypes.SLIME || type == EntityTypes.MAGMA_CUBE || type == EntityTypes.RABBIT || type == EntityTypes.FROG
                || type == EntityTypes.GOAT) {
            // A huge leap
            player.setDeltaMovement(player.getDeltaMovement().x, 1.1, player.getDeltaMovement().z);
            player.syncVelocity = true;
            sound(player, SoundEvents.SLIME_JUMP, 0.8F);
            return 30;
        }
        if (type == EntityTypes.CHICKEN) {
            ItemEntity egg = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.EGG));
            egg.setDefaultPickUpDelay();
            level.addFreshEntity(egg);
            sound(player, SoundEvents.CHICKEN_EGG, 1.0F);
            return 100;
        }
        if (type == EntityTypes.IRON_GOLEM) {
            // Slams the ground, flinging everything close by into the air
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4.0), entity -> entity != player)) {
                if (victim.hurtServer(level, level.damageSources().playerAttack(player), 7.0F)) {
                    victim.setDeltaMovement(victim.getDeltaMovement().add(0.0, 0.6, 0.0));
                }
            }
            level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 4, 1.5, 0.1, 1.5, 0.0);
            sound(player, SoundEvents.IRON_GOLEM_ATTACK, 0.8F);
            return 60;
        }
        if (type == ModEntities.VAMPIRE.get()) {
            player.addEffect(new MobEffectInstance(ModEffects.BLOODLUST, 200));
            sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0F);
            return 200;
        }
        return 0;
    }

    /// The creature's own bite, with its venom if it has any. An iron golem flings what it hits.
    static void bite(EntityType<?> type, ServerPlayer player, LivingEntity target) {
        PossessionState state = player.getData(ModAttachments.POSSESSION_STATE);
        if (state.attackCooldown() > 0) {
            return;
        }
        ServerLevel level = player.level();
        DamageSource bite = level.damageSources().playerAttack(player);
        if (target.hurtServer(level, bite, state.attackDamage())) {
            target.knockback(0.4, Mth.sin(player.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(player.getYRot() * Mth.DEG_TO_RAD), bite, state.attackDamage());
            if (type == EntityTypes.CAVE_SPIDER) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 140), player);
            } else if (type == EntityTypes.WITHER_SKELETON) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200), player);
            } else if (type == EntityTypes.HUSK) {
                target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 140), player);
            } else if (type == EntityTypes.IRON_GOLEM) {
                target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.4, 0.0));
            }
        }
        state = player.getData(ModAttachments.POSSESSION_STATE);
        player.setData(ModAttachments.POSSESSION_STATE, new PossessionState(state.ticksLeft(), state.health(), state.attackDamage(), state.abilityCooldown(), BITE_COOLDOWN));
    }

    private static void setAbilityCooldown(ServerPlayer player, int cooldown) {
        if (Possession.isPossessing(player)) {
            PossessionState state = player.getData(ModAttachments.POSSESSION_STATE);
            player.setData(ModAttachments.POSSESSION_STATE, new PossessionState(state.ticksLeft(), state.health(), state.attackDamage(), cooldown, state.attackCooldown()));
        }
    }

    /// The creeper blows up, and is gone. The vampire is spared their own blast.
    private static void explodeAsCreeper(ServerPlayer player) {
        boolean powered = player.getData(ModAttachments.POSSESSED_FORM).map(form -> form.data().getBooleanOr("powered", false)).orElse(false);
        ExplosionDamageCalculator sparesTheVampire = new ExplosionDamageCalculator() {
            @Override
            public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return entity != player && super.shouldDamageEntity(explosion, entity);
            }
        };
        player.level().explode(player, null, sparesTheVampire, player.getX(), player.getY(), player.getZ(), powered ? 6.0F : 3.0F, false, Level.ExplosionInteraction.MOB);
        Possession.end(player, false, null);
    }

    /// Teleports to the block the vampire is looking at, up to 32 blocks away, like an enderman
    private static boolean teleportAsEnderman(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(32.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return false;
        }
        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        Vec3 from = player.position();
        if (!player.randomTeleport(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, true, state -> false)) {
            return false;
        }
        player.level().sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 30, 0.3, 0.6, 0.3, 0.1);
        sound(player, SoundEvents.ENDERMAN_TELEPORT, 1.0F);
        return true;
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float pitch) {
        BloodSkills.playSound(player, sound, pitch);
    }
}
