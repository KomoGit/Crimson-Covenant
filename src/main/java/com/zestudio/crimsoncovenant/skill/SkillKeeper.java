package com.zestudio.crimsoncovenant.skill;

import java.util.ArrayList;
import java.util.List;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.guide.GuideBooks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/// Skill items and guide books never fall out on death. When a player dies they're set aside (before the inventory is
/// dropped), and handed back when the player respawns.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class SkillKeeper {
    private SkillKeeper() {}

    // Lowest priority, so it only happens once no other mod has cancelled the death
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            setAside(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            giveBack(player);
        }
    }

    public static void setAside(Player player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> kept = new ArrayList<>(player.getData(ModAttachments.KEPT_SKILLS));
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof SkillItem || GuideBooks.isGuide(stack)) {
                kept.add(stack.copy());
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (!kept.isEmpty()) {
            player.setData(ModAttachments.KEPT_SKILLS, List.copyOf(kept));
        }
    }

    public static void giveBack(Player player) {
        List<ItemStack> kept = player.getData(ModAttachments.KEPT_SKILLS);
        if (kept.isEmpty()) {
            return;
        }
        player.setData(ModAttachments.KEPT_SKILLS, List.of());
        for (ItemStack stack : kept) {
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false, Prediction.SERVER_ONLY);
            }
        }
    }
}
