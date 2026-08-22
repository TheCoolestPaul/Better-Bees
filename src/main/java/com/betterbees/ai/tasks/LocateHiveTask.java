package com.betterbees.ai.tasks;

import com.betterbees.config.BetterBeesConfig;
import com.betterbees.registry.ModMemoryTypes;
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

import java.util.Iterator;
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
        BlockPos beePos = bee.blockPosition();
        Candidate best = null;
        Iterator<PoiRecord> records = level.getPoiManager()
                .getInRange(holder -> holder.is(PoiTypeTags.BEE_HOME), beePos, 20, PoiManager.Occupancy.ANY)
                .iterator();
        while (records.hasNext()) {
            BlockPos pos = records.next().getPos();
            if (blacklist.contains(GlobalPos.of(level.dimension(), pos))) continue;
            if (!(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive)
                    || hive.isFull() || hive.isFireNearby()) continue;

            double distance = Math.sqrt(pos.distSqr(beePos));
            double score = distance
                    + 10.0D * hive.getOccupantCount() / BetterBeesConfig.hiveCapacity();
            Candidate candidate = new Candidate(pos, score, distance);
            if (best == null || candidate.score < best.score
                    || candidate.score == best.score && candidate.distance < best.distance) {
                best = candidate;
            }
        }
        if (best != null) ((HiveMemory) bee).betterbees$setMemorizedHome(best.position);
    }

    private record Candidate(BlockPos position, double score, double distance) {}
}
