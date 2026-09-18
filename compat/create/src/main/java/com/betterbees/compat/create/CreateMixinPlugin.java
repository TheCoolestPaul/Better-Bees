package com.betterbees.compat.create;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CreateMixinPlugin implements IMixinConfigPlugin {
    private boolean logged;
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        boolean enabled = FMLLoader.getLoadingModList().getModFileById("create") != null;
        if (enabled && !logged) {
            org.slf4j.LoggerFactory.getLogger("BetterBees/Create").info("Better Bees Create integration enabled");
            logged = true;
        }
        return enabled;
    }
    @Override public void onLoad(String pkg) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
