package com.betterbees.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;

/** Wraps the resolved behavior, preserving other mods' handling of non-hive targets. */
public final class HoneyBottleDispenseBehavior implements DispenseItemBehavior {
    private final DispenseItemBehavior delegate;

    public HoneyBottleDispenseBehavior(DispenseItemBehavior delegate) {
        this.delegate = delegate;
    }

    @Override
    public ItemStack dispense(BlockSource source, ItemStack stack) {
        BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        if (!(source.level().getBlockState(pos).getBlock() instanceof BeehiveBlock)
                || !(source.level().getBlockEntity(pos) instanceof BeehiveBlockEntity hive)) {
            return delegate.dispense(source, stack);
        }
        return new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack bottles) {
                setSuccess(false);
                if (!HiveHoneyService.consume(hive)) return super.execute(source, bottles);
                setSuccess(true);
                hive.emptyAllLivingFromHive(null, hive.getBlockState(), BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                source.level().gameEvent(null, GameEvent.FLUID_PICKUP, source.pos());
                return consumeWithRemainder(source, bottles, new ItemStack(Items.HONEY_BOTTLE));
            }
        }.dispense(source, stack);
    }
}
