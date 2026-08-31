package com.betterbees.validation;

import com.betterbees.BetterBees;
import com.betterbees.ai.BeeAi;
import com.betterbees.hive.HiveHoneyService;
import com.betterbees.mixin.BeehiveAccessor;
import com.betterbees.platform.VersionHooks;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.BeePersistentState;
import com.betterbees.util.BeeScaleService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.Objects;

/** Explicitly enabled CI fixture; normal game sessions never enter this path. */
public final class UpgradeFixture {
    private static final BlockPos UPGRADE_HIVE_POS = new BlockPos(0, 200, 0);
    private static final BlockPos UPGRADE_CHEST_POS = UPGRADE_HIVE_POS.east();

    private UpgradeFixture() {}

    public static void runIfRequested(MinecraftServer server) {
        if (!Boolean.getBoolean("betterbees.upgradeValidation")) return;
        try {
            verify(server.overworld(), Boolean.getBoolean("betterbees.upgradeRequireExisting"));
            BetterBees.LOGGER.info("Better Bees upgrade fixture verified");
        } catch (RuntimeException error) {
            BetterBees.LOGGER.error("Better Bees upgrade fixture validation failed", error);
        } finally {
            server.halt(false);
        }
    }

    public static void verify(ServerLevel level, boolean requireExisting) {
        level.getChunkAt(UPGRADE_HIVE_POS);
        if (requireExisting) {
            require(level.getBlockEntity(UPGRADE_HIVE_POS) instanceof BeehiveBlockEntity,
                    "upgrade fixture is missing after a version hop; refusing to recreate lost data");
        } else {
            // Ordinary GameTests may reuse a run directory. Seed their fixture
            // afresh; upgrade hops always take the strict branch above.
            level.removeBlock(UPGRADE_HIVE_POS, false);
            level.removeBlock(UPGRADE_CHEST_POS, false);
            level.setBlock(UPGRADE_HIVE_POS, Blocks.BEEHIVE.defaultBlockState(), 3);
            level.setBlock(UPGRADE_CHEST_POS, Blocks.CHEST.defaultBlockState(), 3);
            BeehiveBlockEntity hive = (BeehiveBlockEntity) level.getBlockEntity(UPGRADE_HIVE_POS);
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(UPGRADE_CHEST_POS);
            require(hive != null && chest != null, "upgrade fixture block entities must be created");

            HiveHoneyService.set(hive, 13);
            for (int i = 0; i < 2; i++) {
                Bee bee = VersionHooks.createBee(level);
                require(bee != null, "upgrade fixture bee must be created");
                BeeAi.initMemories(bee, level.random);
                BeePersistentState state = (BeePersistentState) bee;
                state.betterbees$restoreMemorizedHome(UPGRADE_HIVE_POS);
                state.betterbees$setHoneyCooldown(777);
                hive.storeBee(BeehiveBlockEntity.Occupant.of(bee));
                bee.discard();
            }

            ItemStack hiveItem = new ItemStack(Items.BEEHIVE);
            VersionHooks.copyHiveToItem(hive, hiveItem, level.registryAccess());
            chest.setItem(0, hiveItem);
            hive.setChanged();
            chest.setChanged();
        }

        BeehiveBlockEntity persistedHive = (BeehiveBlockEntity) level.getBlockEntity(UPGRADE_HIVE_POS);
        ChestBlockEntity persistedChest = (ChestBlockEntity) level.getBlockEntity(UPGRADE_CHEST_POS);
        require(persistedHive != null && persistedChest != null,
                "upgrade fixture must remain available after a version hop");
        requireEqual(HiveHoneyService.get(persistedHive), 13,
                "authoritative honey must survive a version hop");
        requireEqual(persistedHive.getOccupantCount(), 2,
                "stored occupants must survive a version hop");

        ItemStack persistedItem = persistedChest.getItem(0);
        require(!persistedItem.isEmpty(), "silk-touch hive fixture must survive a version hop");
        BeehiveBlockEntity itemHive = new BeehiveBlockEntity(UPGRADE_HIVE_POS, Blocks.BEEHIVE.defaultBlockState());
        itemHive.applyComponentsFromItemStack(persistedItem);
        requireEqual(HiveHoneyService.get(itemHive), 13,
                "hive-item honey must survive a version hop");
        requireEqual(itemHive.getOccupantCount(), 2,
                "hive-item occupants must survive a version hop");

        for (BeehiveBlockEntity.Occupant occupant : ((BeehiveAccessor) persistedHive).betterbees$getBees()) {
            Entity entity = occupant.createEntity(level, UPGRADE_HIVE_POS);
            require(entity instanceof Bee, "stored upgrade occupant must reconstruct as a bee");
            Bee bee = (Bee) entity;
            BeePersistentState state = (BeePersistentState) bee;
            requireEqual(state.betterbees$getMemorizedHome(), UPGRADE_HIVE_POS,
                    "memorized home must survive a version hop");
            requireEqual(state.betterbees$getHoneyCooldown(), 777,
                    "honey cooldown must survive a version hop");
            require(bee.getBrain().hasMemoryValue(ModMemoryTypes.POLLINATING_COOLDOWN.get()),
                    "Better Bees Brain must initialize after a version hop");
            AttributeInstance scale = bee.getAttribute(Attributes.SCALE);
            require(scale != null && scale.hasModifier(BeeScaleService.MODIFIER_ID),
                    "UUID-derived scale must initialize after a version hop");
            bee.discard();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void requireEqual(Object actual, Object expected, String message) {
        require(Objects.equals(actual, expected), message + ": expected " + expected + ", got " + actual);
    }
}
