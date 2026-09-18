package com.betterbees.compat.bumblezone;

import com.betterbees.ai.BeeAi;
import com.betterbees.registry.ModMemoryTypes;
import com.google.common.collect.ImmutableList;
import com.telepathicgrunt.the_bumblezone.items.FlowerHeadwearHelmet;
import com.telepathicgrunt.the_bumblezone.modinit.BzEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.schedule.Activity;
import java.util.Map;

public final class BumblezoneBrain {
    private BumblezoneBrain() {}

    public static void configure(Brain<Bee> brain) {
        brain.addActivity(Activity.ADMIRE_ITEM, 0, ImmutableList.of(new FollowHeadwear()));
    }

    public static boolean eligible(Bee bee) {
        return !bee.isAngry() && bee.getPersistentAngerTarget() == null && bee.getTarget() == null
                && !bee.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !bee.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get()).orElse(false)
                && !bee.isInLove() && !bee.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)
                && !bee.getBrain().hasMemoryValue(MemoryModuleType.TEMPTING_PLAYER);
    }

    public static boolean wearer(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator()
                && !entity.hasEffect(BzEffects.WRATH_OF_THE_HIVE.holder())
                && !FlowerHeadwearHelmet.getFlowerHeadwear(entity).isEmpty();
    }

    @SuppressWarnings("unchecked")
    public static void beforeBrain(Bee bee) {
        Brain<Bee> brain = (Brain<Bee>) bee.getBrain();
        // Bumblezone writes Mob targets, while combat runs from Brain memory. Hidden clears
        // only the Mob target; never allow the old Brain target to survive that operation.
        if (bee.getTarget() != null && bee.isAngry() && !bee.hasStung()) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, bee.getTarget());
        } else {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        HeadwearState state = (HeadwearState) bee;
        LivingEntity previous = state.betterbees$headwearTarget();
        LivingEntity next = previous;
        if (!eligible(bee)) next = null;
        else {
            if (next != null && (!wearer(next) || next.level() != bee.level() || bee.distanceToSqr(next) > 400)) next = null;
            if ((bee.tickCount & 1) != 0) {
                next = bee.level().getNearestEntity(LivingEntity.class,
                        TargetingConditions.forNonCombat().range(20).ignoreLineOfSight().selector(BumblezoneBrain::wearer),
                        bee, bee.getX(), bee.getY(), bee.getZ(), bee.getBoundingBox().inflate(20));
            }
        }
        if ((previous == null) != (next == null)) {
            brain.stopAll((ServerLevel) bee.level(), bee);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            bee.getNavigation().stop();
        }
        state.betterbees$headwearTarget(next);
        BeeAi.updateActivity(bee);
    }

    public static boolean selectActivity(Bee bee) {
        if (((HeadwearState) bee).betterbees$headwearTarget() == null || !eligible(bee)) return false;
        bee.getBrain().setActiveActivityIfPossible(Activity.ADMIRE_ITEM);
        return true;
    }

    private static final class FollowHeadwear extends Behavior<Bee> {
        FollowHeadwear() { super(Map.of()); }
        @Override protected boolean checkExtraStartConditions(ServerLevel level, Bee bee) {
            return eligible(bee) && ((HeadwearState) bee).betterbees$headwearTarget() != null;
        }
        @Override protected boolean canStillUse(ServerLevel level, Bee bee, long time) {
            return checkExtraStartConditions(level, bee);
        }
        @Override protected void tick(ServerLevel level, Bee bee, long time) {
            LivingEntity target = ((HeadwearState) bee).betterbees$headwearTarget();
            bee.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
            bee.getLookControl().setLookAt(target, bee.getMaxHeadYRot() + 20, bee.getMaxHeadXRot());
            if (bee.distanceToSqr(target) < 6) {
                bee.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                bee.getNavigation().stop();
            } else {
                bee.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), 1.0F, 2));
            }
        }
    }
}
