package com.zestudio.crimsoncovenant.entity;

import com.zestudio.crimsoncovenant.block.BloodThroneBlock;
import com.zestudio.crimsoncovenant.block.IronMaidenBlock;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/// The unseen seat on a Blood Throne, there to be ridden by whoever sits on it, and gone once they get up (or the
/// throne's broken). While someone sits, it siphons the captives in the Iron Maidens about the throne: every couple of
/// seconds the sitter heals a little for each, and a vampire drinks a little of each too.
public class ThroneSeat extends Entity {
    public static final int SIPHON_INTERVAL = 40;
    /// What each captive gives, every siphon
    public static final int BLOOD_PER_CAPTIVE = 2;
    public static final float HEALTH_PER_CAPTIVE = 1.0F;
    // The seat's own height: the sitter sits on top of it, so it stands that much below the cushion
    private static final double HEIGHT = 0.1;

    public ThroneSeat(EntityType<? extends ThroneSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static ThroneSeat at(Level level, BlockPos throne) {
        ThroneSeat seat = new ThroneSeat(ModEntities.THRONE_SEAT.get(), level);
        seat.snapTo(throne.getX() + 0.5, throne.getY() + BloodThroneBlock.SEAT_HEIGHT - HEIGHT, throne.getZ() + 0.5, 0.0F, 0.0F);
        return seat;
    }

    private BlockPos thronePos() {
        return BlockPos.containing(this.getX(), this.getY() + 0.25, this.getZ());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState throne = level.getBlockState(this.thronePos());
        if (!(throne.getBlock() instanceof BloodThroneBlock) || (this.tickCount > 2 && this.getPassengers().isEmpty())) {
            this.ejectPassengers();
            this.discard();
            return;
        }
        // The sitter looks out the way the throne faces
        if (this.getFirstPassenger() instanceof LivingEntity sitter && this.tickCount == 1) {
            sitter.setYRot(throne.getValue(BloodThroneBlock.FACING).toYRot());
        }
        if (this.tickCount % SIPHON_INTERVAL == 0 && this.getFirstPassenger() instanceof Player sitter) {
            siphon(sitter, this.thronePos());
        }
    }

    /// Heals the sitter (and feeds a vampire) from the captives about the throne, out of the blood left in them, taking
    /// no more than the sitter needs. Returns how many were drawn on.
    public static int siphon(Player sitter, BlockPos throne) {
        return IronMaidenBlock.restore(sitter, throne, BLOOD_PER_CAPTIVE, HEALTH_PER_CAPTIVE);
    }

    // Up and off, in front of the throne
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockState throne = this.level().getBlockState(this.thronePos());
        Direction front = throne.getBlock() instanceof BloodThroneBlock ? throne.getValue(BloodThroneBlock.FACING) : Direction.NORTH;
        BlockPos step = this.thronePos().relative(front);
        return new Vec3(step.getX() + 0.5, step.getY(), step.getZ() + 0.5);
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
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
