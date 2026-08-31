package com.betterbees.compat.jade;

import com.betterbees.compat.HiveOverlayData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

// Keep server registration independent of Jade's client component interfaces.
enum BetterBeesHiveDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BeehiveBlockEntity hive)) return;
        HiveOverlayData values = HiveOverlayData.from(hive);
        CompoundTag hiveData = new CompoundTag();
        hiveData.putInt("Honey", values.honey());
        hiveData.putInt("HoneyCapacity", values.honeyCapacity());
        hiveData.putInt("Bees", values.bees());
        hiveData.putInt("BeeCapacity", values.beeCapacity());
        data.put("BetterBeesHive", hiveData);
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "beehive.betterbees");
    }
}
