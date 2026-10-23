package com.zestudio.crimsoncovenant.gametest;

import com.zestudio.crimsoncovenant.block.BearTrapBlock;
import com.zestudio.crimsoncovenant.entity.VampireBat;
import com.zestudio.crimsoncovenant.item.Silvering;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModVillagers;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.phys.Vec3;

/// Silvered weapons, and the hunters' side of things
final class SilverGameTests {
    private SilverGameTests() {}

    static void silveringAtTheSmithingTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack ingot = new ItemStack(ModItems.SILVER_INGOT.get());
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(30);
        SmithingRecipeInput input = new SmithingRecipeInput(ItemStack.EMPTY, sword, ingot);
        var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level);
        helper.assertTrue(recipe.isPresent(), Component.literal("A sword and a silver ingot should make a smithing recipe, no template needed"));
        ItemStack silvered = recipe.get().value().assemble(input);
        helper.assertTrue(silvered.is(Items.IRON_SWORD) && Silvering.isSilvered(silvered), Component.literal("The sword should come back silvered"));
        helper.assertValueEqual(silvered.getDamageValue(), 30, "wear on the silvered sword (it's the same sword)");

        helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(ItemStack.EMPTY, new ItemStack(Items.DIAMOND_AXE), ingot), level).isPresent(),
                Component.literal("Axes can be silvered too"));
        helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(ItemStack.EMPTY, silvered, ingot), level).isEmpty(),
                Component.literal("A weapon can only be silvered once"));
        helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(ItemStack.EMPTY, new ItemStack(Items.STICK), ingot), level).isEmpty(),
                Component.literal("Only weapons can be silvered"));
        helper.succeed();
    }

    static void silveredWeaponsWoundUndead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Villagers make handy sword-holders: they don't fight back
        Villager plainHolder = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        Villager silverHolder = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(7, 1, 1));
        ItemStack silveredSword = new ItemStack(Items.IRON_SWORD);
        silveredSword.set(ModDataComponents.SILVERED.get(), true);
        plainHolder.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        silverHolder.setItemSlot(EquipmentSlot.MAINHAND, silveredSword);

        Husk plainHit = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(2, 1, 5));
        Husk silverHit = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(6, 1, 5));
        plainHit.hurtServer(level, level.damageSources().mobAttack(plainHolder), 4.0F);
        silverHit.hurtServer(level, level.damageSources().mobAttack(silverHolder), 4.0F);
        float plainLost = plainHit.getMaxHealth() - plainHit.getHealth();
        float silverLost = silverHit.getMaxHealth() - silverHit.getHealth();
        helper.assertTrue(silverLost > plainLost + 3.0F, Component.literal("Silver should cut the undead deeper: " + silverLost + " against " + plainLost));
        helper.assertTrue(silverHit.hasEffect(ModEffects.SILVER_WOUND) && !plainHit.hasEffect(ModEffects.SILVER_WOUND),
                Component.literal("Only the silvered blade should leave a Silver Wound"));

        float before = silverHit.getHealth();
        silverHit.heal(4.0F);
        helper.assertValueEqual(silverHit.getHealth(), before, "health of a husk healed through a Silver Wound");
        before = plainHit.getHealth();
        plainHit.heal(1.0F);
        helper.assertTrue(plainHit.getHealth() > before, Component.literal("Without the wound, healing should work"));

        // The living don't fear silver
        Villager bystander = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 7));
        bystander.hurtServer(level, level.damageSources().mobAttack(silverHolder), 4.0F);
        helper.assertTrue(!bystander.hasEffect(ModEffects.SILVER_WOUND), Component.literal("Silver shouldn't wound the living"));
        helper.succeed();
    }

    static void silverWoundStopsBloodComingBack(GameTestHelper helper) {
        ServerPlayer player = SkillGameTests.makeVampire(helper, 50);
        player.tickCount = 0;
        BloodPool.tick(player);
        helper.assertValueEqual(BloodPool.get(player), 51, "blood after a refill tick");
        player.addEffect(new MobEffectInstance(ModEffects.SILVER_WOUND, 100));
        BloodPool.tick(player);
        helper.assertValueEqual(BloodPool.get(player), 51, "blood after a refill tick with a Silver Wound");
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void oldHunterRefusesVampires(GameTestHelper helper) {
        Villager hunter = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        hunter.setVillagerData(hunter.getVillagerData().withProfession(ModVillagers.OLD_HUNTER).withLevel(1));
        ServerPlayer player = SkillGameTests.makeVampire(helper, 50);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 2.5));
        player.teleportTo(at.x, at.y, at.z);
        player.interactOn(hunter, InteractionHand.MAIN_HAND, hunter.position());
        helper.assertTrue(hunter.getTradingPlayer() == null, Component.literal("The Old Hunter shouldn't trade with a vampire"));

        player.setData(ModAttachments.IS_VAMPIRE, false);
        player.interactOn(hunter, InteractionHand.MAIN_HAND, hunter.position());
        helper.assertTrue(hunter.getTradingPlayer() == player, Component.literal("The Old Hunter should still trade with the living"));
        player.closeContainer();
        hunter.setTradingPlayer(null);
        SkillGameTests.removePlayer(helper, player);
        hunter.discard();
        helper.succeed();
    }

    static void vampireBatsAreUndeadCaveDwellers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VampireBat bat = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE_BAT.get(), new BlockPos(4, 2, 4));
        helper.assertTrue(Vampirism.isUndead(bat), Component.literal("Vampire bats should be undead"));
        helper.assertTrue(BearTrapBlock.isWeakToSilver(bat), Component.literal("Vampire bats should be weak to silver"));
        helper.assertTrue(bat.isInvertedHealAndHarm(), Component.literal("Healing should hurt a vampire bat, like other undead"));
        // Out in the open they don't spawn on their own, only in caves
        BlockPos open = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.assertTrue(!ModEntities.checkVampireBatSpawnRules(ModEntities.VAMPIRE_BAT.get(), level, EntitySpawnReason.NATURAL, open, level.getRandom()),
                Component.literal("Vampire bats shouldn't spawn naturally under the open sky"));
        bat.discard();
        helper.succeed();
    }
}
