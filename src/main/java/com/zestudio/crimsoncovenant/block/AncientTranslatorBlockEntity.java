package com.zestudio.crimsoncovenant.block;

import java.util.Comparator;
import java.util.List;

import com.zestudio.crimsoncovenant.registry.ModBlockEntities;
import com.zestudio.crimsoncovenant.skill.BloodSkill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// How far an Ancient Translator has got with its tome. The reading runs on life: it only goes on while there are
/// captives in Iron Maidens near the translator to feed it, each maiden (up to MAX_MAIDENS of them) quickening it, and
/// each giving up a little blood as it does. How long a tome takes depends on its tier (see BloodSkill.Tier).
public class AncientTranslatorBlockEntity extends BlockEntity {
    /// The most Iron Maidens that can feed one translator at once
    public static final int MAX_MAIDENS = 4;
    /// Every this many ticks of reading, each maiden feeding it gives up a drop of blood
    public static final int DRAIN_INTERVAL = 100;
    private static final int CHECK_INTERVAL = 20;

    private int progress;
    private int feeders;
    private int recheck;
    private int ticksReading;
    /// Who brought the tome being read
    private @Nullable UUID reader;

    public AncientTranslatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANCIENT_TRANSLATOR.get(), pos, state);
    }

    /// The Iron Maidens with captives feeding the translator at `pos`: the nearest, no more than MAX_MAIDENS
    public static List<IronMaidenBlockEntity> feeders(Level level, BlockPos pos) {
        return IronMaidenBlock.captivesAround(level, pos).stream()
                .sorted(Comparator.comparingDouble(maiden -> maiden.getBlockPos().distSqr(pos)))
                .limit(MAX_MAIDENS)
                .toList();
    }

    /// How far through the tome it is, from 0 to 1
    public float fractionDone() {
        BloodSkill skill = AncientTranslatorBlock.translating(this.getBlockState());
        return skill == null ? 0.0F : Math.min(1.0F, (float) this.progress / skill.tier().translationTicks());
    }

    /// How many Iron Maidens fed it, last it looked
    public int feederCount() {
        return this.feeders;
    }

    public int progress() {
        return this.progress;
    }

    public void setReader(Player player) {
        this.reader = player.getUUID();
        this.setChanged();
    }

    public @Nullable UUID reader() {
        return this.reader;
    }

    void startOver() {
        this.progress = 0;
        this.ticksReading = 0;
        this.recheck = 0;
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AncientTranslatorBlockEntity translator) {
        BloodSkill skill = AncientTranslatorBlock.translating(state);
        if (skill == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        List<IronMaidenBlockEntity> feeding = null;
        if (--translator.recheck <= 0) {
            translator.recheck = CHECK_INTERVAL;
            feeding = feeders(level, pos);
            translator.feeders = feeding.size();
        }
        if (translator.feeders == 0) {
            return;
        }
        translator.progress += translator.feeders;
        if (++translator.ticksReading % DRAIN_INTERVAL == 0) {
            for (IronMaidenBlockEntity maiden : feeding != null ? feeding : feeders(level, pos)) {
                maiden.drain(1);
            }
        }
        translator.setChanged();
        if (translator.progress >= skill.tier().translationTicks()) {
            AncientTranslatorBlock.finish(serverLevel, pos, state);
            translator.startOver();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("progress", 0);
        this.ticksReading = input.getIntOr("ticks_reading", 0);
        this.reader = input.read("reader", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progress", this.progress);
        output.putInt("ticks_reading", this.ticksReading);
        output.storeNullable("reader", UUIDUtil.CODEC, this.reader);
    }
}
