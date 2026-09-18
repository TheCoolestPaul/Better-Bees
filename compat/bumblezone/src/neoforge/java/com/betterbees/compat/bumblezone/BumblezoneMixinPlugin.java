package com.betterbees.compat.bumblezone;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import net.neoforged.fml.loading.FMLLoader;
public final class BumblezoneMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        boolean present = FMLLoader.getLoadingModList().getModFileById("the_bumblezone") != null;
        return applies(mixin, present);
    }
    private static boolean applies(String mixin, boolean present) {
        // The main config uses this guard only to exchange its release redirect for the wrapper.
        if (mixin.startsWith("com.betterbees.mixin.")) {
            return !mixin.endsWith(".BeehiveReleaseContextMixin") || !present;
        }
        return present;
    }
    @Override public void onLoad(String pkg) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
