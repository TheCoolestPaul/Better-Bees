package com.betterbees.registry;

import com.betterbees.BetterBees;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

public final class ModMemoryTypes {
    public static final DeferredRegister<MemoryModuleType<?>> REGISTER =
            DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, BetterBees.MOD_ID);

    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<GlobalPos>> FLOWER_POS =
            register("flower_pos", GlobalPos.CODEC);
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Path>> LAST_PATH =
            registerTransient("last_path");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<List<GlobalPos>>> HIVE_BLACKLIST =
            register("hive_blacklist", GlobalPos.CODEC.listOf());
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> POLLINATING_COOLDOWN =
            register("pollinating_cooldown", Codec.INT);
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> POLLINATING_TICKS =
            register("pollinating_ticks", Codec.INT);
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> SUCCESSFUL_POLLINATING_TICKS =
            register("successful_pollinating_ticks", Codec.INT);
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> COOLDOWN_LOCATE_HIVE =
            register("cooldown_locate_hive", Codec.INT);
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> TRAVELLING_TICKS =
            register("travelling_ticks", Codec.INT);
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> SEARCH_ATTEMPTS =
            register("search_attempts", Codec.INT);
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> STUCK_TICKS =
            register("stuck_ticks", Codec.INT);
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Boolean>> WANTS_HIVE =
            register("wants_hive", Codec.BOOL);

    private ModMemoryTypes() {}

    private static <T> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<T>> register(String name, Codec<T> codec) {
        return REGISTER.register(name, () -> new MemoryModuleType<>(Optional.of(codec)));
    }

    private static <T> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<T>> registerTransient(String name) {
        return REGISTER.register(name, () -> new MemoryModuleType<>(Optional.empty()));
    }
}
