package com.betterbees.client;

import com.betterbees.audio.BeeLoopSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.BeeAggressiveSoundInstance;
import net.minecraft.client.resources.sounds.BeeFlyingSoundInstance;
import net.minecraft.client.resources.sounds.BeeSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Owns only the selected vanilla loops, not a silent sound instance for every tracked bee. */
public final class BeeAudioController {
    private record Loop(Bee bee, BeeSoundInstance sound, boolean angry, long startedAt) {}
    private static final Map<Integer, Loop> playing = new HashMap<>();
    private static ClientLevel world;
    private static long tick;
    private static long nextSelection;

    private BeeAudioController() {}

    public static boolean allow(SoundInstance sound) {
        if (!(sound instanceof BeeSoundInstance) || !ClientAudioConfig.adaptive()) return true;
        // k <= 64. This also rejects vanilla's automatic replacements so they cannot duplicate managed loops.
        for (Loop loop : playing.values()) if (loop.sound == sound) return true;
        return false;
    }

    public static void reset(SoundManager manager) {
        for (Loop loop : playing.values()) manager.stop(loop.sound);
        playing.clear();
        world = null;
        nextSelection = 0;
    }

    public static void tick(Minecraft client) {
        if (!ClientAudioConfig.adaptive()) return;
        SoundManager manager = client.getSoundManager();
        if (world != client.level) {
            reset(manager);
            world = client.level;
        }
        if (world == null || client.isPaused()) return;
        tick++;
        boolean audible = client.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                && client.options.getSoundSourceVolume(SoundSource.NEUTRAL) > 0;
        if (!audible) {
            if (!playing.isEmpty()) reset(manager);
            return;
        }
        Vec3 listener = ClientVersionHooks.listenerPosition(client);
        if (tick >= nextSelection) {
            nextSelection = tick + 5;
            BeeLoopSelector selector = new BeeLoopSelector(ClientAudioConfig.maxLoops());
            for (Entity entity : world.entitiesForRendering()) {
                if (entity instanceof Bee bee && eligible(bee, listener)) {
                    selector.offer(bee.getId(), bee.isAngry(), bee.position().distanceToSqr(listener), playing.containsKey(bee.getId()));
                }
            }
            Set<Integer> selected = selector.selected();
            for (Iterator<Map.Entry<Integer, Loop>> iterator = playing.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<Integer, Loop> entry = iterator.next();
                if (!selected.contains(entry.getKey())) {
                    manager.stop(entry.getValue().sound);
                    iterator.remove();
                }
            }
            for (int id : selected) {
                if (!playing.containsKey(id) && world.getEntity(id) instanceof Bee bee) start(manager, bee);
            }
        }
        // Cheap per-tick maintenance of at most k sounds, including immediate angry/calm transitions.
        for (Iterator<Loop> iterator = playing.values().iterator(); iterator.hasNext();) {
            Loop loop = iterator.next();
            if (!eligible(loop.bee, listener)) {
                manager.stop(loop.sound);
                iterator.remove();
            } else if (loop.angry != loop.bee.isAngry() || loop.sound.isStopped()
                    || tick - loop.startedAt >= 20 && !manager.isActive(loop.sound)) {
                manager.stop(loop.sound);
                start(manager, loop.bee); // Replaces an existing value, without structurally modifying the map.
            }
        }
    }

    private static boolean eligible(Bee bee, Vec3 listener) {
        // Vanilla's speed-based buzz stays below volume 1 and uses the default 16-block attenuation radius.
        return !bee.isRemoved() && !bee.isSilent() && bee.getDeltaMovement().horizontalDistanceSqr() >= 0.0001D
                && bee.position().distanceToSqr(listener) < 16.0D * 16.0D;
    }

    private static void start(SoundManager manager, Bee bee) {
        boolean angry = bee.isAngry();
        BeeSoundInstance sound = angry ? new BeeAggressiveSoundInstance(bee) : new BeeFlyingSoundInstance(bee);
        playing.put(bee.getId(), new Loop(bee, sound, angry, tick));
        manager.queueTickingSound(sound);
    }
}
