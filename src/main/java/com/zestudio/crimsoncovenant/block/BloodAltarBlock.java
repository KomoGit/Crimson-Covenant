package com.zestudio.crimsoncovenant.block;

import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.skill.SkillItem;
import com.zestudio.crimsoncovenant.skill.SkillUpgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/// The Blood Altar: the Ancient Butler's workstation, and where blood skills are upgraded. Use it with a skill in
/// hand, and Tokens of Blood and a Token of Master (from a Master Vampire or Master Hunter) in the inventory: the
/// tokens are poured into the altar's bowl and the skill comes back upgraded (see SkillUpgrade). Upgraded skills do more
/// and cost more blood. Not every skill has an upgrade yet, and each can only be upgraded once.
public class BloodAltarBlock extends DecorBlock {
    public static final int PRICE = 5;
    public static final int MASTER_PRICE = 1;

    public BloodAltarBlock(BlockBehaviour.Properties properties, VoxelShape northShape) {
        super(properties, northShape);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof SkillItem skillItem)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        SkillUpgrade upgrade = SkillUpgrade.forSkill(skillItem.skill());
        if (upgrade == null) {
            return refuse(player, Component.translatable("message.crimsoncovenant.upgrade.none"));
        }
        if (SkillUpgrade.isUpgraded(stack)) {
            return refuse(player, Component.translatable("message.crimsoncovenant.upgrade.already"));
        }
        boolean free = player.getAbilities().instabuild;
        if (!free && (count(player.getInventory(), ModItems.TOKEN_OF_BLOOD.get()) < PRICE
                || count(player.getInventory(), ModItems.TOKEN_OF_MASTER.get()) < MASTER_PRICE)) {
            return refuse(player, Component.translatable("message.crimsoncovenant.upgrade.price", PRICE, MASTER_PRICE));
        }
        if (level instanceof ServerLevel serverLevel) {
            if (!free) {
                take(player.getInventory(), ModItems.TOKEN_OF_BLOOD.get(), PRICE);
                take(player.getInventory(), ModItems.TOKEN_OF_MASTER.get(), MASTER_PRICE);
            }
            stack.set(ModDataComponents.SKILL_UPGRADED.get(), true);
            Component skillName = stack.getHoverName();
            Component upgradeName = Component.translatable("skill_upgrade.crimsoncovenant." + upgrade.getName());
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.upgrade.done", skillName, upgradeName).withStyle(ChatFormatting.RED));
            serverLevel.sendParticles(new DustParticleOptions(0x9B0A14, 1.6F), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 40, 0.35, 0.3, 0.35, 0.0);
            serverLevel.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.6F);
            serverLevel.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.7F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.upgrade.hint", PRICE, MASTER_PRICE).withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult refuse(Player player, Component message) {
        player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.GRAY));
        return InteractionResult.FAIL;
    }

    private static int count(Inventory inventory, Item token) {
        int tokens = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(token)) {
                tokens += stack.getCount();
            }
        }
        return tokens;
    }

    private static void take(Inventory inventory, Item token, int count) {
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(token)) {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
            }
        }
    }

    // Blood drips from the bowl's lip
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(new DustParticleOptions(0x9B0A14, 0.8F), pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 0.62,
                    pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0.0, -0.05, 0.0);
        }
    }
}
