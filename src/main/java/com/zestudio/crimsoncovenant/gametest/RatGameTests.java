package com.zestudio.crimsoncovenant.gametest;

import java.util.List;
import java.util.ArrayList;

import com.zestudio.crimsoncovenant.block.RatBurrowBlock;
import com.zestudio.crimsoncovenant.block.RatBurrowBlockEntity;
import com.zestudio.crimsoncovenant.block.RatTrapBlock;
import com.zestudio.crimsoncovenant.effect.MiasmaEffect;
import com.zestudio.crimsoncovenant.effect.PlagueEffect;
import com.zestudio.crimsoncovenant.entity.MiasmaCloud;
import com.zestudio.crimsoncovenant.entity.Rat;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.network.SkillPayloads;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.world.RatSpawner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.animal.cow.Cow;

/// Rats: their eating and breeding, their burrows, the Miasma, the Plague, and the trap
final class RatGameTests {
    private RatGameTests() {}

    private static List<Rat> ratsIn(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(Rat.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0));
    }

    static void ratsEatCropsAndHaveLitters(GameTestHelper helper) {
        BlockPos[] fields = {new BlockPos(2, 1, 2), new BlockPos(6, 1, 2)};
        for (BlockPos field : fields) {
            helper.setBlock(field.below(), Blocks.FARMLAND);
            helper.setBlock(field, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        }
        Rat first = helper.spawn(ModEntities.RAT.get(), new BlockPos(3, 1, 4));
        Rat second = helper.spawn(ModEntities.RAT.get(), new BlockPos(4, 1, 4));
        helper.assertTrue(Rat.eatCrop(first, helper.absolutePos(fields[0])) && Rat.eatCrop(second, helper.absolutePos(fields[1])),
                Component.literal("Both rats should eat a crop"));
        for (BlockPos field : fields) {
            helper.assertTrue(helper.getBlockState(field).isAir(), Component.literal("An eaten crop should be gone"));
        }
        helper.assertTrue(first.isInLove() && second.isInLove(), Component.literal("Rats that have eaten should be ready to breed"));
        helper.succeedWhen(() -> {
            List<Rat> babies = ratsIn(helper).stream().filter(Rat::isBaby).toList();
            helper.assertValueEqual(babies.size(), 2, "young rats in the litter");
            helper.assertTrue(babies.stream().allMatch(baby -> baby.getAge() <= -23000), Component.literal("Young rats should take a day to grow up"));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0)).isEmpty(),
                    Component.literal("Rats breeding shouldn't leave experience behind"));
        });
    }

    static void ratsEatHayAndPumpkins(GameTestHelper helper) {
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 4));
        BlockPos[] stores = {new BlockPos(2, 1, 2), new BlockPos(4, 1, 2), new BlockPos(6, 1, 2)};
        helper.setBlock(stores[0], Blocks.HAY_BLOCK);
        helper.setBlock(stores[1], Blocks.PUMPKIN);
        helper.setBlock(stores[2], Blocks.MELON);
        for (BlockPos store : stores) {
            helper.assertTrue(Rat.eatCrop(rat, helper.absolutePos(store)) && helper.getBlockState(store).isAir(),
                    Component.literal("Rats should eat " + store + " down to nothing"));
        }
        helper.setBlock(stores[0], Blocks.OAK_PLANKS);
        helper.assertTrue(!Rat.eatCrop(rat, helper.absolutePos(stores[0])), Component.literal("Rats don't eat planks"));
        helper.succeed();
    }

    static void ratsEatRottenFlesh(GameTestHelper helper) {
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 4));
        ItemEntity flesh = new ItemEntity(helper.getLevel(), rat.getX(), rat.getY(), rat.getZ(), new ItemStack(Items.ROTTEN_FLESH, 2));
        helper.getLevel().addFreshEntity(flesh);
        Rat.eatDropped(rat, flesh);
        helper.assertValueEqual(flesh.getItem().getCount(), 1, "rotten flesh left after a rat's meal");
        helper.assertTrue(rat.isInLove(), Component.literal("Rotten flesh should get a rat breeding too"));
        flesh.discard();
        helper.succeed();
    }

    static void ratsLiveInBurrowsAndDefendThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos dirt = new BlockPos(4, 1, 4);
        helper.setBlock(dirt, Blocks.DIRT);
        Rat digger = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 3));
        BlockPos burrowPos = helper.absolutePos(dirt);
        Rat.digInto(digger, burrowPos, Direction.NORTH);
        helper.assertTrue(level.getBlockState(burrowPos).is(ModBlocks.RAT_BURROW.get()), Component.literal("The rat should dig a burrow into the earth"));
        RatBurrowBlockEntity burrow = (RatBurrowBlockEntity) level.getBlockEntity(burrowPos);
        helper.assertTrue(digger.isRemoved() && burrow.occupantCount() == 1, Component.literal("The rat should be inside its burrow"));

        Rat neighbour = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 3));
        helper.assertTrue(burrow.tryEnter(neighbour), Component.literal("Another rat should move in"));

        // Break it open, and out they come, angry
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 1.5));
        player.teleportTo(at.x, at.y, at.z);
        BlockState state = level.getBlockState(burrowPos);
        state.getBlock().playerWillDestroy(level, burrowPos, state, player);
        level.removeBlock(burrowPos, false);
        List<Rat> rats = ratsIn(helper);
        helper.assertValueEqual(rats.size(), 2, "rats out of the broken burrow");
        helper.assertTrue(rats.stream().allMatch(Rat::isAngry), Component.literal("Rats whose home was broken should be angry"));
        rats.forEach(Rat::discard);
        SkillGameTests.removePlayer(helper, player);

        // ...and they go for whoever broke it. (Mobs can't target the always-creative mock player, so a villager
        // stands in as the culprit here.)
        helper.setBlock(dirt, Blocks.DIRT);
        Rat lodger = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 3));
        Rat.digInto(lodger, burrowPos, Direction.NORTH);
        Villager culprit = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(2, 1, 2));
        ((RatBurrowBlockEntity) level.getBlockEntity(burrowPos)).releaseAll(culprit);
        List<Rat> released = ratsIn(helper);
        helper.assertTrue(released.size() == 1 && released.getFirst().getTarget() == culprit, Component.literal("The rats should go for whoever broke their burrow"));
        released.forEach(Rat::discard);
        helper.succeed();
    }

    static void burrowSpotsAreFound(GameTestHelper helper) {
        // A bank of earth with open ground in front of it
        BlockPos dirt = new BlockPos(4, 1, 4);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(dirt.above(), Blocks.STONE);
        var spot = Rat.findBurrowSpot(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 2)), 4);
        helper.assertTrue(spot.isPresent() && spot.get().pos().equals(helper.absolutePos(dirt)) && spot.get().face().getAxis().isHorizontal(),
                Component.literal("A rat should find the side of an earth bank to dig into, found " + spot));
        helper.succeed();
    }

    static void miasmaOverAnInfestedVillage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A bell, claimed as a meeting point the way villagers do, makes this a village
        BlockPos bell = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.BELL);
        level.getPoiManager().take(type -> type.is(PoiTypes.MEETING), (type, pos) -> pos.equals(bell), bell, 2);
        List<Rat> rats = new java.util.ArrayList<>();
        for (int i = 0; i <= Rat.MIASMA_THRESHOLD; i++) {
            rats.add(helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(1 + i % 6, 1, 6 + i / 6)));
        }
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        Vampire vampire = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(5, 1, 4));
        vampire.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));

        // Vampires breathe it easily, and so do the rats
        helper.assertTrue(MiasmaEffect.isImmune(vampire) && MiasmaEffect.isImmune(rats.getFirst()) && !MiasmaEffect.isImmune(villager),
                Component.literal("Vampires and rats shrug off the Miasma, villagers don't"));
        helper.assertTrue(!vampire.addEffect(new MobEffectInstance(ModEffects.MIASMA, 100)), Component.literal("The Miasma shouldn't take hold of a vampire"));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 2));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 2));
        helper.assertTrue(MiasmaEffect.isImmune(zombie) && MiasmaEffect.isImmune(golem), Component.literal("The undead and iron golems shrug off the Miasma too"));
        zombie.discard();
        golem.discard();
        // Long exposure blinds: the count of seconds runs on while the breather stays, and starts over once they leave
        long now = level.getGameTime();
        int seconds = 0;
        for (int i = 0; i < MiasmaCloud.BLINDING_SECONDS; i++) {
            seconds = MiasmaCloud.breathe(villager, now + i * 20L);
        }
        helper.assertValueEqual(seconds, MiasmaCloud.BLINDING_SECONDS, "seconds in the Miasma");
        helper.assertValueEqual(MiasmaCloud.breathe(villager, now + 10_000L), 1, "seconds in the Miasma after a long time away");
        villager.setData(ModAttachments.MIASMA_EXPOSURE, ModAttachments.MiasmaExposure.NONE);

        AABB around = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16.0);
        helper.startSequence()
                // The game notices the bell has made a village a moment after it's placed
                .thenWaitUntil(() -> {
                    rats.getFirst().checkForMiasma(level);
                    helper.assertValueEqual(level.getEntitiesOfClass(MiasmaCloud.class, around).size(), 1, "Miasma clouds over a village with more than ten rats (village "
                            + level.isVillage(rats.getFirst().blockPosition()) + ", rats " + Rat.countRatsNear(level, rats.getFirst().position(), Rat.VILLAGE_RANGE) + ")");
                })
                .thenExecute(() -> {
                    rats.getFirst().checkForMiasma(level);
                    helper.assertValueEqual(level.getEntitiesOfClass(MiasmaCloud.class, around).size(), 1,
                            "Miasma clouds once the rats have gathered it twice (it should thicken, not double)");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(villager.hasEffect(ModEffects.MIASMA) && villager.hasEffect(MobEffects.NAUSEA), Component.literal("The villager should be sickened by the Miasma"));
                    helper.assertTrue(!vampire.hasEffect(ModEffects.MIASMA), Component.literal("The vampire should be untouched"));
                    helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), Component.literal("The Miasma should poison the villager"));
                })
                .thenExecute(() -> level.getEntitiesOfClass(MiasmaCloud.class, around).forEach(MiasmaCloud::discard))
                .thenSucceed();
    }

    static void ratTrapsKillRats(GameTestHelper helper) {
        BlockPos ratTrap = new BlockPos(2, 1, 4);
        BlockPos villagerTrap = new BlockPos(6, 1, 4);
        helper.setBlock(ratTrap, ModBlocks.RAT_TRAP.get().defaultBlockState().setValue(RatTrapBlock.BAITED, true));
        helper.setBlock(villagerTrap, ModBlocks.RAT_TRAP.get());
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), ratTrap);
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, villagerTrap);
        helper.succeedWhen(() -> {
            helper.assertTrue(!rat.isAlive(), Component.literal("The trap should kill the rat"));
            helper.assertBlockProperty(ratTrap, RatTrapBlock.SPRUNG, true);
            helper.assertBlockProperty(ratTrap, RatTrapBlock.BAITED, false);
            helper.assertBlockProperty(villagerTrap, RatTrapBlock.SPRUNG, false);
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), Component.literal("Only rats set off a rat trap"));
        });
    }

    static void ratsTakeTheBait(GameTestHelper helper) {
        BlockPos trap = new BlockPos(6, 1, 4);
        helper.setBlock(trap, ModBlocks.RAT_TRAP.get().defaultBlockState().setValue(RatTrapBlock.BAITED, true));
        Rat rat = helper.spawn(ModEntities.RAT.get(), new BlockPos(1, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(!rat.isAlive(), Component.literal("The rat should go for the bait and get caught, but it's at " + helper.relativeVec(rat.position())));
            helper.assertBlockProperty(trap, RatTrapBlock.SPRUNG, true);
        });
    }

    static void catsCatchRats(GameTestHelper helper) {
        Cat cat = helper.spawn(EntityTypes.CAT, new BlockPos(1, 1, 4));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(7, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(!rat.isAlive(), Component.literal("The cat should run the rat down"));
            cat.discard();
        });
    }

    static void ratsChewBeforeTheyEat(GameTestHelper helper) {
        Rat rat = helper.spawn(ModEntities.RAT.get(), new BlockPos(3, 1, 4));
        ItemEntity flesh = new ItemEntity(helper.getLevel(), rat.getX() + 1.0, rat.getY(), rat.getZ(), new ItemStack(Items.ROTTEN_FLESH, 2));
        flesh.setPickUpDelay(32767);
        helper.getLevel().addFreshEntity(flesh);
        helper.startSequence()
                .thenExecuteAfter(15, () -> helper.assertValueEqual(flesh.getItem().getCount(), 2, "rotten flesh left a moment after the rat reached it"))
                .thenWaitUntil(() -> helper.assertValueEqual(flesh.getItem().getCount(), 1, "rotten flesh left once the rat has chewed through a piece"))
                .thenExecute(() -> {
                    flesh.discard();
                    rat.discard();
                })
                .thenSucceed();
    }

    static void youngRatsGrowFasterAndLiveWithTheirParents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Rat baby = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 2));
        baby.setAge(-24000);
        baby.feed();
        helper.assertTrue(baby.getAge() > -24000 + 4000, Component.literal("A young rat that eats should grow up faster, age " + baby.getAge()));

        BlockPos home = helper.absolutePos(new BlockPos(6, 1, 6));
        Rat mother = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 4));
        Rat father = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(5, 1, 4));
        mother.setHomePos(home);
        mother.spawnChildFromBreeding(level, father);
        List<Rat> litter = ratsIn(helper).stream().filter(rat -> rat.isBaby() && rat != baby).toList();
        helper.assertTrue(litter.size() == 2 && litter.stream().allMatch(young -> home.equals(young.getHomePos())),
                Component.literal("The litter should share their parents' burrow"));
        helper.assertTrue(RatBurrowBlockEntity.CAPACITY >= 4, Component.literal("A burrow should have room for a pair and their litter"));
        helper.assertTrue(baby.getPathfindingMalus(PathType.WATER) < 0.0F, Component.literal("Rats should keep out of the water"));
        helper.succeed();
    }

    static void farmAnimalsCryOutAtRats(GameTestHelper helper) {
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 4));
        helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(5, 1, 4));
        helper.spawnWithNoFreeWill(EntityTypes.HORSE, new BlockPos(6, 1, 2));
        helper.assertValueEqual(rat.alarmFarmAnimals(helper.getLevel()), 2, "farm animals raising the alarm at a rat");
        helper.assertValueEqual(rat.alarmFarmAnimals(helper.getLevel()), 0, "farm animals raising the alarm again straight away");
        helper.succeed();
    }

    static void ratsGnawIntoWoodFromTheSide(GameTestHelper helper) {
        // A plank wall with open ground in front of it: a rat gnaws in from the side
        BlockPos wall = new BlockPos(4, 1, 4);
        helper.setBlock(wall, Blocks.OAK_PLANKS);
        helper.setBlock(wall.above(), Blocks.OAK_PLANKS);
        var spot = Rat.findBurrowSpot(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 2)), 4);
        helper.assertTrue(spot.isPresent() && spot.get().pos().equals(helper.absolutePos(wall)) && spot.get().face().getAxis().isHorizontal(),
                Component.literal("A rat should find the side of a wooden wall to gnaw into, found " + spot));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 3));
        Rat.digInto(rat, spot.get().pos(), spot.get().face());
        helper.assertBlockPresent(ModBlocks.RAT_HOLE.get(), wall);

        // A plank floor, open only above, is no good: rats only gnaw into wood from the side
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.OAK_PLANKS);
        var fromAbove = Rat.findBurrowSpot(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), 0);
        helper.assertTrue(fromAbove.isEmpty() || !fromAbove.get().pos().equals(helper.absolutePos(new BlockPos(1, 0, 1))),
                Component.literal("Rats shouldn't gnaw down into a wooden floor"));
        helper.succeed();
    }

    static void miasmaRisesFromBurrows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos burrow = new BlockPos(6, 1, 6);
        helper.setBlock(burrow, ModBlocks.RAT_BURROW.get().defaultBlockState().setValue(RatBurrowBlock.FACING, Direction.WEST));
        Rat homeless = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 2));
        helper.assertTrue(homeless.miasmaSource(level).equals(helper.absolutePos(burrow.west())), Component.literal("The Miasma should rise from the nearest burrow"));
        Rat housed = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(3, 1, 2));
        BlockPos home = new BlockPos(1, 1, 6);
        helper.setBlock(home, ModBlocks.RAT_BURROW.get().defaultBlockState().setValue(RatBurrowBlock.FACING, Direction.EAST));
        housed.setHomePos(helper.absolutePos(home));
        helper.assertTrue(housed.miasmaSource(level).equals(helper.absolutePos(home.east())), Component.literal("A rat's Miasma should rise from its own burrow"));
        helper.succeed();
    }

    static void ratsBoltFromVillagers(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(2, 1, 4));
        Rat rat = helper.spawn(ModEntities.RAT.get(), new BlockPos(5, 1, 4));
        double start = rat.distanceTo(villager);
        helper.succeedWhen(() -> {
            helper.assertTrue(rat.distanceTo(villager) > start + 3.0, Component.literal("The rat should run from the villager, now " + rat.distanceTo(villager) + " away"));
            rat.discard();
        });
    }

    static void villagersKilledByRatsRiseAsZombies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 4));
        villager.hurtServer(level, level.damageSources().mobAttack(rat), 1000.0F);
        List<ZombieVillager> risen = level.getEntitiesOfClass(ZombieVillager.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0));
        helper.assertTrue(villager.isRemoved() && risen.size() == 1, Component.literal("A villager killed by a rat should rise as a zombie villager"));
        risen.forEach(ZombieVillager::discard);
        rat.discard();
        helper.succeed();
    }

    static void ratsHideAtHomeFromDanger(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos burrow = new BlockPos(6, 1, 4);
        helper.setBlock(burrow, ModBlocks.RAT_BURROW.get().defaultBlockState().setValue(RatBurrowBlock.FACING, Direction.WEST));
        Rat rat = helper.spawn(ModEntities.RAT.get(), new BlockPos(3, 1, 4));
        rat.setHomePos(helper.absolutePos(burrow));
        helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(1, 1, 4));
        helper.succeedWhen(() -> helper.assertTrue(rat.isRemoved() && level.getBlockEntity(helper.absolutePos(burrow)) instanceof RatBurrowBlockEntity home
                && home.occupantCount() == 1, Component.literal("A frightened rat with a burrow should hide in it")));
    }

    static void ratSwarmsHuntLivestock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(6, 1, 6));
        List<Rat> rats = new ArrayList<>();
        for (int i = 0; i < Rat.SWARM_SIZE - 1; i++) {
            rats.add(helper.spawn(ModEntities.RAT.get(), new BlockPos(1 + i % 3, 1, 1 + i / 3)));
        }
        helper.assertTrue(rats.getFirst().raiseSwarm() == null, Component.literal("Nine rats aren't a swarm yet"));
        rats.add(helper.spawn(ModEntities.RAT.get(), new BlockPos(1, 1, 4)));
        helper.assertTrue(rats.getFirst().raiseSwarm() == cow, Component.literal("Ten rats are a swarm, and should go for the cow"));
        helper.assertTrue(rats.stream().allMatch(rat -> rat.getTarget() == cow), Component.literal("The whole swarm should go for the cow at once"));
        helper.succeedWhen(() -> {
            helper.assertTrue(!cow.isAlive(), Component.literal("The swarm should bring the cow down"));
            helper.assertTrue(rats.stream().filter(Rat::isAlive).anyMatch(Rat::isSated), Component.literal("The swarm should feast on its kill"));
            List<ItemEntity> meat = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16.0), item -> item.getItem().is(Items.BEEF));
            helper.assertTrue(meat.isEmpty(), Component.literal("Nothing of the cow's meat should be left"));
            rats.forEach(Rat::discard);
        });
    }

    static void ratsEatAnyDroppedFood(GameTestHelper helper) {
        for (ItemStack food : List.of(new ItemStack(Items.BREAD), new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.APPLE), new ItemStack(Items.CAKE),
                new ItemStack(Items.PUMPKIN_PIE), new ItemStack(Items.WHEAT))) {
            helper.assertTrue(Rat.isScavenge(food), Component.literal("Rats should eat dropped " + food.getHoverName().getString()));
        }
        helper.assertTrue(!Rat.isScavenge(new ItemStack(Items.STONE)), Component.literal("Rats shouldn't eat stone"));
        Rat rat = helper.spawn(ModEntities.RAT.get(), new BlockPos(2, 1, 4));
        ItemEntity cake = new ItemEntity(helper.getLevel(), rat.getX() + 3.0, rat.getY(), rat.getZ(), new ItemStack(Items.CAKE));
        cake.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(cake);
        helper.succeedWhen(() -> {
            helper.assertTrue(cake.isRemoved(), Component.literal("A rat should go and eat a dropped cake"));
            rat.discard();
        });
    }

    static void ratsAreWorthALittleExperience(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 2));
        Rat young = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(4, 1, 2));
        young.setBaby(true);
        int grown = rat.getExperienceReward(level, null);
        helper.assertTrue(grown >= 1 && grown <= 3, Component.literal("A grown rat should be worth a little experience, not " + grown));
        helper.assertTrue(young.shouldDropExperience() && young.getExperienceReward(level, null) == 1, Component.literal("Even a young rat should be worth a bit"));
        rat.discard();
        young.discard();
        helper.succeed();
    }

    static void ratsTurnUpInVillages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spot = helper.absolutePos(new BlockPos(5, 1, 5));
        helper.assertValueEqual(RatSpawner.trySpawnIn(level, spot, level.getRandom()), 0, "rats turning up away from any village");
        // A bell, claimed as a meeting point the way villagers do, makes this a village
        BlockPos bell = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.BELL);
        level.getPoiManager().take(type -> type.is(PoiTypes.MEETING), (type, pos) -> pos.equals(bell), bell, 2);
        int came = RatSpawner.trySpawnIn(level, spot, level.getRandom());
        helper.assertTrue(came >= 1 && came <= 2, Component.literal("A rat or two should turn up in a village at night, not " + came));
        level.getEntitiesOfClass(Rat.class, new AABB(spot).inflate(4.0)).forEach(Rat::discard);
        level.getPoiManager().release(bell);
        helper.succeed();
    }

    static void ironGolemsHuntRats(GameTestHelper helper) {
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 4));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(6, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(golem.getTarget() == rat, Component.literal("The iron golem should go after the rat"));
            rat.discard();
            golem.discard();
        });
    }

    static void plagueSapsTheLivingButNotTheUndead(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Rat rat = helper.spawnWithNoFreeWill(ModEntities.RAT.get(), new BlockPos(2, 1, 4));
        helper.assertTrue(villager.addEffect(new MobEffectInstance(ModEffects.PLAGUE, 400)), Component.literal("Villagers can catch the Plague"));
        helper.assertTrue(!zombie.addEffect(new MobEffectInstance(ModEffects.PLAGUE, 400)) && PlagueEffect.isImmune(rat),
                Component.literal("The undead, and the rats that carry it, don't catch the Plague"));
        helper.succeedWhen(() -> helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), Component.literal("The Plague should sap the villager")));
    }

    static void onlyVampiresOpenTheSkills(GameTestHelper helper) {
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        helper.assertTrue(!SkillPayloads.tryOpenSkillsScreen(player) && player.containerMenu == player.inventoryMenu,
                Component.literal("Someone who isn't a vampire shouldn't get the blood skills menu"));
        player.setData(ModAttachments.IS_VAMPIRE, true);
        helper.assertTrue(SkillPayloads.tryOpenSkillsScreen(player) && player.containerMenu != player.inventoryMenu,
                Component.literal("A vampire should get the blood skills menu"));
        player.closeContainer();
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }
}
