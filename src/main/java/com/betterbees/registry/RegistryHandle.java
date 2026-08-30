package com.betterbees.registry;

import java.util.function.Supplier;

/** Loader-neutral handle resolved after the owning loader commits registries. */
@FunctionalInterface
public interface RegistryHandle<T> extends Supplier<T> {
    @Override T get();
}
