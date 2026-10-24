package com.zestudio.crimsoncovenant.block;

import com.zestudio.crimsoncovenant.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/// A bottle with a ghost in it shimmers: blue specks of light drift up through the glass, the odd sparkle twinkles
/// above the cork, and now and then it chimes, or the ghost inside whispers. All of it client side, just for show.
public class SilveredBottleBlockEntity extends BlockEntity {
    private static final int PALE_BLUE = 0x9AD6FF;
    private static final int DEEP_BLUE = 0x4F9BFF;

    public SilveredBottleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SILVERED_BOTTLE.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SilveredBottleBlockEntity bottle) {
        if (!state.getValue(SilveredBottleBlock.GHOST)) {
            return;
        }
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        // Specks rising inside the glass
        if (random.nextInt(3) == 0) {
            int colour = random.nextBoolean() ? PALE_BLUE : DEEP_BLUE;
            level.addParticle(new DustParticleOptions(colour, 0.3F + random.nextFloat() * 0.2F), x + (random.nextDouble() - 0.5) * 0.3,
                    y + 0.1 + random.nextDouble() * 0.35, z + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.02, 0.0);
        }
        // A twinkle escaping round the cork
        if (random.nextInt(14) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.25, y + 0.68, z + (random.nextDouble() - 0.5) * 0.25,
                    (random.nextDouble() - 0.5) * 0.01, 0.015, (random.nextDouble() - 0.5) * 0.01);
        }
        if (random.nextInt(240) == 0) {
            level.playLocalSound(x, y + 0.3, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.35F, 1.4F + random.nextFloat() * 0.4F, false);
        }
        if (random.nextInt(700) == 0) {
            level.playLocalSound(x, y + 0.3, z, SoundEvents.VEX_AMBIENT, SoundSource.BLOCKS, 0.2F, 1.7F, false);
        }
    }
}
