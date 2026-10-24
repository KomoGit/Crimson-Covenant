package com.zestudio.crimsoncovenant.gametest;

import java.util.Optional;

import com.zestudio.crimsoncovenant.block.CurseforgeBlock;
import com.zestudio.crimsoncovenant.curse.Curse;
import com.zestudio.crimsoncovenant.curse.CurseforgeMenu;
import com.zestudio.crimsoncovenant.curse.Curses;
import com.zestudio.crimsoncovenant.curse.WeaponCurse;
import com.zestudio.crimsoncovenant.entity.SpiritHand;
import com.zestudio.crimsoncovenant.entity.SpiritWyrms;
import com.zestudio.crimsoncovenant.hunter.HunterRanks;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.skill.Enthrall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/// The hunters' curses: forging them into weapons, unleashing them, and what each does while it's loose
final class CurseGameTests {
    private CurseGameTests() {}

    private static ItemStack tome(Curse curse) {
        return new ItemStack(ModItems.CURSED_TOMES.get(curse).get());
    }

    private static ItemStack cursed(ItemStack weapon, Curse curse, int level) {
        weapon.set(ModDataComponents.CURSE.get(), new WeaponCurse(curse, level));
        return weapon;
    }

    // A hunter of the given rank, holding the weapon, standing at (4, 1, 2) facing the +z way
    private static ServerPlayer hunter(GameTestHelper helper, int rank, ItemStack weapon) {
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 2.5));
        player.snapTo(at.x, at.y, at.z, helper.getTestRotation().rotate(Direction.SOUTH).toYRot(), 0.0F);
        player.teleportTo(at.x, at.y, at.z);
        player.setYRot(helper.getTestRotation().rotate(Direction.SOUTH).toYRot());
        player.setYHeadRot(player.getYRot());
        HunterRanks.set(player, rank);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        return player;
    }

    static void curseforgeLaysAndDeepensCurses(GameTestHelper helper) {
        ItemStack once = CurseforgeMenu.forge(new ItemStack(Items.IRON_SWORD), tome(Curse.ENCHAIN));
        helper.assertTrue(WeaponCurse.levelOf(once, Curse.ENCHAIN) == 1, Component.literal("A tome should lay its curse on a sword"));
        ItemStack twice = CurseforgeMenu.forge(once, tome(Curse.ENCHAIN));
        ItemStack thrice = CurseforgeMenu.forge(twice, tome(Curse.ENCHAIN));
        helper.assertTrue(WeaponCurse.levelOf(twice, Curse.ENCHAIN) == 2 && WeaponCurse.levelOf(thrice, Curse.ENCHAIN) == 3,
                Component.literal("The same tome again should deepen the curse a level"));
        helper.assertTrue(CurseforgeMenu.forge(thrice, tome(Curse.ENCHAIN)).isEmpty(), Component.literal("A curse should go no deeper than III"));
        helper.assertTrue(CurseforgeMenu.forge(once, tome(Curse.GUST)).isEmpty(), Component.literal("A weapon should bear only one curse"));
        helper.assertTrue(CurseforgeMenu.forge(new ItemStack(Items.BOW), tome(Curse.GUST)).isEmpty(), Component.literal("A melee curse shouldn't go on a bow"));
        helper.assertTrue(CurseforgeMenu.forge(new ItemStack(Items.IRON_SWORD), tome(Curse.BANISH)).isEmpty(), Component.literal("A bow's curse shouldn't go on a sword"));
        helper.assertTrue(WeaponCurse.levelOf(CurseforgeMenu.forge(new ItemStack(Items.CROSSBOW), tome(Curse.DRAGONSTRIKE)), Curse.DRAGONSTRIKE) == 1,
                Component.literal("The Wrath of the Twin Wyrms should go on a crossbow"));
        helper.assertTrue(Curse.rankFor(1) == HunterRanks.INITIATE && Curse.rankFor(2) == HunterRanks.SLAYER && Curse.rankFor(3) == HunterRanks.GRAND_HUNTER,
                Component.literal("Deeper curses should ask higher hunter ranks"));
        helper.succeed();
    }

    static void aGhostTurnsAnAnvilIntoACurseforge(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, Blocks.CHIPPED_ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST));
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 2.5));
        player.teleportTo(at.x, at.y, at.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GHOST_IN_A_BOTTLE.get()));
        helper.assertTrue(CurseforgeBlock.pourGhost(helper.getLevel(), helper.absolutePos(pos), player, InteractionHand.MAIN_HAND),
                Component.literal("A ghost should pour into an anvil"));
        helper.assertBlockPresent(ModBlocks.CURSEFORGE.get(), pos);
        helper.assertBlockProperty(pos, CurseforgeBlock.FACING, Direction.EAST);
        helper.assertTrue(player.getMainHandItem().is(ModItems.SILVERED_BOTTLE.get()), Component.literal("The bottle should come back empty"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void unleashingAsksRankAndTime(GameTestHelper helper) {
        ServerPlayer player = hunter(helper, HunterRanks.NONE, cursed(new ItemStack(Items.IRON_SWORD), Curse.ENCHAIN, 2));
        helper.assertTrue(Curses.unleash(player) == Curses.Outcome.TOO_LOW_RANK, Component.literal("Only a hunter should be able to unleash a curse"));
        HunterRanks.set(player, HunterRanks.TRACKER);
        helper.assertTrue(Curses.unleash(player) == Curses.Outcome.TOO_LOW_RANK, Component.literal("A level II curse should ask a Slayer"));
        HunterRanks.set(player, HunterRanks.SLAYER);
        helper.assertTrue(Curses.unleash(player) == Curses.Outcome.UNLEASHED, Component.literal("A Slayer should unleash a level II curse"));
        helper.assertTrue(Curses.unleash(player) == Curses.Outcome.ALREADY_LOOSE, Component.literal("A loose curse can't be unleashed again"));
        Curses.spend(player);
        helper.assertTrue(Curses.unleash(player) == Curses.Outcome.STIRRING, Component.literal("A spent curse should need time to stir again"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(Curses.unleash(player) == Curses.Outcome.NO_CURSE, Component.literal("Nothing to unleash on a plain sword"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void shacklesAndGaleOnTheBlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = hunter(helper, HunterRanks.GRAND_HUNTER, cursed(new ItemStack(Items.IRON_SWORD), Curse.ENCHAIN, 1));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
        Curses.unleash(player);
        player.attack(zombie);
        helper.assertTrue(zombie.hasEffect(ModEffects.ENCHAINED) && zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.0,
                Component.literal("A blow under the Shackles should chain the zombie where it stands"));
        Curses.spend(player);

        Zombie farther = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 6));
        float health = farther.getHealth();
        Curses.gust(level, player, 2);
        helper.assertTrue(farther.getHealth() < health, Component.literal("Banshee's Gale should strike what's in front, out of reach of the blade"));
        SkillGameTests.removePlayer(helper, player);
        zombie.discard();
        farther.discard();
        helper.succeed();
    }

    static void theBanishedCantHurtOrHeal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = hunter(helper, HunterRanks.INITIATE, cursed(new ItemStack(Items.BOW), Curse.BANISH, 1));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 6));
        Curses.unleash(player);
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), player.getMainHandItem());
        Vec3 from = helper.absoluteVec(new Vec3(4.5, 2.0, 3.5));
        arrow.setPos(from.x, from.y, from.z);
        arrow.shoot(zombie.getX() - from.x, zombie.getY(0.5) - from.y, zombie.getZ() - from.z, 2.0F, 0.0F);
        level.addFreshEntity(arrow);
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.hasEffect(ModEffects.BANISHED), Component.literal("An arrow under Exile's Mark should banish the zombie"));
            float villagerHealth = villager.getHealth();
            zombie.doHurtTarget(level, villager);
            helper.assertValueEqual(villager.getHealth(), villagerHealth, "villager's health after a banished zombie's blow");
            float zombieHealth = zombie.getHealth();
            zombie.heal(4.0F);
            helper.assertValueEqual(zombie.getHealth(), zombieHealth, "banished zombie's health after healing");
            SkillGameTests.removePlayer(helper, player);
            zombie.discard();
            villager.discard();
        });
    }

    static void ravenousMawFeedsAndStarves(GameTestHelper helper) {
        ServerPlayer player = hunter(helper, HunterRanks.INITIATE, cursed(new ItemStack(Items.IRON_SWORD), Curse.GLUTTONY, 1));
        player.setHealth(6.0F);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
        // A mock player's blow is a weak one (it's never ticked, so its sword never counts): the zombie's on its last breath
        zombie.setHealth(0.05F);
        Curses.unleash(player);
        player.attack(zombie);
        helper.assertTrue(zombie.isDeadOrDying(), Component.literal("The blow should kill the zombie"));
        helper.assertTrue(player.getHealth() > 6.0F, Component.literal("A kill under the Ravenous Maw should heal the hunter"));
        Curses.spend(player);
        helper.assertTrue(!player.hasEffect(MobEffects.WITHER), Component.literal("Fed its kill, the Maw shouldn't wither the hunter"));
        // Unfed, it withers them
        player.setData(ModAttachments.UNLEASHED_CURSE, Optional.of(new Curses.Unleashed(Curse.GLUTTONY, 1, helper.getLevel().getGameTime() + 100, 0)));
        Curses.spend(player);
        helper.assertTrue(player.hasEffect(MobEffects.WITHER), Component.literal("Unfed, the Ravenous Maw should wither the hunter"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void gravecallerRaisesTheSlain(GameTestHelper helper) {
        ServerPlayer player = hunter(helper, HunterRanks.INITIATE, cursed(new ItemStack(Items.IRON_SWORD), Curse.ENTHRALL, 1));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
        zombie.setHealth(0.05F);
        Curses.unleash(player);
        player.attack(zombie);
        helper.assertTrue(zombie.isDeadOrDying(), Component.literal("The blow should kill the zombie"));
        Mob risen = helper.getEntities(EntityTypes.ZOMBIE).stream().filter(z -> z != zombie && z.entityTags().contains(Enthrall.RISEN_TAG)).findFirst().orElse(null);
        helper.assertTrue(risen != null && Enthrall.isThrallOf(risen, player), Component.literal("Gravecaller should raise the slain zombie to serve the hunter"));
        risen.discard();
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void revenantsGripFightsAlongside(GameTestHelper helper) {
        ServerPlayer player = hunter(helper, HunterRanks.INITIATE, cursed(new ItemStack(Items.IRON_SWORD), Curse.SPIRIT_HAND, 1));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 7));
        float health = zombie.getHealth();
        Curses.unleash(player);
        helper.assertValueEqual(helper.getEntities(com.zestudio.crimsoncovenant.registry.ModEntities.SPIRIT_HAND.get()).size(), 1, "spirit hands summoned");
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getHealth() < health, Component.literal("The spirit hand should go after the zombie"));
            helper.getEntities(com.zestudio.crimsoncovenant.registry.ModEntities.SPIRIT_HAND.get()).forEach(SpiritHand::discard);
            SkillGameTests.removePlayer(helper, player);
            zombie.discard();
        });
    }

    static void wraithseekerArrowsFindTheirMark(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = hunter(helper, HunterRanks.GRAND_HUNTER, cursed(new ItemStack(Items.BOW), Curse.TRACING_SHOT, 3));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(7, 1, 7));
        float health = zombie.getHealth();
        Curses.unleash(player);
        // Loosed straight ahead, well wide of the zombie off to the side
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), player.getMainHandItem());
        Vec3 from = helper.absoluteVec(new Vec3(4.5, 2.0, 3.0));
        Vec3 ahead = helper.absoluteVec(new Vec3(4.5, 2.0, 8.0)).subtract(from);
        arrow.setPos(from.x, from.y, from.z);
        level.addFreshEntity(arrow);
        arrow.shoot(ahead.x, ahead.y, ahead.z, 1.2F, 0.0F);
        helper.assertTrue(arrow.getData(ModAttachments.SEEKING) == 3, Component.literal("The arrow should be loosed seeking"));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getHealth() < health, Component.literal("The seeking arrow should bend round to hit the zombie"));
            SkillGameTests.removePlayer(helper, player);
            zombie.discard();
        });
    }

    static void twinWyrmsTearThroughWalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = hunter(helper, HunterRanks.INITIATE, cursed(new ItemStack(Items.BOW), Curse.DRAGONSTRIKE, 1));
        for (int x = 2; x <= 6; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE_BRICKS);
            }
        }
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 7));
        float health = zombie.getHealth();
        Curses.unleash(player);
        // The arrow loosed while the curse is out becomes the wyrms, and the curse is spent on them
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), player.getMainHandItem());
        Vec3 from = helper.absoluteVec(new Vec3(4.5, 1.6, 3.0));
        arrow.setPos(from.x, from.y, from.z);
        Vec3 ahead = helper.absoluteVec(new Vec3(4.5, 1.6, 9.0)).subtract(from);
        arrow.shoot(ahead.x, ahead.y, ahead.z, 2.0F, 0.0F);
        level.addFreshEntity(arrow);
        helper.assertTrue(arrow.isRemoved() || !arrow.isAddedToLevel(), Component.literal("The arrow should become the wyrms"));
        helper.assertValueEqual(helper.getEntities(com.zestudio.crimsoncovenant.registry.ModEntities.SPIRIT_WYRMS.get()).size(), 1, "wyrms loosed");
        helper.assertTrue(Curses.unleashed(player).isEmpty(), Component.literal("The curse should be spent on the wyrms"));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getHealth() <= health - SpiritWyrms.damage(1) + 0.01F, Component.literal("The wyrms should tear through the wall and strike the zombie"));
            SkillGameTests.removePlayer(helper, player);
            zombie.discard();
        });
    }
}
