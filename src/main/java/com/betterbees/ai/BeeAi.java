package com.betterbees.ai;

import com.betterbees.hive.HiveSafetyService;
import com.betterbees.ai.tasks.BeePathfindingTask;
import com.betterbees.ai.tasks.EnterHiveTask;
import com.betterbees.ai.tasks.FindFlowerTask;
import com.betterbees.ai.tasks.FloatTask;
import com.betterbees.ai.tasks.GoToHiveTask;
import com.betterbees.ai.tasks.GrowCropTask;
import com.betterbees.ai.tasks.LocateHiveTask;
import com.betterbees.ai.tasks.PollinateFlowerTask;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.HiveMemory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.behavior.BabyFollowAdult;
import net.minecraft.world.entity.ai.behavior.CountDownCooldownTicks;
import net.minecraft.world.entity.ai.behavior.EraseMemoryIf;
import net.minecraft.world.entity.ai.behavior.FollowTemptation;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

import java.util.function.Predicate;

public final class BeeAi {
    private static final ImmutableList<Activity> ACTIVITY_PRIORITY = ImmutableList.of(Activity.FIGHT, Activity.CELEBRATE, Activity.IDLE);
    private static final UniformInt TIME_BETWEEN_POLLINATING = UniformInt.of(10, 15);
    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(3, 16);

    private BeeAi() {}

    public static void initMemories(Bee bee, RandomSource random) {
        bee.getBrain().setMemory(
                ModMemoryTypes.POLLINATING_COOLDOWN.get(),
                TIME_BETWEEN_POLLINATING.sample(random)
        );
    }

    public static Brain<Bee> makeBrain(Brain<Bee> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initFightActivity(brain);
        initPollinationActivity(brain);
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<Bee> brain) {
        brain.addActivity(Activity.CORE, 0, ImmutableList.of(
                new MoveToTargetSink(),
                new CountDownCooldownTicks(ModMemoryTypes.POLLINATING_COOLDOWN.get()),
                new CountDownCooldownTicks(ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get()),
                new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS)
        ));
    }

    private static void initFightActivity(Brain<Bee> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(
                Activity.FIGHT,
                0,
                ImmutableList.of(
                        StopAttackingIfTargetInvalid.create(),
                        SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.0F),
                        MeleeAttack.create(20),
                        EraseMemoryIf.create(Predicate.not(Bee::isAngry), MemoryModuleType.ATTACK_TARGET),
                        EraseMemoryIf.create(Bee::hasStung, MemoryModuleType.ATTACK_TARGET)
                ),
                MemoryModuleType.ATTACK_TARGET
        );
    }

    private static void initPollinationActivity(Brain<Bee> brain) {
        brain.addActivityWithConditions(
                Activity.CELEBRATE,
                ImmutableList.of(
                        Pair.of(0, BabyFollowAdult.create(ADULT_FOLLOW_RANGE, 1.25F)),
                        Pair.of(1, new FindFlowerTask()),
                        Pair.of(2, new PollinateFlowerTask()),
                        Pair.of(3, EraseMemoryIf.create(Bee::hasNectar, ModMemoryTypes.FLOWER_POS.get())),
                        Pair.of(4, EraseMemoryIf.create(
                                bee -> bee.getBrain().hasMemoryValue(ModMemoryTypes.WANTS_HIVE.get()),
                                ModMemoryTypes.FLOWER_POS.get()
                        ))
                ),
                ImmutableSet.of(
                        Pair.of(MemoryModuleType.TEMPTING_PLAYER, MemoryStatus.VALUE_ABSENT),
                        Pair.of(MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT),
                        Pair.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT),
                        Pair.of(ModMemoryTypes.WANTS_HIVE.get(), MemoryStatus.VALUE_ABSENT),
                        Pair.of(ModMemoryTypes.POLLINATING_COOLDOWN.get(), MemoryStatus.VALUE_ABSENT)
                )
        );
    }

    private static void initIdleActivity(Brain<Bee> brain) {
        brain.addActivityWithConditions(
                Activity.IDLE,
                ImmutableList.of(
                        Pair.of(0, new FloatTask()),
                        Pair.of(1, new GoToHiveTask()),
                        Pair.of(2, new EnterHiveTask()),
                        Pair.of(3, new FollowTemptation(bee -> 0.6F)),
                        Pair.of(4, BabyFollowAdult.create(ADULT_FOLLOW_RANGE, 1.25F)),
                        Pair.of(5, new AnimalMakeLove(EntityType.BEE)),
                        Pair.of(6, new LocateHiveTask()),
                        Pair.of(7, new GrowCropTask()),
                        Pair.of(8, new RunOne<>(ImmutableList.of(Pair.of(new BeePathfindingTask(), 1))))
                ),
                ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT))
        );
    }

    public static void updateActivity(Bee bee) {
        bee.getBrain().setActiveActivityToFirstValid(
                ACTIVITY_PRIORITY
        );
    }

    public static Predicate<ItemStack> getTemptations() {
        return stack -> stack.is(ItemTags.BEE_FOOD);
    }

    public static boolean isHiveNearFire(ServerLevel level, Bee bee) {
        net.minecraft.core.BlockPos home = ((HiveMemory) bee).betterbees$getMemorizedHome();
        BeehiveBlockEntity hive = HiveSafetyService.loadedHive(level, home);
        return hive != null && HiveSafetyService.isFireNearby(level, hive);
    }

    public static void incrementMemory(Brain<?> brain, MemoryModuleType<Integer> type) {
        brain.setMemory(type, brain.getMemory(type).orElse(0) + 1);
    }

}
