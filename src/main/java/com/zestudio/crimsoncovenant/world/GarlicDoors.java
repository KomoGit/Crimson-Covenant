package com.zestudio.crimsoncovenant.world;

import java.util.Comparator;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.GarlicBraidBlock;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModVillagers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/// Plague Doctors help a village prepare for the vampires: they hang a garlic braid over every door they come across
/// that hasn't got one, on the outside, above the door. The wandering doctor goes looking for bare doors (see
/// PlagueDoctor.HangGarlicGoal); a Plague Doctor villager sees to the ones he passes on his rounds.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class GarlicDoors {
    /// How close a doctor villager has to pass a door to hang garlic on it
    public static final int REACH = 4;
    private static final int VILLAGER_INTERVAL = 100;

    private GarlicDoors() {}

    /// The nearest door around `doctor` that he can see and that has no garlic yet: where its braid would go
    public static Optional<BlockPos> findBareDoor(Mob doctor, int range) {
        Level level = doctor.level();
        BlockPos around = doctor.blockPosition();
        Comparator<BlockPos> nearest = Comparator.comparingDouble(pos -> pos.distSqr(around));
        return BlockPos.betweenClosedStream(around.offset(-range, -2, -range), around.offset(range, 3, range))
                .filter(pos -> isLowerDoor(level.getBlockState(pos)) && canSee(doctor, pos))
                .map(pos -> braidSpot(level, pos.immutable()))
                .filter(spot -> spot != null)
                .min(nearest);
    }

    private static boolean isLowerDoor(BlockState state) {
        return state.is(BlockTags.WOODEN_DOORS) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean canSee(Mob doctor, BlockPos door) {
        Vec3 target = Vec3.atCenterOf(door.above());
        BlockHitResult hit = doctor.level().clip(new ClipContext(doctor.getEyePosition(), target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, doctor));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(door) || hit.getBlockPos().equals(door.above());
    }

    /// Where a braid goes for the door whose lower half is at `door`: against the wall above it, on the outside (the
    /// side open to the sky, or else the side the door faces). Null if there's garlic by the door already, or
    /// nothing above it to hang one on.
    public static @Nullable BlockPos braidSpot(Level level, BlockPos door) {
        BlockState state = level.getBlockState(door);
        if (!isLowerDoor(state)) {
            return null;
        }
        BlockPos lintel = door.above(2);
        boolean warded = BlockPos.betweenClosedStream(lintel.offset(-1, -2, -1), lintel.offset(1, 1, 1))
                .anyMatch(pos -> level.getBlockState(pos).is(ModBlocks.GARLIC_BRAID.get()));
        if (warded) {
            return null;
        }
        Direction facing = state.getValue(DoorBlock.FACING);
        Direction outside = !level.canSeeSky(door.relative(facing)) && level.canSeeSky(door.relative(facing.getOpposite())) ? facing.getOpposite() : facing;
        BlockPos spot = lintel.relative(outside);
        return level.getBlockState(spot).isAir() && braid(outside).canSurvive(level, spot) ? spot : null;
    }

    private static BlockState braid(Direction outside) {
        return ModBlocks.GARLIC_BRAID.get().defaultBlockState().setValue(GarlicBraidBlock.FACING, outside).setValue(GarlicBraidBlock.HANGING, false);
    }

    /// Hangs the braid at a spot found by braidSpot, on the wall over the door, facing out
    public static boolean hang(Mob doctor, BlockPos spot) {
        Level level = doctor.level();
        for (Direction outside : Direction.Plane.HORIZONTAL) {
            BlockState braid = braid(outside);
            BlockPos lintel = spot.relative(outside.getOpposite());
            if (level.getBlockState(lintel.below()).getBlock() instanceof DoorBlock && level.getBlockState(spot).isAir() && braid.canSurvive(level, spot)) {
                level.setBlock(spot, braid, 3);
                doctor.swingForAttack(InteractionHand.MAIN_HAND);
                level.playSound(null, spot, SoundEvents.CROP_PLANTED, SoundSource.NEUTRAL, 1.0F, 0.9F);
                return true;
            }
        }
        return false;
    }

    /// A Plague Doctor villager hangs garlic over any bare door he's near
    public static boolean tendDoors(Villager doctor) {
        return findBareDoor(doctor, REACH).map(spot -> hang(doctor, spot)).orElse(false);
    }

    @SubscribeEvent
    static void doctorVillagersTendDoors(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel && !villager.isSleeping()
                && (villager.tickCount + villager.getId()) % VILLAGER_INTERVAL == 0 && villager.getVillagerData().profession().is(ModVillagers.PLAGUE_DOCTOR.getKey())) {
            tendDoors(villager);
        }
    }
}
