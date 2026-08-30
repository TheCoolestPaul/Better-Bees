package com.betterbees.mixin;

import com.betterbees.hive.HiveHoneyService;
import com.betterbees.platform.VersionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.ItemAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BeehiveBlock.class)
public abstract class BeehiveBlockMixin {
    @Inject(method = "getAnalogOutputSignal", at = @At("HEAD"), cancellable = true)
    private void betterbees$scaledComparator(BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) {
            cir.setReturnValue(HiveHoneyService.comparatorLevel(HiveHoneyService.get(hive)));
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void betterbees$incrementalHarvest(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                Player player, InteractionHand hand, BlockHitResult hitResult,
                                                CallbackInfoReturnable<Object> cir) {
        boolean shears = stack.canPerformAction(ItemAbilities.SHEARS_HARVEST);
        boolean bottle = stack.is(Items.GLASS_BOTTLE);
        if (!shears && !bottle) return;
        if (level.isClientSide()) {
            cir.setReturnValue(VersionHooks.itemInteractionSuccess(true));
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) || !HiveHoneyService.canHarvest(hive)) {
            cir.setReturnValue(VersionHooks.itemInteractionPass());
            return;
        }

        Item item = stack.getItem();
        if (shears) {
            level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            BeehiveBlock.popResource(level, pos, HiveHoneyService.randomHoneycomb(level.random));
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            level.gameEvent(player, GameEvent.SHEAR, pos);
        } else {
            stack.shrink(1);
            level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            ItemStack filled = new ItemStack(Items.HONEY_BOTTLE);
            if (stack.isEmpty()) player.setItemInHand(hand, filled);
            else if (!player.getInventory().add(filled)) player.drop(filled, false);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        }

        HiveHoneyService.consume(hive);
        player.awardStat(Stats.ITEM_USED.get(item));
        if (!CampfireBlock.isSmokeyPos(level, pos)) {
            if (!hive.isEmpty()) betterbees$angerNearbyBees(level, pos);
            hive.emptyAllLivingFromHive(player, level.getBlockState(pos), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }
        cir.setReturnValue(VersionHooks.itemInteractionSuccess(false));
    }

    @Unique
    private static void betterbees$angerNearbyBees(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(8.0, 6.0, 8.0);
        List<Player> players = level.getEntitiesOfClass(Player.class, area);
        if (players.isEmpty()) return;
        for (Bee bee : level.getEntitiesOfClass(Bee.class, area)) {
            if (bee.getTarget() == null) bee.setTarget(players.get(level.random.nextInt(players.size())));
        }
    }
}
