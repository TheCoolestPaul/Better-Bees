package com.betterbees.mixin;

import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(BeehiveBlockEntity.class)
public interface BeehiveAccessor {
    @Invoker("getBees")
    List<BeehiveBlockEntity.Occupant> betterbees$getBees();
}
