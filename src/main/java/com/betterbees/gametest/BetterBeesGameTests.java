package com.betterbees.gametest;

import com.betterbees.BetterBees;
import com.betterbees.ai.BeeAi;
import com.betterbees.config.BetterBeesConfig;
import com.betterbees.compat.HiveOverlayData;
import com.betterbees.hive.HiveBreedingService;
import com.betterbees.hive.HiveHoneyService;
import com.betterbees.hive.HiveFlowerIndex;
import com.betterbees.hive.HiveFlowerKnowledge;
import com.betterbees.mixin.BeehiveAccessor;
import com.betterbees.mixin.DispenserBlockAccessor;
import com.betterbees.registry.ModMemoryTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BetterBees.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BetterBeesGameTests {
    private static final BlockPos HIVE_POS = new BlockPos(1, 1, 1);

    private BetterBeesGameTests() {}

    @GameTest(template = "empty")
    public static void beehiveAcceptsConfiguredCapacity(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fillByEntry(helper, hive, BetterBeesConfig.hiveCapacity());
        helper.assertValueEqual(hive.getOccupantCount(), 20, "beehive occupants");
        helper.assertTrue(hive.isFull(), "beehive should report full at 20 occupants");
        Bee extra = EntityType.BEE.create(helper.getLevel());
        helper.assertTrue(extra != null, "extra bee should be constructible");
        hive.addOccupant(extra);
        helper.assertValueEqual(hive.getOccupantCount(), 20, "beehive occupants after rejected entry");
        helper.assertTrue(extra.isAlive(), "rejected bee must remain safely in the world");
        extra.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beeNestUsesSameCapacity(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEE_NEST);
        fillByEntry(helper, hive, BetterBeesConfig.hiveCapacity());
        helper.assertValueEqual(hive.getOccupantCount(), 20, "bee nest occupants");
        helper.assertTrue(hive.isFull(), "bee nest should report full at 20 occupants");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoAdultsCreateOneStoredBaby(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, 2);
        boolean bred = HiveBreedingService.tryBreed(helper.getLevel(), helper.absolutePos(HIVE_POS), hive,
                helper.getLevel().random, 1.0D);
        helper.assertTrue(bred, "forced breeding roll should succeed");
        helper.assertValueEqual(hive.getOccupantCount(), 3, "occupants after one birth");
        int babies = 0;
        for (BeehiveBlockEntity.Occupant occupant : ((BeehiveAccessor) hive).betterbees$getBees()) {
            Entity entity = occupant.createEntity(helper.getLevel(), helper.absolutePos(HIVE_POS));
            if (entity instanceof Bee bee && bee.isBaby()) {
                babies++;
                helper.assertTrue(bee.getBrain().hasMemoryValue(ModMemoryTypes.POLLINATING_COOLDOWN.get()),
                        "stored baby should have a Better Bees Brain");
            }
            if (entity != null) entity.discard();
        }
        helper.assertValueEqual(babies, 1, "stored babies after one birth");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneAdultCannotBreed(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, 1);
        boolean bred = HiveBreedingService.tryBreed(helper.getLevel(), helper.absolutePos(HIVE_POS), hive,
                helper.getLevel().random, 1.0D);
        helper.assertFalse(bred, "one adult must not create a baby");
        helper.assertValueEqual(hive.getOccupantCount(), 1, "occupants after rejected breeding");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedRollDoesNotBreed(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, 2);
        boolean bred = HiveBreedingService.tryBreed(helper.getLevel(), helper.absolutePos(HIVE_POS), hive,
                helper.getLevel().random, 0.0D);
        helper.assertFalse(bred, "zero-percent breeding roll must fail");
        helper.assertValueEqual(hive.getOccupantCount(), 2, "occupants after failed roll");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullHiveDoesNotBreed(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, BetterBeesConfig.hiveCapacity());
        boolean bred = HiveBreedingService.tryBreed(helper.getLevel(), helper.absolutePos(HIVE_POS), hive,
                helper.getLevel().random, 1.0D);
        helper.assertFalse(bred, "full hive must not breed");
        helper.assertValueEqual(hive.getOccupantCount(), 20, "occupants after full-hive roll");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ageCooldownPreventsBreeding(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        Bee adult = EntityType.BEE.create(helper.getLevel());
        Bee coolingDown = EntityType.BEE.create(helper.getLevel());
        helper.assertTrue(adult != null && coolingDown != null, "parent bees should be constructible");
        coolingDown.setAge(100);
        hive.storeBee(BeehiveBlockEntity.Occupant.of(adult));
        hive.storeBee(BeehiveBlockEntity.Occupant.of(coolingDown));
        adult.discard();
        coolingDown.discard();
        boolean bred = HiveBreedingService.tryBreed(helper.getLevel(), helper.absolutePos(HIVE_POS), hive,
                helper.getLevel().random, 1.0D);
        helper.assertFalse(bred, "age cooldown must make a parent ineligible");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twentyOccupantsSurviveNbtRoundTrip(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, BetterBeesConfig.hiveCapacity());
        CompoundTag saved = hive.saveWithoutMetadata(helper.getLevel().registryAccess());
        BeehiveBlockEntity restored = new BeehiveBlockEntity(helper.absolutePos(HIVE_POS), Blocks.BEEHIVE.defaultBlockState());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.getOccupantCount(), 20, "occupants after NBT round trip");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newBeeHasBetterBeesBrain(GameTestHelper helper) {
        Bee bee = EntityType.BEE.create(helper.getLevel());
        helper.assertTrue(bee != null, "bee should be constructible");
        BeeAi.initMemories(bee, helper.getLevel().random);
        helper.assertTrue(bee.getBrain().hasMemoryValue(ModMemoryTypes.POLLINATING_COOLDOWN.get()),
                "Better Bees cooldown memory should be registered on the bee Brain");
        bee.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothHiveTypesStoreConfiguredHoney(GameTestHelper helper) {
        for (Block block : new Block[]{Blocks.BEEHIVE, Blocks.BEE_NEST}) {
            BeehiveBlockEntity hive = placeHive(helper, block);
            HiveHoneyService.set(hive, 0);
            for (int i = 0; i < BetterBeesConfig.honeyCapacity(); i++) {
                helper.assertTrue(HiveHoneyService.add(helper.getLevel(), helper.absolutePos(HIVE_POS), 1),
                        "honey deposit should fit below capacity");
            }
            helper.assertValueEqual(HiveHoneyService.get(hive), 10, "stored honey at default capacity");
            helper.assertFalse(HiveHoneyService.add(helper.getLevel(), helper.absolutePos(HIVE_POS), 1),
                    "deposit above honey capacity must fail");
            helper.assertValueEqual(HiveHoneyService.get(hive), 10, "stored honey after rejected deposit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void harvestCostConsumesExactlyOneHoney(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 1);
        helper.assertTrue(HiveHoneyService.consume(hive), "one stored honey should be harvestable");
        helper.assertValueEqual(HiveHoneyService.get(hive), 0, "honey after one harvest");
        helper.assertFalse(HiveHoneyService.consume(hive), "empty hive must not be harvestable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void honeySurvivesNbtRoundTrip(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 9);
        CompoundTag saved = hive.saveWithoutMetadata(helper.getLevel().registryAccess());
        BeehiveBlockEntity restored = new BeehiveBlockEntity(helper.absolutePos(HIVE_POS), Blocks.BEEHIVE.defaultBlockState());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(HiveHoneyService.get(restored), 9, "honey after NBT round trip");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyHoneyMigratesOneForOne(GameTestHelper helper) {
        BeehiveBlockEntity legacy = new BeehiveBlockEntity(helper.absolutePos(HIVE_POS),
                Blocks.BEEHIVE.defaultBlockState().setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 5));
        helper.assertValueEqual(HiveHoneyService.get(legacy), 5, "legacy vanilla honey level");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void defaultHoneyScalingMatchesPlan(GameTestHelper helper) {
        int[] display = {0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5};
        for (int honey = 0; honey <= 10; honey++) {
            helper.assertValueEqual(HiveHoneyService.scaled(honey, 10, 5), display[honey],
                    "display proxy for honey " + honey);
        }
        helper.assertValueEqual(HiveHoneyService.scaled(0, 10, 15), 0, "empty comparator signal");
        helper.assertValueEqual(HiveHoneyService.scaled(5, 10, 15), 8, "half-full comparator signal");
        helper.assertValueEqual(HiveHoneyService.scaled(10, 10, 15), 15, "full comparator signal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void honeycombRollStaysWithinConfiguredRange(GameTestHelper helper) {
        for (int i = 0; i < 256; i++) {
            int count = HiveHoneyService.randomHoneycombCount(helper.getLevel().random);
            helper.assertTrue(count >= BetterBeesConfig.shearsHoneycombMin()
                            && count <= BetterBeesConfig.shearsHoneycombMax(),
                    "honeycomb roll must stay inside configured bounds");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bottleHarvestConsumesOneHoney(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        helper.useBlock(HIVE_POS, player);
        helper.assertTrue(player.getMainHandItem().is(Items.HONEY_BOTTLE), "bottle harvest should produce one honey bottle");
        helper.assertValueEqual(HiveHoneyService.get(hive), 0, "honey after bottle harvest");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smokedShearsHarvestKeepsOccupants(GameTestHelper helper) {
        helper.setBlock(HIVE_POS.below(), Blocks.CAMPFIRE);
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, 2);
        HiveHoneyService.set(hive, 2);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack shears = new ItemStack(Items.SHEARS);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, shears);
        helper.useBlock(HIVE_POS, player);
        helper.assertValueEqual(HiveHoneyService.get(hive), 1, "honey after shears harvest");
        helper.assertValueEqual(hive.getOccupantCount(), 2, "smoked hive occupants");
        helper.assertValueEqual(shears.getDamageValue(), 1, "shears durability used");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsafeHarvestReleasesBeesWithoutResettingHoney(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, 2);
        HiveHoneyService.set(hive, 4);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        helper.useBlock(HIVE_POS, player);
        helper.assertValueEqual(HiveHoneyService.get(hive), 3, "unsafe harvest must only consume its cost");
        helper.assertValueEqual(hive.getOccupantCount(), 0, "unsafe harvest should release occupants");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hiveItemComponentPreservesExactHoney(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 9);
        ItemStack hiveItem = new ItemStack(Items.BEEHIVE);
        hive.saveToItem(hiveItem, helper.getLevel().registryAccess());
        BeehiveBlockEntity restored = new BeehiveBlockEntity(helper.absolutePos(HIVE_POS), Blocks.BEEHIVE.defaultBlockState());
        restored.applyComponentsFromItemStack(hiveItem);
        helper.assertValueEqual(HiveHoneyService.get(restored), 9, "honey restored from hive item component");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dispenserShearsUseIncrementalHarvest(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 1);
        BlockPos dispenserPos = HIVE_POS.south();
        helper.setBlock(dispenserPos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.NORTH));
        DispenserBlockEntity dispenser = helper.getBlockEntity(dispenserPos);
        dispenser.setItem(0, new ItemStack(Items.SHEARS));
        BlockPos absoluteDispenserPos = helper.absolutePos(dispenserPos);
        ((DispenserBlockAccessor) Blocks.DISPENSER).betterbees$dispenseFrom(
                helper.getLevel(), helper.getLevel().getBlockState(absoluteDispenserPos), absoluteDispenserPos);
        helper.assertValueEqual(HiveHoneyService.get(hive), 0, "honey after dispenser shearing");
        helper.assertValueEqual(dispenser.getItem(0).getDamageValue(), 1, "dispenser shears durability used");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collectiveFlowerScanHonorsPerHiveBudget(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveFlowerIndex index = ((HiveFlowerKnowledge) hive).betterbees$getFlowerIndex();
        BlockPos absoluteHive = helper.absolutePos(HIVE_POS);
        for (int i = 0; i < 20; i++) {
            Bee bee = EntityType.BEE.create(helper.getLevel());
            helper.assertTrue(bee != null, "searching bee should be constructible");
            bee.moveTo(absoluteHive.getX() + 0.5D, absoluteHive.getY(), absoluteHive.getZ() + 0.5D);
            index.request(helper.getLevel(), absoluteHive, bee, 0);
            bee.discard();
        }
        index.tick(helper.getLevel(), absoluteHive);
        helper.assertTrue(index.lastTickChecks() <= BetterBeesConfig.flowerScanBudget(),
                "twenty requests must share one per-hive scan budget");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void discoveredFlowersAreSharedAndSoftReserved(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveFlowerIndex index = ((HiveFlowerKnowledge) hive).betterbees$getFlowerIndex();
        BlockPos absoluteHive = helper.absolutePos(HIVE_POS);
        helper.setBlock(HIVE_POS.east(), Blocks.DANDELION);
        helper.setBlock(HIVE_POS.west(), Blocks.DANDELION);
        Bee first = EntityType.BEE.create(helper.getLevel());
        Bee second = EntityType.BEE.create(helper.getLevel());
        helper.assertTrue(first != null && second != null, "searching bees should be constructible");
        first.moveTo(absoluteHive.getX() + 0.5D, absoluteHive.getY(), absoluteHive.getZ() + 0.5D);
        second.moveTo(first.getX(), first.getY(), first.getZ());
        index.request(helper.getLevel(), absoluteHive, first, 0);
        index.tick(helper.getLevel(), absoluteHive);
        HiveFlowerIndex.Request firstRequest = index.request(helper.getLevel(), absoluteHive, first, 0);
        HiveFlowerIndex.Request secondRequest = index.request(helper.getLevel(), absoluteHive, second, 0);
        helper.assertTrue(firstRequest.status() == HiveFlowerIndex.Status.FOUND
                        && secondRequest.status() == HiveFlowerIndex.Status.FOUND,
                "one hive scan should make cached flowers available to every nestmate");
        helper.assertFalse(firstRequest.flower().equals(secondRequest.flower()),
                "soft reservations should spread equally placed bees across available flowers");
        helper.assertValueEqual(index.reservationCount(firstRequest.flower()), 1,
                "first flower reservation count");
        helper.assertValueEqual(index.reservationCount(secondRequest.flower()), 1,
                "second flower reservation count");
        index.release(first.getUUID());
        helper.assertValueEqual(index.reservationCount(firstRequest.flower()), 0,
                "released reservation count");
        index.invalidate(secondRequest.flower());
        helper.assertValueEqual(index.reservationCount(secondRequest.flower()), 0,
                "invalidated flower reservation count");
        index.tick(helper.getLevel(), absoluteHive);
        helper.assertValueEqual(index.lastTickChecks(), 0,
                "scanner should pause after every requester receives a flower");
        first.discard();
        second.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collectiveFlowerScanPausesAfterCompletedMiss(GameTestHelper helper) {
        HiveFlowerIndex index = new HiveFlowerIndex();
        BlockPos absoluteHive = helper.absolutePos(HIVE_POS);
        Bee bee = EntityType.BEE.create(helper.getLevel());
        helper.assertTrue(bee != null, "searching bee should be constructible");
        bee.moveTo(absoluteHive.getX() + 0.5D, absoluteHive.getY(), absoluteHive.getZ() + 0.5D);
        index.request(helper.getLevel(), absoluteHive, bee, 0);

        for (int tick = 0; tick < 3_000 && index.completedGeneration() == 0; tick++) {
            index.tick(helper.getLevel(), absoluteHive);
        }
        helper.assertTrue(index.completedGeneration() > 0L, "flower generation should complete");
        helper.assertValueEqual(index.activeDemandCount(), 0, "demand after completed generation");
        helper.assertValueEqual(index.lastTickGenerationCompletions(), 1,
                "generation completion diagnostic");
        index.tick(helper.getLevel(), absoluteHive);
        helper.assertValueEqual(index.lastTickChecks(), 0, "completed miss must pause scanning");

        HiveFlowerIndex.Request miss = index.request(helper.getLevel(), absoluteHive, bee, 0);
        helper.assertTrue(miss.status() == HiveFlowerIndex.Status.COMPLETE_MISS,
                "requester should acknowledge the completed miss");
        index.request(helper.getLevel(), absoluteHive, bee, miss.completedGeneration());
        index.tick(helper.getLevel(), absoluteHive);
        helper.assertTrue(index.lastTickChecks() > 0, "renewed demand should resume scanning");
        bee.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonFlowersUseOneConstantTimeCacheProbe(GameTestHelper helper) {
        HiveFlowerIndex index = new HiveFlowerIndex();
        BlockPos absoluteHive = helper.absolutePos(HIVE_POS);
        Bee bee = EntityType.BEE.create(helper.getLevel());
        helper.assertTrue(bee != null, "searching bee should be constructible");
        bee.moveTo(absoluteHive.getX() + 0.5D, absoluteHive.getY(), absoluteHive.getZ() + 0.5D);
        index.request(helper.getLevel(), absoluteHive, bee, 0);
        index.tick(helper.getLevel(), absoluteHive);
        helper.assertValueEqual(index.lastTickCacheProbes(), index.lastTickChecks(),
                "each loaded non-flower should use exactly one membership probe");
        bee.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hundredHiveIndexesRespectAggregateBudget(GameTestHelper helper) {
        BlockPos absoluteHive = helper.absolutePos(HIVE_POS);
        int totalChecks = 0;
        for (int i = 0; i < 100; i++) {
            HiveFlowerIndex index = new HiveFlowerIndex();
            Bee bee = EntityType.BEE.create(helper.getLevel());
            helper.assertTrue(bee != null, "searching bee should be constructible");
            bee.moveTo(absoluteHive.getX() + 0.5D, absoluteHive.getY(), absoluteHive.getZ() + 0.5D);
            index.request(helper.getLevel(), absoluteHive, bee, 0);
            index.tick(helper.getLevel(), absoluteHive);
            totalChecks += index.lastTickChecks();
            bee.discard();
        }
        helper.assertTrue(totalChecks <= 100 * BetterBeesConfig.flowerScanBudget(),
                "one hundred active hive indexes must respect the aggregate scan ceiling");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overlayDataUsesAuthoritativeHiveValues(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 7);
        fill(helper, hive, BetterBeesConfig.hiveCapacity());

        HiveOverlayData data = HiveOverlayData.from(hive);
        helper.assertValueEqual(data.honey(), 7, "overlay honey");
        helper.assertValueEqual(data.honeyCapacity(), BetterBeesConfig.honeyCapacity(), "overlay honey capacity");
        helper.assertValueEqual(data.bees(), BetterBeesConfig.hiveCapacity(), "overlay occupants");
        helper.assertValueEqual(data.beeCapacity(), BetterBeesConfig.hiveCapacity(), "overlay bee capacity");
        helper.succeed();
    }

    private static BeehiveBlockEntity placeHive(GameTestHelper helper, Block block) {
        helper.setBlock(HIVE_POS, block);
        return helper.getBlockEntity(HIVE_POS);
    }

    private static void fill(GameTestHelper helper, BeehiveBlockEntity hive, int count) {
        for (int i = 0; i < count; i++) {
            Bee bee = EntityType.BEE.create(helper.getLevel());
            helper.assertTrue(bee != null, "bee should be constructible");
            hive.storeBee(BeehiveBlockEntity.Occupant.of(bee));
            bee.discard();
        }
    }

    private static void fillByEntry(GameTestHelper helper, BeehiveBlockEntity hive, int count) {
        for (int i = 0; i < count; i++) {
            Bee bee = EntityType.BEE.create(helper.getLevel());
            helper.assertTrue(bee != null, "bee should be constructible");
            hive.addOccupant(bee);
        }
    }
}
