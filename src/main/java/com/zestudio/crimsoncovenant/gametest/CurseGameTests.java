package com.zestudio.crimsoncovenant.gametest;

import java.util.List;

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
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.skill.Enthrall;
import com.zestudio.crimsoncovenant.curse.Earthshatter;
import com.zestudio.crimsoncovenant.curse.Grapple;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.enchantment.Enchantments;

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
        helper.assertTrue(zombie.getData(ModAttachments.ENCHAINED_LOOK), Component.literal("The chained zombie should be seen in chains"));
        zombie.setDeltaMovement(0.8, 0.0, 0.8);
        ModEffects.ENCHAINED.get().applyEffectTick(level, zombie, 0);
        helper.assertTrue(zombie.getDeltaMovement().horizontalDistanceSqr() == 0.0, Component.literal("Nothing should slide a chained zombie along"));
        zombie.removeEffect(ModEffects.ENCHAINED);
        helper.assertTrue(!zombie.getData(ModAttachments.ENCHAINED_LOOK), Component.literal("Freed, the zombie's chains should be gone"));
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
            helper.assertTrue(zombie.getData(ModAttachments.BANISHED_LOOK), Component.literal("The banished zombie should be drawn faint"));
            float villagerHealth = villager.getHealth();
            zombie.doHurtTarget(level, villager);
            helper.assertValueEqual(villager.getHealth(), villagerHealth, "villager's health after a banished zombie's blow");
            float zombieHealth = zombie.getHealth();
            zombie.heal(4.0F);
            helper.assertValueEqual(zombie.getHealth(), zombieHealth, "banished zombie's health after healing");
            zombie.removeEffect(ModEffects.BANISHED);
            helper.assertTrue(!zombie.getData(ModAttachments.BANISHED_LOOK), Component.literal("Unbanished, the zombie should be solid again"));
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
        // Always awake: no unleashing
        player.attack(zombie);
        helper.assertTrue(zombie.isDeadOrDying(), Component.literal("The blow should kill the zombie"));
        helper.assertTrue(player.getHealth() > 6.0F, Component.literal("A kill with the Ravenous Maw should heal the hunter"));
        long now = helper.getLevel().getGameTime();
        helper.assertValueEqual(player.getData(ModAttachments.MAW_FED), now, "when the Maw last fed");
        // Unfed too long, it gnaws at them (the mock player is never ticked: gnaw as a tick would, on a whole second)
        player.setData(ModAttachments.MAW_FED, now - Curses.gluttonyPatience(1) - 1);
        player.tickCount = 20;
        Curses.gnaw(helper.getLevel(), player);
        helper.assertTrue(player.hasEffect(MobEffects.HUNGER), Component.literal("Unfed, the Ravenous Maw should gnaw at the hunter"));
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
        // In survival, so the sword wears as it's used (a creative player's never does)
        player.setGameMode(GameType.SURVIVAL);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 7));
        // Only the hand should hurt it, not the sun
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1000));
        float health = zombie.getHealth();
        helper.assertTrue(Curses.unleash(player) == Curses.Outcome.ALWAYS_AWAKE, Component.literal("Revenant's Grip should need no unleashing"));
        // Only held, it stays in hand
        SpiritHand.tend(helper.getLevel(), player);
        helper.assertTrue(helper.getEntities(ModEntities.SPIRIT_HAND.get()).isEmpty(), Component.literal("Held, the sword should stay in hand until right-clicked"));
        helper.assertTrue(SpiritHand.sendOut(helper.getLevel(), player, InteractionHand.MAIN_HAND) != null, Component.literal("A right-click should send the sword out"));
        List<SpiritHand> hands = helper.getEntities(ModEntities.SPIRIT_HAND.get());
        helper.assertTrue(hands.size() == 1 && hands.getFirst().getWeapon().is(Items.IRON_SWORD), Component.literal("A spirit hand should take up the sword"));
        helper.assertTrue(player.getMainHandItem().isEmpty(), Component.literal("The sword should leave the hunter's hand for the spirit's"));
        helper.assertTrue(SpiritHand.blowDamage(new ItemStack(Items.IRON_SWORD), 1) < health, Component.literal("A spirit hand's blow shouldn't fell a zombie at a stroke"));
        SpiritHand hand = hands.getFirst();
        Vec3 start = hand.position();
        helper.startSequence()
                .thenExecuteAfter(2, () -> helper.assertTrue(hand.position().distanceTo(start) < 1.0,
                        Component.literal("The spirit hand should glide toward the zombie, not leap to it")))
                .thenWaitUntil(() -> helper.assertTrue(zombie.getHealth() < health, Component.literal("The spirit hand should go after the zombie")))
                .thenExecute(() -> helper.assertTrue(hand.getWeapon().getDamageValue() > 0, Component.literal("The spirit hand's blows should wear the sword")))
                .thenExecute(() -> {
                    // The hunter's free to do other things: another slot, something else in hand
                    int swordSlot = player.getInventory().getSelectedSlot();
                    player.getInventory().setSelectedSlot((swordSlot + 1) % Inventory.SELECTION_SIZE);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
                    SpiritHand.tend(helper.getLevel(), player);
                    helper.assertTrue(!hand.isRemoved(), Component.literal("Another slot selected, the spirit hand should fight on"));
                    // Right-clicked, it comes back
                    helper.assertTrue(hand.interact(player, InteractionHand.MAIN_HAND, hand.position()).consumesAction() && hand.isRemoved(),
                            Component.literal("Right-clicked by its owner, the spirit hand should give the sword back"));
                    ItemStack back = player.getInventory().getItem(swordSlot);
                    helper.assertTrue(back.is(Items.IRON_SWORD) && WeaponCurse.levelOf(back, Curse.SPIRIT_HAND) == 1,
                            Component.literal("The cursed sword should be back in its own slot"));
                    SkillGameTests.removePlayer(helper, player);
                    zombie.discard();
                })
                .thenSucceed();
    }

    static void wraithseekerArrowsFindTheirMark(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = hunter(helper, HunterRanks.GRAND_HUNTER, cursed(new ItemStack(Items.BOW), Curse.TRACING_SHOT, 3));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(7, 1, 7));
        // Only the arrow should hurt it, not the sun
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1000));
        // Always awake: a first arrow strikes the zombie, and marks it
        Arrow first = new Arrow(level, player, new ItemStack(Items.ARROW), player.getMainHandItem());
        zombie.hurtServer(level, level.damageSources().arrow(first, player), 1.0F);
        helper.assertTrue(Curses.markOf(player) == zombie, Component.literal("A Wraithseeker arrow should mark the zombie it strikes"));
        zombie.setInvulnerableTime(0);
        float health = zombie.getHealth();
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

    static void wraithseekerTrickShotsComeRound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = hunter(helper, HunterRanks.INITIATE, cursed(new ItemStack(Items.BOW), Curse.TRACING_SHOT, 1));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 8));
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1000));
        Curses.mark(player, zombie);
        float health = zombie.getHealth();
        // A trick shot coming in fast at a steep angle, the way one loosed up into the sky comes down: here (the test plot
        // has a ceiling) on its side, at full speed straight for a wall, with the mark off to one side just short of it.
        // It should turn onto the mark, not plunge on into the wall (the ground, in the sky shot) beside it.
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 9; z++) {
                helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
            }
        }
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), player.getMainHandItem());
        Vec3 from = helper.absoluteVec(new Vec3(1.5, 2.0, 4.0));
        arrow.setPos(from.x, from.y, from.z);
        level.addFreshEntity(arrow);
        Vec3 toWall = helper.absoluteVec(new Vec3(7.0, 2.0, 4.0)).subtract(from);
        arrow.shoot(toWall.x, toWall.y, toWall.z, 3.0F, 0.0F);
        // Well past the moment it's loosed, when it hunts nothing yet
        arrow.tickCount = 10;
        helper.assertTrue(arrow.getData(ModAttachments.SEEKING) == 1, Component.literal("The arrow should be loosed hunting the mark"));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getHealth() < health, Component.literal("A fast arrow coming in steep should turn onto the mark, not into the wall beside it"));
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
            helper.assertTrue(zombie.getHealth() <= health - 2.0F * SpiritWyrms.damage(1) + 0.01F,
                    Component.literal("The wyrms should tear through the wall and bite the zombie again and again as they pass through it"));
            helper.assertTrue(zombie.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4, Component.literal("The wyrms should pass through the zombie without shoving it"));
            // They tear on a long way, through walls and into other tests: away with them
            helper.getLevel().getEntities(com.zestudio.crimsoncovenant.registry.ModEntities.SPIRIT_WYRMS.get(), wyrms -> true).forEach(SpiritWyrms::discard);
            SkillGameTests.removePlayer(helper, player);
            zombie.discard();
        });
    }

    static void hauntedWeaponsCantBeMended(GameTestHelper helper) {
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        ItemStack haunted = cursed(new ItemStack(Items.IRON_SWORD), Curse.ENCHAIN, 1);
        haunted.setDamageValue(100);
        // An anvil: iron to mend it, or a second sword joined to it
        AnvilMenu anvil = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
        anvil.getSlot(0).set(haunted.copy());
        anvil.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
        anvil.createResult();
        helper.assertTrue(anvil.getSlot(2).getItem().isEmpty(), Component.literal("An anvil shouldn't mend a haunted sword with iron"));
        anvil.getSlot(1).set(new ItemStack(Items.IRON_SWORD));
        anvil.createResult();
        helper.assertTrue(anvil.getSlot(2).getItem().isEmpty(), Component.literal("An anvil shouldn't mend a haunted sword with another"));
        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        plain.setDamageValue(100);
        anvil.getSlot(0).set(plain);
        anvil.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
        anvil.createResult();
        helper.assertTrue(!anvil.getSlot(2).getItem().isEmpty(), Component.literal("An anvil should still mend a plain sword"));
        // A grindstone
        GrindstoneMenu grindstone = new GrindstoneMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
        grindstone.getSlot(0).set(haunted.copy());
        grindstone.getSlot(1).set(new ItemStack(Items.IRON_SWORD));
        grindstone.slotsChanged(grindstone.getSlot(0).container);
        helper.assertTrue(grindstone.getSlot(2).getItem().isEmpty(), Component.literal("A grindstone shouldn't join a haunted sword to another"));
        // A sword that mends itself won't be haunted
        ItemStack mending = new ItemStack(Items.IRON_SWORD);
        mending.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        helper.assertTrue(CurseforgeMenu.forge(mending, new ItemStack(ModItems.CURSED_TOMES.get(Curse.ENCHAIN).get())).isEmpty(),
                Component.literal("A sword with Mending shouldn't take a haunting"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void gravequakeThrowsThemDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack whip = cursed(new ItemStack(ModItems.WHIP.get()), Curse.EARTHSHATTER, 1);
        helper.assertTrue(CurseforgeMenu.forge(new ItemStack(Items.IRON_SWORD), tome(Curse.EARTHSHATTER)).isEmpty(),
                Component.literal("Gravequake should only go on a whip"));
        ServerPlayer player = hunter(helper, HunterRanks.INITIATE, whip);
        // One zombie in the quake's way, one behind the wielder
        Zombie before = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 6));
        Zombie behind = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 0));
        before.removeFreeWill();
        behind.removeFreeWill();
        helper.startSequence()
                // Let them settle on the ground first
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(Curses.unleash(player) == Curses.Outcome.UNLEASHED, Component.literal("Gravequake should be unleashed"));
                    helper.assertTrue(Earthshatter.isDown(before) && before.getData(ModAttachments.KNOCKED_DOWN_UNTIL) > 0L,
                            Component.literal("The zombie in the quake's way should be thrown down, and seen lying there"));
                    helper.assertTrue(!Earthshatter.isDown(behind), Component.literal("The zombie behind the wielder shouldn't be"));
                    helper.assertValueEqual(before.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.0, "the fallen zombie's speed");
                    helper.assertTrue(!behind.hurtServer(level, level.damageSources().mobAttack(before), 4.0F), Component.literal("The fallen can't land a blow"));
                    helper.assertTrue(Curses.unleashed(player).isEmpty(), Component.literal("The quake should be spent at once"));
                    helper.assertTrue(Curses.unleash(player) == Curses.Outcome.STIRRING, Component.literal("It should need time to stir again"));
                })
                .thenExecuteAfter(Earthshatter.KNOCKDOWN_TICKS + 2, () -> {
                    helper.assertTrue(!Earthshatter.isDown(before) && before.getData(ModAttachments.KNOCKED_DOWN_UNTIL) == 0L,
                            Component.literal("After three seconds the zombie should get up"));
                })
                .thenExecute(() -> {
                    SkillGameTests.removePlayer(helper, player);
                    before.discard();
                    behind.discard();
                })
                .thenSucceed();
    }

    static void wraithsTetherHaulsYouIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A wall across the way, five blocks ahead of the wielder (well beyond the whip's own reach)
        for (int x = 1; x <= 7; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
            }
        }
        ItemStack whip = cursed(new ItemStack(ModItems.WHIP.get()), Curse.GRAPPLE, 1);
        ServerPlayer player = hunter(helper, HunterRanks.NONE, whip);
        helper.assertTrue(!Grapple.hook(level, player, whip), Component.literal("Wraith's Tether shouldn't answer one who isn't a hunter"));
        HunterRanks.set(player, HunterRanks.INITIATE);
        helper.assertTrue(Grapple.hook(level, player, whip) && Grapple.isGrappling(player), Component.literal("The lash should hook the wall"));
        Vec3 haul = Grapple.haul(player);
        helper.assertTrue(Math.abs(haul.length() - Grapple.SPEED) < 0.01 && haul.normalize().dot(player.getLookAngle()) > 0.8,
                Component.literal("The wielder should be hauled towards the wall at full speed, not " + haul));
        Grapple.tick(level, player);
        helper.assertTrue(player.getDeltaMovement().distanceTo(haul) < 0.01, Component.literal("A tick of hauling should carry the wielder along"));
        player.setData(ModAttachments.GRAPPLE, java.util.Optional.empty());
        // A creature within the whip's own reach is pulled in instead
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
        helper.assertTrue(!Grapple.hook(level, player, whip), Component.literal("With a creature in reach, the whip should pull it, not hook the wall"));
        zombie.discard();
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void hauntingsCostSouls(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        helper.assertValueEqual(CurseforgeMenu.soulCost(plain, Curse.ENCHAIN), Curse.ENCHAIN.souls(), "souls to haunt a plain sword");
        helper.assertTrue(Curse.TRACING_SHOT.souls() > Curse.BANISH.souls(), Component.literal("Wraithseeker should cost more than the other bow hauntings"));
        ItemStack enchanted = new ItemStack(Items.IRON_SWORD);
        enchanted.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 1);
        helper.assertValueEqual(CurseforgeMenu.soulCost(enchanted, Curse.ENCHAIN), Curse.ENCHAIN.souls() * 2, "souls to haunt an enchanted sword");
        ItemStack haunted = cursed(new ItemStack(Items.IRON_SWORD), Curse.ENCHAIN, 1);
        helper.assertValueEqual(CurseforgeMenu.soulCost(haunted, Curse.ENCHAIN), (int) Math.floor(Curse.ENCHAIN.souls() * 1.25), "souls to haunt a haunted sword deeper");
        ItemStack both = cursed(enchanted.copy(), Curse.ENCHAIN, 1);
        helper.assertValueEqual(CurseforgeMenu.soulCost(both, Curse.ENCHAIN), (int) Math.floor(Curse.ENCHAIN.souls() * 2 * 1.25), "souls to haunt an enchanted, haunted sword deeper");

        // At the anvil: too few souls and the haunting can't be taken; enough, and they're all spent
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        CurseforgeMenu forge = new CurseforgeMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
        forge.getSlot(CurseforgeMenu.TOME_SLOT).set(tome(Curse.ENCHAIN));
        forge.getSlot(CurseforgeMenu.WEAPON_SLOT).set(new ItemStack(Items.IRON_SWORD));
        forge.getSlot(CurseforgeMenu.GHOST_SLOT).set(new ItemStack(ModItems.TORMENTED_SOUL.get(), Curse.ENCHAIN.souls() - 1));
        forge.createResult();
        helper.assertTrue(forge.getCost() == Curse.ENCHAIN.souls() && !forge.getSlot(CurseforgeMenu.RESULT_SLOT).mayPickup(player),
                Component.literal("Short of souls, the haunting shouldn't be taken"));
        forge.getSlot(CurseforgeMenu.GHOST_SLOT).set(new ItemStack(ModItems.TORMENTED_SOUL.get(), Curse.ENCHAIN.souls() + 1));
        forge.createResult();
        helper.assertTrue(forge.getSlot(CurseforgeMenu.RESULT_SLOT).mayPickup(player), Component.literal("With souls enough, it should be taken"));
        forge.getSlot(CurseforgeMenu.RESULT_SLOT).onTake(player, forge.getSlot(CurseforgeMenu.RESULT_SLOT).getItem());
        helper.assertValueEqual(forge.getSlot(CurseforgeMenu.GHOST_SLOT).getItem().getCount(), 1, "souls left after the haunting");
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void theFallenCanBeStruckWhereTheyLie(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
        float standing = zombie.getPickRadius();
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Earthshatter.knockDown(zombie, player);
        helper.assertTrue(zombie.getPickRadius() >= zombie.getBbHeight() - zombie.getBbWidth() * 0.5F - 0.01F && zombie.getPickRadius() > standing,
                Component.literal("Lying flat, the zombie should be struck where its body lies"));
        float health = zombie.getHealth();
        helper.assertTrue(zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 3.0F) && zombie.getHealth() < health,
                Component.literal("The fallen should take blows"));
        zombie.removeEffect(com.zestudio.crimsoncovenant.registry.ModEffects.KNOCKED_DOWN);
        helper.assertValueEqual(zombie.getPickRadius(), standing, "the zombie's pick radius once it's up");
        zombie.discard();
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }
}
