package com.zestudio.crimsoncovenant.hunter;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/// A player can join the hunters and rise through their ranks, each rank unlocking more hunter gear (see HunterGear).
///
/// To join, offer a hunter party's leader (the one with the banner) a Silver Coin: the player becomes an Initiate.
/// To rise, offer any hunter Tokens of Blood, with silver ingots in the inventory: proof of vampires slain, and silver
/// for the next rank's gear. Each rank costs more than the last. Vampires, and anyone carrying the curse, are turned
/// away, and a hunter who turns into a vampire is cast out (see Vampirism).
public final class HunterRanks {
    public static final int NONE = 0;
    public static final int INITIATE = 1;
    public static final int TRACKER = 2;
    public static final int SLAYER = 3;
    public static final int WARDEN = 4;
    public static final int GRAND_HUNTER = 5;
    public static final int MAX_RANK = GRAND_HUNTER;
    private static final String[] NAMES = {"none", "initiate", "tracker", "slayer", "warden", "grand_hunter"};

    public static final int JOIN_PRICE = 1;
    private static final int TOKENS_PER_RANK = 5;
    private static final int SILVER_PER_RANK = 2;

    private HunterRanks() {}

    public static int get(Player player) {
        return player.getData(ModAttachments.HUNTER_RANK);
    }

    public static void set(Player player, int rank) {
        player.setData(ModAttachments.HUNTER_RANK, Math.clamp(rank, NONE, MAX_RANK));
    }

    public static boolean isHunter(Player player) {
        return get(player) >= INITIATE;
    }

    public static Component name(int rank) {
        return Component.translatable("hunter_rank." + CrimsonCovenant.MODID + "." + NAMES[Math.clamp(rank, NONE, MAX_RANK)]);
    }

    /// Tokens of Blood asked to reach this rank: 5 for Tracker, 10 for Slayer, 15 for Warden, 20 for Grand Hunter
    public static int tokensFor(int rank) {
        return TOKENS_PER_RANK * (rank - 1);
    }

    /// Silver ingots asked to reach this rank: 2 for Tracker up to 8 for Grand Hunter
    public static int silverFor(int rank) {
        return SILVER_PER_RANK * (rank - 1);
    }

    /// Called when a player uses an item on a hunter. Returns null when it isn't an offering, so the hunter carries on
    /// as usual.
    public static @Nullable InteractionResult offer(Hunter hunter, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean coin = held.is(ModItems.SILVER_COIN.get());
        boolean tokens = held.is(ModItems.TOKEN_OF_BLOOD.get());
        if (!coin && !tokens) {
            return null;
        }
        if (!(hunter.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (Vampirism.isTainted(player)) {
            return refuse(player, "message.crimsoncovenant.hunter.cursed");
        }
        int rank = get(player);
        if (coin && rank == NONE) {
            if (!hunter.isLeader()) {
                return refuse(player, "message.crimsoncovenant.hunter.ask_leader");
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(JOIN_PRICE);
            }
            promote(level, hunter, player, INITIATE, "message.crimsoncovenant.hunter.joined");
            return InteractionResult.SUCCESS;
        }
        if (rank == NONE) {
            return refuse(player, "message.crimsoncovenant.hunter.not_a_hunter");
        }
        if (coin) {
            // Already one of us: tell them what the next rank asks for
            return rank >= MAX_RANK ? refuse(player, "message.crimsoncovenant.hunter.highest")
                    : refuse(player, "message.crimsoncovenant.hunter.next", name(rank + 1), tokensFor(rank + 1), silverFor(rank + 1));
        }
        if (rank >= MAX_RANK) {
            return refuse(player, "message.crimsoncovenant.hunter.highest");
        }
        int next = rank + 1;
        Inventory inventory = player.getInventory();
        boolean free = player.getAbilities().instabuild;
        if (!free && (count(inventory, ModItems.TOKEN_OF_BLOOD.get()) < tokensFor(next) || count(inventory, ModItems.SILVER_INGOT.get()) < silverFor(next))) {
            return refuse(player, "message.crimsoncovenant.hunter.next", name(next), tokensFor(next), silverFor(next));
        }
        if (!free) {
            take(inventory, ModItems.TOKEN_OF_BLOOD.get(), tokensFor(next));
            take(inventory, ModItems.SILVER_INGOT.get(), silverFor(next));
        }
        promote(level, hunter, player, next, "message.crimsoncovenant.hunter.promoted");
        return InteractionResult.SUCCESS;
    }

    private static void promote(ServerLevel level, Hunter hunter, Player player, int rank, String messageKey) {
        set(player, rank);
        player.sendSystemMessage(Component.translatable(messageKey, name(rank)).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.crimsoncovenant.hunter.unlocked", HunterGear.unlockedAt(rank)).withStyle(ChatFormatting.GRAY));
        level.playSound(null, hunter.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8F, 1.2F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, hunter.getX(), hunter.getY(1.0), hunter.getZ(), 10, 0.4, 0.3, 0.4, 0.0);
    }

    /// Becoming a vampire ends a hunter's career
    public static void castOut(Player player) {
        if (isHunter(player)) {
            set(player, NONE);
            player.sendSystemMessage(Component.translatable("message.crimsoncovenant.hunter.cast_out").withStyle(ChatFormatting.DARK_RED));
        }
    }

    private static InteractionResult refuse(Player player, String messageKey, Object... args) {
        player.sendOverlayMessage(Component.translatable(messageKey, args).withStyle(ChatFormatting.GRAY));
        return InteractionResult.FAIL;
    }

    private static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void take(Inventory inventory, Item item, int count) {
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
            }
        }
    }
}
