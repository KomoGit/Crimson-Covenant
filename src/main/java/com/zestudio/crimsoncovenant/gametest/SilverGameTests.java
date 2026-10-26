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
import java.util.List;
import com.zestudio.crimsoncovenant.block.SilveredBottleBlock;
import com.zestudio.crimsoncovenant.entity.Ghost;
import com.zestudio.crimsoncovenant.entity.SilverDustCloud;
import com.zestudio.crimsoncovenant.item.GhostBottleItem;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

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
        ServerPlayer player = SkillGameTests.makeVampire(helper, 20);
        player.tickCount = 0;
        BloodPool.tick(player);
        helper.assertValueEqual(BloodPool.get(player), 21, "blood after a refill tick");
        player.addEffect(new MobEffectInstance(ModEffects.SILVER_WOUND, 100));
        BloodPool.tick(player);
        helper.assertValueEqual(BloodPool.get(player), 21, "blood after a refill tick with a Silver Wound");
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void plagueDoctorRefusesVampires(GameTestHelper helper) {
        Villager hunter = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        hunter.setVillagerData(hunter.getVillagerData().withProfession(ModVillagers.PLAGUE_DOCTOR).withLevel(1));
        ServerPlayer player = SkillGameTests.makeVampire(helper, 50);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 2.5));
        player.teleportTo(at.x, at.y, at.z);
        player.interactOn(hunter, InteractionHand.MAIN_HAND, hunter.position());
        helper.assertTrue(hunter.getTradingPlayer() == null, Component.literal("The Plague Doctor shouldn't trade with a vampire"));

        player.setData(ModAttachments.IS_VAMPIRE, false);
        player.interactOn(hunter, InteractionHand.MAIN_HAND, hunter.position());
        helper.assertTrue(hunter.getTradingPlayer() == player, Component.literal("The Plague Doctor should still trade with the living"));
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

    static void silveredBottlesHoldGhosts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack glass = new ItemStack(Items.GLASS);
        CraftingInput input = CraftingInput.of(3, 2, List.of(glass, new ItemStack(ModItems.SILVER_DUST.get()), glass, ItemStack.EMPTY, glass, ItemStack.EMPTY));
        var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
        helper.assertTrue(recipe.isPresent() && recipe.get().value().assemble(input).is(ModItems.SILVERED_BOTTLE.get()),
                Component.literal("Three glass and silver dust should make a Silvered Bottle"));

        // Bottling a ghost
        Ghost ghost = helper.spawnWithNoFreeWill(ModEntities.GHOST.get(), new BlockPos(4, 2, 4));
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 2.5));
        player.teleportTo(at.x, at.y, at.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SILVERED_BOTTLE.get()));
        player.interactOn(ghost, InteractionHand.MAIN_HAND, ghost.position());
        helper.assertTrue(ghost.isRemoved() && player.getMainHandItem().is(ModItems.GHOST_IN_A_BOTTLE.get()), Component.literal("An empty Silvered Bottle should catch a ghost"));

        // Set down, the bottle keeps its ghost, glows, and gives it back when broken
        BlockState full = player.getMainHandItem().getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(ModBlocks.SILVERED_BOTTLE.get().defaultBlockState());
        helper.assertTrue(full.getValue(SilveredBottleBlock.GHOST) && full.getLightEmission() > 0, Component.literal("A placed Ghost in a Bottle should hold its ghost, and glow"));
        BlockPos stand = new BlockPos(2, 1, 2);
        helper.setBlock(stand, full);
        BlockPos absolute = helper.absolutePos(stand);
        helper.assertTrue(Block.getDrops(full, level, absolute, null).stream().anyMatch(stack -> stack.is(ModItems.GHOST_IN_A_BOTTLE.get())),
                Component.literal("A broken bottle should give back the ghost in it"));
        BlockState empty = ModBlocks.SILVERED_BOTTLE.get().defaultBlockState();
        helper.assertTrue(Block.getDrops(empty, level, absolute, null).stream().anyMatch(stack -> stack.is(ModItems.SILVERED_BOTTLE.get())),
                Component.literal("An empty bottle should drop itself"));
        helper.assertTrue(new ItemStack(ModBlocks.SILVERED_BOTTLE.get()).is(ModItems.SILVERED_BOTTLE.get()), Component.literal("The bottle block's own item should be the empty bottle"));

        // Uncorked, the ghost comes out
        Ghost freed = GhostBottleItem.release(level, helper.absoluteVec(new Vec3(6.5, 2.0, 6.5)));
        helper.assertTrue(freed != null && freed.isAlive(), Component.literal("Uncorking the bottle should let the ghost out"));
        freed.discard();
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    /// One ingot breaks into nuggets, four mint coins, and nine make a block, which breaks back into nine
    static void silverIsMintedAndStored(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack ingot = new ItemStack(ModItems.SILVER_INGOT.get());
        assertCrafts(helper, level, CraftingInput.of(1, 1, List.of(ingot)), ModItems.SILVER_NUGGET.get(), 9, "One silver ingot");
        assertCrafts(helper, level, CraftingInput.of(2, 2, List.of(ingot, ingot, ingot, ingot)), ModItems.SILVER_COIN.get(), 8, "Four silver ingots");
        assertCrafts(helper, level, CraftingInput.of(3, 3, java.util.Collections.nCopies(9, ingot)), ModItems.SILVER_BLOCK.get(), 1, "Nine silver ingots");
        assertCrafts(helper, level, CraftingInput.of(1, 1, List.of(new ItemStack(ModItems.SILVER_BLOCK.get()))), ModItems.SILVER_INGOT.get(), 9, "A Block of Silver");

        // Like a block of iron: a stone pickaxe or better to mine it, and a beacon will stand on it
        BlockState block = ModBlocks.SILVER_BLOCK.get().defaultBlockState();
        helper.assertTrue(block.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) && block.is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL)
                && block.is(net.minecraft.tags.BlockTags.BEACON_BASE_BLOCKS), Component.literal("A Block of Silver should mine and serve a beacon like a block of iron"));
        helper.succeed();
    }

    private static void assertCrafts(GameTestHelper helper, ServerLevel level, CraftingInput input, net.minecraft.world.item.Item item, int count, String what) {
        var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
        ItemStack result = recipe.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(item) && result.getCount() == count,
                Component.literal(what + " should craft " + count + " " + item + ", not " + result));
    }

    static void silverOreKicksUpDust(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        BlockPos ore = new BlockPos(4, 1, 4);
        helper.setBlock(ore, ModBlocks.SILVER_ORE.get());
        ItemStack silkTouch = new ItemStack(Items.IRON_PICKAXE);
        silkTouch.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, silkTouch);
        player.gameMode.destroyBlock(helper.absolutePos(ore));
        AABB around = new AABB(helper.absolutePos(ore)).inflate(4.0);
        helper.assertTrue(level.getEntitiesOfClass(SilverDustCloud.class, around).isEmpty(), Component.literal("Ore lifted out whole with Silk Touch shouldn't give off dust"));

        helper.setBlock(ore, ModBlocks.SILVER_ORE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        player.gameMode.destroyBlock(helper.absolutePos(ore));
        helper.assertValueEqual(level.getEntitiesOfClass(SilverDustCloud.class, around).size(), 1, "dust clouds over the broken silver ore");
        SkillGameTests.removePlayer(helper, player);

        // The undead that breathe it in are burned; the living aren't
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 5));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(5, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), Component.literal("Silver dust should burn the undead who breathe it"));
            helper.assertValueEqual(villager.getHealth(), villager.getMaxHealth(), "health of a villager in the silver dust");
            level.getEntitiesOfClass(SilverDustCloud.class, around).forEach(SilverDustCloud::discard);
            zombie.discard();
            villager.discard();
        });
    }
}
