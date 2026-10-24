package com.zestudio.crimsoncovenant.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.effect.MiasmaEffect;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// The Miasma: a sickly green fog that settles over a village overrun by rats (more than ten, see Rat). It lingers
/// as long as the rats keep coming and drifts off a minute after. Anyone breathing it is poisoned and sickened (see
/// MiasmaEffect), and anyone who stays in it for half a minute or more goes blind too. Vampires and rats don't mind.
///
/// A thrown Potion of Cleanse (splash or lingering) clears it away, and keeps it off that spot for five minutes however many rats there
/// are; the Plague Doctor who comes when it settles does just that (see PlagueDoctor).
public class MiasmaCloud extends Entity {
    public static final double RADIUS = 7.0;
    private static final double HEIGHT = 4.0;
    /// How long the cloud hangs about once the rats stop feeding it
    public static final int LIFETIME = 1200;
    /// Each burrow breeds its own cloud; gathering this close to one just keeps it going
    private static final double GATHER_RANGE = 8.0;
    private static final int BREATH_INTERVAL = 20;
    private static final int EFFECT_TICKS = 80;
    /// Seconds in the cloud before it blinds
    public static final int BLINDING_SECONDS = 30;
    private static final int GREEN = 0x6E9A35;
    private static final int DARK_GREEN = 0x3F5E1E;

    /// How long a Cleanse keeps the Miasma away from where it burst, and how far around
    public static final int WARD_TICKS = 6000;
    private static final double WARD_RANGE = 12.0;
    /// How long after it settles a Plague Doctor turns up to deal with it
    public static final int DOCTOR_DELAY = 200;
    private static final Map<ServerLevel, List<Ward>> WARDS = new WeakHashMap<>();

    private record Ward(Vec3 centre, long until) {}

    private int life = LIFETIME;

    public MiasmaCloud(EntityType<? extends MiasmaCloud> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /// Thickens the Miasma over a rat-infested village: freshens the cloud already there, or brings a new one
    public static @Nullable MiasmaCloud gatherOver(ServerLevel level, BlockPos pos) {
        if (isWarded(level, Vec3.atCenterOf(pos))) {
            return null;
        }
        List<MiasmaCloud> clouds = level.getEntitiesOfClass(MiasmaCloud.class, new AABB(pos).inflate(GATHER_RANGE));
        if (!clouds.isEmpty()) {
            MiasmaCloud cloud = clouds.getFirst();
            cloud.life = LIFETIME;
            return cloud;
        }
        MiasmaCloud cloud = new MiasmaCloud(ModEntities.MIASMA_CLOUD.get(), level);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
        cloud.setPos(pos.getX() + 0.5, Math.min(pos.getY(), ground.getY()), pos.getZ() + 0.5);
        level.addFreshEntity(cloud);
        return cloud;
    }

    /// A Cleanse burst at `at`: the Miasma there blows away, and stays away for a while
    public static void cleanseAround(ServerLevel level, Vec3 at) {
        for (MiasmaCloud cloud : level.getEntitiesOfClass(MiasmaCloud.class, new AABB(at, at).inflate(RADIUS + 4.0))) {
            level.sendParticles(ParticleTypes.CLOUD, cloud.getX(), cloud.getY() + 1.0, cloud.getZ(), 40, RADIUS * 0.5, 1.0, RADIUS * 0.5, 0.02);
            level.playSound(null, cloud.getX(), cloud.getY(), cloud.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 1.0F, 1.4F);
            cloud.discard();
        }
        List<Ward> wards = WARDS.computeIfAbsent(level, key -> new ArrayList<>());
        long now = level.getGameTime();
        wards.removeIf(ward -> ward.until() < now);
        wards.add(new Ward(at, now + WARD_TICKS));
    }

    /// Lifts every Cleanse's protection in the level (for tests, which share one world)
    public static void forgetWards(ServerLevel level) {
        WARDS.remove(level);
    }

    public static boolean isWarded(ServerLevel level, Vec3 at) {
        long now = level.getGameTime();
        return WARDS.getOrDefault(level, List.of()).stream().anyMatch(ward -> ward.until() >= now && ward.centre().distanceToSqr(at) <= WARD_RANGE * WARD_RANGE);
    }

    public AABB area() {
        return new AABB(this.getX() - RADIUS, this.getY() - 1.0, this.getZ() - RADIUS, this.getX() + RADIUS, this.getY() + HEIGHT, this.getZ() + RADIUS);
    }

    public boolean contains(Entity entity) {
        double dx = entity.getX() - this.getX();
        double dz = entity.getZ() - this.getZ();
        return dx * dx + dz * dz <= RADIUS * RADIUS && this.area().intersects(entity.getBoundingBox());
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
        this.billow(level);
        if (this.tickCount % BREATH_INTERVAL == 0) {
            this.sicken(level);
        }
        if (this.tickCount == DOCTOR_DELAY) {
            PlagueDoctor.answerTheCall(level, this.blockPosition());
        }
    }

    // Green fog rolling about the place, thinning out as it fades
    private void billow(ServerLevel level) {
        int puffs = this.life < 200 ? 2 : 6;
        for (int i = 0; i < puffs; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(this.random.nextDouble()) * RADIUS;
            double x = this.getX() + Math.cos(angle) * distance;
            double z = this.getZ() + Math.sin(angle) * distance;
            double y = this.getY() + this.random.nextDouble() * HEIGHT * 0.6;
            int colour = this.random.nextBoolean() ? GREEN : DARK_GREEN;
            level.sendParticles(new DustParticleOptions(colour, 2.5F + this.random.nextFloat()), x, y, z, 1, 0.3, 0.1, 0.3, 0.0);
        }
    }

    private void sicken(ServerLevel level) {
        long now = level.getGameTime();
        for (LivingEntity breather : level.getEntitiesOfClass(LivingEntity.class, this.area(), entity -> entity.isAlive() && this.contains(entity))) {
            if (MiasmaEffect.isImmune(breather)) {
                continue;
            }
            breather.addEffect(new MobEffectInstance(ModEffects.MIASMA, EFFECT_TICKS), this);
            int seconds = breathe(breather, now);
            if (seconds >= BLINDING_SECONDS) {
                breather.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EFFECT_TICKS), this);
            }
        }
    }

    /// Counts another second in the Miasma, starting the count over if the creature has been out of it a while.
    /// Returns how many seconds it's been in.
    public static int breathe(LivingEntity breather, long now) {
        ModAttachments.MiasmaExposure exposure = breather.getData(ModAttachments.MIASMA_EXPOSURE);
        int seconds = now - exposure.lastBreath() > BREATH_INTERVAL * 5L ? 1 : exposure.seconds() + 1;
        breather.setData(ModAttachments.MIASMA_EXPOSURE, new ModAttachments.MiasmaExposure(now, seconds));
        return seconds;
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
