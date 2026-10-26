package com.zestudio.crimsoncovenant.block;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.entity.GhostSteed;
import com.zestudio.crimsoncovenant.entity.HalloweenSpirit;
import com.zestudio.crimsoncovenant.entity.HeadlessHorseman;
import com.zestudio.crimsoncovenant.entity.SpectralHorseman;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModAttachments.Terror;
import com.zestudio.crimsoncovenant.registry.ModBlockEntities;
import com.zestudio.crimsoncovenant.skill.NightOfTerror;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/// What a Head on a Pike does, once a second:
/// - Wards: every monster within WARD_RADIUS (bosses aside) is put to flight from it for a few seconds, so they keep
///   clear of the land round it.
/// - By night, it keeps the Spectral Horseman riding (see entity.SpectralHorseman), calling him up again if he's gone;
///   by day, it lets him rest.
public class HeadOnAPikeBlockEntity extends BlockEntity {
    public static final double WARD_RADIUS = 50.0;
    private static final double WARD_HEIGHT = 24.0;
    /// How long each touch of the ward sends a monster running (renewed while it stays near)
    private static final int WARD_TICKS = 60;
    private static final int INTERVAL = 20;

    private @Nullable UUID horseman;

    public HeadOnAPikeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEAD_ON_A_PIKE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HeadOnAPikeBlockEntity pike) {
        if (!(level instanceof ServerLevel serverLevel) || Math.floorMod(level.getGameTime() + pos.hashCode(), INTERVAL) != 0) {
            return;
        }
        ward(serverLevel, pos);
        pike.keepWatch(serverLevel, serverLevel.isDarkOutside());
    }

    /// Whether the ward turns `mob` away: any monster but bosses and the Horseman's own
    public static boolean isWarded(Mob mob) {
        return mob instanceof Enemy && mob.isAlive() && !mob.is(Tags.EntityTypes.BOSSES) && !(mob instanceof HeadlessHorseman)
                && !(mob instanceof HalloweenSpirit) && !(mob instanceof GhostSteed);
    }

    /// Sends every monster within WARD_RADIUS of the pike at `pos` running from it. Returns how many.
    public static int ward(ServerLevel level, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        long until = level.getGameTime() + WARD_TICKS;
        int warded = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(WARD_RADIUS, WARD_HEIGHT, WARD_RADIUS),
                mob -> isWarded(mob) && Mth.lengthSquared(mob.getX() - centre.x, mob.getZ() - centre.z) <= WARD_RADIUS * WARD_RADIUS)) {
            boolean alreadyRunning = NightOfTerror.isTerrified(mob);
            mob.setData(ModAttachments.TERROR, Optional.of(new Terror(centre, until, false)));
            mob.setTarget(null);
            if (!alreadyRunning) {
                level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY(1.0) + 0.3, mob.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            }
            warded++;
        }
        return warded;
    }

    /// The Spectral Horseman this pike keeps, if he's about
    public @Nullable SpectralHorseman findHorseman(ServerLevel level) {
        if (this.horseman != null && level.getEntity(this.horseman) instanceof SpectralHorseman found && found.isAlive()) {
            return found;
        }
        // Unloaded and loaded again, he may not be where the pike last knew him
        return level.getEntitiesOfClass(SpectralHorseman.class, new AABB(this.worldPosition).inflate(64.0),
                found -> this.worldPosition.equals(found.getPike())).stream().findFirst().orElse(null);
    }

    /// By night, calls the Spectral Horseman up if he isn't riding; by day, sends him away
    public void keepWatch(ServerLevel level, boolean night) {
        SpectralHorseman rider = this.findHorseman(level);
        if (!night) {
            if (rider != null) {
                rider.fade();
            }
            this.horseman = null;
            return;
        }
        if (rider == null) {
            float angle = level.getRandom().nextFloat() * Mth.TWO_PI;
            int x = Mth.floor(this.worldPosition.getX() + 0.5 + Mth.cos(angle) * 4.0);
            int z = Mth.floor(this.worldPosition.getZ() + 0.5 + Mth.sin(angle) * 4.0);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            rider = SpectralHorseman.rise(level, this.worldPosition, new Vec3(x + 0.5, y, z + 0.5));
        }
        UUID keeping = rider != null ? rider.getUUID() : null;
        if (!java.util.Objects.equals(keeping, this.horseman)) {
            this.horseman = keeping;
            this.setChanged();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.horseman = input.read("Horseman", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Horseman", UUIDUtil.CODEC, this.horseman);
    }
}
