package com.zestudio.crimsoncovenant.gametest;

import java.util.List;

import com.zestudio.crimsoncovenant.block.AncientTranslatorBlock;
import com.zestudio.crimsoncovenant.block.GravestoneBlock;
import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.guide.Bestiary;
import com.zestudio.crimsoncovenant.guide.GuideBooks;
import com.zestudio.crimsoncovenant.guide.SkillLore;
import com.zestudio.crimsoncovenant.guide.Souls;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.skill.AncientTomeItem;
import com.zestudio.crimsoncovenant.skill.BloodSkill;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.skill.SkillKeeper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/// Tests for the guide books: souls, unlocking blood skills in the Codex, the translator and the wild keeping to what's
/// unlocked, the books turning into each other, and the bestiary
final class GuideGameTests {
    private GuideGameTests() {}

    // A vampire standing in the test, near its middle
    private static ServerPlayer vampireAt(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = SkillGameTests.makeVampire(helper, 0);
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(pos));
        player.teleportTo(at.x, at.y, at.z);
        return player;
    }

    static void soulsUnlockSkills(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 1, 6), ModBlocks.ANCIENT_TRANSLATOR.get());
        ServerPlayer player = vampireAt(helper, new BlockPos(4, 1, 3));
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(ModItems.GHOST_IN_A_BOTTLE.get()));
        player.getInventory().add(new ItemStack(ModItems.TORMENTED_SOUL.get(), 2));
        helper.assertValueEqual(Souls.count(player), 3, "souls carried");

        // A Neo skill, for one soul (a tormented one, the cheaper, goes first, and its glass bottle comes back)
        helper.assertTrue(SkillLore.unlock(player, BloodSkill.DASH) == SkillLore.Outcome.UNLOCKED, Component.literal("Dash should unlock for a soul"));
        helper.assertValueEqual(Souls.count(player), 2, "souls left after unlocking Dash");
        helper.assertTrue(player.getInventory().contains(stack -> stack.is(Items.GLASS_BOTTLE)), Component.literal("The tormented soul's bottle should come back"));
        helper.assertTrue(SkillLore.unlock(player, BloodSkill.DASH) == SkillLore.Outcome.ALREADY, Component.literal("Dash is already unlocked"));
        // The Unusual skills are sealed until two Neo ones are unlocked
        helper.assertTrue(SkillLore.unlock(player, BloodSkill.BLOOD_SPEAR) == SkillLore.Outcome.TIER_SEALED, Component.literal("Unusual skills start sealed"));
        helper.assertTrue(SkillLore.unlock(player, BloodSkill.DAYWALKER) == SkillLore.Outcome.UNLOCKED, Component.literal("Daywalker should unlock"));
        // Now open, but an Unusual skill takes two souls, and only one's left
        helper.assertTrue(SkillLore.isTierOpen(player, BloodSkill.Tier.UNUSUAL), Component.literal("Two Neo skills should open the Unusual ones"));
        helper.assertTrue(SkillLore.unlock(player, BloodSkill.BLOOD_SPEAR) == SkillLore.Outcome.NOT_ENOUGH_SOULS, Component.literal("Blood Spear takes two souls"));
        helper.assertTrue(SkillLore.unlocked(player).equals(java.util.EnumSet.of(BloodSkill.DASH, BloodSkill.DAYWALKER)), Component.literal("Dash and Daywalker are unlocked"));

        // Away from a translator, nothing can be unlocked; nor by anyone who isn't a vampire
        player.getInventory().add(new ItemStack(ModItems.GHOST_IN_A_BOTTLE.get(), 4));
        Vec3 away = helper.absoluteVec(new Vec3(4.5, 1.0, 3.5)).add(0.0, 0.0, -30.0);
        player.teleportTo(away.x, away.y, away.z);
        helper.assertTrue(SkillLore.unlock(player, BloodSkill.FLIGHT) == SkillLore.Outcome.NO_TRANSLATOR, Component.literal("Unlocking needs a translator near"));
        ServerPlayer human = SkillGameTests.makePlayer(helper);
        helper.assertTrue(SkillLore.check(human, BloodSkill.DASH) == SkillLore.Outcome.NOT_A_VAMPIRE, Component.literal("Only vampires unlock blood skills"));
        SkillGameTests.removePlayer(helper, player);
        SkillGameTests.removePlayer(helper, human);
        helper.succeed();
    }

    static void translationsFillTheCodex(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, ModBlocks.ANCIENT_TRANSLATOR.get());
        ServerPlayer player = vampireAt(helper, new BlockPos(4, 1, 2));
        player.setData(ModAttachments.UNLOCKED_SKILLS, List.of(BloodSkill.DASH.getName()));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BloodSkill.DASH.tome()));
        helper.useBlock(pos, player);
        helper.assertBlockProperty(pos, AncientTranslatorBlock.TOME, BloodSkill.DASH.ordinal() + 1);
        helper.assertTrue(!SkillLore.isTranslated(player, BloodSkill.DASH), Component.literal("Not read yet"));
        BlockPos at = helper.absolutePos(pos);
        AncientTranslatorBlock.finish(level, at, level.getBlockState(at));
        helper.assertTrue(SkillLore.isTranslated(player, BloodSkill.DASH), Component.literal("Its translation done, Dash's page should be filled in for whoever brought the tome"));
        helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM).forEach(net.minecraft.world.entity.Entity::discard);
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void lockedTomesStayHidden(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = vampireAt(helper, new BlockPos(4, 1, 4));
        LootTable tomes = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("crimsoncovenant", "chests/tomes_rare")));
        // Nothing unlocked: not a tome to be found
        helper.assertValueEqual(tomesFound(level, tomes, player), 0, "tomes found with nothing unlocked");
        // Everything unlocked: tomes turn up
        player.setData(ModAttachments.UNLOCKED_SKILLS, java.util.Arrays.stream(BloodSkill.values()).map(BloodSkill::getName).toList());
        helper.assertTrue(tomesFound(level, tomes, player) > 0, Component.literal("With every skill unlocked, tomes should turn up"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    // Tomes in a hundred rolls of the table, opened by `player`
    private static int tomesFound(ServerLevel level, LootTable table, Player player) {
        int found = 0;
        for (int i = 0; i < 100; i++) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player).create(LootContextParamSets.CHEST);
            found += (int) table.getRandomItems(params).stream().filter(stack -> stack.getItem() instanceof AncientTomeItem).count();
        }
        return found;
    }

    static void glassBottlesTormentGhosts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos grave = new BlockPos(2, 1, 2);
        helper.setBlock(grave, ModBlocks.GRAVESTONE.get().defaultBlockState().setValue(GravestoneBlock.SPIRIT, GravestoneBlock.Spirit.RESTING));
        Ghost ghost = GravestoneBlock.rise(level, helper.absolutePos(grave), level.getBlockState(helper.absolutePos(grave)));
        helper.assertTrue(ghost != null, Component.literal("The grave's ghost should rise"));
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        player.interactOn(ghost, InteractionHand.MAIN_HAND, ghost.position());
        helper.assertTrue(ghost.isRemoved(), Component.literal("The ghost should be crammed into the bottle"));
        helper.assertTrue(player.getMainHandItem().is(ModItems.TORMENTED_SOUL.get()), Component.literal("The glass bottle should hold a tormented soul"));
        helper.assertBlockProperty(grave, GravestoneBlock.SPIRIT, GravestoneBlock.Spirit.NONE);
        helper.assertTrue(Souls.isSoul(player.getMainHandItem()), Component.literal("A tormented soul is a soul"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void guideBooksChain(GameTestHelper helper) {
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        player.getInventory().clearContent();
        GuideBooks.give(player, GuideBooks.Book.LEDGER);
        Vampirism.turn(player);
        helper.assertTrue(player.getInventory().contains(stack -> stack.is(ModItems.CODEX_SANGUINIS.get()))
                && !player.getInventory().contains(stack -> stack.is(ModItems.NIGHTS_LEDGER.get())), Component.literal("Turned, the Ledger should become the Codex Sanguinis"));
        Vampirism.cure(player);
        helper.assertTrue(player.getInventory().contains(stack -> stack.is(ModItems.NIGHTS_LEDGER.get()))
                && !player.getInventory().contains(stack -> stack.is(ModItems.CODEX_SANGUINIS.get())), Component.literal("Cured, the Codex should become the Ledger again"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void guidesAreKeptOnDeath(GameTestHelper helper) {
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        player.getInventory().clearContent();
        GuideBooks.give(player, GuideBooks.Book.LEDGER);
        player.getInventory().add(new ItemStack(Items.STICK));
        // Dying: set aside before the inventory falls out
        SkillKeeper.setAside(player);
        helper.assertTrue(!player.getInventory().contains(GuideBooks::isGuide) && player.getInventory().contains(stack -> stack.is(Items.STICK)),
                Component.literal("The guide (and only the guide) should be set aside from what's dropped"));
        SkillKeeper.giveBack(player);
        helper.assertTrue(player.getInventory().contains(stack -> stack.is(ModItems.NIGHTS_LEDGER.get())), Component.literal("Respawned, the guide should be handed back"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void bestiaryFillsAsYouSee(GameTestHelper helper) {
        ServerPlayer player = vampireAt(helper, new BlockPos(1, 1, 4));
        helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(5, 1, 4));
        helper.assertTrue(!Bestiary.hasSeen(player, ModEntities.VAMPIRE.get()), Component.literal("Not seen yet"));
        Bestiary.lookAround(player);
        helper.assertTrue(Bestiary.hasSeen(player, ModEntities.VAMPIRE.get()), Component.literal("A vampire in plain sight should go in the bestiary"));
        helper.assertTrue(!Bestiary.hasSeen(player, ModEntities.WEREWOLF.get()), Component.literal("Nor a werewolf, never seen"));
        helper.getEntities(ModEntities.VAMPIRE.get()).forEach(net.minecraft.world.entity.Entity::discard);
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }
}
