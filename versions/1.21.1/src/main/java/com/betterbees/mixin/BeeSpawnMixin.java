package com.betterbees.mixin;

import com.betterbees.ai.BeeAi;
import com.betterbees.util.BeeScaleService;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Bee.class)
public abstract class BeeSpawnMixin extends Animal {
    protected BeeSpawnMixin(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        BeeAi.initMemories((Bee) (Object) this, level.getRandom());
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        BeeScaleService.apply((Bee) (Object) this);
        return result;
    }
}
