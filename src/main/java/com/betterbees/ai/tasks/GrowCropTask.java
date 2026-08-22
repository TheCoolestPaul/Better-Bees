package com.betterbees.ai.tasks;

import com.betterbees.mixin.BeeAccessor;
import com.betterbees.util.BeePollenHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public final class GrowCropTask extends Behavior<Bee> {
    public GrowCropTask() {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    private boolean canUse(ServerLevel level, Bee bee) {
        BeeAccessor accessor = (BeeAccessor) bee;
        if (accessor.betterbees$getCropsGrownSincePollination() >= 10) {
            accessor.betterbees$setHasNectar(false);
            accessor.betterbees$resetCropsGrownSincePollination();
            return false;
        }
        return level.getRandom().nextFloat() >= 0.3F && bee.hasNectar();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Bee bee) {
        return canUse(level, bee);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Bee bee, long gameTime) {
        return canUse(level, bee);
    }

    @Override
    protected void tick(ServerLevel level, Bee bee, long gameTime) {
        if (bee.getRandom().nextInt(30) != 0) {
            return;
        }
        for (int distance = 1; distance <= 2; distance++) {
            BlockPos pos = bee.blockPosition().below(distance);
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.BEE_GROWABLES)) {
                continue;
            }
            BlockState grown = BeePollenHelper.tryGrow(level, pos, state, bee.getRandom());
            if (grown != null) {
                level.levelEvent(2011, pos, 15);
                level.setBlockAndUpdate(pos, grown);
                ((BeeAccessor) bee).betterbees$incrementCropsGrownSincePollination();
            }
        }
    }
}
