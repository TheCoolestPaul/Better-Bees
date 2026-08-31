package com.betterbees.mixin;

import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(BeehiveBlockEntity.class)
public interface BeehiveAccessor {
    @Invoker("getBees")
    List<BeehiveBlockEntity.Occupant> betterbees$getBees();

    @Invoker("releaseAllOccupants")
    List<Entity> betterbees$releaseAllOccupants(BlockState state, BeehiveBlockEntity.BeeReleaseStatus status);
}
