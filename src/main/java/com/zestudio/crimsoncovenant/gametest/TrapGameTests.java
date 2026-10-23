package com.zestudio.crimsoncovenant.gametest;

import java.util.Set;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.block.BearTrapBlock;
import com.zestudio.crimsoncovenant.block.SilverNetTrapBlock;
import com.zestudio.crimsoncovenant.block.SilverSpikesBlock;
import com.zestudio.crimsoncovenant.entity.BloodSpear;
import com.zestudio.crimsoncovenant.entity.HemorrhageZone;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.entity.HunterChemist;
import com.zestudio.crimsoncovenant.entity.MasterVampire;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.entity.Werewolf;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.skill.NightOfTerror;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/// Tests for the hunters' silver gear: the bear trap, silver arrows and the Potion of Silver
final class TrapGameTests {
    private TrapGameTests() {}

    static void bearTrapSnapsAndHolds(GameTestHelper helper) {
        BlockPos villagerTrap = new BlockPos(2, 1, 4);
        BlockPos huskTrap = new BlockPos(6, 1, 4);
        helper.setBlock(villagerTrap, ModBlocks.BEAR_TRAP.get());
        helper.setBlock(huskTrap, ModBlocks.BEAR_TRAP.get());
        Villager villager = helper.spawn(EntityTypes.VILLAGER, villagerTrap);
        Husk husk = helper.spawn(EntityTypes.HUSK, huskTrap);
        Vec3 huskTrapMiddle = Vec3.atBottomCenterOf(helper.absolutePos(huskTrap));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertBlockProperty(villagerTrap, BearTrapBlock.SPRUNG, true);
                    helper.assertBlockProperty(huskTrap, BearTrapBlock.SPRUNG, true);
                })
                .thenExecute(() -> {
                    float villagerLost = villager.getMaxHealth() - villager.getHealth();
                    float huskLost = husk.getMaxHealth() - husk.getHealth();
                    helper.assertTrue(villagerLost > 0.0F, Component.literal("The trap should bite whoever steps in it"));
                    helper.assertTrue(huskLost > villagerLost + 4.0F, Component.literal("Silver should bite the undead much harder: husk lost " + huskLost + ", villager " + villagerLost));
                    // Give the husk something to chase
                    husk.setTarget(villager);
                })
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(husk.position().distanceTo(huskTrapMiddle) < 0.3,
                        Component.literal("The husk should be held fast in the trap, but is " + husk.position().distanceTo(huskTrapMiddle) + " away")))
                .thenExecute(() -> helper.destroyBlock(huskTrap))
                .thenWaitUntil(() -> helper.assertTrue(!BearTrapBlock.isHeldBy(husk, helper.absolutePos(huskTrap)), Component.literal("Breaking the trap should set the husk free")))
                .thenExecute(() -> {
                    husk.discard();
                    villager.discard();
                })
                .thenSucceed();
    }

    static void masterVampiresTearFreeOfTraps(GameTestHelper helper) {
        BlockPos trap = new BlockPos(4, 1, 4);
        helper.setBlock(trap, ModBlocks.BEAR_TRAP.get());
        MasterVampire master = helper.spawn(ModEntities.MASTER_VAMPIRE.get(), trap);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BearTrapBlock.heldIn(master).isPresent(), Component.literal("The trap should catch the master")))
                .thenWaitUntil(() -> {
                    helper.assertBlockNotPresent(ModBlocks.BEAR_TRAP.get(), trap);
                    helper.assertTrue(BearTrapBlock.heldIn(master).isEmpty(), Component.literal("The master should tear itself free"));
                })
                // Torn apart, not dropped
                .thenExecute(() -> helper.assertItemEntityNotPresent(ModItems.BEAR_TRAP.get()))
                .thenExecute(master::discard)
                .thenSucceed();
    }

    static void huntersFreeTrappedHunters(GameTestHelper helper) {
        BlockPos trap = new BlockPos(1, 1, 4);
        helper.setBlock(trap, ModBlocks.BEAR_TRAP.get());
        Hunter trapped = helper.spawn(ModEntities.HUNTER.get(), trap);
        Hunter rescuer = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(7, 1, 4));
        // Only here to rescue: no picking fights with the undead in neighbouring tests
        trapped.targetSelector.removeAllGoals(goal -> true);
        rescuer.targetSelector.removeAllGoals(goal -> true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BearTrapBlock.heldIn(trapped).isPresent(), Component.literal("The trap should catch the hunter")))
                .thenWaitUntil(() -> {
                    helper.assertBlockNotPresent(ModBlocks.BEAR_TRAP.get(), trap);
                    helper.assertTrue(BearTrapBlock.heldIn(trapped).isEmpty(), Component.literal("The other hunter should pry him free"));
                })
                // Pried open, the trap drops whole
                .thenExecute(() -> helper.assertItemEntityPresent(ModItems.BEAR_TRAP.get()))
                .thenExecute(() -> {
                    trapped.discard();
                    rescuer.discard();
                })
                .thenSucceed();
    }

    static void huntersThrowGarlicAtUndead(GameTestHelper helper) {
        Hunter hunter = helper.spawn(ModEntities.HUNTER.get(), new BlockPos(1, 1, 4));
        // A crossbow keeps it at a distance, where it throws
        hunter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        // Its only target is this husk, not the undead in neighbouring tests
        hunter.targetSelector.removeAllGoals(goal -> true);
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(6, 1, 4));
        hunter.setTarget(husk);
        helper.succeedWhen(() -> {
            // The crossbow goal lets go of its target after each shot, and with no targeting goals left it's put back by hand
            if (hunter.getTarget() == null) {
                hunter.setTarget(husk);
            }
            helper.assertTrue(husk.hasEffect(ModEffects.GARLIC), Component.literal("The hunter should splash the husk with garlic"));
            hunter.discard();
            husk.discard();
        });
    }

    static void masterVampiresCastBloodSkills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MasterVampire master = helper.spawnWithNoFreeWill(ModEntities.MASTER_VAMPIRE.get(), new BlockPos(1, 1, 1));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(7, 1, 6));
        // So the noon sun can't be what hurts it
        vampire.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float vampireHealth = vampire.getHealth();
        master.hurlBloodSpear(level, villager);
        helper.assertTrue(!level.getEntitiesOfClass(BloodSpear.class, master.getBoundingBox().inflate(4.0), spear -> spear.getOwner() == master).isEmpty(),
                Component.literal("The master should hurl a Blood Spear"));
        master.boilHemorrhage(level, villager);
        helper.assertTrue(!level.getEntitiesOfClass(HemorrhageZone.class, villager.getBoundingBox().inflate(1.0), zone -> zone.getOwner() == master).isEmpty(),
                Component.literal("The master should boil a hemorrhage under its target"));
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), Component.literal("The villager should be hurt"));
            helper.assertTrue(vampire.getHealth() >= vampireHealth, Component.literal("The master's hemorrhage should spare its own kind"));
            level.getEntitiesOfClass(HemorrhageZone.class, villager.getBoundingBox().inflate(4.0)).forEach(zone -> zone.discard());
            master.discard();
            villager.discard();
            vampire.discard();
        });
    }

    static void chemistsThrowTheRightPotions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HunterChemist chemist = helper.spawnWithNoFreeWill(ModEntities.HUNTER_CHEMIST.get(), new BlockPos(1, 1, 4));
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(7, 1, 4));
        Werewolf werewolf = helper.spawnWithNoFreeWill(ModEntities.WEREWOLF.get(), new BlockPos(7, 1, 7));
        Set<Holder<Potion>> againstUndead = Set.of(ModPotions.HOLY_WATER, ModPotions.GARLIC, ModPotions.SILVER, Potions.STRONG_HEALING);
        for (int i = 0; i < 10; i++) {
            Holder<Potion> potion = chemist.choosePotion(husk);
            helper.assertTrue(againstUndead.stream().anyMatch(choice -> potion.is(choice)), Component.literal("Against the undead, not " + potion));
        }
        helper.assertTrue(chemist.choosePotion(werewolf).is(Potions.SLOWNESS), Component.literal("A fresh living foe gets slowed first"));
        chemist.performRangedAttack(husk, 1.0F);
        helper.assertTrue(!level.getEntitiesOfClass(ThrownSplashPotion.class, chemist.getBoundingBox().inflate(3.0)).isEmpty(), Component.literal("The chemist should throw a potion"));

        // A witch's ingredients when it dies
        LootTable drops = level.getServer().reloadableRegistries().getLootTable(ModEntities.HUNTER_CHEMIST.get().getDefaultLootTable().orElseThrow());
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, chemist).withParameter(LootContextParams.ORIGIN, chemist.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic()).create(LootContextParamSets.ENTITY);
        int items = 0;
        for (int i = 0; i < 20; i++) {
            items += drops.getRandomItems(params).stream().mapToInt(ItemStack::getCount).sum();
        }
        helper.assertTrue(items > 10, Component.literal("A chemist should drop ingredients, got " + items + " in 20 kills"));
        chemist.discard();
        husk.discard();
        werewolf.discard();
        helper.succeed();
    }

    static void chemistsHealHurtHunters(GameTestHelper helper) {
        HunterChemist chemist = helper.spawn(ModEntities.HUNTER_CHEMIST.get(), new BlockPos(1, 1, 4));
        // Here only to heal: no picking fights with the undead in neighbouring tests
        chemist.targetSelector.removeAllGoals(goal -> true);
        Hunter hurt = helper.spawnWithNoFreeWill(ModEntities.HUNTER.get(), new BlockPos(5, 1, 4));
        hurt.setHealth(hurt.getMaxHealth() * 0.3F);
        float before = hurt.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt.getHealth() > before, Component.literal("The chemist should splash the hurt hunter with healing"));
            chemist.discard();
            hurt.discard();
        });
    }


    static void silverSpikesStab(GameTestHelper helper) {
        BlockPos villagerSpikes = new BlockPos(2, 1, 4);
        BlockPos huskSpikes = new BlockPos(6, 1, 4);
        helper.setBlock(villagerSpikes, ModBlocks.SILVER_SPIKES.get());
        helper.setBlock(huskSpikes, ModBlocks.SILVER_SPIKES.get());
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, villagerSpikes);
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, huskSpikes);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), Component.literal("The spikes should stab the villager"));
                    helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), Component.literal("The spikes should stab the husk"));
                })
                .thenExecute(() -> {
                    float villagerLost = villager.getMaxHealth() - villager.getHealth();
                    float huskLost = husk.getMaxHealth() - husk.getHealth();
                    helper.assertTrue(huskLost > villagerLost + 3.0F, Component.literal("Silver should stab the undead harder: husk lost " + huskLost + ", villager " + villagerLost));
                    helper.assertTrue(husk.hasEffect(MobEffects.SLOWNESS), Component.literal("Silver should slow the undead"));
                    // Off the plate, so the spikes are free to sink back
                    villager.discard();
                    husk.discard();
                })
                .thenWaitUntil(() -> helper.assertBlockProperty(huskSpikes, SilverSpikesBlock.EXTENDED, false))
                .thenSucceed();
    }

    static void silverNetTrapCatchesPassersBy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(4, 4, 4), Blocks.STONE);
        BlockPos trap = new BlockPos(4, 3, 4);
        helper.setBlock(trap, ModBlocks.SILVER_NET_TRAP.get());
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(4, 1, 4));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(trap, SilverNetTrapBlock.SPRUNG, true))
                .thenExecute(() -> helper.assertTrue(level.getBlockState(husk.blockPosition()).is(ModBlocks.SILVER_NET.get()),
                        Component.literal("The net should land where the husk stands")))
                .thenWaitUntil(() -> helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), Component.literal("The silver net should burn the husk")))
                .thenExecute(husk::discard)
                .thenSucceed();
    }

    static void huntersShootSilverArrows(GameTestHelper helper) {
        Hunter hunter = helper.spawnWithNoFreeWill(ModEntities.HUNTER.get(), new BlockPos(4, 1, 4));
        helper.assertTrue(hunter.getProjectile(new ItemStack(Items.CROSSBOW)).is(ModItems.SILVER_ARROW.get()), Component.literal("Hunters should load silver arrows"));
        hunter.discard();
        helper.succeed();
    }

    static void silverSplashScattersUndead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(3, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 4));
        husk.addEffect(new MobEffectInstance(ModEffects.SILVER_DREAD, 100));
        villager.addEffect(new MobEffectInstance(ModEffects.SILVER_DREAD, 100));
        helper.assertTrue(NightOfTerror.isTerrified(husk), Component.literal("Silver should send the undead running"));
        helper.assertTrue(!NightOfTerror.isTerrified(villager), Component.literal("Silver shouldn't scare the living"));

        var recipes = level.getServer().getRecipeManager();
        for (String recipe : new String[] {"bear_trap", "hunters_table", "blood_altar", "brewing/potion_awkward_silver_dust", "brewing/potion_silver_gunpowder", "brewing/splash_potion_silver_dragon_breath"}) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, CrimsonCovenant.id(recipe))).isPresent(), Component.literal("Missing recipe " + recipe));
        }
        // The job-site recipes show up in the recipe book once the player has what they need
        for (String unlock : new String[] {"recipes/decorations/hunters_table", "recipes/decorations/blood_altar", "recipes/misc/bear_trap"}) {
            helper.assertTrue(level.getServer().getAdvancements().get(CrimsonCovenant.id(unlock)) != null, Component.literal("Missing recipe unlock " + unlock));
        }
        husk.discard();
        villager.discard();
        helper.succeed();
    }
}
