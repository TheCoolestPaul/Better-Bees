package com.betterbees.gametest;

import com.betterbees.util.HiveMemory;
import com.betterbees.ai.sensors.BeeNearbySensor;
import com.betterbees.ai.sensors.BeeAdultSensor;
import com.betterbees.ai.sensors.BeeSensing;
import com.betterbees.mixin.BrainSensorsAccessor;
import com.betterbees.registry.ModSensorTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.entity.ai.sensing.AdultSensor;
import java.util.List;
import com.betterbees.mixin.PathNavigationAccessor;
import com.betterbees.ai.tasks.BeePathfindingTask;
import com.betterbees.ai.tasks.EnterHiveTask;
import com.betterbees.ai.NavigationBudget;
import com.betterbees.hive.HiveSafetyService;
import com.betterbees.hive.HiveRuntimeState;
import com.betterbees.hive.HiveRuntimeAccess;
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
    public static void quietBeeSensorsAndOptOut(GameTestHelper helper) {
        Bee bee = VersionHooks.createBee(helper.getLevel());
        BeeNearbySensor nearby = nearbySensor(bee);
        BeeAdultSensor adult = adultSensor(bee);
        BeeSensing.beforeBehaviors(helper.getLevel(), bee);
        for (int tick = 0; tick < 60; tick++) {
            nearby.tick(helper.getLevel(), bee);
            adult.tick(helper.getLevel(), bee);
        }
        int expected = BetterBeesConfig.adaptiveEntitySensing() ? 0 : 3;
        VersionHooks.assertValueEqual(helper, nearby.scanCount(), (long) expected, "quiet nearby scans / native opt-out cadence");
        VersionHooks.assertValueEqual(helper, adult.scanCount(), (long) expected, "adult scans / native opt-out cadence");
        VersionHooks.assertTrue(helper, bee.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).isPresent()
                != BetterBeesConfig.adaptiveEntitySensing(), "opt-out keeps native visible-entity memory publication");
        BetterBees.LOGGER.info("Entity sensing regression: adaptive={}, nearbyScans={}, adultScans={}",
                BetterBeesConfig.adaptiveEntitySensing(), nearby.scanCount(), adult.scanCount());
        bee.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sensingWakesBeforeBehaviors(GameTestHelper helper) {
        if (!BetterBeesConfig.adaptiveEntitySensing()) { helper.succeed(); return; }
        Bee bee = VersionHooks.createBee(helper.getLevel());
        Bee target = VersionHooks.createBee(helper.getLevel());
        BeeNearbySensor sensor = nearbySensor(bee);
        List<Runnable> reasons = List.of(
                () -> bee.setAge(-24000), () -> bee.setInLove(null),
                () -> bee.getBrain().setMemory(MemoryModuleType.BREED_TARGET, target),
                () -> com.betterbees.validation.SensingVersionHooks.setAnger(bee, 200),
                () -> com.betterbees.validation.SensingVersionHooks.setAngerTarget(bee, target), () -> bee.setTarget(target),
                () -> bee.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target), () -> bee.hurtTime = 10);
        for (int i = 0; i < reasons.size(); i++) {
            final int index = i;
            helper.runAfterDelay(i * 3 + 1, () -> {
                bee.setAge(0); bee.resetLove(); com.betterbees.validation.SensingVersionHooks.setAnger(bee, 0);
                bee.setPersistentAngerTarget(null); bee.setTarget(null); bee.hurtTime = 0;
                bee.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
                bee.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                BeeSensing.beforeBehaviors(helper.getLevel(), bee);
                VersionHooks.assertFalse(helper, bee.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES),
                        "quiet transition clears the visible snapshot");
            });
            helper.runAfterDelay(i * 3 + 2, () -> {
                long before = sensor.scanCount();
                reasons.get(index).run();
                // Exercise the actual Brain mixin, rather than calling the wake-up helper directly.
                ((net.minecraft.world.entity.ai.Brain<Bee>) bee.getBrain()).tick(helper.getLevel(), bee);
                VersionHooks.assertValueEqual(helper, sensor.scanCount(), before + 1, "wake reason " + index);
                for (int tick = 0; tick < 20; tick++) sensor.tick(helper.getLevel(), bee);
                BeeSensing.beforeBehaviors(helper.getLevel(), bee);
                VersionHooks.assertValueEqual(helper, sensor.scanCount(), before + 1, "scheduled/forced scans must not duplicate");
                if (index == reasons.size() - 1) { bee.discard(); target.discard(); helper.succeed(); }
            });
        }
    }

    @GameTest(template = "empty")
    public static void activeSensingKeepsPeriodicSchedule(GameTestHelper helper) {
        Bee bee = VersionHooks.createBee(helper.getLevel());
        com.betterbees.validation.SensingVersionHooks.setAnger(bee, 1000);
        BeeNearbySensor sensor = nearbySensor(bee);
        long[] first = new long[1];
        for (int tick = 1; tick <= 61; tick++) {
            final int currentTick = tick;
            helper.runAfterDelay(tick, () -> {
                sensor.tick(helper.getLevel(), bee);
                BeeSensing.beforeBehaviors(helper.getLevel(), bee);
                if (currentTick == 1) first[0] = sensor.scanCount();
                if (currentTick == 61) {
                    VersionHooks.assertValueEqual(helper, sensor.scanCount() - first[0], 3L, "three periodic scans per sixty ticks");
                    bee.discard(); helper.succeed();
                }
            });
        }
    }

    @GameTest(template = "empty")
    public static void babySensingMatchesVanillaVisibility(GameTestHelper helper) {
        Bee baby = sensingBee(helper, 4.5D, 3.0D, 4.5D);
        Bee blocked = sensingBee(helper, 6.5D, 3.0D, 4.5D);
        Bee visible = sensingBee(helper, 4.5D, 3.0D, 7.5D);
        // Outside 1.21.1's fixed radius, inside later versions' configured follow range.
        Bee distant = sensingBee(helper, 4.5D, 23.0D, 4.5D);
        baby.setAge(-24000);
        baby.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32.0D);
        for (int y = 1; y <= 5; y++) for (int z = 2; z <= 6; z++) {
            helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
        }
        // Force the adult sensor to be the first scheduled sensor on this baby's first tick.
        if (!BetterBeesConfig.adaptiveEntitySensing()) {
            for (int tick = 0; tick < 20; tick++) nearbySensor(baby).tick(helper.getLevel(), baby);
        }
        for (int tick = 0; tick < 20; tick++) adultSensor(baby).tick(helper.getLevel(), baby);
        var actual = baby.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElseThrow();
        var actualVisible = baby.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElseThrow();
        var actualAdult = baby.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
        VersionHooks.assertTrue(helper, actualVisible.contains(visible), "unobstructed bee must be visible");
        VersionHooks.assertFalse(helper, actualVisible.contains(blocked), "wall must block visibility, including melee visibility");
        VersionHooks.assertTrue(helper, actualAdult.orElse(null) == visible, "baby follows the closest visible adult");
        new NativeNearbyProbe().scan(helper.getLevel(), baby);
        new NativeAdultProbe().scan(helper.getLevel(), baby);
        VersionHooks.assertTrue(helper, actual.equals(baby.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElseThrow()),
                "native candidate membership and distance ordering");
        var nativeVisible = baby.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElseThrow();
        for (Bee candidate : List.of(blocked, visible, distant)) {
            VersionHooks.assertValueEqual(helper, actualVisible.contains(candidate), nativeVisible.contains(candidate),
                    "native per-bee visibility and version-specific range");
        }
        VersionHooks.assertTrue(helper, actualAdult.equals(baby.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT)),
                "native adult selection");
        if (BetterBeesConfig.adaptiveEntitySensing()) {
            VersionHooks.assertValueEqual(helper, nearbySensor(baby).scanCount(), 1L, "adult-first scheduling wakes nearby sensor once");
            VersionHooks.assertValueEqual(helper, adultSensor(baby).scanCount(), 1L, "adult-first scheduling does not duplicate adult sensing");
        }
        // On the next snapshot, an unobstructed target in melee range can actually be hit.
        helper.runAfterDelay(3, () -> {
            VersionHooks.moveTo(blocked, baby.getX(), baby.getY(), baby.getZ() + 0.2D);
            // These stationary fixture mobs have NoAI; emulate the normal per-tick vanilla LOS-cache reset.
            baby.getSensing().tick();
            baby.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, blocked);
            for (int tick = 0; tick < 20; tick++) nearbySensor(baby).tick(helper.getLevel(), baby);
            float health = blocked.getHealth();
            boolean attacked = net.minecraft.world.entity.ai.behavior.MeleeAttack.create(20).tryStart(helper.getLevel(), baby, helper.getLevel().getGameTime());
            VersionHooks.assertTrue(helper, blocked.getHealth() < health, "active sensing permits melee: started=" + attacked
                    + ", visible=" + baby.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElseThrow().contains(blocked)
                    + ", inRange=" + baby.isWithinMeleeAttackRange(blocked)
                    + ", alive=" + blocked.isAlive() + ", lineOfSight=" + baby.hasLineOfSight(blocked)
                    + ", scans=" + nearbySensor(baby).scanCount());
            baby.discard(); blocked.discard(); visible.discard(); distant.discard(); helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void adaptiveSensingPermitsMating(GameTestHelper helper) {
        Bee first = sensingBee(helper, 4.5D, 3.0D, 4.5D);
        Bee second = sensingBee(helper, 4.8D, 3.0D, 4.5D);
        for (Bee bee : List.of(first, second)) {
            bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_COOLDOWN.get(), 400);
            bee.setNoAi(false);
            bee.setInLove(null);
        }
        var bounds = first.getBoundingBox().inflate(6);
        helper.succeedWhen(() -> {
            var babies = helper.getLevel().getEntitiesOfClass(Bee.class, bounds, Bee::isBaby);
            VersionHooks.assertTrue(helper, !babies.isEmpty(), "visible compatible mates must produce an offspring");
            first.discard(); second.discard(); babies.forEach(Entity::discard);
        });
    }

    @GameTest(template = "empty")
    public static void sensingClearsReferencesAndHiveState(GameTestHelper helper) {
        if (!BetterBeesConfig.adaptiveEntitySensing()) { helper.succeed(); return; }
        Bee bee = sensingBee(helper, 4.5D, 3.0D, 4.5D);
        Bee neighbor = sensingBee(helper, 6.5D, 3.0D, 4.5D);
        com.betterbees.validation.SensingVersionHooks.setAnger(bee, 1000);
        BeeSensing.beforeBehaviors(helper.getLevel(), bee);
        VersionHooks.assertTrue(helper, bee.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElseThrow().contains(neighbor),
                "initial snapshot contains neighbor");
        neighbor.discard();
        helper.runAfterDelay(1, () -> {
            for (int i = 0; i < 20; i++) nearbySensor(bee).tick(helper.getLevel(), bee);
            VersionHooks.assertFalse(helper, bee.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of()).contains(neighbor),
                    "next scan drops removed entities");
            com.betterbees.validation.SensingVersionHooks.setAnger(bee, 0);
            BeeSensing.beforeBehaviors(helper.getLevel(), bee);
            for (var memory : List.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                    MemoryModuleType.NEAREST_VISIBLE_ADULT)) {
                VersionHooks.assertFalse(helper, bee.getBrain().hasMemoryValue(memory), "quiet state drops entity references");
            }
            BeeNearbySensor oldSensor = nearbySensor(bee);
            BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
            hive.addOccupant(bee);
            Entity restored = ((BeehiveAccessor) hive).betterbees$getBees().get(0).createEntity(helper.getLevel(), hive.getBlockPos());
            VersionHooks.assertTrue(helper, restored instanceof Bee, "stored bee can be recreated");
            Bee restoredBee = (Bee) restored;
            VersionHooks.assertTrue(helper, nearbySensor(restoredBee) != oldSensor, "hive exit gets fresh sensor instances");
            VersionHooks.assertValueEqual(helper, nearbySensor(restoredBee).scanCount(), 0L, "sensor runtime state is not serialized");
            restored.discard(); helper.succeed();
        });
    }

    private static BeeNearbySensor nearbySensor(Bee bee) {
        return (BeeNearbySensor) ((BrainSensorsAccessor) bee.getBrain()).betterbees$getSensors().get(ModSensorTypes.BEE_NEARBY_ENTITIES.get());
    }

    @GameTest(template = "empty")
    public static void sensingDimensionAndNonBeeIsolation(GameTestHelper helper) {
        var villager = com.betterbees.validation.SensingVersionHooks.createNonBee(helper.getLevel());
        var nativeSensor = ((BrainSensorsAccessor) villager.getBrain()).betterbees$getSensors()
                .get(net.minecraft.world.entity.ai.sensing.SensorType.NEAREST_LIVING_ENTITIES);
        VersionHooks.assertTrue(helper, nativeSensor != null && !(nativeSensor instanceof BeeNearbySensor),
                "non-bees retain their native nearby sensor");
        for (int tick = 0; tick < 20; tick++) {
            ((net.minecraft.world.entity.ai.sensing.Sensor<net.minecraft.world.entity.LivingEntity>) nativeSensor).tick(helper.getLevel(), villager);
        }
        ((net.minecraft.world.entity.ai.Brain<net.minecraft.world.entity.LivingEntity>) villager.getBrain()).tick(helper.getLevel(), villager);
        VersionHooks.assertTrue(helper, villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES),
                "bee hook must not clear non-bee memories");
        villager.discard();
        ServerLevel otherLevel = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (otherLevel != null && BetterBeesConfig.adaptiveEntitySensing()) {
            Bee first = VersionHooks.createBee(helper.getLevel());
            Bee second = VersionHooks.createBee(otherLevel);
            com.betterbees.validation.SensingVersionHooks.setAnger(first, 1000);
            com.betterbees.validation.SensingVersionHooks.setAnger(second, 1000);
            BeeNearbySensor sensor = nearbySensor(first);
            sensor.updateDemand(helper.getLevel(), first);
            sensor.updateDemand(otherLevel, second);
            sensor.updateDemand(helper.getLevel(), first);
            VersionHooks.assertValueEqual(helper, sensor.scanCount(), 3L, "dimension changes invalidate the per-tick guard and demand state");
            first.discard(); second.discard();
        }
        helper.succeed();
    }

    private static BeeAdultSensor adultSensor(Bee bee) {
        return (BeeAdultSensor) ((BrainSensorsAccessor) bee.getBrain()).betterbees$getSensors().get(ModSensorTypes.BEE_NEAREST_ADULT.get());
    }

    @GameTest(template = "empty")
    public static void forcedSensingPreparesVanillaRange(GameTestHelper helper) {
        Bee baby = sensingBee(helper, 4.5D, 3.0D, 4.5D);
        Bee adult = sensingBee(helper, 4.5D, 3.0D, 7.5D);
        Bee previousOwner = VersionHooks.createBee(helper.getLevel());
        baby.setAge(-24000);
        baby.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32.0D);
        previousOwner.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1.0D);
        // Newer Sensor.tick prepares shared targeting conditions for its own entity.
        // A forced refresh must prepare the waking bee's range without ticking its schedule.
        NativeNearbyProbe probe = new NativeNearbyProbe();
        for (int i = 0; i < 20; i++) probe.tick(helper.getLevel(), previousOwner);
        if (BetterBeesConfig.adaptiveEntitySensing()) {
            BeeSensing.beforeBehaviors(helper.getLevel(), baby);
        } else {
            for (int i = 0; i < 20; i++) nearbySensor(baby).tick(helper.getLevel(), baby);
            for (int i = 0; i < 20; i++) adultSensor(baby).tick(helper.getLevel(), baby);
        }
        boolean visible = baby.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElseThrow().contains(adult);
        VersionHooks.assertTrue(helper, visible, "forced snapshot must not inherit another entity's short visibility range");
        VersionHooks.assertTrue(helper, baby.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT).orElse(null) == adult,
                "forced adult selection must use the baby's native range");
        for (int i = 0; i < 20; i++) probe.tick(helper.getLevel(), baby);
        VersionHooks.assertValueEqual(helper, visible,
                baby.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElseThrow().contains(adult),
                "forced visibility must agree with a full native scheduled scan");
        baby.discard(); adult.discard(); previousOwner.discard(); helper.succeed();
    }

    private static Bee sensingBee(GameTestHelper helper, double x, double y, double z) {
        Bee bee = VersionHooks.createBee(helper.getLevel());
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        VersionHooks.moveTo(bee, origin.getX() + x, origin.getY() + y, origin.getZ() + z);
        bee.setNoAi(true);
        helper.getLevel().addFreshEntity(bee);
        return bee;
    }

    private static final class NativeNearbyProbe extends NearestLivingEntitySensor<Bee> {
        void scan(ServerLevel level, Bee bee) { super.doTick(level, bee); }
    }

    private static final class NativeAdultProbe extends AdultSensor {
        void scan(ServerLevel level, Bee bee) { super.doTick(level, bee); }
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sixtyBeesReturnToThreeHives(GameTestHelper helper) {
        java.util.List<BeehiveBlockEntity> hives = new java.util.ArrayList<>();
        java.util.List<Bee> returning = new java.util.ArrayList<>();
        for (int column = 0; column < 3; column++) {
            BlockPos relative = new BlockPos(column + 4, 1, 4);
            helper.setBlock(relative, Blocks.BEEHIVE);
            BeehiveBlockEntity hive = VersionHooks.getBlockEntity(helper, relative, BeehiveBlockEntity.class);
            hives.add(hive);
            BlockPos home = hive.getBlockPos();
            for (int occupant = 0; occupant < 20; occupant++) {
                Bee bee = VersionHooks.createBee(helper.getLevel());
                VersionHooks.assertTrue(helper, bee != null, "returning bee should be constructible");
                // Keep this navigation fixture's population fixed while normal breeding remains enabled elsewhere.
                bee.setAge(1_000);
                // Start just outside entry range, with a small grid rather than 20 overlapping hitboxes.
                VersionHooks.moveTo(bee, home.getX() + 0.5D + (occupant % 5 - 2) * 0.3D,
                        home.getY() + 2.6D, home.getZ() + 0.5D + (occupant / 5 - 1.5D) * 0.3D);
                ((HiveMemory) bee).betterbees$setMemorizedHome(home);
                bee.getBrain().setMemory(ModMemoryTypes.WANTS_HIVE.get(), true);
                bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_COOLDOWN.get(), 400);
                helper.getLevel().addFreshEntity(bee);
                returning.add(bee);
            }
        }
        helper.runAfterDelay(390, () -> returning.stream().filter(Bee::isAlive).limit(6).forEach(bee ->
                BetterBees.LOGGER.warn("Return test pending: pos={}, home={}, wantsHive={}, navigationDone={}, locateCooldown={}",
                        bee.position(), ((HiveMemory) bee).betterbees$getMemorizedHome(),
                        bee.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get()), bee.getNavigation().isDone(),
                        bee.getBrain().getMemory(ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get()))));
        helper.succeedWhen(() -> {
            for (BeehiveBlockEntity hive : hives) {
                VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 20, "all nestmates must finish return-home navigation");
            }
        });
    }

    @GameTest(template = "empty")
    public static void entryRechecksFireAfterSharedSafeResult(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        BlockPos home = hive.getBlockPos();
        Bee bee = VersionHooks.createBee(helper.getLevel());
        VersionHooks.assertTrue(helper, bee != null, "bee should be constructible");
        VersionHooks.moveTo(bee, home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D);
        ((HiveMemory) bee).betterbees$setMemorizedHome(home);
        bee.getBrain().setMemory(ModMemoryTypes.WANTS_HIVE.get(), true);
        VersionHooks.assertFalse(helper, BeeAi.isHiveNearFire(helper.getLevel(), bee), "initially safe hive");
        helper.getLevel().setBlock(home.above(), Blocks.FIRE.defaultBlockState(), 2);
        VersionHooks.assertFalse(helper, BeeAi.isHiveNearFire(helper.getLevel(), bee), "AI shares this tick's snapshot");
        VersionHooks.assertTrue(helper, hive.isFireNearby(), "vanilla fire checks remain uncached");
        new EnterHiveTask().tryStart(helper.getLevel(), bee, helper.getLevel().getGameTime());
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 0, "fresh entry check must reject new fire");
        VersionHooks.assertTrue(helper, bee.isAlive(), "rejected entrant remains alive");
        helper.getLevel().setBlock(home.above(), Blocks.AIR.defaultBlockState(), 2);
        new EnterHiveTask().tryStart(helper.getLevel(), bee, helper.getLevel().getGameTime());
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 1, "safe return still enters normally");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replacementHiveDoesNotReuseFireCache(GameTestHelper helper) {
        BeehiveBlockEntity original = placeHive(helper, Blocks.BEEHIVE);
        HiveRuntimeState oldState = ((HiveRuntimeAccess) original).betterbees$getRuntimeState();
        oldState.fireNearby(helper.getLevel().getGameTime(), () -> true);
        helper.setBlock(HIVE_POS, Blocks.AIR);
        BeehiveBlockEntity replacement = placeHive(helper, Blocks.BEEHIVE);
        VersionHooks.assertTrue(helper, oldState != ((HiveRuntimeAccess) replacement).betterbees$getRuntimeState(),
                "replacement hive owns fresh transient state");
        VersionHooks.assertFalse(helper, HiveSafetyService.isFireNearby(helper.getLevel(), replacement),
                "replacement hive must be scanned");
        BlockPos unloaded = new BlockPos(30_000_000, 100, 30_000_000);
        VersionHooks.assertTrue(helper, HiveSafetyService.loadedHive(helper.getLevel(), unloaded) == null,
                "unloaded hive must not be resolved");
        VersionHooks.assertFalse(helper, helper.getLevel().hasChunkAt(unloaded), "validation must not load the chunk");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pathRequestsRestoreBudgetAndReturnBlocksWandering(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        Bee bee = VersionHooks.createBee(helper.getLevel());
        VersionHooks.assertTrue(helper, bee != null, "bee should be constructible");
        BlockPos home = hive.getBlockPos();
        VersionHooks.moveTo(bee, home.getX() + 2.5D, home.getY(), home.getZ() + 0.5D);
        bee.getNavigation().setMaxVisitedNodesMultiplier(0.75F);
        for (float budget : new float[]{10.0F, 0.5F}) {
            NavigationBudget.moveTo(bee.getNavigation(), budget, home.getX(), home.getY(), home.getZ(), 1.0D);
            VersionHooks.assertValueEqual(helper,
                    ((PathNavigationAccessor) bee.getNavigation()).betterbees$getMaxVisitedNodesMultiplier(),
                    0.75F, "each request restores the previous navigation budget");
            bee.getNavigation().stop();
        }
        ((HiveMemory) bee).betterbees$setMemorizedHome(home);
        bee.getBrain().setMemory(ModMemoryTypes.WANTS_HIVE.get(), true);
        for (int attempt = 0; attempt < 100; attempt++) {
            VersionHooks.assertFalse(helper, new BeePathfindingTask()
                    .tryStart(helper.getLevel(), bee, helper.getLevel().getGameTime()), "idle wander must not compete with return home");
        }
        // Vanilla rate-limits recomputation for the first 20 world ticks.
        helper.runAfterDelay(21, () -> {
            NavigationBudget.moveTo(bee.getNavigation(), 10.0F, home.getX(), home.getY(), home.getZ(), 1.0D);
            bee.getNavigation().recomputePath();
            VersionHooks.assertValueEqual(helper,
                    ((PathNavigationAccessor) bee.getNavigation()).betterbees$getMaxVisitedNodesMultiplier(),
                    0.75F, "block-update recalculation restores the previous navigation budget");
            VersionHooks.assertTrue(helper, bee.getNavigation().getPath() != null
                    && bee.getNavigation().getPath().canReach(), "recalculation still reaches the home");
            bee.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void soundThrottlePreservesEntryAndEmergencyRelease(GameTestHelper helper) {
        BeehiveBlockEntity hive = placeHive(helper, Blocks.BEEHIVE);
        HiveHoneyService.set(hive, 7);
        fillByEntry(helper, hive, BetterBeesConfig.hiveCapacity());
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), BetterBeesConfig.hiveCapacity(), "all entrants stored despite sound throttle");
        if (BetterBeesConfig.hiveTransitionIntervalTicks() > 0) {
            VersionHooks.assertFalse(helper, ((HiveRuntimeAccess) hive).betterbees$getRuntimeState()
                    .allowTransitionSound(helper.getLevel().getGameTime(), BetterBeesConfig.hiveTransitionIntervalTicks()),
                    "actual entry playback must consume the hive sound budget");
        }
        hive.emptyAllLivingFromHive(null, hive.getBlockState(), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        VersionHooks.assertValueEqual(helper, hive.getOccupantCount(), 0, "emergency release bypasses no entity operations");
        VersionHooks.assertValueEqual(helper, HiveOverlayData.from(hive).honey(), 7, "sound throttling leaves honey untouched");
        // A block removed before evacuation still owns its cooldown until the synchronous release finishes.
        BeehiveBlockEntity detached = new BeehiveBlockEntity(hive.getBlockPos(), hive.getBlockState());
        detached.setLevel(helper.getLevel());
        fill(helper, detached, 20);
        detached.emptyAllLivingFromHive(null, detached.getBlockState(), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        VersionHooks.assertValueEqual(helper, detached.getOccupantCount(), 0, "detached hive evacuates every bee");
        if (BetterBeesConfig.hiveTransitionIntervalTicks() > 0) {
            VersionHooks.assertFalse(helper, ((HiveRuntimeAccess) detached).betterbees$getRuntimeState()
                    .allowTransitionSound(helper.getLevel().getGameTime(), BetterBeesConfig.hiveTransitionIntervalTicks()),
                    "evacuation uses its owner's budget, even when another hive occupies the same position");
        }
        helper.succeed();
    }

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
