package com.zestudio.crimsoncovenant.item;

import java.util.function.Consumer;

import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/// A bottle of blood, sold by the Ancient Butler and carried by vampires (who drop it when they die). A vampire who
/// drinks it refills their blood bar a good way. It's tainted with the curse: a human who drinks it is sick to their
/// stomach and catches Sanguine Vampiris. A werewolf drinks it like anything else: the curse has no hold on them (see
/// WerewolfBelt).
public class BloodBottleItem extends Item {
    public static final int BLOOD = 30;

    public BloodBottleItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof Player player) {
            if (Vampirism.isVampire(player)) {
                BloodPool.add(player, BLOOD);
            } else if (!WerewolfBelt.isTurned(player)) {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200));
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200));
                if (!Vampirism.isInfected(player)) {
                    Vampirism.infect(player, Vampirism.INFECTION_DURATION);
                    player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.blood_bottle.tainted").withStyle(ChatFormatting.DARK_RED));
                }
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.crimsoncovenant.blood_bottle.hint", BLOOD).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.crimsoncovenant.blood_bottle.warning").withStyle(ChatFormatting.DARK_RED));
    }
}
