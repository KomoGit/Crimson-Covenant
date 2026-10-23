package com.zestudio.crimsoncovenant.item;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/// A sword of solidified blood from the Ephemeral Sword skill: as good as an iron sword, and it never wears out,
/// but it can't be thrown away and it fades once its time is up (or when its owner dies).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public class EphemeralSwordItem extends Item {
    /// One minute
    public static final int LIFETIME = 1200;

    public EphemeralSwordItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemStack create(long gameTime) {
        ItemStack sword = new ItemStack(ModItems.EPHEMERAL_SWORD.get());
        sword.set(ModDataComponents.EXPIRES_AT.get(), gameTime + LIFETIME);
        return sword;
    }

    public static boolean hasFaded(ItemStack stack, long gameTime) {
        Long expiresAt = stack.get(ModDataComponents.EXPIRES_AT.get());
        return expiresAt != null && gameTime >= expiresAt;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        // One taken from the creative menu starts its minute now
        if (!stack.has(ModDataComponents.EXPIRES_AT.get())) {
            stack.set(ModDataComponents.EXPIRES_AT.get(), level.getGameTime() + LIFETIME);
        }
        if (hasFaded(stack, level.getGameTime())) {
            stack.setCount(0);
            level.sendParticles(new DustParticleOptions(0x9B0A14, 1.2F), owner.getX(), owner.getY(0.6), owner.getZ(), 15, 0.3, 0.4, 0.3, 0.0);
            level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.crimsoncovenant.ephemeral_sword.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /// It can't be thrown away: a sword tossed out of the inventory goes straight back in
    @SubscribeEvent
    static void cantBeThrown(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (stack.getItem() instanceof EphemeralSwordItem) {
            // Cancelling a toss deletes the item, so it's put back by hand
            event.setCanceled(true);
            event.getPlayer().getInventory().add(stack.copy());
        }
    }

    /// It fades with its owner rather than being dropped
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void fadesOnDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            removeAll(player);
        }
    }

    public static void removeAll(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).getItem() instanceof EphemeralSwordItem) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
