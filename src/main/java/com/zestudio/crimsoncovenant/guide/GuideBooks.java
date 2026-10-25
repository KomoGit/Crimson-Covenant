package com.zestudio.crimsoncovenant.guide;

import java.util.function.Supplier;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/// The guide books: every player starts with the Night's Ledger, an outsider's guide to the night (how to join each
/// side, the events, the creatures). Joining a side turns it into that side's own book: a vampire's becomes the Codex
/// Sanguinis. Cured, it's a Ledger again. A guide is never lost to death (see skill.SkillKeeper).
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class GuideBooks {
    public enum Book {
        LEDGER("nights_ledger", () -> ModItems.NIGHTS_LEDGER.get()),
        CODEX("codex_sanguinis", () -> ModItems.CODEX_SANGUINIS.get());

        private final String name;
        private final Supplier<Item> item;

        Book(String name, Supplier<Item> item) {
            this.name = name;
            this.item = item;
        }

        public String getName() {
            return this.name;
        }

        public Item item() {
            return this.item.get();
        }

        public Component title() {
            return Component.translatable("item.crimsoncovenant." + this.name);
        }
    }

    private GuideBooks() {}

    /// The book this player should carry: their side's, or the Ledger
    public static Book bookFor(Player player) {
        return Vampirism.isVampire(player) ? Book.CODEX : Book.LEDGER;
    }

    public static boolean isGuide(ItemStack stack) {
        return stack.getItem() instanceof GuideBookItem;
    }

    // A player's first time in the world: their guide. Only for real players (not the stand-ins mods and game tests make,
    // which are kinds of ServerPlayer of their own)
    @SubscribeEvent
    static void giveOnFirstJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getClass() == ServerPlayer.class && !player.getData(ModAttachments.GUIDE_GIVEN)) {
            player.setData(ModAttachments.GUIDE_GIVEN, true);
            give(player, bookFor(player));
        }
    }

    /// The player's guide turns into the book of the side they're on now (or, if they've none on them, they're given it)
    public static void chain(ServerPlayer player) {
        Book book = bookFor(player);
        Inventory inventory = player.getInventory();
        boolean found = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isGuide(stack)) {
                found = true;
                if (!stack.is(book.item())) {
                    inventory.setItem(slot, new ItemStack(book.item()));
                }
            }
        }
        if (!found) {
            give(player, book);
        }
        player.sendSystemMessage(Component.translatable("message.crimsoncovenant.guide.becomes", book.title()).withStyle(ChatFormatting.GOLD));
    }

    public static void give(ServerPlayer player, Book book) {
        player.getInventory().placeItemBackInInventory(new ItemStack(book.item()), Prediction.SERVER_ONLY);
    }
}
