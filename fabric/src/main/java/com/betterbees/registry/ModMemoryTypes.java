package com.betterbees.registry;

import com.betterbees.BetterBees;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;

import java.util.List;
import java.util.Optional;

public final class ModMemoryTypes {
    public static final RegistryHandle<MemoryModuleType<GlobalPos>> FLOWER_POS = persistent("flower_pos", GlobalPos.CODEC);
    public static final RegistryHandle<MemoryModuleType<Path>> LAST_PATH = transientType("last_path");
    public static final RegistryHandle<MemoryModuleType<List<GlobalPos>>> HIVE_BLACKLIST = persistent("hive_blacklist", GlobalPos.CODEC.listOf());
    public static final RegistryHandle<MemoryModuleType<Integer>> POLLINATING_COOLDOWN = persistent("pollinating_cooldown", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> POLLINATING_TICKS = persistent("pollinating_ticks", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> SUCCESSFUL_POLLINATING_TICKS = persistent("successful_pollinating_ticks", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> COOLDOWN_LOCATE_HIVE = persistent("cooldown_locate_hive", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> TRAVELLING_TICKS = persistent("travelling_ticks", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> SEARCH_ATTEMPTS = persistent("search_attempts", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Integer>> STUCK_TICKS = persistent("stuck_ticks", Codec.INT);
    public static final RegistryHandle<MemoryModuleType<Boolean>> WANTS_HIVE = persistent("wants_hive", Codec.BOOL);

    private static boolean initialized;
    private ModMemoryTypes() {}
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        bind(FLOWER_POS, "flower_pos"); bind(LAST_PATH, "last_path"); bind(HIVE_BLACKLIST, "hive_blacklist");
        bind(POLLINATING_COOLDOWN, "pollinating_cooldown"); bind(POLLINATING_TICKS, "pollinating_ticks");
        bind(SUCCESSFUL_POLLINATING_TICKS, "successful_pollinating_ticks"); bind(COOLDOWN_LOCATE_HIVE, "cooldown_locate_hive");
        bind(TRAVELLING_TICKS, "travelling_ticks"); bind(SEARCH_ATTEMPTS, "search_attempts");
        bind(STUCK_TICKS, "stuck_ticks"); bind(WANTS_HIVE, "wants_hive");
    }
    private static <T> RegistryHandle<MemoryModuleType<T>> persistent(String ignored, Codec<T> codec) {
        FabricRegistryHandle<MemoryModuleType<T>> handle = new FabricRegistryHandle<>();
        handle.bind(new MemoryModuleType<>(Optional.of(codec)));
        return handle;
    }
    private static <T> RegistryHandle<MemoryModuleType<T>> transientType(String ignored) {
        FabricRegistryHandle<MemoryModuleType<T>> handle = new FabricRegistryHandle<>();
        handle.bind(new MemoryModuleType<>(Optional.empty()));
        return handle;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void bind(RegistryHandle<? extends MemoryModuleType<?>> handle, String name) {
        Registry.register(BuiltInRegistries.MEMORY_MODULE_TYPE, ResourceLocation.fromNamespaceAndPath(BetterBees.MOD_ID, name), handle.get());
    }
}
