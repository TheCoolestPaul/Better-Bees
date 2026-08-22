package com.betterbees.ai.tasks;

import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.config.BetterBeesConfig;
import com.betterbees.util.HiveMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

import java.util.List;
import java.util.Map;

public final class LocateHiveTask extends Behavior<Bee> {
    public LocateHiveTask() {
        super(Map.of(ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get(), MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Bee bee) {
        return ((HiveMemory) bee).betterbees$getMemorizedHome() == null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Bee bee, long gameTime) {
        return false;
    }

    @Override
    protected void start(ServerLevel level, Bee bee, long gameTime) {
        bee.getBrain().setMemory(ModMemoryTypes.COOLDOWN_LOCATE_HIVE.get(), 200);
        List<GlobalPos> blacklist = bee.getBrain().getMemory(ModMemoryTypes.HIVE_BLACKLIST.get()).orElseGet(List::of);
        level.getPoiManager().getInRange(holder -> holder.is(PoiTypeTags.BEE_HOME), bee.blockPosition(), 20,
                        PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .filter(pos -> level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive
                        && !hive.isFull() && !hive.isFireNearby())
                .filter(pos -> !blacklist.contains(GlobalPos.of(level.dimension(), pos)))
                .min((left, right) -> {
                    double leftDistance = Math.sqrt(left.distSqr(bee.blockPosition()));
                    double rightDistance = Math.sqrt(right.distSqr(bee.blockPosition()));
                    BeehiveBlockEntity leftHive = (BeehiveBlockEntity) level.getBlockEntity(left);
                    BeehiveBlockEntity rightHive = (BeehiveBlockEntity) level.getBlockEntity(right);
                    double leftScore = leftDistance + 10.0D * leftHive.getOccupantCount() / BetterBeesConfig.hiveCapacity();
                    double rightScore = rightDistance + 10.0D * rightHive.getOccupantCount() / BetterBeesConfig.hiveCapacity();
                    int scoreComparison = Double.compare(leftScore, rightScore);
                    return scoreComparison != 0 ? scoreComparison : Double.compare(leftDistance, rightDistance);
                })
                .ifPresent(pos -> ((HiveMemory) bee).betterbees$setMemorizedHome(pos));
    }
}
