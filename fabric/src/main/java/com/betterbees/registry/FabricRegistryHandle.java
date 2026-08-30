package com.betterbees.registry;

final class FabricRegistryHandle<T> implements RegistryHandle<T> {
    private T value;
    void bind(T value) { this.value = value; }
    @Override public T get() {
        if (value == null) throw new IllegalStateException("Better Bees registry handle was resolved before initialization");
        return value;
    }
}
