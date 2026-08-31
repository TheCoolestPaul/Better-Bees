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
import com.betterbees.platform.VersionHooks;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.BeeScaleService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(BetterBees.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BetterBeesGameTests {
    private static final BlockPos HIVE_POS = new BlockPos(1, 1, 1);

    private BetterBeesGameTests() {}

    @GameTest(template = "empty")
    public static void beehiveAcceptsConfiguredCapacity(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fillByEntry(helper, hive, BetterBeesConfig.hiveCapacity());
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 20, "beehive occupants");
        VersionHooks.assertTrue(helper, hive.isFull(), "beehive should report full at 20 occupants");
        Bee extra = VersionHooks.createBee(helper.getLevel());
        VersionHooks.assertTrue(helper, extra != null, "extra bee should be constructible");
        hive.addOccupant(extra);
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 20, "beehive occupants after rejected entry");
        VersionHooks.assertTrue(helper, extra.isAlive(), "rejected bee must remain safely in the world");
        extra.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beeNestUsesSameCapacity(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEE_NEST);
        fillByEntry(helper, hive, BetterBeesConfig.hiveCapacity());
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 20, "bee nest occupants");
        VersionHooks.assertTrue(helper, hive.isFull(), "bee nest should report full at 20 occupants");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoAdultsCreateOneStoredBaby(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, 2);
        boolean bred = HiveBreedingService.tryBreed(helper.getLevel(), helper.absolutePos(HIVE_POS), hive,
                helper.getLevel().random, 1.0D);
        VersionHooks.assertTrue(helper, bred, "forced breeding roll should succeed");
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 3, "occupants after one birth");
        int babies = 0;
        for (BeehiveBlockEntity.Occupant occupant : ((BeehiveAccessor) hive).betterbees$getBees()) {
            Entity entity = occupant.createEntity(helper.getLevel(), helper.absolutePos(HIVE_POS));
            if (entity instanceof Bee bee && bee.isBaby()) {
                babies++;
                VersionHooks.assertTrue(helper, bee.getBrain().hasMemoryValue(ModMemoryTypes.POLLINATING_COOLDOWN.get()),
                        "stored baby should have a Better Bees Brain");
                AttributeInstance scale = bee.getAttribute(Attributes.SCALE);
                VersionHooks.assertTrue(helper, scale != null && scale.hasModifier(BeeScaleService.MODIFIER_ID),
                        "stored baby should reconstruct with its individual scale");
            }
            if (entity != null) entity.discard();
        }
        VersionHooks.assertValueEqual(helper, babies, 1, "stored babies after one birth");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneAdultCannotBreed(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, 1);
        boolean bred = HiveBreedingService.tryBreed(helper.getLevel(), helper.absolutePos(HIVE_POS), hive,
                helper.getLevel().random, 1.0D);
        VersionHooks.assertFalse(helper, bred, "one adult must not create a baby");
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 1, "occupants after rejected breeding");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedRollDoesNotBreed(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, 2);
        boolean bred = HiveBreedingService.tryBreed(helper.getLevel(), helper.absolutePos(HIVE_POS), hive,
                helper.getLevel().random, 0.0D);
        VersionHooks.assertFalse(helper, bred, "zero-percent breeding roll must fail");
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 2, "occupants after failed roll");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullHiveDoesNotBreed(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, BetterBeesConfig.hiveCapacity());
        boolean bred = HiveBreedingService.tryBreed(helper.getLevel(), helper.absolutePos(HIVE_POS), hive,
                helper.getLevel().random, 1.0D);
        VersionHooks.assertFalse(helper, bred, "full hive must not breed");
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 20, "occupants after full-hive roll");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ageCooldownPreventsBreeding(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        Bee adult = VersionHooks.createBee(helper.getLevel());
        Bee coolingDown = VersionHooks.createBee(helper.getLevel());
        VersionHooks.assertTrue(helper, adult != null && coolingDown != null, "parent bees should be constructible");
        coolingDown.setAge(100);
        hive.storeBee(BeehiveBlockEntity.Occupant.of(adult));
        hive.storeBee(BeehiveBlockEntity.Occupant.of(coolingDown));
        adult.discard();
        coolingDown.discard();
        boolean bred = HiveBreedingService.tryBreed(helper.getLevel(), helper.absolutePos(HIVE_POS), hive,
                helper.getLevel().random, 1.0D);
        VersionHooks.assertFalse(helper, bred, "age cooldown must make a parent ineligible");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twentyOccupantsSurviveNbtRoundTrip(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        fill(helper, hive, BetterBeesConfig.hiveCapacity());
        CompoundTag saved = VersionHooks.saveHive(hive, helper.getLevel().registryAccess());
        BeehiveBlockEntity restored = new BeehiveBlockEntity(helper.absolutePos(HIVE_POS), Blocks.BEEHIVE.defaultBlockState());
        VersionHooks.loadHive(restored, saved, helper.getLevel().registryAccess());
        VersionHooks.assertValueEqual(helper, restored.getOccupantCount(), 20, "occupants after NBT round trip");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void forwardUpgradeFixtureRetainsBetterBeesData(GameTestHelper helper) {
        com.betterbees.validation.UpgradeFixture.verify(helper.getLevel(),
                Boolean.getBoolean("betterbees.upgradeRequireExisting"));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newBeeHasBetterBeesBrain(GameTestHelper helper) {
        Bee bee = VersionHooks.createBee(helper.getLevel());
        VersionHooks.assertTrue(helper, bee != null, "bee should be constructible");
        BeeAi.initMemories(bee, helper.getLevel().random);
        VersionHooks.assertTrue(helper, bee.getBrain().hasMemoryValue(ModMemoryTypes.POLLINATING_COOLDOWN.get()),
                "Better Bees cooldown memory should be registered on the bee Brain");
        bee.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beeScaleIsStableUniformAndConfigurable(GameTestHelper helper) {
        UUID sample = UUID.fromString("6f3f37a2-928d-41c6-88f6-c88ea50c34be");
        float first = BeeScaleService.scale(sample, 0.20D, 0.35D);
        float repeated = BeeScaleService.scale(sample, 0.20D, 0.35D);
        VersionHooks.assertTrue(helper, Float.compare(first, repeated) == 0, "one UUID must always produce the same scale");
        VersionHooks.assertTrue(helper, first >= 0.20F && first <= 0.35F, "default scale must remain inside its bounds");
        VersionHooks.assertTrue(helper, Float.compare(first, BeeScaleService.scale(sample, 0.35D, 0.20D)) == 0,
                "inverted bounds must normalize to the same scale");
        VersionHooks.assertTrue(helper, Float.compare(1.0F, BeeScaleService.scale(sample, 1.0D, 1.0D)) == 0,
                "equal vanilla bounds must disable scaling");

        Set<Float> observed = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            UUID uuid = UUID.nameUUIDFromBytes(("betterbees-scale-" + i).getBytes(StandardCharsets.UTF_8));
            observed.add(BeeScaleService.scale(uuid, 0.20D, 0.35D));
        }
        VersionHooks.assertTrue(helper, observed.size() > 48, "UUID mapping should produce varied individual scales");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeScaleAttributeControlsPhysicalBeeSize(GameTestHelper helper) {
        Bee bee = VersionHooks.createBee(helper.getLevel());
        VersionHooks.assertTrue(helper, bee != null, "bee should be constructible");
        VersionHooks.assertTrue(helper, BeeScaleService.apply(bee), "server should apply an individual bee scale");

        AttributeInstance scaleAttribute = bee.getAttribute(Attributes.SCALE);
        VersionHooks.assertTrue(helper, scaleAttribute != null, "bee should expose the generic scale attribute");
        VersionHooks.assertTrue(helper, scaleAttribute.hasModifier(BeeScaleService.MODIFIER_ID),
                "individual scale should use the Better Bees transient modifier");
        VersionHooks.assertTrue(helper, Attributes.SCALE.value().isClientSyncable(),
                "vanilla scale attribute must synchronize to clients");

        float scale = bee.getScale();
        VersionHooks.assertTrue(helper, scale >= BetterBeesConfig.minimumBeeScale()
                        && scale <= BetterBeesConfig.maximumBeeScale(),
                "applied scale must use the effective configured range");
        float adultWidth = bee.getDimensions(Pose.STANDING).width();
        float adultHeight = bee.getDimensions(Pose.STANDING).height();
        VersionHooks.assertTrue(helper, Math.abs(adultWidth - bee.getType().getDimensions().width() * scale) < 0.0001F,
                "adult hitbox width should match the rendered scale");
        VersionHooks.assertTrue(helper, Math.abs(adultHeight - bee.getType().getDimensions().height() * scale) < 0.0001F,
                "adult hitbox height should match the rendered scale");

        bee.setBaby(true);
        VersionHooks.assertTrue(helper, Math.abs(bee.getDimensions(Pose.STANDING).width() - adultWidth * 0.5F) < 0.0001F,
                "baby width should retain vanilla's additional half-size multiplier");
        VersionHooks.assertTrue(helper, Math.abs(bee.getDimensions(Pose.STANDING).height() - adultHeight * 0.5F) < 0.0001F,
                "baby height should retain vanilla's additional half-size multiplier");
        bee.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beeScaleUsesUuidWithoutPersistentModifierData(GameTestHelper helper) {
        Bee original = VersionHooks.createBee(helper.getLevel());
        Bee restored = VersionHooks.createBee(helper.getLevel());
        VersionHooks.assertTrue(helper, original != null && restored != null, "bees should be constructible");
        UUID uuid = UUID.fromString("1903c183-36f8-4778-9d81-5b88c7243379");
        original.setUUID(uuid);
        restored.setUUID(uuid);
        BeeScaleService.apply(original);
        BeeScaleService.apply(restored);
        VersionHooks.assertTrue(helper, Float.compare(original.getScale(), restored.getScale()) == 0,
                "the same saved UUID must restore the same individual scale");

        CompoundTag saved = VersionHooks.saveBee(original);
        VersionHooks.assertFalse(helper, VersionHooks.containsPersistentScaleModifier(saved),
                "transient scale modifier must not be written to entity or hive NBT");
        original.discard();
        restored.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothHiveTypesStoreConfiguredHoney(GameTestHelper helper) {
        for (Block block : new Block[]{Blocks.BEEHIVE, Blocks.BEE_NEST}) {
            BeehiveBlockEntity hive = placeHive(helper, block);
            HiveHoneyService.set(hive, 0);
            for (int i = 0; i < BetterBeesConfig.honeyCapacity(); i++) {
                VersionHooks.assertTrue(helper, HiveHoneyService.add(helper.getLevel(), helper.absolutePos(HIVE_POS), 1),
                        "honey deposit should fit below capacity");
            }
            VersionHooks.assertValueEqual(helper, HiveHoneyService.get(hive), BetterBeesConfig.honeyCapacity(),
                    "stored honey at configured capacity");
            VersionHooks.assertFalse(helper, HiveHoneyService.add(helper.getLevel(), helper.absolutePos(HIVE_POS), 1),
                    "deposit above honey capacity must fail");
            VersionHooks.assertValueEqual(helper, HiveHoneyService.get(hive), BetterBeesConfig.honeyCapacity(),
                    "stored honey after rejected deposit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void harvestCostConsumesExactlyOneHoney(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 1);
        VersionHooks.assertTrue(helper, HiveHoneyService.consume(hive), "one stored honey should be harvestable");
        VersionHooks.assertValueEqual(helper, HiveHoneyService.get(hive), 0, "honey after one harvest");
        VersionHooks.assertFalse(helper, HiveHoneyService.consume(hive), "empty hive must not be harvestable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void honeySurvivesNbtRoundTrip(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 9);
        CompoundTag saved = VersionHooks.saveHive(hive, helper.getLevel().registryAccess());
        BeehiveBlockEntity restored = new BeehiveBlockEntity(helper.absolutePos(HIVE_POS), Blocks.BEEHIVE.defaultBlockState());
        VersionHooks.loadHive(restored, saved, helper.getLevel().registryAccess());
        VersionHooks.assertValueEqual(helper, HiveHoneyService.get(restored), 9, "honey after NBT round trip");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyHoneyMigratesOneForOne(GameTestHelper helper) {
        BeehiveBlockEntity legacy = new BeehiveBlockEntity(helper.absolutePos(HIVE_POS),
                Blocks.BEEHIVE.defaultBlockState().setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 5));
        VersionHooks.assertValueEqual(helper, HiveHoneyService.get(legacy), 5, "legacy vanilla honey level");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void defaultHoneyScalingMatchesPlan(GameTestHelper helper) {
        int[] display = {0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5};
        for (int honey = 0; honey <= 20; honey++) {
            VersionHooks.assertValueEqual(helper, HiveHoneyService.scaled(honey, 20, 5), display[honey],
                    "display proxy for honey " + honey);
        }
        VersionHooks.assertValueEqual(helper, HiveHoneyService.scaled(0, 20, 15), 0, "empty comparator signal");
        VersionHooks.assertValueEqual(helper, HiveHoneyService.scaled(10, 20, 15), 8, "half-full comparator signal");
        VersionHooks.assertValueEqual(helper, HiveHoneyService.scaled(20, 20, 15), 15, "full comparator signal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void honeycombRollStaysWithinConfiguredRange(GameTestHelper helper) {
        for (int i = 0; i < 256; i++) {
            int count = HiveHoneyService.randomHoneycombCount(helper.getLevel().random);
            VersionHooks.assertTrue(helper, count >= BetterBeesConfig.shearsHoneycombMin()
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
        VersionHooks.assertTrue(helper, player.getMainHandItem().is(Items.HONEY_BOTTLE), "bottle harvest should produce one honey bottle");
        VersionHooks.assertValueEqual(helper, HiveHoneyService.get(hive), 0, "honey after bottle harvest");
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
        VersionHooks.assertValueEqual(helper, HiveHoneyService.get(hive), 1, "honey after shears harvest");
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 2, "smoked hive occupants");
        VersionHooks.assertValueEqual(helper, shears.getDamageValue(), 1, "shears durability used");
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
        VersionHooks.assertValueEqual(helper, HiveHoneyService.get(hive), 3, "unsafe harvest must only consume its cost");
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 0, "unsafe harvest should release occupants");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hiveItemComponentPreservesExactHoney(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 9);
        ItemStack hiveItem = new ItemStack(Items.BEEHIVE);
        VersionHooks.copyHiveToItem(hive, hiveItem, helper.getLevel().registryAccess());
        BeehiveBlockEntity restored = new BeehiveBlockEntity(helper.absolutePos(HIVE_POS), Blocks.BEEHIVE.defaultBlockState());
        restored.applyComponentsFromItemStack(hiveItem);
        VersionHooks.assertValueEqual(helper, HiveHoneyService.get(restored), 9, "honey restored from hive item component");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dispenserShearsUseIncrementalHarvest(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 1);
        BlockPos dispenserPos = HIVE_POS.south();
        helper.setBlock(dispenserPos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.NORTH));
        DispenserBlockEntity dispenser = VersionHooks.getBlockEntity(helper, dispenserPos, DispenserBlockEntity.class);
        dispenser.setItem(0, new ItemStack(Items.SHEARS));
        BlockPos absoluteDispenserPos = helper.absolutePos(dispenserPos);
        ((DispenserBlockAccessor) Blocks.DISPENSER).betterbees$dispenseFrom(
                helper.getLevel(), helper.getLevel().getBlockState(absoluteDispenserPos), absoluteDispenserPos);
        VersionHooks.assertValueEqual(helper, HiveHoneyService.get(hive), 0, "honey after dispenser shearing");
        VersionHooks.assertValueEqual(helper, dispenser.getItem(0).getDamageValue(), 1, "dispenser shears durability used");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collectiveFlowerScanHonorsPerHiveBudget(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveFlowerIndex index = ((HiveFlowerKnowledge) hive).betterbees$getFlowerIndex();
        BlockPos absoluteHive = helper.absolutePos(HIVE_POS);
        for (int i = 0; i < 20; i++) {
            Bee bee = VersionHooks.createBee(helper.getLevel());
            VersionHooks.assertTrue(helper, bee != null, "searching bee should be constructible");
            VersionHooks.moveTo(bee, absoluteHive.getX() + 0.5D, absoluteHive.getY(), absoluteHive.getZ() + 0.5D);
            index.request(helper.getLevel(), absoluteHive, bee, 0);
            bee.discard();
        }
        index.tick(helper.getLevel(), absoluteHive);
        VersionHooks.assertTrue(helper, index.lastTickChecks() <= BetterBeesConfig.flowerScanBudget(),
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
        Bee first = VersionHooks.createBee(helper.getLevel());
        Bee second = VersionHooks.createBee(helper.getLevel());
        VersionHooks.assertTrue(helper, first != null && second != null, "searching bees should be constructible");
        VersionHooks.moveTo(first, absoluteHive.getX() + 0.5D, absoluteHive.getY(), absoluteHive.getZ() + 0.5D);
        VersionHooks.moveTo(second, first.getX(), first.getY(), first.getZ());
        index.request(helper.getLevel(), absoluteHive, first, 0);
        index.tick(helper.getLevel(), absoluteHive);
        HiveFlowerIndex.Request firstRequest = index.request(helper.getLevel(), absoluteHive, first, 0);
        HiveFlowerIndex.Request secondRequest = index.request(helper.getLevel(), absoluteHive, second, 0);
        VersionHooks.assertTrue(helper, firstRequest.status() == HiveFlowerIndex.Status.FOUND
                        && secondRequest.status() == HiveFlowerIndex.Status.FOUND,
                "one hive scan should make cached flowers available to every nestmate");
        VersionHooks.assertFalse(helper, firstRequest.flower().equals(secondRequest.flower()),
                "soft reservations should spread equally placed bees across available flowers");
        VersionHooks.assertValueEqual(helper, index.reservationCount(firstRequest.flower()), 1,
                "first flower reservation count");
        VersionHooks.assertValueEqual(helper, index.reservationCount(secondRequest.flower()), 1,
                "second flower reservation count");
        index.release(first.getUUID());
        VersionHooks.assertValueEqual(helper, index.reservationCount(firstRequest.flower()), 0,
                "released reservation count");
        index.invalidate(secondRequest.flower());
        VersionHooks.assertValueEqual(helper, index.reservationCount(secondRequest.flower()), 0,
                "invalidated flower reservation count");
        index.tick(helper.getLevel(), absoluteHive);
        VersionHooks.assertValueEqual(helper, index.lastTickChecks(), 0,
                "scanner should pause after every requester receives a flower");
        first.discard();
        second.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collectiveFlowerScanPausesAfterCompletedMiss(GameTestHelper helper) {
        HiveFlowerIndex index = new HiveFlowerIndex();
        BlockPos absoluteHive = helper.absolutePos(HIVE_POS);
        Bee bee = VersionHooks.createBee(helper.getLevel());
        VersionHooks.assertTrue(helper, bee != null, "searching bee should be constructible");
        VersionHooks.moveTo(bee, absoluteHive.getX() + 0.5D, absoluteHive.getY(), absoluteHive.getZ() + 0.5D);
        index.request(helper.getLevel(), absoluteHive, bee, 0);

        for (int tick = 0; tick < 3_000 && index.completedGeneration() == 0; tick++) {
            index.tick(helper.getLevel(), absoluteHive);
        }
        VersionHooks.assertTrue(helper, index.completedGeneration() > 0L, "flower generation should complete");
        VersionHooks.assertValueEqual(helper, index.activeDemandCount(), 0, "demand after completed generation");
        VersionHooks.assertValueEqual(helper, index.lastTickGenerationCompletions(), 1,
                "generation completion diagnostic");
        index.tick(helper.getLevel(), absoluteHive);
        VersionHooks.assertValueEqual(helper, index.lastTickChecks(), 0, "completed miss must pause scanning");

        HiveFlowerIndex.Request miss = index.request(helper.getLevel(), absoluteHive, bee, 0);
        VersionHooks.assertTrue(helper, miss.status() == HiveFlowerIndex.Status.COMPLETE_MISS,
                "requester should acknowledge the completed miss");
        index.request(helper.getLevel(), absoluteHive, bee, miss.completedGeneration());
        index.tick(helper.getLevel(), absoluteHive);
        VersionHooks.assertTrue(helper, index.lastTickChecks() > 0, "renewed demand should resume scanning");
        bee.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonFlowersUseOneConstantTimeCacheProbe(GameTestHelper helper) {
        HiveFlowerIndex index = new HiveFlowerIndex();
        BlockPos absoluteHive = helper.absolutePos(HIVE_POS);
        Bee bee = VersionHooks.createBee(helper.getLevel());
        VersionHooks.assertTrue(helper, bee != null, "searching bee should be constructible");
        VersionHooks.moveTo(bee, absoluteHive.getX() + 0.5D, absoluteHive.getY(), absoluteHive.getZ() + 0.5D);
        index.request(helper.getLevel(), absoluteHive, bee, 0);
        index.tick(helper.getLevel(), absoluteHive);
        VersionHooks.assertValueEqual(helper, index.lastTickCacheProbes(), index.lastTickChecks(),
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
            Bee bee = VersionHooks.createBee(helper.getLevel());
            VersionHooks.assertTrue(helper, bee != null, "searching bee should be constructible");
            VersionHooks.moveTo(bee, absoluteHive.getX() + 0.5D, absoluteHive.getY(), absoluteHive.getZ() + 0.5D);
            index.request(helper.getLevel(), absoluteHive, bee, 0);
            index.tick(helper.getLevel(), absoluteHive);
            totalChecks += index.lastTickChecks();
            bee.discard();
        }
        VersionHooks.assertTrue(helper, totalChecks <= 100 * BetterBeesConfig.flowerScanBudget(),
                "one hundred active hive indexes must respect the aggregate scan ceiling");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overlayDataUsesAuthoritativeHiveValues(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 7);
        fill(helper, hive, BetterBeesConfig.hiveCapacity());

        HiveOverlayData data = HiveOverlayData.from(hive);
        VersionHooks.assertValueEqual(helper, data.honey(), 7, "overlay honey");
        VersionHooks.assertValueEqual(helper, data.honeyCapacity(), BetterBeesConfig.honeyCapacity(), "overlay honey capacity");
        VersionHooks.assertValueEqual(helper, data.bees(), BetterBeesConfig.hiveCapacity(), "overlay occupants");
        VersionHooks.assertValueEqual(helper, data.beeCapacity(), BetterBeesConfig.hiveCapacity(), "overlay bee capacity");
        helper.succeed();
    }

    private static BeehiveBlockEntity placeHive(GameTestHelper helper, Block block) {
        helper.setBlock(HIVE_POS, block);
        return VersionHooks.getBlockEntity(helper, HIVE_POS, BeehiveBlockEntity.class);
    }

    private static void fill(GameTestHelper helper, BeehiveBlockEntity hive, int count) {
        for (int i = 0; i < count; i++) {
            Bee bee = VersionHooks.createBee(helper.getLevel());
            VersionHooks.assertTrue(helper, bee != null, "bee should be constructible");
            hive.storeBee(BeehiveBlockEntity.Occupant.of(bee));
            bee.discard();
        }
    }

    private static void fillByEntry(GameTestHelper helper, BeehiveBlockEntity hive, int count) {
        for (int i = 0; i < count; i++) {
            Bee bee = VersionHooks.createBee(helper.getLevel());
            VersionHooks.assertTrue(helper, bee != null, "bee should be constructible");
            hive.addOccupant(bee);
        }
    }
}
