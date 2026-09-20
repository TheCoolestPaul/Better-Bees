package com.betterbees.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cache topology, never eligibility: bees still evaluate and tick behaviors every tick. */
@Mixin(Brain.class)
public abstract class BeeBrainSchedulingMixin<E extends LivingEntity> {
    @Shadow @Final private Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority;
    @Shadow @Final private Set<Activity> activeActivities;
    @Unique private List<Activity> betterbees$activities;
    @Unique private List<List<BehaviorControl<? super E>>> betterbees$groups;
    @Unique private List<BehaviorControl<? super E>> betterbees$running;
    @Unique private boolean betterbees$ticking;

    // All public activity-registration overloads converge here, including compatibility additions.
    @Inject(method = {"addActivityAndRemoveMemoriesWhenStopped", "removeAllBehaviors"}, at = @At("HEAD"))
    private void betterbees$invalidate(CallbackInfo ci) {
        betterbees$groups = null;
    }

    @Unique
    private void betterbees$prepare() {
        if (betterbees$groups != null) return;
        betterbees$activities = new ArrayList<>();
        betterbees$groups = new ArrayList<>();
        for (var priorities : availableBehaviorsByPriority.values()) {
            for (var entry : priorities.entrySet()) {
                betterbees$activities.add(entry.getKey());
                betterbees$groups.add(new ArrayList<>(entry.getValue()));
            }
        }
    }

    @Inject(method = "startEachNonRunningBehavior", at = @At("HEAD"), cancellable = true)
    private void betterbees$start(ServerLevel level, E entity, CallbackInfo ci) {
        if (!(entity instanceof Bee)) return;
        betterbees$prepare();
        var groups = betterbees$groups;
        var activities = betterbees$activities;
        long time = level.getGameTime();
        for (int group = 0; group < groups.size(); group++) {
            if (!activeActivities.contains(activities.get(group))) continue;
            var behaviors = groups.get(group);
            for (int index = 0; index < behaviors.size(); index++) {
                var behavior = behaviors.get(index);
                if (behavior.getStatus() == Behavior.Status.STOPPED) behavior.tryStart(level, entity, time);
            }
        }
        ci.cancel();
    }

    @Inject(method = "tickEachRunningBehavior", at = @At("HEAD"), cancellable = true)
    private void betterbees$tick(ServerLevel level, E entity, CallbackInfo ci) {
        if (!(entity instanceof Bee) || betterbees$ticking) return;
        betterbees$prepare();
        if (betterbees$running == null) betterbees$running = new ArrayList<>();
        // Preserve vanilla's snapshot: a behavior may stop/start another behavior during its tick.
        for (int group = 0; group < betterbees$groups.size(); group++) {
            var behaviors = betterbees$groups.get(group);
            for (int index = 0; index < behaviors.size(); index++) {
                var behavior = behaviors.get(index);
                if (behavior.getStatus() == Behavior.Status.RUNNING) betterbees$running.add(behavior);
            }
        }
        betterbees$ticking = true;
        try {
            long time = level.getGameTime();
            for (int index = 0; index < betterbees$running.size(); index++) {
                betterbees$running.get(index).tickOrStop(level, entity, time);
            }
        } finally {
            betterbees$running.clear();
            betterbees$ticking = false;
        }
        ci.cancel();
    }
}
