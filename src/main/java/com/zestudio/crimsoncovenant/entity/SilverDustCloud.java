package com.zestudio.crimsoncovenant.entity;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModDamageTypes;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.bus.api.EventPriority;

/// A puff of silver dust, kicked up when silver ore is broken (unless it's lifted out whole, with Silk Touch). It
/// hangs in the air a few seconds, glittering, and the undead that breathe it in are burned from inside: vampire
/// players too. The living don't mind it.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class SilverDustCloud extends Entity {
    public static final int LIFETIME = 100;
    public static final double RADIUS = 2.0;
    private static final int BREATH_INTERVAL = 10;
    public static final float DAMAGE = 2.0F;
    private static final int SILVER = 0xD8DEE4;
    private static final int PALE_SILVER = 0xF2F5F8;

    private int life = LIFETIME;

    public SilverDustCloud(EntityType<? extends SilverDustCloud> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SilverDustCloud kickUp(ServerLevel level, BlockPos ore) {
        SilverDustCloud cloud = new SilverDustCloud(ModEntities.SILVER_DUST_CLOUD.get(), level);
        cloud.snapTo(ore.getX() + 0.5, ore.getY(), ore.getZ() + 0.5, 0.0F, 0.0F);
        level.addFreshEntity(cloud);
        return cloud;
    }

    public static boolean isSilverOre(BlockState state) {
        return state.is(ModBlocks.SILVER_ORE.get()) || state.is(ModBlocks.DEEPSLATE_SILVER_ORE.get());
    }

    // Last, so it's only for ore that really does break
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void oreGivesUpItsDust(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !isSilverOre(event.getState())) {
            return;
        }
        ItemStack tool = event.getPlayer().getMainHandItem();
        if (EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), tool) > 0) {
            return;
        }
        kickUp(level, event.getPos());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (--this.life <= 0) {
            this.discard();
            return;
        }
        this.glitter(level);
        if (this.tickCount % BREATH_INTERVAL == 0) {
            this.burnTheUndead(level);
        }
    }

    // A glittering haze, settling as it thins
    private void glitter(ServerLevel level) {
        int specks = this.life < 30 ? 1 : 4;
        for (int i = 0; i < specks; i++) {
            double x = this.getX() + (this.random.nextDouble() - 0.5) * RADIUS * 2.0;
            double y = this.getY() + this.random.nextDouble() * RADIUS;
            double z = this.getZ() + (this.random.nextDouble() - 0.5) * RADIUS * 2.0;
            int colour = this.random.nextBoolean() ? SILVER : PALE_SILVER;
            level.sendParticles(new DustParticleOptions(colour, 0.8F + this.random.nextFloat() * 0.6F), x, y, z, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private void burnTheUndead(ServerLevel level) {
        AABB area = new AABB(this.getX() - RADIUS, this.getY() - 0.5, this.getZ() - RADIUS, this.getX() + RADIUS, this.getY() + RADIUS * 1.5, this.getZ() + RADIUS);
        DamageSource dust = level.damageSources().source(ModDamageTypes.SILVER_DUST);
        for (LivingEntity breather : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive() && Vampirism.isUndead(entity))) {
            breather.hurtServer(level, dust, DAMAGE);
        }
    }

    public int getLife() {
        return this.life;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.life = input.getIntOr("life", LIFETIME);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("life", this.life);
    }
}
