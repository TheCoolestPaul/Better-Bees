package com.betterbees.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

enum BetterBeesHiveProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("minecraft", "beehive.betterbees");
    private static final String DATA_KEY = "BetterBeesHive";
    private static final String HONEY_KEY = "Honey";
    private static final String HONEY_CAPACITY_KEY = "HoneyCapacity";
    private static final String BEES_KEY = "Bees";
    private static final String BEE_CAPACITY_KEY = "BeeCapacity";

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!config.get(JadeIds.MC_BEEHIVE) || !accessor.getServerData().contains(DATA_KEY)) {
            return;
        }

        CompoundTag data = accessor.getServerData().getCompound(DATA_KEY);
        if (!hasCompletePayload(data)) {
            return;
        }

        int honey = data.getInt(HONEY_KEY);
        int honeyCapacity = data.getInt(HONEY_CAPACITY_KEY);
        int bees = data.getInt(BEES_KEY);
        int beeCapacity = data.getInt(BEE_CAPACITY_KEY);
        IThemeHelper theme = IThemeHelper.get();

        // Jade tags every line from its vanilla provider with this UID. Only
        // replace it after receiving complete authoritative server data.
        tooltip.remove(JadeIds.MC_BEEHIVE);
        MutableComponent honeyValue = Component.translatable("jade.fraction", honey, honeyCapacity);
        MutableComponent beeValue = Component.translatable("jade.fraction", bees, beeCapacity);
        tooltip.add(Component.translatable(
                "jade.beehive.honey",
                honey >= honeyCapacity ? theme.success(honeyValue) : theme.info(honeyValue)));
        tooltip.add(Component.translatable(
                "jade.beehive.bees",
                bees >= beeCapacity ? theme.success(beeValue) : theme.info(beeValue)));
    }

    private static boolean hasCompletePayload(CompoundTag data) {
        return data.contains(HONEY_KEY)
                && data.contains(HONEY_CAPACITY_KEY)
                && data.contains(BEES_KEY)
                && data.contains(BEE_CAPACITY_KEY)
                && data.getInt(HONEY_CAPACITY_KEY) > 0
                && data.getInt(BEE_CAPACITY_KEY) > 0;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public boolean isRequired() {
        // Visibility is controlled by Jade's existing minecraft:beehive option.
        return true;
    }

    @Override
    public int getDefaultPriority() {
        return 1;
    }
}
