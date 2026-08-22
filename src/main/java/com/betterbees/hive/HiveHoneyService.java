package com.betterbees.hive;

import com.betterbees.config.BetterBeesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HiveHoneyService {
    private HiveHoneyService() {}

    public static int get(BeehiveBlockEntity hive) {
        return ((HiveHoneyStorage) hive).betterbees$getHoney();
    }

    public static void set(BeehiveBlockEntity hive, int honey) {
        ((HiveHoneyStorage) hive).betterbees$setHoney(Math.max(0, honey));
        syncDisplay(hive);
        Level level = hive.getLevel();
        if (level != null) {
            BlockState state = level.getBlockState(hive.getBlockPos());
            level.updateNeighbourForOutputSignal(hive.getBlockPos(), state.getBlock());
        }
    }

    public static boolean add(Level level, BlockPos pos, int amount) {
        if (!(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) || amount <= 0) return false;
        int current = get(hive);
        int capacity = BetterBeesConfig.honeyCapacity();
        if (current >= capacity) return false;
        set(hive, Math.min(capacity, current + amount));
        return true;
    }

    public static boolean consume(BeehiveBlockEntity hive) {
        int cost = BetterBeesConfig.harvestCost();
        int current = get(hive);
        if (current < cost) return false;
        set(hive, current - cost);
        return true;
    }

    public static boolean canHarvest(BeehiveBlockEntity hive) {
        return get(hive) >= BetterBeesConfig.harvestCost();
    }

    public static int randomHoneycombCount(RandomSource random) {
        return Mth.nextInt(random, BetterBeesConfig.shearsHoneycombMin(), BetterBeesConfig.shearsHoneycombMax());
    }

    public static ItemStack randomHoneycomb(RandomSource random) {
        return new ItemStack(Items.HONEYCOMB, randomHoneycombCount(random));
    }

    public static int displayLevel(int honey) {
        return scaled(honey, BetterBeesConfig.honeyCapacity(), 5);
    }

    public static int comparatorLevel(int honey) {
        return scaled(honey, BetterBeesConfig.honeyCapacity(), 15);
    }

    public static int scaled(int honey, int capacity, int maximum) {
        if (honey <= 0) return 0;
        return Mth.clamp(Math.round((float) maximum * honey / Math.max(1, capacity)), 0, maximum);
    }

    public static void syncDisplay(BeehiveBlockEntity hive) {
        Level level = hive.getLevel();
        if (level == null) return;
        BlockPos pos = hive.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            int display = displayLevel(get(hive));
            if (state.getValue(BeehiveBlock.HONEY_LEVEL) != display) {
                level.setBlockAndUpdate(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, display));
            }
        }
        ((HiveHoneyStorage) hive).betterbees$markHoneyDisplaySynced();
    }
}
