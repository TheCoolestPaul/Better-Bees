package com.betterbees.ai.tasks;

import com.betterbees.mixin.BeeAccessor;
import com.betterbees.hive.HiveFlowerService;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.HiveMemory;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class PollinateFlowerTask extends Behavior<Bee> {
    private int lastSoundTick;
    private Vec3 hoverPos;

    public PollinateFlowerTask() {
        super(Map.of(ModMemoryTypes.FLOWER_POS.get(), MemoryStatus.VALUE_PRESENT), 601);
    }

    private boolean valid(ServerLevel level, Bee bee) {
        return bee.getBrain().getMemory(ModMemoryTypes.FLOWER_POS.get())
                .filter(pos -> pos.dimension() == level.dimension())
                .filter(pos -> level.hasChunkAt(pos.pos()))
                .filter(pos -> level.getBlockState(pos.pos()).is(BlockTags.FLOWERS)
                        && level.getBlockState(pos.pos()).getFluidState().isEmpty()).isPresent()
                && bee.getBrain().getMemory(ModMemoryTypes.POLLINATING_COOLDOWN.get()).isEmpty()
                && !bee.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get()).orElse(false)
                && !level.isRaining() && !level.isNight();
    }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Bee bee) { return valid(level, bee); }
    @Override protected boolean canStillUse(ServerLevel level, Bee bee, long gameTime) {
        return valid(level, bee)
                && bee.getBrain().getMemory(ModMemoryTypes.POLLINATING_TICKS.get()).orElse(0) <= 600
                && bee.getBrain().getMemory(ModMemoryTypes.SUCCESSFUL_POLLINATING_TICKS.get()).orElse(0) <= 400;
    }

    @Override
    protected void start(ServerLevel level, Bee bee, long gameTime) {
        lastSoundTick = 0;
        hoverPos = null;
        bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_TICKS.get(), 0);
        bee.getBrain().setMemory(ModMemoryTypes.SUCCESSFUL_POLLINATING_TICKS.get(), 0);
        bee.resetTicksWithoutNectarSinceExitingHive();
    }

    @Override
    protected void stop(ServerLevel level, Bee bee, long gameTime) {
        GlobalPos flower = bee.getBrain().getMemory(ModMemoryTypes.FLOWER_POS.get()).orElse(null);
        BlockPos home = ((HiveMemory) bee).betterbees$getMemorizedHome();
        boolean successful = bee.getBrain().getMemory(ModMemoryTypes.SUCCESSFUL_POLLINATING_TICKS.get()).orElse(0) > 400;
        if (successful) {
            ((BeeAccessor) bee).betterbees$setHasNectar(true);
            bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_COOLDOWN.get(), 400);
            bee.getBrain().setMemory(ModMemoryTypes.WANTS_HIVE.get(), true);
        } else {
            bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_COOLDOWN.get(),
                    UniformInt.of(120, 240).sample(level.random));
            if (flower != null && (!level.hasChunkAt(flower.pos())
                    || !level.getBlockState(flower.pos()).is(BlockTags.FLOWERS)
                    || !level.getBlockState(flower.pos()).getFluidState().isEmpty()) && home != null) {
                HiveFlowerService.invalidate(level, home, flower.pos());
            }
        }
        if (home != null) HiveFlowerService.release(level, home, bee);
        bee.getBrain().eraseMemory(ModMemoryTypes.FLOWER_POS.get());
        bee.getBrain().eraseMemory(ModMemoryTypes.POLLINATING_TICKS.get());
        bee.getBrain().eraseMemory(ModMemoryTypes.SUCCESSFUL_POLLINATING_TICKS.get());
        bee.getNavigation().stop();
        ((BeeAccessor) bee).betterbees$setRemainingFlowerCooldown(200);
        hoverPos = null;
    }

    @Override
    protected void tick(ServerLevel level, Bee bee, long gameTime) {
        int ticks = bee.getBrain().getMemory(ModMemoryTypes.POLLINATING_TICKS.get()).orElse(0) + 1;
        bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_TICKS.get(), ticks);
        GlobalPos flower = bee.getBrain().getMemory(ModMemoryTypes.FLOWER_POS.get()).orElse(null);
        if (flower == null || !level.hasChunkAt(flower.pos())
                || !level.getBlockState(flower.pos()).is(BlockTags.FLOWERS)
                || !level.getBlockState(flower.pos()).getFluidState().isEmpty()) {
            return;
        }
        Vec3 center = Vec3.atBottomCenterOf(flower.pos()).add(0.0D, 0.6D, 0.0D);
        if (center.distanceTo(bee.position()) > 1.0D) {
            hoverPos = center;
            bee.getMoveControl().setWantedPosition(center.x, center.y, center.z, 0.35D);
            return;
        }
        if (hoverPos == null || bee.position().distanceTo(hoverPos) <= 0.1D && bee.getRandom().nextInt(25) == 0) {
            hoverPos = center.add(offset(bee), 0.0D, offset(bee));
            bee.getNavigation().stop();
        }
        bee.getMoveControl().setWantedPosition(hoverPos.x, hoverPos.y, hoverPos.z, 0.35D);
        bee.getLookControl().setLookAt(center.x, center.y, center.z);
        int successful = bee.getBrain().getMemory(ModMemoryTypes.SUCCESSFUL_POLLINATING_TICKS.get()).orElse(0) + 1;
        bee.getBrain().setMemory(ModMemoryTypes.SUCCESSFUL_POLLINATING_TICKS.get(), successful);
        if (bee.getRandom().nextFloat() < 0.05F && successful > lastSoundTick + 59) {
            lastSoundTick = successful;
            bee.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
        }
    }

    private static double offset(Bee bee) {
        return (bee.getRandom().nextFloat() * 2.0F - 1.0F) / 3.0F;
    }
}
