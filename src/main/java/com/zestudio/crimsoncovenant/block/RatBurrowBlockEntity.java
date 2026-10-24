package com.zestudio.crimsoncovenant.block;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zestudio.crimsoncovenant.entity.Rat;
import com.zestudio.crimsoncovenant.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/// The rats living in a burrow, kept the way a beehive keeps its bees: taken out of the world while they're inside,
/// and put back when they come out. They come out at night (unless it's raining), once they've been in a while.
public class RatBurrowBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    /// Room for a pair and their young
    public static final int CAPACITY = 6;
    /// The least time a rat stays in once it's gone in
    private static final int MIN_STAY = 200;
    private static final List<String> FORGOTTEN_TAGS = List.of("Air", "Brain", "DeathTime", "FallDistance", "FallFlying", "Fire", "HurtByTimestamp",
            "HurtTime", "Motion", "OnGround", "PortalCooldown", "Pos", "Rotation", "UUID", "Passengers", "leash");

    private final List<Occupant> occupants = new ArrayList<>();

    public record Occupant(TypedEntityData<EntityType<?>> entityData, int ticksInside) {
        public static final Codec<Occupant> CODEC = RecordCodecBuilder.create(i -> i.group(
                TypedEntityData.codec(EntityType.CODEC).fieldOf("entity_data").forGetter(Occupant::entityData),
                Codec.INT.fieldOf("ticks_inside").forGetter(Occupant::ticksInside))
                .apply(i, Occupant::new));

        static Occupant of(Entity entity) {
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
                TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
                entity.save(output);
                FORGOTTEN_TAGS.forEach(output::discard);
                return new Occupant(TypedEntityData.of(entity.getType(), output.buildResult()), 0);
            }
        }

        @Nullable Entity createEntity(Level level) {
            CompoundTag tag = this.entityData.copyTagWithoutId();
            FORGOTTEN_TAGS.forEach(tag::remove);
            return EntityType.loadEntityRecursive(this.entityData.type(), tag, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
        }
    }

    public RatBurrowBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RAT_BURROW.get(), pos, state);
    }

    public boolean isFull() {
        return this.occupants.size() >= CAPACITY;
    }

    public int occupantCount() {
        return this.occupants.size();
    }

    /// The rat goes in, if there's room
    public boolean tryEnter(Rat rat) {
        if (this.isFull() || this.level == null) {
            return false;
        }
        rat.setHomePos(this.worldPosition);
        rat.stopRiding();
        rat.ejectPassengers();
        this.occupants.add(Occupant.of(rat));
        rat.discard();
        this.level.playSound(null, this.worldPosition, SoundEvents.ROOTED_DIRT_STEP, SoundSource.NEUTRAL, 0.8F, 1.6F);
        this.setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RatBurrowBlockEntity burrow) {
        if (burrow.occupants.isEmpty()) {
            return;
        }
        // Out only at night, and not in the rain
        boolean night = !Rat.keepsIndoors(level);
        List<Occupant> staying = new ArrayList<>();
        for (Occupant occupant : burrow.occupants) {
            int ticks = occupant.ticksInside() + 1;
            if (night && ticks >= MIN_STAY && burrow.release(level, occupant) != null) {
                continue;
            }
            staying.add(new Occupant(occupant.entityData(), ticks));
        }
        burrow.occupants.clear();
        burrow.occupants.addAll(staying);
        burrow.setChanged();
    }

    // Out through the hole, if there's room on the doorstep
    private @Nullable Rat release(Level level, Occupant occupant) {
        BlockPos door = RatBurrowBlock.doorstep(this.getBlockState(), this.worldPosition);
        if (!level.getBlockState(door).getCollisionShape(level, door).isEmpty()) {
            return null;
        }
        if (!(occupant.createEntity(level) instanceof Rat rat)) {
            return null;
        }
        rat.snapTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        rat.leftBurrow(this.worldPosition);
        level.addFreshEntity(rat);
        level.playSound(null, this.worldPosition, SoundEvents.ROOTED_DIRT_STEP, SoundSource.NEUTRAL, 0.8F, 1.4F);
        return rat;
    }

    /// Everyone out, now. When it's a player who broke the burrow open, every rat that lived here, out or in, goes for
    /// them.
    public void releaseAll(@Nullable LivingEntity culprit) {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        List<Rat> released = new ArrayList<>();
        for (Occupant occupant : this.occupants) {
            Rat rat = this.release(level, occupant);
            if (rat == null && occupant.createEntity(level) instanceof Rat stranded) {
                // The doorstep's blocked: come out where the burrow was
                stranded.snapTo(this.worldPosition.getX() + 0.5, this.worldPosition.getY(), this.worldPosition.getZ() + 0.5, 0.0F, 0.0F);
                stranded.leftBurrow(this.worldPosition);
                level.addFreshEntity(stranded);
                rat = stranded;
            }
            if (rat != null) {
                released.add(rat);
            }
        }
        this.occupants.clear();
        this.setChanged();
        if (culprit == null) {
            return;
        }
        released.addAll(level.getEntitiesOfClass(Rat.class, new AABB(this.worldPosition).inflate(Rat.VILLAGE_RANGE),
                rat -> this.worldPosition.equals(rat.getHomePos())));
        for (Rat rat : released) {
            rat.becomeAngryAt(culprit);
            rat.setHomePos(null);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // Burrows broken some other way (an explosion, say) let their rats go without a grudge
        this.releaseAll(null);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.occupants.clear();
        input.read("rats", Occupant.CODEC.listOf()).ifPresent(this.occupants::addAll);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("rats", Occupant.CODEC.listOf(), List.copyOf(this.occupants));
    }
}
