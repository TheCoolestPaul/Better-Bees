package com.betterbees.compat.create;

import com.betterbees.hive.HiveHoneyService;
import com.betterbees.hive.HiveHoneyStorage;
import com.betterbees.platform.VersionHooks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllFluids;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.fluids.OpenEndedPipe;
import com.simibubi.create.content.fluids.pipes.VanillaFluidTargets;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("betterbees")
@PrefixGameTestTemplate(false)
public final class CreateGameTests {
    private static final BlockPos HIVE = new BlockPos(3, 2, 3);

    private static BeehiveBlockEntity hive(GameTestHelper helper, int honey) {
        helper.setBlock(HIVE, Blocks.BEEHIVE);
        var hive = VersionHooks.getBlockEntity(helper, HIVE, BeehiveBlockEntity.class);
        HiveHoneyService.set(hive, honey);
        return hive;
    }

    private static OpenEndedPipe pipe(GameTestHelper helper, BeehiveBlockEntity hive) {
        var pipe = new OpenEndedPipe(new BlockFace(hive.getBlockPos().south(), Direction.NORTH));
        pipe.manageSource(helper.getLevel(), hive);
        return pipe;
    }

    @GameTest(template = "empty")
    public static void createPipesConserveHoney(GameTestHelper helper) {
        var hive = hive(helper, 2);
        var pipe = pipe(helper, hive);
        var handler = pipe.provideHandler().getCapability();
        var state = hive.getBlockState();
        helper.assertValueEqual(handler.drain(1000, FluidAction.SIMULATE).getAmount(), 250, "simulation capped to one batch");
        helper.assertValueEqual(HiveHoneyService.get(hive), 2, "simulation leaves honey");
        helper.assertValueEqual(hive.getBlockState(), state, "simulation leaves display");
        helper.assertTrue(handler.drain(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE).isEmpty(), "wrong fluid rejected");
        helper.assertValueEqual(HiveHoneyService.get(hive), 2, "wrong filter leaves honey");
        helper.assertTrue(handler.drain(0, FluidAction.EXECUTE).isEmpty(), "zero request rejected");
        helper.assertValueEqual(handler.drain(100, FluidAction.EXECUTE).getAmount(), 100, "partial batch delivered");
        helper.assertValueEqual(HiveHoneyService.get(hive), 1, "one harvest charged");
        // Create serializes the unreturned 150 mB in its own buffer, not the hive.
        var saved = pipe.serializeNBT(helper.getLevel().registryAccess());
        pipe = OpenEndedPipe.fromNBT(saved, helper.getLevel().registryAccess(), hive.getBlockPos().south());
        pipe.manageSource(helper.getLevel(), hive);
        handler = pipe.provideHandler().getCapability();
        helper.assertTrue(handler.drain(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE).isEmpty(), "buffer filter rejects water");
        helper.assertValueEqual(handler.drain(1000, FluidAction.EXECUTE).getAmount(), 150, "buffer survives reload");
        helper.assertValueEqual(HiveHoneyService.get(hive), 1, "buffer draining costs no additional honey");
        var secondPump = pipe(helper, hive).provideHandler().getCapability();
        helper.assertValueEqual(secondPump.drain(1000, FluidAction.EXECUTE).getAmount(), 250, "second pump gets remaining batch only");
        helper.assertTrue(handler.drain(1000, FluidAction.EXECUTE).isEmpty(), "exhausted hive returns no fluid");
        helper.assertValueEqual(HiveHoneyService.get(hive), 0, "two batches consume two harvests");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createDrainUsesStorageAndLeavesOtherTargets(GameTestHelper helper) {
        var hive = hive(helper, 1);
        var level = helper.getLevel();
        var pos = hive.getBlockPos();
        helper.assertValueEqual(VanillaFluidTargets.drainBlock(level, pos, hive.getBlockState(), false).getAmount(), 250, "display-zero honey can drain");
        level.setBlockAndUpdate(pos, hive.getBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
        helper.assertTrue(VanillaFluidTargets.drainBlock(level, pos, hive.getBlockState(), false).isEmpty(), "stale full display cannot create fluid");
        helper.setBlock(HIVE, Blocks.LAVA_CAULDRON);
        helper.assertValueEqual(VanillaFluidTargets.drainBlock(level, pos, level.getBlockState(pos), false).getAmount(), 1000, "cauldrons unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createPipesRespectConfiguredHarvestCost(GameTestHelper helper) throws Exception {
        var field = com.betterbees.config.BetterBeesConfig.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        var saved = com.betterbees.config.BetterBeesConfig.snapshot();
        var d = saved;
        try {
            field.set(null, new com.betterbees.config.ConfigSnapshot(d.maxWanderRadius(), d.flowerLocateRange(),
                    d.searchAttempts(), d.flowerScanBudget(), d.flowerCacheSize(), d.hivePathFailuresBeforeBlacklist(),
                    d.hiveCapacity(), 5, 5, 1, 3, false, d.breedingIntervalTicks(), d.breedingChance(),
                    d.minimumBeeScale(), d.maximumBeeScale(), d.hiveTransitionIntervalTicks(), d.adaptiveEntitySensing()));
            var hive = hive(helper, 4);
            var handler = pipe(helper, hive).provideHandler().getCapability();
            helper.assertTrue(handler.drain(250, FluidAction.EXECUTE).isEmpty(), "below configured harvest cost");
            helper.assertValueEqual(HiveHoneyService.get(hive), 4, "failed drain leaves honey");
            HiveHoneyService.set(hive, 5);
            helper.assertValueEqual(handler.drain(1000, FluidAction.EXECUTE).getAmount(), 250, "configured cost still yields one bottle");
            helper.assertValueEqual(HiveHoneyService.get(hive), 0, "configured five-honey cost applied");
        } finally {
            field.set(null, saved);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createDeployerHarvestsWithoutDisturbingBees(GameTestHelper helper) throws Exception {
        var hive = hive(helper, 2);
        for (int i = 0; i < 4; i++) hive.storeBee(BeehiveBlockEntity.Occupant.of(VersionHooks.createBee(helper.getLevel())));
        var pos = HIVE.south(2);
        helper.setBlock(pos, AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        var deployer = VersionHooks.getBlockEntity(helper, pos, DeployerBlockEntity.class);
        deployer.initialize();
        var player = deployer.getPlayer();
        helper.assertTrue(player != null, "real deployer initializes fake player");
        var activate = DeployerBlockEntity.class.getDeclaredMethod("activate");
        activate.setAccessible(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        activate.invoke(deployer);
        helper.assertTrue(player.getMainHandItem().is(Items.HONEY_BOTTLE), "deployer fills bottle");
        helper.assertValueEqual(HiveHoneyService.get(hive), 1, "bottle costs one harvest");
        helper.assertValueEqual(hive.getOccupantCount(), 4, "deployer preserves bees without smoke");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        activate.invoke(deployer);
        helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 1, "deployer damages shears once");
        helper.assertValueEqual(HiveHoneyService.get(hive), 0, "shears cost one harvest");
        helper.assertValueEqual(hive.getOccupantCount(), 4, "shears preserve bees without smoke");
        helper.assertTrue(player.getInventory().countItem(Items.HONEYCOMB) >= 1, "Create collects honeycomb drops");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createHoneyProcessingRoundTrip(GameTestHelper helper) {
        var level = helper.getLevel();
        var emptied = GenericItemEmptying.emptyItem(level, new ItemStack(Items.HONEY_BOTTLE), false);
        helper.assertValueEqual(emptied.getFirst().getAmount(), 250, "honey bottle drains to 250 mB");
        helper.assertTrue(emptied.getSecond().is(Items.GLASS_BOTTLE), "emptying returns bottle");
        var honey = new FluidStack(AllFluids.HONEY.get().getSource(), 250);
        helper.assertValueEqual(FillingBySpout.getRequiredAmountForItem(level, new ItemStack(Items.GLASS_BOTTLE), honey), 250, "spout needs 250 mB");
        helper.assertTrue(FillingBySpout.fillItem(level, 250, new ItemStack(Items.GLASS_BOTTLE), honey).is(Items.HONEY_BOTTLE), "spout refills bottle");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createMountedDeployerHarvests(GameTestHelper helper) {
        var hive = hive(helper, 2);
        hive.storeBee(BeehiveBlockEntity.Occupant.of(VersionHooks.createBee(helper.getLevel())));
        var contraption = new BearingContraption(false, Direction.UP);
        contraption.getStorage().initialize();
        var collected = new net.neoforged.neoforge.items.ItemStackHandler(9);
        contraption.getStorage().attachExternal(collected);
        var state = AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Mode", "USE");
        var info = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(BlockPos.ZERO, state, tag);
        var context = new com.simibubi.create.content.contraptions.behaviour.MovementContext(helper.getLevel(), info, contraption);
        context.position = net.minecraft.world.phys.Vec3.atCenterOf(hive.getBlockPos());
        context.rotation = vector -> vector;
        context.data.put("HeldItem", new ItemStack(Items.GLASS_BOTTLE).save(helper.getLevel().registryAccess()));
        var behaviour = new com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour();
        behaviour.visitNewPosition(context, hive.getBlockPos());
        var player = (com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer) context.temporaryData;
        helper.assertTrue(player.getMainHandItem().is(Items.HONEY_BOTTLE), "mounted deployer fills bottle");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        behaviour.visitNewPosition(context, hive.getBlockPos());
        helper.assertValueEqual(HiveHoneyService.get(hive), 0, "mounted harvests cost exactly twice");
        helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 1, "mounted shears lose one durability");
        helper.assertValueEqual(hive.getOccupantCount(), 1, "mounted harvesting preserves occupants");
        helper.assertTrue(collected.getStackInSlot(0).is(Items.HONEYCOMB), "mounted output goes to contraption inventory");
        behaviour.stopMoving(context);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createContraptionPreservesOverCapacityHive(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var hive = hive(helper, 27);
        var storage = (HiveHoneyStorage) hive;
        storage.betterbees$setLoadingOccupants(true);
        for (int i = 0; i < 24; i++) {
            var bee = VersionHooks.createBee(level);
            ((com.betterbees.util.HiveMemory) bee).betterbees$setMemorizedHome(hive.getBlockPos());
            hive.storeBee(BeehiveBlockEntity.Occupant.of(bee));
        }
        storage.betterbees$setLoadingOccupants(false);
        var moving = new BearingContraption(false, Direction.UP);
        helper.assertTrue(moving.assemble(level, hive.getBlockPos().below()), "hive assembles");
        moving.getStorage().initialize();
        moving.removeBlocksFromWorld(level, BlockPos.ZERO);
        var restored = Contraption.fromNBT(level, moving.writeNBT(level.registryAccess(), false), false);
        var destination = helper.absolutePos(HIVE.east(4));
        restored.addBlocksToWorld(level, new StructureTransform(destination, 0, 0, 0));
        var placed = (BeehiveBlockEntity) level.getBlockEntity(destination);
        helper.assertTrue(placed != null, "hive placed after moving");
        helper.assertValueEqual(HiveHoneyService.get(placed), 27, "over-capacity honey retained");
        helper.assertValueEqual(placed.getOccupantCount(), 24, "over-capacity occupants retained");
        helper.assertTrue(placed != hive, "placement creates fresh transient hive state");
        var released = ((com.betterbees.mixin.BeehiveAccessor) placed).betterbees$releaseAllOccupants(
                placed.getBlockState(), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        helper.assertValueEqual(released.size(), 24, "moved occupants can leave hive");
        for (var bee : released) {
            helper.assertValueEqual(((com.betterbees.util.HiveMemory) bee).betterbees$getMemorizedHome(), destination,
                    "released bee remembers the moved hive");
            bee.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createMountedDeployerHonorsBottleFilter(GameTestHelper helper) {
        var hive = hive(helper, 1);
        var contraption = new BearingContraption(false, Direction.UP);
        contraption.getStorage().initialize();
        var inventory = new net.neoforged.neoforge.items.ItemStackHandler(3);
        inventory.setStackInSlot(0, new ItemStack(Items.STONE));
        inventory.setStackInSlot(1, new ItemStack(Items.GLASS_BOTTLE, 2));
        contraption.getStorage().attachExternal(inventory);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Mode", "USE");
        tag.put("Filter", com.simibubi.create.content.logistics.filter.FilterItemStack.of(new ItemStack(Items.GLASS_BOTTLE))
                .serializeNBT(helper.getLevel().registryAccess()));
        var state = AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH);
        var info = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(BlockPos.ZERO, state, tag);
        var context = new com.simibubi.create.content.contraptions.behaviour.MovementContext(helper.getLevel(), info, contraption);
        context.position = net.minecraft.world.phys.Vec3.atCenterOf(hive.getBlockPos());
        context.rotation = vector -> vector;
        var behaviour = new com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour();
        behaviour.visitNewPosition(context, hive.getBlockPos());
        helper.assertTrue(inventory.getStackInSlot(0).is(Items.STONE), "filter skips unrelated input");
        helper.assertValueEqual(inventory.getStackInSlot(1).getCount(), 1, "filter extracts exactly one empty bottle");
        helper.assertTrue(inventory.getStackInSlot(2).is(Items.HONEY_BOTTLE), "filled output collected separately");
        helper.assertValueEqual(HiveHoneyService.get(hive), 0, "filtered harvest consumes cost");
        behaviour.stopMoving(context);
        helper.succeed();
    }
}
