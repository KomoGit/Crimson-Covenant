package com.zestudio.crimsoncovenant.block;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zestudio.crimsoncovenant.entity.Rat;
import com.zestudio.crimsoncovenant.registry.ModBlockEntities;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/// The villager (or Plague Doctor, or witch) shut up in an Iron Maiden, taken out of the world the way a beehive takes its bees, along with how much
/// blood is left in them. Every draw a coffin or a Blood Throne makes on them takes some; once it's all gone there's
/// nothing left of them, and the maiden stands empty, ready for another. While there's someone inside, a thread of
/// blood runs from the maiden to any coffin or throne nearby.
public class IronMaidenBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    /// How much blood there is in a captive, all told: ten nights' feeding in a coffin
    public static final int BLOOD_IN_A_CAPTIVE = 250;
    /// The chance a captive bled dry rises again, as a zombie villager
    public static final float RISE_CHANCE = 0.2F;
    /// How far a coffin (or a Blood Throne) can be for the blood to reach it
    public static final int COFFIN_RANGE = 6;
    private static final int BLOOD_RED = 0x8A0B0B;

    private @Nullable Captive captive;
    // Client side only: the coffin (or throne) the blood runs to, looked for now and then
    private @Nullable BlockPos coffin;
    private int coffinCheck;

    /// Someone taken captive (a villager, a Plague Doctor, a witch: see ModTags.Entities.IRON_MAIDEN_CAPTIVES), and the
    /// blood left in them
    public record Captive(TypedEntityData<EntityType<?>> creature, int blood) {
        // Saved as "villager", which is all they once could be
        public static final Codec<Captive> CODEC = RecordCodecBuilder.create(i -> i.group(
                TypedEntityData.codec(EntityType.CODEC).fieldOf("villager").forGetter(Captive::creature),
                Codec.INT.optionalFieldOf("blood", BLOOD_IN_A_CAPTIVE).forGetter(Captive::blood))
                .apply(i, Captive::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Captive> STREAM_CODEC = StreamCodec.composite(
                TypedEntityData.streamCodec(EntityType.STREAM_CODEC), Captive::creature,
                ByteBufCodecs.VAR_INT, Captive::blood,
                Captive::new);

        public static Captive of(Mob creature) {
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(creature.problemPath(), LOGGER)) {
                TagValueOutput output = TagValueOutput.createWithContext(reporter, creature.registryAccess());
                creature.save(output);
                return new Captive(TypedEntityData.of(creature.getType(), output.buildResult()), BLOOD_IN_A_CAPTIVE);
            }
        }

        public Captive withBlood(int blood) {
            return new Captive(this.creature, blood);
        }

        /// What they are, for telling the player ("Villager", "Witch")
        public Component name() {
            return this.creature.type().getDescription();
        }

        @Nullable Mob release(Level level, Vec3 at) {
            CompoundTag tag = this.creature.copyTagWithoutId();
            tag.remove("Pos");
            Entity entity = EntityType.loadEntityRecursive(this.creature.type(), tag, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
            if (!(entity instanceof Mob freed)) {
                return null;
            }
            freed.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
            return freed;
        }
    }

    public IronMaidenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRON_MAIDEN.get(), pos, state);
    }

    public @Nullable Captive getCaptive() {
        return this.captive;
    }

    public boolean hasCaptive() {
        return this.captive != null;
    }

    public void setCaptive(@Nullable Captive captive) {
        this.captive = captive;
        this.setChanged();
        if (this.level != null) {
            IronMaidenBlock.setOccupied(this.level, this.worldPosition, captive != null);
        }
    }

    /// The captive is let out, in front of the maiden
    public @Nullable Mob release() {
        Mob freed = this.letOut();
        this.setCaptive(null);
        return freed;
    }

    // Out in front of the maiden, leaving the block itself be
    private @Nullable Mob letOut() {
        if (this.captive == null || !(this.level instanceof ServerLevel level)) {
            return null;
        }
        Direction front = this.getBlockState().getValue(IronMaidenBlock.FACING);
        Vec3 at = Vec3.atBottomCenterOf(this.worldPosition.relative(front));
        Mob freed = this.captive.release(level, at);
        if (freed != null) {
            level.addFreshEntity(freed);
            level.playSound(null, this.worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        return freed;
    }

    /// Broken open, the maiden sets her captive free
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        this.letOut();
        this.captive = null;
    }

    /// Draws up to `amount` blood from the captive, and returns how much there was to take. Once they're bled dry,
    /// there's nothing left of them.
    public int drain(int amount) {
        if (this.captive == null || amount <= 0) {
            return 0;
        }
        int taken = Math.min(amount, this.captive.blood());
        if (this.captive.blood() - taken <= 0) {
            this.perish();
        } else {
            this.captive = this.captive.withBlood(this.captive.blood() - taken);
            this.setChanged();
        }
        return taken;
    }

    /// Bled dry at last: nothing left of the captive. Now and then (see RISE_CHANCE) what's left gets up again.
    public void perish() {
        this.perish(this.level != null && this.level.getRandom().nextFloat() < RISE_CHANCE);
    }

    /// Bled dry. If `risesAgain` and they were a villager, they step out of the maiden a zombie villager, still with
    /// their trades and their standing, the way one killed by a zombie does. Returns the zombie villager, if they rose.
    public @Nullable ZombieVillager perish(boolean risesAgain) {
        if (this.captive == null || !(this.level instanceof ServerLevel level)) {
            return null;
        }
        ZombieVillager risen = null;
        if (risesAgain) {
            Direction front = this.getBlockState().getValue(IronMaidenBlock.FACING);
            if (this.captive.release(level, Vec3.atBottomCenterOf(this.worldPosition.relative(front))) instanceof Villager body) {
                risen = Rat.turnToZombie(level, body);
            }
        }
        this.setCaptive(null);
        level.playSound(null, this.worldPosition, SoundEvents.VILLAGER_DEATH, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.SOUL, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.4, this.worldPosition.getZ() + 0.5, 6, 0.2, 0.4, 0.2, 0.02);
        return risen;
    }

    // A thread of blood, running from the maiden to the coffin; and the captive, heard now and then
    public static void clientTick(Level level, BlockPos pos, BlockState state, IronMaidenBlockEntity maiden) {
        if (!state.getValue(IronMaidenBlock.OCCUPIED)) {
            return;
        }
        captiveNoises(level, pos);
        if (--maiden.coffinCheck <= 0) {
            maiden.coffinCheck = 40;
            maiden.coffin = findDrinker(level, pos);
        }
        if (maiden.coffin == null || level.getGameTime() % 2 != 0) {
            return;
        }
        Vec3 from = Vec3.atCenterOf(pos).add(0.0, 0.6, 0.0);
        boolean throne = level.getBlockState(maiden.coffin).getBlock() instanceof BloodThroneBlock;
        Vec3 to = Vec3.atCenterOf(maiden.coffin).add(0.0, throne ? 0.3 : -0.1, 0.0);
        // Specks spaced along the line and drifting toward the coffin, so the blood seems to flow
        float flow = (level.getGameTime() % 40L) / 40.0F;
        for (int i = 0; i < 4; i++) {
            double t = (flow + i / 4.0) % 1.0;
            Vec3 at = from.lerp(to, t).add(0.0, Math.sin(t * Math.PI) * 0.6, 0.0);
            level.addParticle(new DustParticleOptions(BLOOD_RED, 0.7F), at.x, at.y, at.z, 0.0, 0.0, 0.0);
        }
    }

    /// Muffled through the iron: a moan, a plea, banging on the doors, the rattle of chains. Client side, just for whoever
    /// is near enough to hear.
    private static void captiveNoises(Level level, BlockPos pos) {
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        float pitch = 0.75F + random.nextFloat() * 0.15F;
        switch (random.nextInt(900)) {
            case 0, 1 -> level.playLocalSound(x, y, z, SoundEvents.VILLAGER_HURT, SoundSource.BLOCKS, 0.35F, pitch, false);
            case 2 -> level.playLocalSound(x, y, z, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.35F, pitch, false);
            case 3 -> level.playLocalSound(x, y, z, SoundEvents.VILLAGER_AMBIENT, SoundSource.BLOCKS, 0.3F, 0.7F, false);
            case 4 -> level.playLocalSound(x, y, z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 0.15F, 1.4F, false);
            case 5 -> level.playLocalSound(x, y, z, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.5F, 0.8F, false);
            default -> {
            }
        }
    }

    /// The nearest coffin, Blood Throne or working Ancient Translator within reach of the maiden at `pos`, for the blood
    /// to run to
    public static @Nullable BlockPos findDrinker(Level level, BlockPos pos) {
        BlockPos nearest = null;
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-COFFIN_RANGE, -2, -COFFIN_RANGE), pos.offset(COFFIN_RANGE, 2, COFFIN_RANGE))) {
            BlockState state = level.getBlockState(at);
            boolean drinker = state.getBlock() instanceof CoffinBlock || state.getBlock() instanceof BloodThroneBlock
                    || (state.getBlock() instanceof AncientTranslatorBlock && AncientTranslatorBlock.isTranslating(state));
            if (drinker && (nearest == null || at.distSqr(pos) < nearest.distSqr(pos))) {
                nearest = at.immutable();
            }
        }
        return nearest;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.captive = input.read("captive", Captive.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("captive", Captive.CODEC, this.captive);
    }

    // The captive goes with the maiden when it's picked up
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.captive = components.get(ModDataComponents.CAPTIVE.get());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.captive != null) {
            components.set(ModDataComponents.CAPTIVE.get(), this.captive);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("captive");
    }
}
