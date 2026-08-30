package com.betterbees.platform;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import com.betterbees.util.BeeScaleService;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;

public final class VersionHooks {
    private VersionHooks() {}

    public static Bee createBee(ServerLevel level) {
        return EntityType.BEE.create(level);
    }

    public static void copyHiveToItem(BeehiveBlockEntity hive, ItemStack stack, HolderLookup.Provider registries) {
        hive.saveToItem(stack, registries);
    }

    public static Object itemInteractionSuccess(boolean clientSide) {
        return ItemInteractionResult.sidedSuccess(clientSide);
    }

    public static Object itemInteractionPass() {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static void pushProfiler(Level level, String section) {
        level.getProfiler().push(section);
    }

    public static void popProfiler(Level level) {
        level.getProfiler().pop();
    }

    public static boolean isNight(Level level) {
        return level.isNight();
    }

    public static void assertTrue(GameTestHelper helper, boolean value, String message) { helper.assertTrue(value, message); }
    public static void assertFalse(GameTestHelper helper, boolean value, String message) { helper.assertFalse(value, message); }
    public static <T> void assertValueEqual(GameTestHelper helper, T actual, T expected, String message) {
        helper.assertValueEqual(actual, expected, message);
    }
    public static CompoundTag saveHive(BeehiveBlockEntity hive, HolderLookup.Provider registries) {
        return hive.saveWithoutMetadata(registries);
    }
    public static void loadHive(BeehiveBlockEntity hive, CompoundTag tag, HolderLookup.Provider registries) {
        hive.loadWithComponents(tag, registries);
    }
    public static CompoundTag saveBee(Bee bee) {
        CompoundTag tag = new CompoundTag();
        bee.saveWithoutId(tag);
        return tag;
    }
    public static boolean containsPersistentScaleModifier(CompoundTag tag) {
        return tag.getList("attributes", CompoundTag.TAG_COMPOUND).toString().contains(BeeScaleService.MODIFIER_ID.toString());
    }
    public static void registerGameTests(IEventBus modBus) {}
    @SuppressWarnings("unchecked") public static <T extends BlockEntity> T getBlockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        return (T) helper.getBlockEntity(pos);
    }
    public static void moveTo(Bee bee, double x, double y, double z) { bee.moveTo(x, y, z); }
}
