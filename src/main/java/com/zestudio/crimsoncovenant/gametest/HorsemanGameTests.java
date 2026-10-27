package com.zestudio.crimsoncovenant.gametest;

import java.util.List;

import com.zestudio.crimsoncovenant.block.HeadOnAPikeBlockEntity;
import com.zestudio.crimsoncovenant.entity.GhostSteed;
import com.zestudio.crimsoncovenant.entity.HalloweenSpirit;
import com.zestudio.crimsoncovenant.entity.HeadlessHorseman;
import com.zestudio.crimsoncovenant.entity.HorsemansSteed;
import com.zestudio.crimsoncovenant.entity.PumpkinBomb;
import com.zestudio.crimsoncovenant.entity.SpectralHorseman;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModEffects;
import com.zestudio.crimsoncovenant.registry.ModEntities;
import com.zestudio.crimsoncovenant.registry.ModItems;
import com.zestudio.crimsoncovenant.registry.ModPotions;
import com.zestudio.crimsoncovenant.skill.NightOfTerror;
import com.zestudio.crimsoncovenant.world.CrookedTreePiece;
import com.zestudio.crimsoncovenant.world.HorsemanSummoning;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// The Headless Horseman's fight, his spirit, his head on a pike, and the Crooked Tree where he's called up
final class HorsemanGameTests {
    private HorsemanGameTests() {}

    /// Takes these away, with whatever they're riding, so they can't ride off into other tests
    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity == null) {
                continue;
            }
            if (entity.getVehicle() != null) {
                entity.getVehicle().discard();
            }
            entity.discard();
        }
    }

    /// Calls up the Horseman of a Crooked Tree whose foot is at `tree`, at `at` (both relative), held still
    private static HeadlessHorseman callUp(GameTestHelper helper, BlockPos tree, Vec3 at) {
        helper.setBlock(tree, ModBlocks.CROOKED_LOG.get());
        HeadlessHorseman horseman = HeadlessHorseman.summon(helper.getLevel(), helper.absoluteVec(at), null, helper.absolutePos(tree), null);
        horseman.removeFreeWill();
        return horseman;
    }

    static void horsemanFightsOnWhenHisHorseFalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tree = helper.absolutePos(new BlockPos(1, 1, 1));
        HeadlessHorseman horseman = callUp(helper, new BlockPos(1, 1, 1), new Vec3(6.5, 1.0, 6.5));
        helper.assertTrue(horseman.getVehicle() instanceof HorsemansSteed steed && tree.equals(steed.getTree()),
                Component.literal("He should ride his own horse, bound to his tree"));
        HorsemansSteed fallen = (HorsemansSteed) horseman.getVehicle();
        fallen.kill(level);
        helper.assertTrue(horseman.isAlive() && !horseman.isPassenger(), Component.literal("He should fight on, on foot, when his horse falls"));
        List<HorsemansSteed> buried = level.getEntitiesOfClass(HorsemansSteed.class, new AABB(tree).inflate(8.0), HorsemansSteed::isRising);
        helper.assertTrue(buried.size() == 1, Component.literal("His horse should lie buried by the tree, not " + buried.size() + " of them"));
        HorsemansSteed risen = buried.get(0);
        double ground = helper.absoluteVec(new Vec3(0.0, 1.0, 0.0)).y;
        helper.assertTrue(risen.getY() < ground - 1.0, Component.literal("It should start out under the ground"));
        helper.assertTrue(!risen.hurtServer(level, level.damageSources().magic(), 50.0F), Component.literal("Nothing should hurt it while it's in the ground"));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!risen.isRising() && risen.getY() > ground - 0.2,
                        Component.literal("It should dig its way back out, not stay at " + helper.relativeVec(risen.position()))))
                .thenWaitUntil(() -> helper.assertTrue(horseman.getVehicle() == risen, Component.literal("He should mount it again when it comes back to him: horse at " + helper.relativeVec(risen.position()) + " path " + risen.getNavigation().getPath() + ", him at " + helper.relativeVec(horseman.position()) + " riding " + horseman.getVehicle())))
                .thenExecute(() -> discard(horseman))
                .thenSucceed();
    }

    static void slainHorsemanRisesFromHisTree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tree = helper.absolutePos(new BlockPos(1, 1, 1));
        HeadlessHorseman horseman = callUp(helper, new BlockPos(1, 1, 1), new Vec3(6.5, 1.0, 6.5));
        HorsemansSteed steed = (HorsemansSteed) horseman.getVehicle();
        steed.removeFreeWill();
        horseman.kill(level);
        List<HalloweenSpirit> spirits = level.getEntitiesOfClass(HalloweenSpirit.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16.0));
        helper.assertTrue(spirits.size() == 1, Component.literal("One Spirit of Halloween should rise, not " + spirits.size()));
        HalloweenSpirit spirit = spirits.get(0);
        helper.assertTrue(spirit.isRising() && spirit.position().distanceTo(Vec3.atBottomCenterOf(tree)) < 1.0,
                Component.literal("It should rise up out of his tree, not out of him"));
        helper.assertTrue(spirit.findSteed() == steed && steed.isAlive(), Component.literal("It should go after the horse he fell from"));
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16.0),
                item -> item.getItem().is(ModItems.HORSEMANS_HEAD.get())).isEmpty(), Component.literal("The Horseman himself shouldn't drop his head"));
        // Nothing but holy water touches the spirit
        helper.assertTrue(!spirit.hurtServer(level, level.damageSources().generic(), 50.0F)
                && !spirit.hurtServer(level, level.damageSources().magic(), 50.0F), Component.literal("Only holy water should touch the spirit"));
        ModEffects.HOLY_WATER.get().applyInstantaneousEffect(level, null, null, spirit, 0, 1.0);
        helper.assertTrue(spirit.isAlive() && spirit.getHolyHitsLeft() == HalloweenSpirit.HOLY_HITS - 1, Component.literal("One splash should only drive it back"));
        ModEffects.HOLY_WATER.get().applyInstantaneousEffect(level, null, null, spirit, 0, 1.0);
        helper.assertTrue(spirit.getHolyHitsLeft() == HalloweenSpirit.HOLY_HITS - 1, Component.literal("A second splash straight after shouldn't count"));
        helper.startSequence()
                .thenExecuteAfter(HalloweenSpirit.HOLY_HIT_GAP + 1, () -> ModEffects.HOLY_WATER.get().applyInstantaneousEffect(level, null, null, spirit, 0, 1.0))
                .thenExecuteAfter(HalloweenSpirit.HOLY_HIT_GAP + 1, () -> {
                    helper.assertTrue(spirit.isAlive(), Component.literal("Two splashes shouldn't be enough"));
                    ModEffects.HOLY_WATER.get().applyInstantaneousEffect(level, null, null, spirit, 0, 1.0);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(!spirit.isAlive(), Component.literal("The third splash should banish it"));
                    helper.assertTrue(!steed.isAlive(), Component.literal("His horse should crumble with it"));
                    helper.assertTrue(!level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, spirit.getBoundingBox().inflate(4.0),
                            item -> item.getItem().is(ModItems.HORSEMANS_HEAD.get())).isEmpty(), Component.literal("Banished, it should give up the head"));
                })
                .thenExecute(() -> discard(spirit, steed))
                .thenSucceed();
    }

    static void spiritThatReachesHisHorseRidesAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tree = helper.absolutePos(new BlockPos(6, 1, 4));
        HorsemansSteed steed = HorsemansSteed.bred(level, tree);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 4.5));
        steed.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(steed);
        steed.removeFreeWill();
        HalloweenSpirit spirit = HalloweenSpirit.riseFrom(level, tree, Vec3.atBottomCenterOf(tree), steed);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(!spirit.isAlive(), Component.literal("The spirit should reach his horse"));
                    helper.assertTrue(steed.getFirstPassenger() instanceof HeadlessHorseman horseman && horseman.hasBossBar(),
                            Component.literal("The Horseman should ride again, on that same horse"));
                })
                .thenExecute(() -> discard(steed.getFirstPassenger()))
                .thenSucceed();
    }

    static void horsemansBlowsBlindNowAndThen(GameTestHelper helper) {
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        helper.assertTrue(!HeadlessHorseman.afflict(player, 3) && !player.hasEffect(MobEffects.BLINDNESS), Component.literal("Most blows are only blows"));
        helper.assertTrue(HeadlessHorseman.afflict(player, 0), Component.literal("One blow in nine should afflict"));
        helper.assertTrue(player.hasEffect(MobEffects.BLINDNESS) && player.hasEffect(MobEffects.SLOWNESS), Component.literal("It should blind and slow"));
        helper.assertTrue(!HeadlessHorseman.afflict(zombie, 0), Component.literal("Only players are blinded"));
        SkillGameTests.removePlayer(helper, player);
        zombie.discard();
        helper.succeed();
    }

    static void pumpkinBombsThrowFolkClearButSpareTheGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie thrower = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 2));
        Zombie caught = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(5, 1, 5));
        helper.setBlock(new BlockPos(4, 1, 5), Blocks.GLASS);
        PumpkinBomb bomb = new PumpkinBomb(level, thrower);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 5.5));
        float health = caught.getHealth();
        PumpkinBomb.burst(level, at, bomb);
        helper.assertTrue(caught.getHealth() < health, Component.literal("The bomb should hurt those it catches"));
        helper.assertTrue(caught.getDeltaMovement().x > 0.1 && caught.getDeltaMovement().y > 0.1, Component.literal("It should throw them clear, not " + caught.getDeltaMovement()));
        helper.assertTrue(thrower.getHealth() == thrower.getMaxHealth(), Component.literal("It should spare whoever threw it"));
        helper.assertBlockPresent(Blocks.GLASS, new BlockPos(4, 1, 5));
        thrower.discard();
        caught.discard();
        helper.succeed();
    }

    static void horsemanWontChargeThroughHolyWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AreaEffectCloud cloud = new AreaEffectCloud(level, helper.absoluteVec(new Vec3(4.5, 1.0, 4.5)).x, helper.absoluteVec(new Vec3(4.5, 1.0, 4.5)).y,
                helper.absoluteVec(new Vec3(4.5, 1.0, 4.5)).z);
        cloud.setPotionContents(new PotionContents(ModPotions.HOLY_WATER));
        cloud.setRadius(1.5F);
        cloud.setDuration(200);
        level.addFreshEntity(cloud);
        Vec3 west = helper.absoluteVec(new Vec3(0.5, 1.0, 4.5));
        Vec3 east = helper.absoluteVec(new Vec3(8.5, 1.0, 4.5));
        Vec3 corner = helper.absoluteVec(new Vec3(0.5, 1.0, 0.5));
        helper.assertTrue(HeadlessHorseman.isHallowed(level, helper.absoluteVec(new Vec3(4.5, 1.0, 4.5))), Component.literal("The cloud's ground is hallowed"));
        helper.assertTrue(!HeadlessHorseman.isHallowed(level, corner), Component.literal("Ground well off from it isn't"));
        helper.assertTrue(HeadlessHorseman.isPathHallowed(level, west, east), Component.literal("A charge across the cloud should be refused"));
        helper.assertTrue(!HeadlessHorseman.isPathHallowed(level, west, corner), Component.literal("A charge round it is fine"));
        cloud.discard();
        helper.succeed();
    }

    static void jackOLanternByACrookedTreeCallsHimUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lantern = new BlockPos(4, 1, 4);
        helper.setBlock(lantern, Blocks.JACK_O_LANTERN);
        helper.assertTrue(HorsemanSummoning.summon(level, helper.absolutePos(lantern), true, null) == null,
                Component.literal("No Crooked Tree near, no Horseman"));
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.CROOKED_LOG.get());
        helper.assertTrue(HorsemanSummoning.summon(level, helper.absolutePos(lantern), false, null) == null,
                Component.literal("By day, no Horseman"));
        helper.assertBlockPresent(Blocks.JACK_O_LANTERN, lantern);
        HeadlessHorseman horseman = HorsemanSummoning.summon(level, helper.absolutePos(lantern), true, null);
        helper.assertTrue(horseman != null && horseman.getVehicle() != null, Component.literal("By night, by the tree, he should come riding"));
        helper.assertBlockPresent(Blocks.AIR, lantern);
        helper.assertTrue(horseman.isPersistenceRequired(), Component.literal("Called up, he shouldn't wander off and fade"));
        helper.setBlock(lantern, Blocks.JACK_O_LANTERN);
        helper.assertTrue(HorsemanSummoning.summon(level, helper.absolutePos(lantern), true, null) == null,
                Component.literal("Only one Horseman at a time"));
        discard(horseman);
        helper.succeed();
    }

    static void headOnAPikeWardsAndCallsItsGhost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pikeAt = new BlockPos(4, 1, 4);
        helper.setBlock(pikeAt, ModBlocks.HEAD_ON_A_PIKE.get());
        BlockPos pike = helper.absolutePos(pikeAt);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(7, 1, 7));
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(7, 1, 1));
        helper.assertTrue(HeadOnAPikeBlockEntity.ward(level, pike) >= 1 && NightOfTerror.isTerrified(zombie), Component.literal("Monsters should shun the pike"));
        helper.assertTrue(!NightOfTerror.isTerrified(cow), Component.literal("Only monsters are warded off"));
        HeadOnAPikeBlockEntity watch = (HeadOnAPikeBlockEntity) level.getBlockEntity(pike);
        watch.keepWatch(level, true);
        SpectralHorseman ghost = watch.findHorseman(level);
        helper.assertTrue(ghost != null && ghost.getVehicle() instanceof GhostSteed, Component.literal("By night the pike should call up the Spectral Horseman"));
        watch.keepWatch(level, true);
        helper.assertTrue(level.getEntitiesOfClass(SpectralHorseman.class, new AABB(pike).inflate(64.0)).size() == 1, Component.literal("Only one rides for each pike"));
        helper.assertTrue(!ghost.hurtServer(level, level.damageSources().magic(), 50.0F), Component.literal("Nothing should hurt the Spectral Horseman"));
        helper.assertTrue(ghost.isPrey(zombie) && !ghost.isPrey(cow), Component.literal("He should hunt monsters only"));
        helper.assertTrue(ghost.canTrample(cow) && !ghost.canTrample(villager), Component.literal("He tramples creatures in his way, but never villagers"));
        ServerPlayer player = SkillGameTests.makePlayer(helper);
        helper.assertTrue(!ghost.canTrample(player) && !ghost.canAttack(player), Component.literal("He never harms players"));
        SkillGameTests.removePlayer(helper, player);
        watch.keepWatch(level, false);
        helper.assertTrue(!ghost.isAlive() && watch.findHorseman(level) == null, Component.literal("By day he should fade"));
        helper.setBlock(pikeAt, Blocks.AIR);
        zombie.discard();
        cow.discard();
        villager.discard();
        helper.succeed();
    }

    static void crookedTreeStandsBesideItsRoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(-200 * 16, 0, 0);
        CrookedTreePiece yard = new CrookedTreePiece(origin, Rotation.CLOCKWISE_90);
        BoundingBox box = yard.getBoundingBox();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        yard.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(3L), box,
                ChunkPos.containing(yard.pos(0, 0, 0)), origin);
        helper.assertTrue(level.getBlockState(yard.pos(0, 1, 0)).is(ModBlocks.CROOKED_LOG.get()), Component.literal("The Crooked Tree should stand in the middle"));
        int ring = CrookedTreePiece.RING;
        for (BlockPos road : List.of(yard.pos(ring, 0, 0), yard.pos(-ring, 0, 0), yard.pos(0, 0, ring), yard.pos(0, 0, -ring), yard.pos(9, 0, 9),
                yard.pos(0, 0, CrookedTreePiece.RADIUS + 1), yard.pos(0, 0, -CrookedTreePiece.RADIUS - 1))) {
            helper.assertTrue(level.getBlockState(road).is(Blocks.DIRT_PATH) && level.getBlockState(road.above()).isAir(),
                    Component.literal("The road should run round the tree and out through both gates, clear at " + road));
        }
        int radius = CrookedTreePiece.RADIUS;
        helper.assertTrue(level.getBlockState(yard.pos(-radius, 1, 7)).is(Blocks.DARK_OAK_FENCE) && level.getBlockState(yard.pos(radius, 1, -12)).is(Blocks.DARK_OAK_FENCE)
                && level.getBlockState(yard.pos(8, 1, radius)).is(Blocks.DARK_OAK_FENCE), Component.literal("It should be fenced in all round"));
        helper.assertTrue(level.getBlockState(yard.pos(0, 1, radius)).isAir() && level.getBlockState(yard.pos(0, 1, -radius)).isAir(),
                Component.literal("The gates should stand open"));
        helper.assertTrue(HorsemanSummoning.findCrookedTree(level, yard.pos(-3, 1, 0)) != null, Component.literal("A jack o'lantern by the tree should find it"));
        helper.succeed();
    }
}
