package com.zestudio.crimsoncovenant.curse;

import java.util.Optional;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.WhipLash;
import com.zestudio.crimsoncovenant.item.WhipItem;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.GrappleLine;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// Wraith's Tether (the Grapple haunting, whips only, always awake): right-click a block and the lash hooks round it,
/// stays taut, and hauls its wielder in to it: onto its top if that's the face they hooked, else up against its side.
/// A creature within the whip's own reach is pulled in instead, as a right-click always does (see item.WhipItem).
/// Being hauled, the wielder takes no fall damage; the haul ends when they get there, or soon after they'd have got
/// there (when something's in the way).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Grapple {
    /// How fast the wielder is hauled, in blocks a tick
    public static final double SPEED = 1.2;
    /// Close enough: the haul's done
    private static final double ARRIVED = 0.8;
    /// Extra ticks allowed, beyond the time the haul should take
    private static final int SPARE_TICKS = 10;
    /// Hooked on the side of a block, the wielder stops this far out from it
    private static final double OFF_THE_WALL = 0.5;

    private Grapple() {}

    /// How far the lash can reach to hook a block
    public static double range(int level) {
        return 8.0 + 8.0 * level;
    }

    public static boolean isGrappling(Player player) {
        return player.getData(ModAttachments.GRAPPLE).isPresent();
    }

    /// Hooks the block the player's aiming at, if their whip bears Wraith's Tether (and they're of rank for it), the block
    /// is within its reach, and no creature's in the whip's own reach to be pulled in instead. Returns whether it hooked.
    public static boolean hook(ServerLevel level, Player player, ItemStack whip) {
        int tether = Curses.looseLevel(player, Curse.GRAPPLE, whip);
        if (tether == 0 || !WhipItem.isWhip(whip) || !WhipLash.reached(level, player).isEmpty()) {
            return false;
        }
        Vec3 eyes = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eyes, eyes.add(player.getViewVector(1.0F).scale(range(tether))), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        Vec3 anchor = hit.getLocation();
        Direction face = hit.getDirection();
        // Onto the top, feet first; else up against the side, body to the block
        Vec3 target = face == Direction.UP ? anchor
                : anchor.add(new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()).scale(OFF_THE_WALL)).subtract(0.0, player.getBbHeight() * 0.5, 0.0);
        int ticks = (int) Math.ceil(target.distanceTo(player.position()) / SPEED) + SPARE_TICKS;
        player.setData(ModAttachments.GRAPPLE, Optional.of(new GrappleLine(target, level.getGameTime() + ticks)));
        WhipLash.crack(player, WhipLash.Kind.SHOW, anchor, ticks + WhipLash.LASH_TICKS);
        level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 0.6F);
        return true;
    }

    /// Where the haul takes the player this tick (none if they're not being hauled, or there already)
    public static Vec3 haul(Player player) {
        Optional<GrappleLine> line = player.getData(ModAttachments.GRAPPLE);
        if (line.isEmpty()) {
            return Vec3.ZERO;
        }
        Vec3 toward = line.get().target().subtract(player.position());
        double distance = toward.length();
        // Slowing over the last stretch, so they arrive rather than fly past
        return distance < ARRIVED ? Vec3.ZERO : toward.scale(Math.min(SPEED, distance * 0.6) / distance);
    }

    /// Hauls the player in a tick's worth, and lets go once they're there (or the time's up, or the whip's put away)
    public static void tick(ServerLevel level, Player player) {
        Optional<GrappleLine> line = player.getData(ModAttachments.GRAPPLE);
        if (line.isEmpty()) {
            return;
        }
        Vec3 haul = haul(player);
        if (haul == Vec3.ZERO || level.getGameTime() > line.get().until() || !player.isAlive() || !WhipItem.isWhip(player.getMainHandItem())) {
            player.setData(ModAttachments.GRAPPLE, Optional.empty());
            player.resetFallDistance();
            return;
        }
        player.setDeltaMovement(haul);
        player.syncVelocity = true;
        player.resetFallDistance();
    }

    @SubscribeEvent
    static void hauling(PlayerTickEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            tick(level, event.getEntity());
        }
    }
}
