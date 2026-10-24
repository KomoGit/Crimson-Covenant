package com.zestudio.crimsoncovenant.gametest;

import com.zestudio.crimsoncovenant.block.RatBurrowBlock;
import com.zestudio.crimsoncovenant.block.RatTrapBlock;
import com.zestudio.crimsoncovenant.effect.CleanseEffect;
import com.zestudio.crimsoncovenant.effect.MiasmaEffect;
import com.zestudio.crimsoncovenant.effect.PlagueEffect;
import com.zestudio.crimsoncovenant.entity.MiasmaCloud;
import com.zestudio.crimsoncovenant.entity.PlagueDoctor;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.registry.ModVillagers;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.zestudio.crimsoncovenant.block.GarlicBraidBlock;
import com.zestudio.crimsoncovenant.mixin.StructureTemplatePoolAccessor;
import com.zestudio.crimsoncovenant.world.DoctorHouses;
import com.zestudio.crimsoncovenant.world.GarlicDoors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/// The Plague Doctor, and his Potion of Cleanse
final class PlagueDoctorGameTests {
    private PlagueDoctorGameTests() {}

    private static PlagueDoctor spawnDoctor(GameTestHelper helper, BlockPos pos) {
        return helper.spawnWithNoFreeWill(ModEntities.PLAGUE_DOCTOR.get(), pos);
    }

    static void cleanseCuresTheSick(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        villager.addEffect(new MobEffectInstance(ModEffects.PLAGUE, 600));
        villager.addEffect(new MobEffectInstance(ModEffects.MIASMA, 600));
        villager.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 600));
        CleanseEffect.cleanse(villager);
        helper.assertTrue(!villager.hasEffect(ModEffects.PLAGUE) && !villager.hasEffect(ModEffects.MIASMA) && !villager.hasEffect(MobEffects.NAUSEA),
                Component.literal("Cleanse should cure the Plague and the Miasma"));
        helper.succeed();
    }

    static void thrownCleanseClearsTheMiasma(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(4, 1, 4));
        MiasmaCloud cloud = MiasmaCloud.gatherOver(level, ground);
        helper.assertTrue(cloud != null, Component.literal("The Miasma should gather"));
        ItemStack cleanse = PotionContents.createItemStack(Items.SPLASH_POTION, ModPotions.CLEANSE);
        ThrownSplashPotion potion = new ThrownSplashPotion(level, ground.getX() + 0.5, ground.getY() + 3.0, ground.getZ() + 0.5, cleanse);
        potion.setDeltaMovement(0.0, -0.5, 0.0);
        level.addFreshEntity(potion);
        helper.succeedWhen(() -> {
            helper.assertTrue(cloud.isRemoved(), Component.literal("A thrown Cleanse should clear the Miasma"));
            helper.assertTrue(MiasmaCloud.gatherOver(level, ground) == null, Component.literal("The Miasma shouldn't gather again where Cleanse has burst"));
            MiasmaCloud.forgetWards(level);
        });
    }

    static void doctorCuresForEmeralds(GameTestHelper helper) {
        PlagueDoctor doctor = spawnDoctor(helper, new BlockPos(4, 1, 4));
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 2.5));
        player.teleportTo(at.x, at.y, at.z);
        player.addEffect(new MobEffectInstance(ModEffects.PLAGUE, 600));
        player.setData(ModAttachments.INFECTION_TICKS, 1000);
        int price = PlagueDoctor.CURE_PLAGUE + PlagueDoctor.CURE_CURSE;
        helper.assertValueEqual(PlagueDoctor.cureCost(player), price, "the price of curing the Plague and Sanguine Vampiris");

        // Not enough
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, price - 1));
        player.interactOn(doctor, InteractionHand.MAIN_HAND, doctor.position());
        helper.assertTrue(player.hasEffect(ModEffects.PLAGUE) && Vampirism.isInfected(player), Component.literal("No cure without the full price"));

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, price + 2));
        player.interactOn(doctor, InteractionHand.MAIN_HAND, doctor.position());
        helper.assertTrue(!player.hasEffect(ModEffects.PLAGUE) && !Vampirism.isInfected(player), Component.literal("The doctor should cure the Plague and the curse"));
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "emeralds left after the cure");

        // A vampire is past help
        player.setData(ModAttachments.IS_VAMPIRE, true);
        helper.assertTrue(!doctor.treat(player), Component.literal("There is no cure for a vampire"));
        SkillGameTests.removePlayer(helper, player);
        helper.succeed();
    }

    static void doctorSellsCleanse(GameTestHelper helper) {
        PlagueDoctor doctor = spawnDoctor(helper, new BlockPos(4, 1, 4));
        boolean sellsCleanse = doctor.getOffers().stream().anyMatch(offer -> offer.getCostA().is(Items.EMERALD)
                && offer.getResult().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(ModPotions.CLEANSE));
        helper.assertTrue(sellsCleanse, Component.literal("The doctor should sell Potions of Cleanse for emeralds"));
        helper.assertTrue(MiasmaEffect.isImmune(doctor) && PlagueEffect.isImmune(doctor), Component.literal("The doctor's mask keeps out the Miasma and the Plague"));
        helper.succeed();
    }

    static void doctorTrapsBurrows(GameTestHelper helper) {
        BlockPos burrow = new BlockPos(5, 1, 5);
        helper.setBlock(burrow, ModBlocks.RAT_BURROW.get().defaultBlockState().setValue(RatBurrowBlock.FACING, Direction.NORTH));
        BlockPos doorstep = burrow.north();
        PlagueDoctor doctor = helper.spawn(ModEntities.PLAGUE_DOCTOR.get(), new BlockPos(1, 1, 1));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModBlocks.RAT_TRAP.get(), doorstep);
            helper.assertBlockProperty(doorstep, RatTrapBlock.BAITED, true);
            doctor.discard();
        });
    }

    static void doctorWardsOffUndeadWithSilver(GameTestHelper helper) {
        PlagueDoctor doctor = helper.spawn(ModEntities.PLAGUE_DOCTOR.get(), new BlockPos(2, 1, 4));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 4));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setTarget(doctor);
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.hasEffect(ModEffects.SILVER_DREAD), Component.literal("The doctor should throw silver at the zombie coming for him"));
            doctor.discard();
            zombie.discard();
        });
    }

    static void doctorVillagersCureToo(GameTestHelper helper) {
        Villager doctor = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        doctor.setVillagerData(doctor.getVillagerData().withProfession(ModVillagers.PLAGUE_DOCTOR).withLevel(1));
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 2.5));
        player.teleportTo(at.x, at.y, at.z);
        player.addEffect(new MobEffectInstance(ModEffects.PLAGUE, 600));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, PlagueDoctor.CURE_PLAGUE));
        player.interactOn(doctor, InteractionHand.MAIN_HAND, doctor.position());
        helper.assertTrue(!player.hasEffect(ModEffects.PLAGUE) && player.getMainHandItem().isEmpty(), Component.literal("A Plague Doctor villager should cure for emeralds"));
        helper.assertTrue(doctor.getTradingPlayer() == null, Component.literal("Curing shouldn't open his trades"));
        helper.assertTrue(spawnDoctor(helper, new BlockPos(1, 1, 1)).is(ModTags.Entities.VAMPIRE_PREY), Component.literal("Vampires should hunt Plague Doctors"));
        SkillGameTests.removePlayer(helper, player);
        doctor.discard();
        helper.succeed();
    }

    static void villagesBuildDoctorsHouses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pools = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        for (String village : DoctorHouses.VILLAGES) {
            StructureTemplatePool houses = pools.getValue(Identifier.withDefaultNamespace("village/" + village + "/houses"));
            helper.assertTrue(houses != null && ((StructureTemplatePoolAccessor) houses).crimsoncovenant$templates().stream()
                    .anyMatch(element -> element.toString().contains(DoctorHouses.houseFor(village))), Component.literal("A " + village + " village should be able to build a Plague Doctor's house"));
            StructureTemplate house = level.getServer().getStructureTemplateManager().get(Identifier.parse(DoctorHouses.houseFor(village))).orElse(null);
            helper.assertTrue(house != null, Component.literal("The " + village + " Plague Doctor's house should exist"));
            StructurePlaceSettings settings = new StructurePlaceSettings();
            helper.assertValueEqual(house.filterBlocks(BlockPos.ZERO, settings, ModBlocks.HUNTERS_TABLE.get()).size(), 1, "Hunter's Tables in the " + village + " house");
            helper.assertValueEqual(house.filterBlocks(BlockPos.ZERO, settings, ModBlocks.GARLIC_BRAID.get()).size(), 1, "garlic braids over the " + village + " house's door");
            boolean joinsTheStreet = house.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW).stream()
                    .anyMatch(info -> info.nbt() != null && info.nbt().getStringOr("name", "").equals("minecraft:building_entrance"));
            helper.assertTrue(joinsTheStreet, Component.literal("The " + village + " house should join onto a street"));
        }
        helper.succeed();
    }

    // A wall with a door in it, facing south, at x 2-6, z 4
    private static BlockPos buildDoorway(GameTestHelper helper) {
        for (int x = 2; x <= 6; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE_BRICKS);
            }
        }
        BlockPos door = new BlockPos(4, 1, 4);
        BlockState oak = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        helper.setBlock(door.above(), oak.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(door, oak.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        return door;
    }

    static void doctorVillagersHangGarlicOverDoors(GameTestHelper helper) {
        BlockPos door = buildDoorway(helper);
        BlockPos spot = new BlockPos(4, 3, 5);
        Villager doctor = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 7));
        doctor.setVillagerData(doctor.getVillagerData().withProfession(ModVillagers.PLAGUE_DOCTOR).withLevel(1));
        helper.assertTrue(GarlicDoors.tendDoors(doctor), Component.literal("A Plague Doctor villager should hang garlic over a bare door he passes"));
        helper.assertBlockPresent(ModBlocks.GARLIC_BRAID.get(), spot);
        helper.assertBlockProperty(spot, GarlicBraidBlock.FACING, Direction.SOUTH);
        helper.assertTrue(!GarlicDoors.tendDoors(doctor), Component.literal("Once is enough: no second braid on the same door"));
        helper.assertTrue(helper.getBlockState(door).is(Blocks.OAK_DOOR), Component.literal("The door should still be there"));
        doctor.discard();
        helper.succeed();
    }

    static void wanderingDoctorHangsGarlicOverDoors(GameTestHelper helper) {
        buildDoorway(helper);
        PlagueDoctor doctor = helper.spawn(ModEntities.PLAGUE_DOCTOR.get(), new BlockPos(1, 1, 8));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModBlocks.GARLIC_BRAID.get(), new BlockPos(4, 3, 5));
            doctor.discard();
        });
    }

    static void doctorAnswersTheCall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos village = helper.absolutePos(new BlockPos(4, 1, 4));
        // Doctors left behind by earlier tests would count as one already come
        level.getEntitiesOfClass(PlagueDoctor.class, new AABB(village).inflate(160.0)).forEach(PlagueDoctor::discard);
        PlagueDoctor doctor = PlagueDoctor.answerTheCall(level, village);
        helper.assertTrue(doctor != null, Component.literal("A Plague Doctor should come when the Miasma settles"));
        helper.assertTrue(PlagueDoctor.answerTheCall(level, village) == null, Component.literal("Only one doctor comes"));
        level.getEntitiesOfClass(PlagueDoctor.class, new AABB(village).inflate(64.0)).forEach(PlagueDoctor::discard);
        helper.succeed();
    }
}
