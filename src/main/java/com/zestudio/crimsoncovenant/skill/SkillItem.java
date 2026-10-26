package com.zestudio.crimsoncovenant.skill;

import java.util.function.Consumer;


import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/// A translated blood skill. It isn't held to be used: it's worn in one of the skill slots (use it to put it on, or
/// open the Blood Skills screen) and cast with that slot's key, paying its blood cost. Only vampires can cast.
/// Skills stay with their owner through death (see SkillKeeper), but can be moved around and traded freely.
public class SkillItem extends Item {
    private final BloodSkill skill;

    public SkillItem(BloodSkill skill, Item.Properties properties) {
        super(properties);
        this.skill = skill;
    }

    public BloodSkill skill() {
        return this.skill;
    }

    /// Using a skill puts it on, in the first free skill slot
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (SkillSlots.isWearing(player, this.skill)) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.crimsoncovenant.skill.already_worn").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            if (!SkillSlots.equip(player, stack)) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.crimsoncovenant.skill.slots_full").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.crimsoncovenant.skill.description." + this.skill.getName()).withStyle(ChatFormatting.GRAY));
        int baseCost = this.skill.cost() + SkillUpgrade.extraCost(stack);
        builder.accept(Component.translatable("item.crimsoncovenant.skill.cost", baseCost).withStyle(ChatFormatting.DARK_RED));
        SkillUpgrade upgrade = SkillUpgrade.forSkill(this.skill);
        if (upgrade != null) {
            Component name = Component.translatable("skill_upgrade.crimsoncovenant." + upgrade.getName());
            if (SkillUpgrade.isUpgraded(stack)) {
                builder.accept(Component.translatable("item.crimsoncovenant.skill.upgraded", name).withStyle(ChatFormatting.RED));
                builder.accept(Component.translatable("skill_upgrade.crimsoncovenant." + upgrade.getName() + ".description").withStyle(ChatFormatting.GRAY));
            } else {
                builder.accept(Component.translatable("item.crimsoncovenant.skill.upgradable", name, upgrade.extraCost()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        builder.accept(Component.translatable("item.crimsoncovenant.skill.vampires_only").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
