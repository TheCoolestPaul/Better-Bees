package com.betterbees.hive;

import com.betterbees.ai.BeeAi;
import com.betterbees.config.BetterBeesConfig;
import com.betterbees.mixin.BeehiveAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

import java.util.ArrayList;
import java.util.List;

public final class HiveBreedingService {
    private static final int VANILLA_BABY_AGE = -24000;

    private HiveBreedingService() {}

    public static void tick(ServerLevel level, BlockPos pos, BeehiveBlockEntity hive) {
        if (!BetterBeesConfig.indoorBreedingEnabled()) return;
        int interval = BetterBeesConfig.breedingIntervalTicks();
        long phase = Math.floorMod(mixPosition(pos), interval);
        if (Math.floorMod(level.getGameTime(), interval) != phase) return;
        tryBreed(level, pos, hive, level.random, BetterBeesConfig.breedingChance());
    }

    public static boolean tryBreed(ServerLevel level, BlockPos pos, BeehiveBlockEntity hive,
                                   RandomSource random, double chance) {
        int count = hive.getOccupantCount();
        if (count < 2 || count >= BetterBeesConfig.hiveCapacity() || random.nextDouble() >= chance) return false;

        List<Bee> eligible = new ArrayList<>();
        List<Entity> reconstructed = new ArrayList<>();
        try {
            for (BeehiveBlockEntity.Occupant occupant : ((BeehiveAccessor) hive).betterbees$getBees()) {
                Entity entity = occupant.createEntity(level, pos);
                if (entity != null) reconstructed.add(entity);
                if (entity instanceof Bee bee && bee.getAge() == 0) eligible.add(bee);
            }
            if (eligible.size() < 2) return false;
            int firstIndex = random.nextInt(eligible.size());
            int secondIndex = random.nextInt(eligible.size() - 1);
            if (secondIndex >= firstIndex) secondIndex++;
            Bee child = eligible.get(firstIndex).getBreedOffspring(level, eligible.get(secondIndex));
            if (child == null) return false;
            child.setAge(VANILLA_BABY_AGE);
            BeeAi.initMemories(child, random);
            if (hive.getOccupantCount() >= BetterBeesConfig.hiveCapacity()) {
                child.discard();
                return false;
            }
            hive.storeBee(BeehiveBlockEntity.Occupant.of(child));
            hive.setChanged();
            child.discard();
            return true;
        } finally {
            reconstructed.forEach(Entity::discard);
        }
    }

    private static long mixPosition(BlockPos pos) {
        long value = pos.asLong();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        return value;
    }
}
