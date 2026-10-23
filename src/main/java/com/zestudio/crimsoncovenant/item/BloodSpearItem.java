package com.zestudio.crimsoncovenant.item;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.entity.BloodSpear;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.skill.SkillUpgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/// The spear the Blood Spear skill puts in the vampire's hand. It's thrown exactly like a trident: hold use to draw
/// it back over the shoulder, let go to throw (see entity.BloodSpear for what it does when it lands). It's conjured
/// (see ConjuredItems): it can't be dropped, and fades if it isn't thrown within 10 seconds.
public class BloodSpearItem extends Item implements ConjuredItems.Conjured {
    public static final int LIFETIME = 200;
    /// Like a trident, it has to be drawn back for half a second before it can be thrown
    public static final int MIN_DRAW = 10;
    private static final float THROW_SPEED = 2.8F;

    public BloodSpearItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemStack create(long gameTime) {
        return ConjuredItems.create(ModItems.BLOOD_SPEAR.get(), gameTime, LIFETIME);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        int drawn = this.getUseDuration(stack, entity) - remainingTime;
        if (drawn < MIN_DRAW) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel) {
            throwSpear(serverLevel, entity, SkillUpgrade.isUpgraded(stack));
        }
        stack.shrink(1);
        return true;
    }

    public static void throwSpear(ServerLevel level, LivingEntity thrower, boolean crimsonLightning) {
        BloodSpear spear = new BloodSpear(level, thrower);
        spear.setCrimsonLightning(crimsonLightning);
        spear.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), 0.0F, THROW_SPEED, 0.5F);
        level.addFreshEntity(spear);
        level.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        ConjuredItems.tickFade(stack, level, owner, LIFETIME);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.crimsoncovenant.blood_spear.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
