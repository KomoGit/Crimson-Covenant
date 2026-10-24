package com.zestudio.crimsoncovenant.gametest;

import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.hunter.HunterGear;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.item.Silvering;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.phys.Vec3;

/// Joining the hunters, rising through their ranks, and the gear each rank unlocks
final class HunterRankGameTests {
    private HunterRankGameTests() {}

    private static Hunter spawnHunter(GameTestHelper helper, BlockPos pos, boolean leader) {
        Hunter hunter = helper.spawnWithNoFreeWill(ModEntities.HUNTER.get(), pos);
        hunter.setLeader(leader);
        hunter.targetSelector.removeAllGoals(goal -> true);
        return hunter;
    }

    private static ServerPlayer playerNear(GameTestHelper helper, Hunter hunter) {
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Vec3 at = hunter.position().add(0.0, 0.0, 1.5);
        player.teleportTo(at.x, at.y, at.z);
        return player;
    }

    private static void offer(ServerPlayer player, Hunter hunter, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.interactOn(hunter, InteractionHand.MAIN_HAND, hunter.position());
    }

    static void joinTheHuntersWithASilverCoin(GameTestHelper helper) {
        Hunter leader = spawnHunter(helper, new BlockPos(2, 1, 4), true);
        Hunter follower = spawnHunter(helper, new BlockPos(6, 1, 4), false);
        ServerPlayer player = playerNear(helper, follower);

        offer(player, follower, new ItemStack(ModItems.SILVER_COIN.get()));
        helper.assertValueEqual(HunterRanks.get(player), HunterRanks.NONE, "rank after offering a coin to a hunter who isn't the leader");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "coins left after the follower turned the offer down");

        offer(player, leader, player.getMainHandItem());
        helper.assertValueEqual(HunterRanks.get(player), HunterRanks.INITIATE, "rank after offering the leader a Silver Coin");
        helper.assertTrue(player.getMainHandItem().isEmpty(), Component.literal("The leader should take the coin"));

        // Vampires are turned away
        ServerPlayer vampire = SkillGameTests.makeVampire(helper, 50);
        vampire.teleportTo(player.getX(), player.getY(), player.getZ());
        offer(vampire, leader, new ItemStack(ModItems.SILVER_COIN.get()));
        helper.assertValueEqual(HunterRanks.get(vampire), HunterRanks.NONE, "a vampire's rank after offering the leader a coin");

        SkillGameTests.removePlayer(helper, player);
        SkillGameTests.removePlayer(helper, vampire);
        helper.succeed();
    }

    static void riseThroughTheRanks(GameTestHelper helper) {
        Hunter hunter = spawnHunter(helper, new BlockPos(4, 1, 4), false);
        ServerPlayer player = playerNear(helper, hunter);
        HunterRanks.set(player, HunterRanks.INITIATE);
        int tokens = HunterRanks.tokensFor(HunterRanks.TRACKER);
        int silver = HunterRanks.silverFor(HunterRanks.TRACKER);

        // One silver ingot short (kept out of the hand, which holds the tokens)
        player.getInventory().setItem(9, new ItemStack(ModItems.SILVER_INGOT.get(), silver - 1));
        offer(player, hunter, new ItemStack(ModItems.TOKEN_OF_BLOOD.get(), tokens));
        helper.assertValueEqual(HunterRanks.get(player), HunterRanks.INITIATE, "rank when one silver ingot short");

        player.getInventory().setItem(10, new ItemStack(ModItems.SILVER_INGOT.get(), 1));
        offer(player, hunter, player.getMainHandItem());
        helper.assertValueEqual(HunterRanks.get(player), HunterRanks.TRACKER, "rank after paying for Tracker");
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack left = player.getInventory().getItem(slot);
            helper.assertTrue(!left.is(ModItems.TOKEN_OF_BLOOD.get()) && !left.is(ModItems.SILVER_INGOT.get()), Component.literal("The tokens and silver should be spent"));
        }
        helper.assertTrue(HunterRanks.tokensFor(HunterRanks.SLAYER) > tokens, Component.literal("Each rank should cost more than the last"));

        // Turning into a vampire ends it
        HunterRanks.set(player, HunterRanks.WARDEN);
        Vampirism.turn(player);
        helper.assertValueEqual(HunterRanks.get(player), HunterRanks.NONE, "rank after turning into a vampire");
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void craftingHunterGearNeedsTheRank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(level, helper.absolutePos(new BlockPos(4, 1, 4))));
        // Silver arrows: a nugget over a stick over a feather
        menu.getSlot(1).set(new ItemStack(ModItems.SILVER_NUGGET.get()));
        menu.getSlot(4).set(new ItemStack(Items.STICK));
        menu.getSlot(7).set(new ItemStack(Items.FEATHER));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), Component.literal("Someone who isn't a hunter shouldn't be able to craft silver arrows"));

        HunterRanks.set(player, HunterRanks.INITIATE);
        menu.slotsChanged(menu.getSlot(1).container);
        helper.assertTrue(menu.getSlot(0).getItem().is(ModItems.SILVER_ARROW.get()), Component.literal("An Initiate should be able to craft silver arrows"));

        // Plain things are still for everyone
        HunterRanks.set(player, HunterRanks.NONE);
        menu.getSlot(1).set(ItemStack.EMPTY);
        menu.getSlot(7).set(ItemStack.EMPTY);
        menu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
        menu.getSlot(4).set(new ItemStack(Items.OAK_PLANKS));
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.STICK), Component.literal("Anyone should still be able to craft sticks"));
        menu.getSlot(1).container.clearContent();
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void silveringNeedsASlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        HunterRanks.set(player, HunterRanks.TRACKER);
        SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(level, helper.absolutePos(new BlockPos(4, 1, 4))));
        menu.getSlot(SmithingMenu.BASE_SLOT).set(new ItemStack(Items.IRON_SWORD));
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(ModItems.SILVER_INGOT.get()));
        helper.assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(), Component.literal("A Tracker shouldn't be able to silver a weapon"));

        HunterRanks.set(player, HunterRanks.SLAYER);
        menu.slotsChanged(menu.getSlot(SmithingMenu.BASE_SLOT).container);
        helper.assertTrue(Silvering.isSilvered(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem()), Component.literal("A Slayer should be able to silver a weapon"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void brewingHunterPotionsNeedsTheRank(GameTestHelper helper) {
        BlockPos forTracker = new BlockPos(2, 1, 4);
        BlockPos forNobody = new BlockPos(6, 1, 4);
        for (BlockPos pos : new BlockPos[] {forTracker, forNobody}) {
            helper.setBlock(pos, Blocks.BREWING_STAND);
            BrewingStandBlockEntity stand = helper.getBlockEntity(pos, BrewingStandBlockEntity.class);
            stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
            stand.setItem(3, new ItemStack(ModItems.SILVER_DUST.get()));
            stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        }
        // As if a Tracker last opened one stand, and someone who isn't a hunter the other
        helper.getBlockEntity(forTracker, BrewingStandBlockEntity.class).setData(ModAttachments.BREWER_RANK, HunterRanks.TRACKER);
        helper.getBlockEntity(forNobody, BrewingStandBlockEntity.class).setData(ModAttachments.BREWER_RANK, HunterRanks.NONE);
        helper.succeedWhen(() -> {
            ItemStack trackersBrew = helper.getBlockEntity(forTracker, BrewingStandBlockEntity.class).getItem(0);
            PotionContents brewed = trackersBrew.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            helper.assertTrue(brewed != null && brewed.is(ModPotions.HOLY_WATER), Component.literal("The Tracker's stand should brew Holy Water"));
            PotionContents untouched = helper.getBlockEntity(forNobody, BrewingStandBlockEntity.class).getItem(0).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            helper.assertTrue(untouched != null && untouched.is(Potions.WATER), Component.literal("A stand opened by someone who isn't a hunter shouldn't brew Holy Water"));
        });
    }

    static void gearRanks(GameTestHelper helper) {
        helper.assertValueEqual(HunterGear.requiredRank(new ItemStack(Items.IRON_SWORD)), 0, "rank needed for a plain sword");
        helper.assertValueEqual(HunterGear.requiredRank(new ItemStack(ModItems.BEAR_TRAP.get())), HunterRanks.TRACKER, "rank needed for a bear trap");
        helper.assertValueEqual(HunterGear.requiredRank(new ItemStack(ModItems.SILVER_NET_TRAP.get())), HunterRanks.WARDEN, "rank needed for a net trap");
        helper.assertValueEqual(HunterGear.requiredRank(PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.SILVER)), HunterRanks.WARDEN, "rank needed for a splash Potion of Silver");
        helper.assertValueEqual(HunterGear.requiredRank(PotionContents.createItemStack(Items.LINGERING_POTION, ModPotions.HOLY_WATER)), HunterRanks.GRAND_HUNTER, "rank needed for lingering Holy Water");
        helper.assertValueEqual(HunterGear.requiredRank(PotionContents.createItemStack(Items.LINGERING_POTION, Potions.HEALING)), 0, "rank needed for a lingering Potion of Healing");
        helper.succeed();
    }
}
