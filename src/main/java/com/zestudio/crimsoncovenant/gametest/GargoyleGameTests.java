package com.zestudio.crimsoncovenant.gametest;

import java.util.List;

import com.zestudio.crimsoncovenant.entity.Gargoyle;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class GargoyleGameTests {
    private GargoyleGameTests() {}

    /// Test worlds sit at noon, so a gargoyle out in the open is stone. A roof over the arena keeps them awake.
    private static void roofOver(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE_BRICKS);
            }
        }
    }

    private static Gargoyle setDown(GameTestHelper helper, ServerPlayer player, ItemStack statue, BlockPos floor) {
        player.setItemInHand(InteractionHand.MAIN_HAND, statue);
        BlockPos at = helper.absolutePos(floor);
        statue.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
        List<Gargoyle> found = helper.getLevel().getEntitiesOfClass(Gargoyle.class, new AABB(at.above()).inflate(0.5));
        return found.isEmpty() ? null : found.getFirst();
    }

    static void statuesWakeOnlyForTheirSide(GameTestHelper helper) {
        ServerPlayer human = SkillGameTests.makePlayer(helper);
        ServerPlayer vampire = SkillGameTests.makeVampire(helper, 0);

        helper.assertTrue(setDown(helper, vampire, new ItemStack(ModItems.CATHEDRAL_GARGOYLE_STATUE.get()), new BlockPos(2, 0, 2)) == null,
                Component.literal("The hunters' silver shouldn't wake for a vampire"));
        helper.assertTrue(setDown(helper, human, new ItemStack(ModItems.BLOOD_GARGOYLE_STATUE.get()), new BlockPos(6, 0, 2)) == null,
                Component.literal("Blood magic shouldn't answer the living"));

        ItemStack silver = new ItemStack(ModItems.CATHEDRAL_GARGOYLE_STATUE.get());
        Gargoyle cathedral = setDown(helper, human, silver, new BlockPos(2, 0, 6));
        helper.assertTrue(cathedral != null && cathedral.getType() == ModEntities.CATHEDRAL_GARGOYLE.get(), Component.literal("A Cathedral Gargoyle should wake for the living"));
        helper.assertTrue(silver.isEmpty(), Component.literal("Setting down the statue should use it up"));
        helper.assertTrue(cathedral.isMaker(human) && !cathedral.isFollowing() && cathedral.hasHome(), Component.literal("It should serve its maker, guarding where it was set down"));

        Gargoyle blood = setDown(helper, vampire, new ItemStack(ModItems.BLOOD_GARGOYLE_STATUE.get()), new BlockPos(6, 0, 6));
        helper.assertTrue(blood != null && blood.getSide() == Gargoyle.Side.VAMPIRE, Component.literal("A Blood Gargoyle should wake for a vampire"));

        // Each is the other's enemy
        helper.assertTrue(cathedral.isEnemy(blood) && blood.isEnemy(cathedral), Component.literal("Gargoyles of the two sides should fight"));
        SkillGameTests.removePlayer(helper, human);
        SkillGameTests.removePlayer(helper, vampire);
        helper.succeed();
    }

    static void makersTurnedAreMakersNoMore(GameTestHelper helper) {
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Gargoyle gargoyle = setDown(helper, player, new ItemStack(ModItems.CATHEDRAL_GARGOYLE_STATUE.get()), new BlockPos(4, 0, 4));
        helper.assertTrue(gargoyle != null && !gargoyle.isEnemy(player) && !gargoyle.canAttack(player),
                Component.literal("A gargoyle should never turn on its maker"));
        player.setData(ModAttachments.IS_VAMPIRE, true);
        // (Mock players are always in creative, which gargoyles leave be, so isEnemy can't be checked here)
        helper.assertTrue(!gargoyle.isMaker(player) && !gargoyle.isAlly(player), Component.literal("A maker who turns vampire is their gargoyle's master no more"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void sunlightTurnsGargoylesToStone(GameTestHelper helper) {
        Gargoyle outside = helper.spawnWithNoFreeWill(ModEntities.CATHEDRAL_GARGOYLE.get(), new BlockPos(2, 1, 4));
        // A little shelter for the other one
        for (int x = 5; x <= 7; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE_BRICKS);
            }
        }
        Gargoyle sheltered = helper.spawnWithNoFreeWill(ModEntities.BLOOD_GARGOYLE.get(), new BlockPos(6, 1, 4));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(outside.isStone(), Component.literal("A gargoyle under the noon sun should turn to stone")))
                // Sky light takes a moment to settle under the new roof, so give the sheltered one its next look
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(!sheltered.isStone(), Component.literal("A gargoyle in the shade should stay awake"));
                    float before = outside.getHealth();
                    outside.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10.0F);
                    helper.assertTrue(before - outside.getHealth() <= 2.0F, Component.literal("Stone should shrug off most of a blow"));
                })
                .thenSucceed();
    }

    static void cathedralGargoyleHuntsTheUndeadWithSilver(GameTestHelper helper) {
        roofOver(helper);
        Gargoyle gargoyle = helper.spawn(ModEntities.CATHEDRAL_GARGOYLE.get(), new BlockPos(1, 1, 4));
        Hunter hunter = helper.spawnWithNoFreeWill(ModEntities.HUNTER.get(), new BlockPos(3, 1, 4));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(gargoyle.getTarget() != hunter, Component.literal("A Cathedral Gargoyle shouldn't attack hunters"));
                    helper.assertTrue(zombie.hasEffect(ModEffects.SILVER_WOUND), Component.literal("Its silver claws should wound the undead"));
                })
                .thenExecute(() -> {
                    zombie.discard();
                    hunter.discard();
                    // Left be, it would fly off hunting the undead of the tests next door
                    gargoyle.discard();
                })
                .thenSucceed();
    }

    static void bloodGargoyleDrinksFromTheLiving(GameTestHelper helper) {
        roofOver(helper);
        Gargoyle gargoyle = helper.spawn(ModEntities.BLOOD_GARGOYLE.get(), new BlockPos(1, 1, 4));
        gargoyle.setHealth(20.0F);
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(3, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 4));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(gargoyle.getTarget() != vampire, Component.literal("A Blood Gargoyle shouldn't attack vampires"));
                    helper.assertTrue(villager.hasEffect(ModEffects.BLOOD_LOSS) || villager.isDeadOrDying(), Component.literal("It should bleed the living"));
                    helper.assertTrue(gargoyle.getHealth() > 20.0F, Component.literal("It should drink what it spills"));
                })
                .thenExecute(() -> {
                    villager.discard();
                    vampire.discard();
                })
                .thenSucceed();
    }

    static void huntersAndVampiresFightGargoyles(GameTestHelper helper) {
        roofOver(helper);
        Hunter hunter = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(1, 1, 1));
        Gargoyle blood = helper.spawnWithNoFreeWill(ModEntities.BLOOD_GARGOYLE.get(), new BlockPos(3, 1, 1));
        Vampire vampire = helper.spawn(ModEntities.VAMPIRE.get(), new BlockPos(1, 1, 7));
        Gargoyle cathedral = helper.spawnWithNoFreeWill(ModEntities.CATHEDRAL_GARGOYLE.get(), new BlockPos(3, 1, 7));
        helper.succeedWhen(() -> {
            helper.assertTrue(hunter.getTarget() == blood, Component.literal("Hunters should hunt Blood Gargoyles"));
            helper.assertTrue(vampire.getTarget() == cathedral, Component.literal("Vampires should hunt Cathedral Gargoyles"));
            // Left be, they'd go after the tests next door
            hunter.discard();
            blood.discard();
            vampire.discard();
            cathedral.discard();
        });
    }
}
