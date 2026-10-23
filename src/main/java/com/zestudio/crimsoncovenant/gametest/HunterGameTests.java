package com.zestudio.crimsoncovenant.gametest;

import java.util.List;

import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.MasterHunter;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.world.HunterPartySpawner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/// GameTests for the hunter. Registered in ModGameTests.
final class HunterGameTests {
    private HunterGameTests() {}

    static void partyHasOneLeader(GameTestHelper helper) {
        int spawned = Hunter.spawnParty(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), helper.getLevel().getRandom(),
                EntitySpawnReason.PATROL, ModEntities.HUNTER.get());
        helper.assertTrue(spawned == Hunter.PARTY_SIZE, Component.literal("Expected a party of 3 hunters, got " + spawned));

        List<Hunter> hunters = helper.getEntities(ModEntities.HUNTER.get());
        List<Hunter> leaders = hunters.stream().filter(Hunter::isLeader).toList();
        helper.assertTrue(leaders.size() == 1, Component.literal("Expected exactly 1 leader, got " + leaders.size()));

        Hunter leader = leaders.getFirst();
        helper.assertTrue(leader.getItemBySlot(EquipmentSlot.HEAD).is(ItemTags.BANNERS), Component.literal("Leader should wear a banner"));
        helper.assertTrue(leader.getMainHandItem().is(Items.IRON_AXE), Component.literal("Leader should carry an iron axe"));

        for (Hunter hunter : hunters) {
            if (!hunter.isLeader()) {
                helper.assertTrue(hunter.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), Component.literal("Only the leader should wear a banner"));
                helper.assertTrue(hunter.getMainHandItem().is(Items.IRON_AXE) || hunter.getMainHandItem().is(Items.CROSSBOW),
                        Component.literal("Followers should carry an iron axe or a crossbow"));
            }
        }
        helper.succeed();
    }

    static void masterHunterLeadsWithShieldAndSmiteAxe(GameTestHelper helper) {
        int spawned = Hunter.spawnParty(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), helper.getLevel().getRandom(),
                EntitySpawnReason.PATROL, ModEntities.MASTER_HUNTER.get());
        helper.assertTrue(spawned == Hunter.PARTY_SIZE, Component.literal("Expected a party of 3 hunters, got " + spawned));
        List<MasterHunter> masters = helper.getEntities(ModEntities.MASTER_HUNTER.get());
        helper.assertTrue(masters.size() == 1, Component.literal("Expected 1 Master Hunter, got " + masters.size()));
        MasterHunter master = masters.getFirst();
        helper.assertTrue(master.isLeader(), Component.literal("Master Hunter should lead the party"));
        helper.assertTrue(master.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD), Component.literal("Master Hunter should carry a shield"));
        helper.assertTrue(smiteLevel(helper, master.getMainHandItem()) == 3 && master.getMainHandItem().is(ItemTags.AXES),
                Component.literal("Master Hunter should carry a Smite III axe"));
        // Followers are hunters, now and then a chemist among them
        int followers = helper.getEntities(ModEntities.HUNTER.get()).size() + helper.getEntities(ModEntities.HUNTER_CHEMIST.get()).size();
        helper.assertTrue(followers == 2, Component.literal("Master Hunter should have 2 followers, has " + followers));
        helper.succeed();
    }

    static void masterHunterBlocksFromTheFront(GameTestHelper helper) {
        // Facing east (+X): one zombie in front, one behind
        MasterHunter master = helper.spawnWithNoFreeWill(ModEntities.MASTER_HUNTER.get(), new BlockPos(4, 1, 4));
        master.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        master.setYRot(-90.0F);
        master.setYHeadRot(-90.0F);
        master.setYBodyRot(-90.0F);
        Zombie front = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
        Zombie behind = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 4));
        ServerLevel level = helper.getLevel();
        int blockedFromFront = 0;
        int blockedFromBehind = 0;
        for (int i = 0; i < 30; i++) {
            master.damageCooldownTime = 0;
            if (!master.hurtServer(level, level.damageSources().mobAttack(front), 1.0F)) {
                blockedFromFront++;
            }
            master.damageCooldownTime = 0;
            if (!master.hurtServer(level, level.damageSources().mobAttack(behind), 1.0F)) {
                blockedFromBehind++;
            }
        }
        helper.assertTrue(blockedFromFront > 0, Component.literal("Shield should block some hits from the front"));
        helper.assertTrue(blockedFromFront < 30, Component.literal("Shield should not block every hit (front " + blockedFromFront + ", behind " + blockedFromBehind + ", health " + master.getHealth() + ")"));
        helper.assertTrue(blockedFromBehind == 0, Component.literal("Shield should never block hits from behind, blocked " + blockedFromBehind));
        helper.assertTrue(master.getItemBySlot(EquipmentSlot.OFFHAND).getDamageValue() > 0, Component.literal("Blocking should wear down the shield"));
        helper.succeed();
    }

    static void masterHunterThrowsHolyWater(GameTestHelper helper) {
        MasterHunter master = helper.spawn(ModEntities.MASTER_HUNTER.get(), new BlockPos(1, 1, 4));
        master.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        // Husks don't burn in daylight, so any fire comes from the Holy Water. Extra health so it survives the axe.
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(7, 1, 4));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
        husk.setHealth(500.0F);
        helper.succeedWhen(() -> helper.assertTrue(husk.hasEffect(ModEffects.HOLY_FLAME), Component.literal("Master Hunter should set the husk alight with Holy Water")));
    }

    static void undeadAttackHunters(GameTestHelper helper) {
        Hunter hunter = helper.spawnWithNoFreeWill(ModEntities.HUNTER.get(), new BlockPos(4, 1, 4));
        // Helmets stop them burning in the test world's daylight
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 2));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(7, 1, 6));
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(zombie.getTarget() == hunter, Component.literal("Zombies should attack hunters on sight"));
                    helper.assertTrue(skeleton.getTarget() == hunter, Component.literal("Skeletons should attack hunters on sight"));
                })
                // Don't leave them roaming into neighbouring tests
                .thenExecute(() -> {
                    zombie.discard();
                    skeleton.discard();
                })
                .thenSucceed();
    }

    static void hunterBecomesMasterByKillingMasterVampire(GameTestHelper helper) {
        MasterVampire master = helper.spawnWithNoFreeWill(ModEntities.MASTER_VAMPIRE.get(), new BlockPos(6, 1, 4));
        master.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        master.setHealth(1.0F);
        Hunter hunter = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(2, 1, 4));
        hunter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        // Focused on this Master only, so undead from neighbouring tests can't distract it
        hunter.targetSelector.removeAllGoals(goal -> true);
        hunter.setTarget(master);
        helper.succeedWhen(() -> {
            helper.assertTrue(!master.isAlive(), Component.literal("Hunter should slay the Master Vampire"));
            List<MasterHunter> masters = helper.getEntities(ModEntities.MASTER_HUNTER.get());
            helper.assertTrue(masters.size() == 1, Component.literal("The hunter should rise as a Master Hunter"));
            helper.assertTrue(helper.getEntities(ModEntities.HUNTER.get()).isEmpty(), Component.literal("The regular hunter should be gone"));
            helper.assertTrue(masters.getFirst().getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD), Component.literal("The new Master Hunter should get its gear"));
        });
    }

    static void masterHunterDrinksRegenerationAfterBattle(GameTestHelper helper) {
        MasterHunter master = helper.spawn(ModEntities.MASTER_HUNTER.get(), new BlockPos(4, 1, 4));
        master.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        master.setHealth(30.0F);
        // Keep it calm: undead from neighbouring tests would otherwise count as a fight
        master.targetSelector.removeAllGoals(goal -> true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(master.isDrinkingRegeneration(), Component.literal("Wounded Master Hunter should drink once things are calm")))
                .thenWaitUntil(() -> {
                    helper.assertTrue(master.hasEffect(MobEffects.REGENERATION), Component.literal("The potion should give Regeneration"));
                    helper.assertTrue(master.getMainHandItem().is(Items.IRON_AXE), Component.literal("The axe should be back in hand after drinking"));
                })
                .thenSucceed();
    }

    static void masterHunterThrowsLingeringAtGroups(GameTestHelper helper) {
        MasterHunter master = helper.spawn(ModEntities.MASTER_HUNTER.get(), new BlockPos(1, 1, 4));
        master.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        for (BlockPos pos : List.of(new BlockPos(7, 1, 3), new BlockPos(7, 1, 5))) {
            Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, pos);
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
            husk.setHealth(500.0F);
        }
        helper.succeedWhen(() -> helper.assertTrue(!helper.getEntities(EntityTypes.AREA_EFFECT_CLOUD).isEmpty(),
                Component.literal("Master Hunter should throw lingering Holy Water at a group of undead")));
    }

    static void dropsTokenOfBloodWhenKilledByPlayer(GameTestHelper helper) {
        Hunter hunter = helper.spawnWithNoFreeWill(ModEntities.HUNTER.get(), new BlockPos(4, 1, 4));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        hunter.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertItemEntityPresent(ModItems.TOKEN_OF_BLOOD.get());
        helper.succeed();
    }

    private static int smiteLevel(GameTestHelper helper, ItemStack weapon) {
        return EnchantmentHelper.getItemEnchantmentLevel(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SMITE), weapon);
    }

    static void partySpawnsFromAboveGround(GameTestHelper helper) {
        // Spawn spots from heightmaps aren't always exactly on the ground, so the leader has to find it too
        int spawned = Hunter.spawnParty(helper.getLevel(), helper.absolutePos(new BlockPos(4, 3, 4)), helper.getLevel().getRandom(), EntitySpawnReason.PATROL);
        helper.assertTrue(spawned == Hunter.PARTY_SIZE, Component.literal("Expected a party of 3 hunters, got " + spawned));
        helper.succeed();
    }

    static void spawnerKnowsWhenItsNight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerClockManager clocks = level.getServer().clockManager();
        Holder<WorldClock> overworldClock = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
        long originalTime = level.getOverworldClockTime();
        try {
            clocks.setTotalTicks(overworldClock, 6000L);
            helper.assertTrue(!HunterPartySpawner.isNight(level), Component.literal("Noon should not count as night"));
            clocks.setTotalTicks(overworldClock, 24000L * 5 + 18000L);
            helper.assertTrue(HunterPartySpawner.isNight(level), Component.literal("Midnight on day 6 should count as night"));
        } finally {
            clocks.setTotalTicks(overworldClock, originalTime);
        }
        helper.succeed();
    }

    static void targetsUndead(GameTestHelper helper) {
        Hunter hunter = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(2, 1, 4));
        hunter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
        helper.succeedWhen(() -> helper.assertTrue(hunter.getTarget() == zombie, Component.literal("Hunter should target the zombie")));
    }

    static void targetsVampire(GameTestHelper helper) {
        Hunter hunter = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(2, 1, 4));
        hunter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(6, 1, 4));
        vampire.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        helper.succeedWhen(() -> helper.assertTrue(hunter.getTarget() == vampire, Component.literal("Hunter should target the vampire")));
    }

    static void crossbowShoots(GameTestHelper helper) {
        Hunter hunter = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(1, 1, 4));
        hunter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        // Husks are undead but don't burn in daylight, and with no AI they stay put as a target
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(7, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), Component.literal("Crossbow hunter should shoot the husk"));
            // Only a silver arrow slows what it hits
            helper.assertTrue(husk.hasEffect(MobEffects.SLOWNESS), Component.literal("The hunter's bolt should be silver-tipped"));
        });
    }

    static void ignoresAnimals(GameTestHelper helper) {
        Hunter hunter = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(2, 1, 4));
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(6, 1, 4));
        helper.runAfterDelay(100, () -> {
            // Checks the cow specifically: undead from neighbouring tests may legitimately catch its eye
            helper.assertTrue(hunter.getTarget() != cow && cow.getHealth() == cow.getMaxHealth(), Component.literal("Hunter should not attack animals"));
            helper.succeed();
        });
    }
}
