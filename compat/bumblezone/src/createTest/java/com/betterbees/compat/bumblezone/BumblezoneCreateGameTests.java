package com.betterbees.compat.bumblezone;

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
public final class BumblezoneCreateGameTests {
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
    public static void createDeployerHarvestsWithoutDisturbingBeesWithCombCutter(GameTestHelper helper) throws Exception {
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
        player.setItemInHand(InteractionHand.MAIN_HAND, BumblezoneGameTests.enchantedShears(helper));
        activate.invoke(deployer);
        helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 1, "deployer damages shears once");
        helper.assertValueEqual(HiveHoneyService.get(hive), 0, "shears cost one harvest");
        helper.assertValueEqual(hive.getOccupantCount(), 4, "shears preserve bees without smoke");
        helper.assertTrue(player.getInventory().countItem(Items.HONEYCOMB) >= 7, "Create collects base and Comb Cutter drops");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void createMountedDeployerHarvestsWithCombCutter(GameTestHelper helper) {
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
        player.setItemInHand(InteractionHand.MAIN_HAND, BumblezoneGameTests.enchantedShears(helper));
        behaviour.visitNewPosition(context, hive.getBlockPos());
        helper.assertValueEqual(HiveHoneyService.get(hive), 0, "mounted harvests cost exactly twice");
        helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 1, "mounted shears lose one durability");
        helper.assertValueEqual(hive.getOccupantCount(), 1, "mounted harvesting preserves occupants");
        helper.assertTrue(collected.getStackInSlot(0).is(Items.HONEYCOMB), "mounted output goes to contraption inventory");
        helper.assertTrue(collected.getStackInSlot(0).getCount() >= 7 && collected.getStackInSlot(0).getCount() <= 9,
                "mounted output includes exactly one Comb Cutter bonus");
        behaviour.stopMoving(context);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bumblezoneContraptionPreservesOverCapacityHive(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var hive = hive(helper, 27);
        var storage = (HiveHoneyStorage) hive;
        storage.betterbees$setLoadingOccupants(true);
        for (int i = 0; i < 24; i++) {
            var bee = com.telepathicgrunt.the_bumblezone.modinit.BzEntities.VARIANT_BEE.get().create(level);
            bee.setVariant("blue_bee");
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
            helper.assertTrue(bee instanceof com.telepathicgrunt.the_bumblezone.entities.mobs.VariantBeeEntity
                    && ((com.telepathicgrunt.the_bumblezone.entities.mobs.VariantBeeEntity)bee).getVariant().equals("blue_bee"), "variant survives contraption round trip");
            helper.assertValueEqual(((com.betterbees.util.HiveMemory) bee).betterbees$getMemorizedHome(), destination,
                    "released bee remembers the moved hive");
            bee.discard();
        }
        helper.succeed();
    }
}
