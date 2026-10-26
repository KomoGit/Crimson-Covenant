package com.zestudio.crimsoncovenant.gametest;

import java.util.List;

import com.zestudio.crimsoncovenant.block.BloodThroneBlock;
import com.zestudio.crimsoncovenant.block.CoffinBlock;
import com.zestudio.crimsoncovenant.entity.ThroneSeat;
import com.zestudio.crimsoncovenant.entity.PlagueDoctor;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.entity.Vampire;
import com.zestudio.crimsoncovenant.entity.Werewolf;
import com.zestudio.crimsoncovenant.entity.Hunter;
import com.zestudio.crimsoncovenant.block.IronMaidenBlock;
import com.zestudio.crimsoncovenant.block.IronMaidenBlockEntity;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModDataComponents;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.vampirism.BloodPool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.ClockAdjustment;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/// Coffins, and the Iron Maidens kept around them
final class CoffinGameTests {
    private CoffinGameTests() {}

    // A two-part bed, head to the north of the foot
    private static BlockPos placeBed(GameTestHelper helper, Block bed, BlockPos foot) {
        BlockState state = bed.defaultBlockState().setValue(AbstractBedBlock.FACING, Direction.NORTH);
        helper.setBlock(foot, state.setValue(AbstractBedBlock.PART, BedPart.FOOT));
        helper.setBlock(foot.north(), state.setValue(AbstractBedBlock.PART, BedPart.HEAD));
        return foot.north();
    }

    private static boolean sleepIn(GameTestHelper helper, ServerPlayer player, BlockPos head) {
        BlockPos pos = helper.absolutePos(head);
        BlockState state = helper.getLevel().getBlockState(pos);
        AbstractBedBlock bed = (AbstractBedBlock) state.getBlock();
        return player.startSleepInBed(bed, state, bed.getBedRule(helper.getLevel(), pos), pos).right().isPresent();
    }

    private static void standBeside(GameTestHelper helper, ServerPlayer player, BlockPos head) {
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(head.east()));
        player.teleportTo(at.x, at.y, at.z);
    }

    static void coffinsLetYouSleepByDay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerClockManager clocks = level.getServer().clockManager();
        Holder<WorldClock> overworld = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
        long original = level.getOverworldClockTime();
        try {
            clocks.setTotalTicks(overworld, 6000L);
            helper.assertTrue(!level.isDarkOutside(), Component.literal("It should be noon for this test"));
            BlockPos bed = placeBed(helper, Blocks.BED.pick(DyeColor.RED), new BlockPos(2, 1, 4));
            BlockPos coffin = placeBed(helper, ModBlocks.COFFIN.get(), new BlockPos(6, 1, 4));
            ServerPlayer player = SkillGameTests.makePlayer(helper);
            standBeside(helper, player, bed);
            helper.assertTrue(!sleepIn(helper, player, bed), Component.literal("Nobody sleeps in a bed at noon"));
            standBeside(helper, player, coffin);
            helper.assertTrue(sleepIn(helper, player, coffin) && player.isSleeping(), Component.literal("A coffin should let you sleep at noon"));
            helper.assertTrue(CoffinBlock.isInCoffin(player), Component.literal("The sleeper should count as lying in a coffin"));
            player.stopSleepInBed(true, true);

            // Sleeping the day away wakes you at nightfall
            SleepFinishedTimeEvent event = NeoForge.EVENT_BUS.post(new SleepFinishedTimeEvent(level, new ClockAdjustment.Marker(ClockTimeMarkers.WAKE_UP_FROM_SLEEP)));
            helper.assertTrue(event.getAdjustment() instanceof ClockAdjustment.Marker marker && marker.marker() == ClockTimeMarkers.NIGHT,
                    Component.literal("A day spent asleep should end at nightfall, not the next morning"));
            SkillGameTests.removePlayer(helper, player);
        } finally {
            clocks.setTotalTicks(overworld, original);
        }
        helper.succeed();
    }

    static void vampiresSleepOnlyInCoffins(GameTestHelper helper) {
        BlockPos bed = placeBed(helper, Blocks.BED.pick(DyeColor.RED), new BlockPos(2, 1, 4));
        BlockPos coffin = placeBed(helper, ModBlocks.COFFIN.get(), new BlockPos(6, 1, 4));
        ServerPlayer vampire = SkillGameTests.makeVampire(helper, 50);
        helper.assertTrue(CoffinBlock.barsVampires(helper.getBlockState(bed)) && !CoffinBlock.barsVampires(helper.getBlockState(coffin)),
                Component.literal("Beds, but not coffins, should be barred to vampires"));
        standBeside(helper, vampire, bed);
        helper.assertTrue(!sleepIn(helper, vampire, bed), Component.literal("A vampire shouldn't be able to sleep in a bed"));
        standBeside(helper, vampire, coffin);
        helper.assertTrue(sleepIn(helper, vampire, coffin), Component.literal("A vampire should be able to sleep in a coffin"));
        vampire.stopSleepInBed(true, true);
        SkillGameTests.removePlayer(helper, vampire);
        helper.succeed();
    }

    static void kinDontKeepVampiresAwake(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos coffin = helper.absolutePos(placeBed(helper, ModBlocks.COFFIN.get(), new BlockPos(4, 1, 4)));
        ServerPlayer vampire = SkillGameTests.makeVampire(helper, 50);
        Vampire kin = helper.spawnWithNoFreeWill(ModEntities.VAMPIRE.get(), new BlockPos(1, 1, 1));
        helper.assertTrue(!CoffinBlock.isThreatened(level, vampire, coffin), Component.literal("A vampire about shouldn't keep a vampire from resting"));
        Hunter hunter = helper.spawnWithNoFreeWill(ModEntities.HUNTER.get(), new BlockPos(7, 1, 1));
        helper.assertTrue(CoffinBlock.isThreatened(level, vampire, coffin), Component.literal("A hunter nearby should keep a vampire awake"));
        hunter.discard();
        Werewolf werewolf = helper.spawnWithNoFreeWill(ModEntities.WEREWOLF.get(), new BlockPos(7, 1, 7));
        helper.assertTrue(CoffinBlock.isThreatened(level, vampire, coffin), Component.literal("A werewolf nearby should keep a vampire awake"));
        werewolf.discard();
        kin.discard();
        SkillGameTests.removePlayer(helper, vampire);
        helper.succeed();
    }

    private static IronMaidenBlockEntity placeMaiden(GameTestHelper helper, BlockPos pos) {
        BlockState lower = ModBlocks.IRON_MAIDEN.get().defaultBlockState().setValue(IronMaidenBlock.FACING, Direction.SOUTH);
        helper.setBlock(pos, lower);
        helper.setBlock(pos.above(), lower.setValue(IronMaidenBlock.HALF, DoubleBlockHalf.UPPER));
        return (IronMaidenBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    static void ironMaidensTakeVillagersCaptive(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER).withLevel(3));
        villager.setVillagerXp(77);
        int trades = villager.getOffers().size();
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 2.5));
        player.teleportTo(at.x, at.y, at.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_MAIDEN.get()));
        player.interactOn(villager, InteractionHand.MAIN_HAND, villager.position());
        ItemStack full = player.getMainHandItem();
        helper.assertTrue(villager.isRemoved() && full.has(ModDataComponents.CAPTIVE.get()), Component.literal("An Iron Maiden used on a villager should take them captive"));
        SkillGameTests.removePlayer(helper, player);

        // Set down, it keeps them, and shows it
        BlockPos pos = new BlockPos(2, 1, 2);
        IronMaidenBlockEntity maiden = placeMaiden(helper, pos);
        maiden.setCaptive(full.get(ModDataComponents.CAPTIVE.get()));
        helper.assertBlockProperty(pos, IronMaidenBlock.OCCUPIED, true);
        helper.assertBlockProperty(pos.above(), IronMaidenBlock.OCCUPIED, true);
        helper.assertValueEqual(maiden.getCaptive().blood(), IronMaidenBlockEntity.BLOOD_IN_A_CAPTIVE, "blood in a fresh captive");

        // And lets them go again, as they were
        Villager freed = (Villager) maiden.release();
        helper.assertTrue(freed != null && freed.isAlive() && freed.getVillagerData().profession().is(VillagerProfession.FARMER),
                Component.literal("The captive should come out alive, still a farmer"));
        helper.assertValueEqual(freed.getVillagerData().level(), 3, "the freed villager's level");
        helper.assertValueEqual(freed.getVillagerXp(), 77, "the freed villager's experience");
        helper.assertValueEqual(freed.getOffers().size(), trades, "the freed villager's trades");
        helper.assertBlockProperty(pos, IronMaidenBlock.OCCUPIED, false);
        freed.discard();

        // Broken, a maiden sets her captive free, and comes away empty
        maiden.setCaptive(full.get(ModDataComponents.CAPTIVE.get()));
        helper.assertTrue(Block.getDrops(helper.getBlockState(pos), level, helper.absolutePos(pos), maiden).stream()
                        .noneMatch(stack -> stack.has(ModDataComponents.CAPTIVE.get())),
                Component.literal("A broken maiden shouldn't take her captive with her"));
        level.destroyBlock(helper.absolutePos(pos), true);
        List<Villager> walkedOut = level.getEntitiesOfClass(Villager.class, new AABB(helper.absolutePos(pos)).inflate(3.0), Villager::isAlive);
        helper.assertTrue(walkedOut.size() == 1 && walkedOut.getFirst().getVillagerData().level() == 3,
                Component.literal("Broken free, the villager should still be a level 3 farmer"));
        helper.assertBlockNotPresent(ModBlocks.IRON_MAIDEN.get(), pos);
        helper.assertBlockNotPresent(ModBlocks.IRON_MAIDEN.get(), pos.above());
        walkedOut.forEach(Villager::discard);
        helper.succeed();
    }

    static void witchesAndPlagueDoctorsCanBeTakenToo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.teleportTo(at.x, at.y, at.z);
        Witch witch = helper.spawnWithNoFreeWill(EntityTypes.WITCH, new BlockPos(2, 1, 4));
        PlagueDoctor doctor = helper.spawnWithNoFreeWill(ModEntities.PLAGUE_DOCTOR.get(), new BlockPos(6, 1, 4));
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(4, 1, 6));
        for (Mob captive : List.of(witch, doctor)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_MAIDEN.get()));
            player.interactOn(captive, InteractionHand.MAIN_HAND, captive.position());
            IronMaidenBlockEntity.Captive taken = player.getMainHandItem().get(ModDataComponents.CAPTIVE.get());
            helper.assertTrue(captive.isRemoved() && taken != null && taken.creature().type() == captive.getType(),
                    Component.literal("An Iron Maiden should take a " + captive.getType().getDescription().getString() + " captive"));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_MAIDEN.get()));
        player.interactOn(cow, InteractionHand.MAIN_HAND, cow.position());
        helper.assertTrue(!cow.isRemoved() && !player.getMainHandItem().has(ModDataComponents.CAPTIVE.get()), Component.literal("A cow shouldn't fit in an Iron Maiden"));
        SkillGameTests.removePlayer(helper, player);
        cow.discard();

        // A captive witch comes back out a witch
        Witch other = helper.spawnWithNoFreeWill(EntityTypes.WITCH, new BlockPos(6, 1, 6));
        IronMaidenBlockEntity maiden = placeMaiden(helper, new BlockPos(1, 1, 1));
        maiden.setCaptive(IronMaidenBlockEntity.Captive.of(other));
        other.discard();
        helper.assertTrue(maiden.release() instanceof Witch freed && freed.isAlive(), Component.literal("A freed witch should come out a witch"));
        level.getEntitiesOfClass(Witch.class, new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0)).forEach(Witch::discard);
        helper.succeed();
    }

    static void captivesRunDry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos coffin = placeBed(helper, ModBlocks.COFFIN.get(), new BlockPos(4, 1, 4));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 7));
        IronMaidenBlockEntity.Captive fresh = IronMaidenBlockEntity.Captive.of(villager);
        villager.discard();
        BlockPos pos = new BlockPos(1, 1, 1);
        IronMaidenBlockEntity maiden = placeMaiden(helper, pos);
        // Nearly bled dry already: there's only so much left to take
        maiden.setCaptive(fresh.withBlood(10));
        ServerPlayer vampire = SkillGameTests.makeVampire(helper, 0);
        IronMaidenBlock.feedOnCaptives(vampire, helper.absolutePos(coffin));
        helper.assertValueEqual(BloodPool.get(vampire), 10, "blood drunk from a captive with 10 left");
        helper.assertTrue(!maiden.hasCaptive(), Component.literal("Bled dry, there should be nothing left of the captive"));
        helper.assertBlockProperty(pos, IronMaidenBlock.OCCUPIED, false);

        // Sometimes the dead rise again, still knowing their trade
        Villager smith = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        smith.setVillagerData(smith.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.TOOLSMITH).withLevel(2));
        maiden.setCaptive(IronMaidenBlockEntity.Captive.of(smith));
        smith.discard();
        ZombieVillager risen = maiden.perish(true);
        helper.assertTrue(risen != null && risen.getVillagerData().profession().is(VillagerProfession.TOOLSMITH) && risen.getVillagerData().level() == 2,
                Component.literal("A captive who rises should be a zombie villager, still a level 2 toolsmith"));
        helper.assertTrue(!maiden.hasCaptive(), Component.literal("The risen captive should have left the maiden"));
        risen.discard();

        // A fresh one gives its share, and has that much less left
        maiden.setCaptive(fresh);
        IronMaidenBlock.feedOnCaptives(vampire, helper.absolutePos(coffin));
        helper.assertValueEqual(maiden.getCaptive().blood(), IronMaidenBlockEntity.BLOOD_IN_A_CAPTIVE - IronMaidenBlock.BLOOD_PER_CAPTIVE, "blood left after a night's feeding");

        // Only what's wanted is taken: a vampire nearly full takes just enough to top up...
        BloodPool.set(vampire, BloodPool.max(vampire) - 5);
        int before = maiden.getCaptive().blood();
        IronMaidenBlock.feedOnCaptives(vampire, helper.absolutePos(coffin));
        helper.assertValueEqual(before - maiden.getCaptive().blood(), 5, "blood taken by a vampire 5 short of full");
        // ...and one wanting for nothing leaves the captive be
        before = maiden.getCaptive().blood();
        helper.assertValueEqual(IronMaidenBlock.feedOnCaptives(vampire, helper.absolutePos(coffin)), 0, "captives drawn on by a sated vampire");
        helper.assertValueEqual(maiden.getCaptive().blood(), before, "blood left in a captive after a sated vampire's sleep");
        SkillGameTests.removePlayer(helper, vampire);
        helper.succeed();
    }

    static void vampiresFeedOnCaptivesInTheirSleep(GameTestHelper helper) {
        BlockPos coffin = placeBed(helper, ModBlocks.COFFIN.get(), new BlockPos(4, 1, 4));
        for (BlockPos pos : new BlockPos[] {new BlockPos(1, 1, 1), new BlockPos(7, 1, 1)}) {
            Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 7));
            placeMaiden(helper, pos).setCaptive(IronMaidenBlockEntity.Captive.of(villager));
            villager.discard();
        }
        helper.assertTrue(IronMaidenBlockEntity.findDrinker(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))) != null,
                Component.literal("A maiden should find the coffin it bleeds into"));
        ServerPlayer vampire = SkillGameTests.makeVampire(helper, 10);
        vampire.setHealth(4.0F);
        helper.assertValueEqual(IronMaidenBlock.feedOnCaptives(vampire, helper.absolutePos(coffin)), 2, "captives fed on");
        helper.assertValueEqual(BloodPool.get(vampire), 10 + 2 * IronMaidenBlock.BLOOD_PER_CAPTIVE, "blood after a sleep among captives");
        helper.assertValueEqual(vampire.getHealth(), 4.0F + 2 * IronMaidenBlock.HEALTH_PER_CAPTIVE, "health after a sleep among captives");

        // A Blood Throne among them siphons them too, while you sit
        BlockPos throne = new BlockPos(4, 1, 1);
        helper.setBlock(throne, ModBlocks.BLOOD_THRONE.get());
        ThroneSeat seat = BloodThroneBlock.sit(helper.getLevel(), helper.absolutePos(throne), vampire);
        helper.assertTrue(seat != null && vampire.getVehicle() == seat, Component.literal("Using a Blood Throne should seat you on it"));
        ServerPlayer other = SkillGameTests.makePlayer(helper);
        helper.assertTrue(BloodThroneBlock.sit(helper.getLevel(), helper.absolutePos(throne), other) == null, Component.literal("Only one can sit on a throne at a time"));
        SkillGameTests.removePlayer(helper, other);
        vampire.setHealth(4.0F);
        int blood = BloodPool.get(vampire);
        helper.assertValueEqual(ThroneSeat.siphon(vampire, helper.absolutePos(throne)), 2, "captives siphoned by the throne");
        helper.assertValueEqual(BloodPool.get(vampire), blood + 2 * ThroneSeat.BLOOD_PER_CAPTIVE, "blood after a siphon on the throne");
        helper.assertTrue(vampire.getHealth() > 4.0F, Component.literal("Sitting on the throne should heal you"));
        vampire.stopRiding();
        seat.discard();
        SkillGameTests.removePlayer(helper, vampire);
        helper.succeed();
    }
}
