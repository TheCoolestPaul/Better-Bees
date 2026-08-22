package com.betterbees.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public final class BeePollenHelper {
    private static final Map<Block, GrowthHandler> GROWTH_HANDLERS = new HashMap<>();

    static {
        GROWTH_HANDLERS.put(Blocks.SWEET_BERRY_BUSH, (level, pos, state, random) -> {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            return age < 3 ? state.setValue(SweetBerryBushBlock.AGE, age + 1) : null;
        });
        GROWTH_HANDLERS.put(Blocks.CAVE_VINES, BeePollenHelper::growBonemealable);
        GROWTH_HANDLERS.put(Blocks.CAVE_VINES_PLANT, BeePollenHelper::growBonemealable);
    }

    private BeePollenHelper() {}

    public static BlockState tryGrow(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state) ? null : crop.getStateForAge(crop.getAge(state) + 1);
        }
        if (state.getBlock() instanceof StemBlock) {
            int age = state.getValue(StemBlock.AGE);
            return age < 7 ? state.setValue(StemBlock.AGE, age + 1) : null;
        }
        GrowthHandler handler = GROWTH_HANDLERS.get(state.getBlock());
        return handler == null ? null : handler.apply(level, pos, state, random);
    }

    private static BlockState growBonemealable(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            RandomSource random
    ) {
        BonemealableBlock block = (BonemealableBlock) state.getBlock();
        if (!block.isValidBonemealTarget(level, pos, state)) {
            return null;
        }
        block.performBonemeal(level, random, pos, state);
        return level.getBlockState(pos);
    }

    @FunctionalInterface
    private interface GrowthHandler {
        BlockState apply(ServerLevel level, BlockPos pos, BlockState state, RandomSource random);
    }
}
