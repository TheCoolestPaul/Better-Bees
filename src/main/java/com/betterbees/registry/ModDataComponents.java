package com.betterbees.registry;

import com.betterbees.BetterBees;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(BetterBees.MOD_ID);

    public static final RegistryHandle<DataComponentType<Integer>> HONEY = registerHoney();

    private static RegistryHandle<DataComponentType<Integer>> registerHoney() {
        var holder = REGISTER.<Integer>registerComponentType("honey", builder -> builder
                    .persistent(Codec.intRange(0, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
        return holder::get;
    }

    private ModDataComponents() {}
}
