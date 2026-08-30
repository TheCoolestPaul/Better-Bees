package com.betterbees.registry;

import com.betterbees.BetterBees;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

public final class ModMemoryTypes {
    public static final DeferredRegister<MemoryModuleType<?>> REGISTER =
            DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, BetterBees.MOD_ID);

    public static final RegistryHandle<MemoryModuleType<GlobalPos>> FLOWER_POS =
            register("flower_pos", GlobalPos.CODEC);
    public static final RegistryHandle<MemoryModuleType<Path>> LAST_PATH =
            registerTransient("last_path");
    public static final RegistryHandle<MemoryModuleType<List<GlobalPos>>> HIVE_BLACKLIST =
            register("hive_blacklist", GlobalPos.CODEC.listOf());
    public static final RegistryHandle<MemoryModuleType<Integer>> POLLINATING_COOLDOWN =
            register("pollinating_cooldown", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> POLLINATING_TICKS =
            register("pollinating_ticks", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> SUCCESSFUL_POLLINATING_TICKS =
            register("successful_pollinating_ticks", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> COOLDOWN_LOCATE_HIVE =
            register("cooldown_locate_hive", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> TRAVELLING_TICKS =
            register("travelling_ticks", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> SEARCH_ATTEMPTS =
            register("search_attempts", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> STUCK_TICKS =
            register("stuck_ticks", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Boolean>> WANTS_HIVE =
            register("wants_hive", Codec.BOOL);

    private ModMemoryTypes() {}

    private static <T> RegistryHandle<MemoryModuleType<T>> register(String name, Codec<T> codec) {
        var holder = REGISTER.register(name, () -> new MemoryModuleType<>(Optional.of(codec)));
        return holder::get;
    }

    private static <T> RegistryHandle<MemoryModuleType<T>> registerTransient(String name) {
        var holder = REGISTER.register(name, () -> new MemoryModuleType<T>(Optional.empty()));
        return holder::get;
    }
}
