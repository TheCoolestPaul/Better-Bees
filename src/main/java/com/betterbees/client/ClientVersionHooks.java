package com.betterbees.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class ClientVersionHooks {
    private ClientVersionHooks() {}
    public static Vec3 listenerPosition(Minecraft client) { return client.gameRenderer.getMainCamera().getPosition(); }
}
