package com.betterbees.compat.jade;

import com.betterbees.compat.HiveOverlayData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

enum BetterBeesHiveProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("minecraft", "beehive.betterbees");
    private static final String DATA_KEY = "BetterBeesHive", HONEY_KEY = "Honey",
            HONEY_CAPACITY_KEY = "HoneyCapacity", BEES_KEY = "Bees", BEE_CAPACITY_KEY = "BeeCapacity";

    @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BeehiveBlockEntity hive)) return;
        HiveOverlayData values = HiveOverlayData.from(hive);
        CompoundTag hiveData = new CompoundTag();
        hiveData.putInt(HONEY_KEY, values.honey());
        hiveData.putInt(HONEY_CAPACITY_KEY, values.honeyCapacity());
        hiveData.putInt(BEES_KEY, values.bees());
        hiveData.putInt(BEE_CAPACITY_KEY, values.beeCapacity());
        data.put(DATA_KEY, hiveData);
    }

    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!config.get(JadeIds.MC_BEEHIVE)) return;
        CompoundTag data = accessor.getServerData().getCompound(DATA_KEY).orElse(null);
        if (!hasCompletePayload(data)) return;
        int honey = data.getInt(HONEY_KEY).orElse(0), honeyCapacity = data.getInt(HONEY_CAPACITY_KEY).orElse(0);
        int bees = data.getInt(BEES_KEY).orElse(0), beeCapacity = data.getInt(BEE_CAPACITY_KEY).orElse(0);
        IThemeHelper theme = IThemeHelper.get();
        tooltip.remove(JadeIds.MC_BEEHIVE);
        MutableComponent honeyValue = Component.translatable("jade.fraction", honey, honeyCapacity);
        MutableComponent beeValue = Component.translatable("jade.fraction", bees, beeCapacity);
        tooltip.add(Component.translatable("jade.beehive.honey", honey >= honeyCapacity ? theme.success(honeyValue) : theme.info(honeyValue)));
        tooltip.add(Component.translatable("jade.beehive.bees", bees >= beeCapacity ? theme.success(beeValue) : theme.info(beeValue)));
    }

    private static boolean hasCompletePayload(CompoundTag data) {
        return data != null && data.contains(HONEY_KEY) && data.contains(HONEY_CAPACITY_KEY)
                && data.contains(BEES_KEY) && data.contains(BEE_CAPACITY_KEY)
                && data.getInt(HONEY_CAPACITY_KEY).orElse(0) > 0 && data.getInt(BEE_CAPACITY_KEY).orElse(0) > 0;
    }
    @Override public ResourceLocation getUid() { return UID; }
    @Override public boolean isRequired() { return true; }
    @Override public int getDefaultPriority() { return 1; }
}
