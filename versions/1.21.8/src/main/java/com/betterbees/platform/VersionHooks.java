package com.betterbees.platform;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import com.betterbees.util.BeeScaleService;
import com.betterbees.gametest.ModernGameTestRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;

public final class VersionHooks {
    private VersionHooks() {}

    public static Bee createBee(ServerLevel level) {
        return EntityType.BEE.create(level, EntitySpawnReason.COMMAND);
    }

    public static void copyHiveToItem(BeehiveBlockEntity hive, ItemStack stack, HolderLookup.Provider registries) {
        stack.applyComponents(hive.collectComponents());
    }

    public static Object itemInteractionSuccess(boolean clientSide) {
        return clientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    public static Object itemInteractionPass() {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    public static void pushProfiler(Level level, String section) { Profiler.get().push(section); }
    public static void popProfiler(Level level) { Profiler.get().pop(); }
    public static boolean isNight(Level level) { return level.isDarkOutside(); }
    public static void assertTrue(GameTestHelper helper, boolean value, String message) { helper.assertTrue(value, Component.literal(message)); }
    public static void assertFalse(GameTestHelper helper, boolean value, String message) { helper.assertFalse(value, Component.literal(message)); }
    public static <T> void assertValueEqual(GameTestHelper helper, T actual, T expected, String message) {
        helper.assertValueEqual(actual, expected, Component.literal(message));
    }
    public static CompoundTag saveHive(BeehiveBlockEntity hive, HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        hive.saveWithoutMetadata(output);
        return output.buildResult();
    }
    public static void loadHive(BeehiveBlockEntity hive, CompoundTag tag, HolderLookup.Provider registries) {
        hive.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    }
    public static CompoundTag saveBee(Bee bee) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, bee.registryAccess());
        bee.saveWithoutId(output);
        return output.buildResult();
    }
    public static boolean containsPersistentScaleModifier(CompoundTag tag) {
        return tag.toString().contains(BeeScaleService.MODIFIER_ID.toString());
    }
    public static void registerGameTests(IEventBus modBus) { ModernGameTestRegistrar.register(modBus); }
    public static <T extends BlockEntity> T getBlockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        return helper.getBlockEntity(pos, type);
    }
    public static void moveTo(Bee bee, double x, double y, double z) { bee.snapTo(x, y, z); }
}
