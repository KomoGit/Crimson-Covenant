package com.zestudio.crimsoncovenant.item;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.BearTrapBlock;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModEffects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/// A silvered weapon (swords, axes and spears, silvered at the smithing table with a silver ingot, see
/// SilveringRecipe) hits anything weak to silver harder, and leaves it with a Silver Wound: no healing, and for a
/// vampire player no blood coming back, for a few seconds. It works the same in a hunter's hands.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class Silvering {
    public static final float BONUS_DAMAGE = 4.0F;
    public static final int WOUND_DURATION = 100;

    private Silvering() {}

    public static boolean isSilvered(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SILVERED.get(), false);
    }

    @SubscribeEvent
    static void silverBites(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        // Only blows struck with the weapon in hand, not arrows or tridents thrown by someone holding one
        ItemStack weapon = event.getSource().getWeaponItem();
        if (weapon == null || !isSilvered(weapon) || event.getSource().getDirectEntity() != event.getSource().getEntity()
                || !BearTrapBlock.isWeakToSilver(victim)) {
            return;
        }
        event.setAmount(event.getAmount() + BONUS_DAMAGE);
        victim.addEffect(new MobEffectInstance(ModEffects.SILVER_WOUND, WOUND_DURATION), event.getSource().getEntity());
    }

    @SubscribeEvent
    static void describe(ItemTooltipEvent event) {
        if (isSilvered(event.getItemStack())) {
            // Right under the name, like a trim
            int line = Math.min(1, event.getToolTip().size());
            event.getToolTip().add(line, Component.translatable("item.crimsoncovenant.silvered").withStyle(ChatFormatting.GRAY));
        }
    }
}
