package com.zestudio.crimsoncovenant.vampirism;

import java.util.EnumSet;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.GarlicBraidBlock;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModVillagers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/// Hung garlic keeps the undead away. Zombies, skeletons, vampires and the like that come within 8 blocks of a
/// Garlic Braid turn and walk away from it, so a braid over a door keeps them out of the house. Bosses and Master
/// Vampires don't care. A vampire player can stand it, but not up close: within 4 blocks the reek weakens them.
///
/// The braids are found through the point-of-interest index (like villagers find their beds), so looking for one
/// is cheap.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class GarlicWard {
    public static final int RADIUS = 8;
    public static final int VAMPIRE_PLAYER_RADIUS = 4;
    private static final int CHECK_INTERVAL = 10;
    private static final int REEK_DURATION = 60;

    private GarlicWard() {}

    public static Optional<BlockPos> nearestGarlic(ServerLevel level, BlockPos pos, int radius) {
        return level.getPoiManager().findClosest(type -> type.is(ModVillagers.GARLIC_BRAID_POI.getKey()), pos, radius, PoiManager.Occupancy.ANY);
    }

    public static boolean shunsGarlic(PathfinderMob mob) {
        return mob.is(EntityTypeTags.UNDEAD) && !mob.is(Tags.EntityTypes.BOSSES) && !(mob instanceof MasterVampire);
    }

    @SubscribeEvent
    static void addShunGoal(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof PathfinderMob mob && shunsGarlic(mob)) {
            mob.goalSelector.addGoal(-1, new ShunGarlicGoal(mob));
        }
    }

    /// Called every tick for vampire players, from Vampirism
    public static void tickVampirePlayer(ServerPlayer player) {
        if (player.tickCount % CHECK_INTERVAL == 0 && !player.isCreative() && !player.isSpectator()) {
            reelFromGarlic(player);
        }
    }

    /// Weakens a vampire player standing too close to hung garlic. Returns whether there was any.
    public static boolean reelFromGarlic(ServerPlayer player) {
        if (nearestGarlic(player.level(), player.blockPosition(), VAMPIRE_PLAYER_RADIUS).isEmpty()) {
            return false;
        }
        if (!player.hasEffect(ModEffects.GARLIC)) {
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.garlic.reek").withStyle(ChatFormatting.DARK_RED));
        }
        player.addEffect(new MobEffectInstance(ModEffects.GARLIC, REEK_DURATION));
        return true;
    }

    /// Walks away from any braid within reach, for as long as one is within reach. It takes over movement, so the
    /// mob can't chase anyone standing under the garlic.
    static class ShunGarlicGoal extends Goal {
        private static final double SPEED = 1.2;
        private final PathfinderMob mob;
        private @Nullable BlockPos garlic;
        private int untilNextSniff = 0;

        ShunGarlicGoal(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            // Goals are only looked at every other tick, so this counts checks rather than ticks
            if (--this.untilNextSniff > 0 || !(this.mob.level() instanceof ServerLevel level)) {
                return false;
            }
            this.untilNextSniff = reducedTickDelay(CHECK_INTERVAL);
            this.garlic = nearestGarlic(level, this.mob.blockPosition(), RADIUS).orElse(null);
            return this.garlic != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.garlic != null && this.mob.level().getBlockState(this.garlic).getBlock() instanceof GarlicBraidBlock
                    && this.mob.blockPosition().closerThan(this.garlic, RADIUS + 1);
        }

        @Override
        public void start() {
            this.walkAway();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.mob.getNavigation().isDone() || this.mob.tickCount % 20 == 0) {
                this.walkAway();
            }
        }

        @Override
        public void stop() {
            this.garlic = null;
            this.mob.getNavigation().stop();
        }

        private void walkAway() {
            if (this.garlic == null) {
                return;
            }
            Vec3 away = DefaultRandomPos.getPosAway(this.mob, RADIUS * 2, 7, Vec3.atCenterOf(this.garlic));
            if (away != null) {
                this.mob.getNavigation().moveTo(away.x, away.y, away.z, SPEED);
            }
        }
    }
}
