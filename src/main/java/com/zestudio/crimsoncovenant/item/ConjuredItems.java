package com.zestudio.crimsoncovenant.item;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/// Items a blood skill conjures (the Ephemeral Sword, the Blood Spear): they can't be thrown away, they fade when
/// their time runs out, and they fade with their owner rather than being dropped on death. Such items implement
/// [Conjured].
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class ConjuredItems {
    private ConjuredItems() {}

    /// Marks an item as conjured
    public interface Conjured {}

    public static ItemStack create(Item item, long gameTime, int lifetime) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.EXPIRES_AT.get(), gameTime + lifetime);
        return stack;
    }

    public static boolean hasFaded(ItemStack stack, long gameTime) {
        Long expiresAt = stack.get(ModDataComponents.EXPIRES_AT.get());
        return expiresAt != null && gameTime >= expiresAt;
    }

    /// Called from the item's inventoryTick: once its time is up, it's gone in a puff of blood
    public static void tickFade(ItemStack stack, ServerLevel level, Entity owner, int lifetime) {
        // One taken from the creative menu starts its time now
        if (!stack.has(ModDataComponents.EXPIRES_AT.get())) {
            stack.set(ModDataComponents.EXPIRES_AT.get(), level.getGameTime() + lifetime);
        }
        if (hasFaded(stack, level.getGameTime())) {
            stack.setCount(0);
            level.sendParticles(new DustParticleOptions(0x9B0A14, 1.2F), owner.getX(), owner.getY(0.6), owner.getZ(), 15, 0.3, 0.4, 0.3, 0.0);
            level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    /// Removes every copy of this conjured item from the player's inventory
    public static void removeAll(Player player, Item item) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    /// A conjured item tossed out of the inventory goes straight back in
    @SubscribeEvent
    static void cantBeThrown(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (stack.getItem() instanceof Conjured) {
            // Cancelling a toss deletes the item, so it's put back by hand
            event.setCanceled(true);
            event.getPlayer().getInventory().add(stack.copy());
        }
    }

    /// Conjured items fade with their owner rather than being dropped
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void fadeOnDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).getItem() instanceof Conjured) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
    }
}
