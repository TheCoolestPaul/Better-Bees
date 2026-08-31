package com.betterbees.compat.jade;

import com.betterbees.BetterBees;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("jade")
public final class BetterBeesJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BetterBeesHiveDataProvider.INSTANCE, BeehiveBlockEntity.class);
        BetterBees.LOGGER.info("Better Bees Jade server registration complete");
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BetterBeesHiveProvider.INSTANCE, BeehiveBlock.class);
        BetterBees.LOGGER.info("Better Bees Jade client registration complete");
    }
}
