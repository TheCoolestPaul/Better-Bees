package com.betterbees.registry;

import com.betterbees.BetterBees;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

public final class ModDataComponents {
    private static final FabricRegistryHandle<DataComponentType<Integer>> HONEY_HANDLE = new FabricRegistryHandle<>();
    public static final RegistryHandle<DataComponentType<Integer>> HONEY = HONEY_HANDLE;
    private static boolean initialized;

    private ModDataComponents() {}
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        DataComponentType<Integer> type = DataComponentType.<Integer>builder()
                .persistent(Codec.intRange(0, Integer.MAX_VALUE)).networkSynchronized(ByteBufCodecs.VAR_INT).build();
        HONEY_HANDLE.bind(Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                ResourceLocation.fromNamespaceAndPath(BetterBees.MOD_ID, "honey"), type));
    }
}
