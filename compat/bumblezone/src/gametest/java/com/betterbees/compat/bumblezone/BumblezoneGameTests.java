package com.betterbees.compat.bumblezone;

import com.betterbees.compat.BeeCompatibility;
import com.betterbees.hive.HiveBreedingService;
import com.betterbees.hive.HiveHoneyService;
import com.betterbees.mixin.BeehiveAccessor;
import com.betterbees.platform.VersionHooks;
import com.betterbees.registry.ModMemoryTypes;
import com.betterbees.util.HiveMemory;
import com.telepathicgrunt.the_bumblezone.entities.mobs.VariantBeeEntity;
import com.telepathicgrunt.the_bumblezone.items.essence.EssenceOfTheBees;
import com.telepathicgrunt.the_bumblezone.modinit.BzEffects;
import com.telepathicgrunt.the_bumblezone.modinit.BzEnchantments;
import com.telepathicgrunt.the_bumblezone.modinit.BzEntities;
import com.telepathicgrunt.the_bumblezone.modinit.BzItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("betterbees")
@PrefixGameTestTemplate(false)
public final class BumblezoneGameTests {
    private static final BlockPos HIVE = new BlockPos(3, 2, 3);
    private BumblezoneGameTests() {}

    private static BeehiveBlockEntity hive(GameTestHelper h, int honey) {
        h.setBlock(HIVE, Blocks.BEEHIVE);
        var hive = VersionHooks.getBlockEntity(h, HIVE, BeehiveBlockEntity.class);
        HiveHoneyService.set(hive, honey);
        return hive;
    }
    public static ItemStack enchantedShears(GameTestHelper h) {
        var tool = new ItemStack(Items.SHEARS);
        tool.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, BzEnchantments.COMB_CUTTER)), 2);
        return tool;
    }
    private static VariantBeeEntity variant(GameTestHelper h, double x) {
        var bee = BzEntities.VARIANT_BEE.get().create(h.getLevel());
        bee.setVariant("blue_bee");
        var p = h.absolutePos(new BlockPos((int)x, 3, 4));
        bee.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        bee.setNoAi(true);
        h.getLevel().addFreshEntity(bee);
        return bee;
    }
    private static net.minecraft.server.level.ServerPlayer serverPlayer(GameTestHelper h) {
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "bumblezone-test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        var player = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        // No login handshake: a GameTest has no modded client to receive Bumblezone's sync packets.
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player, cookie) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        var pos = h.absolutePos(HIVE.south());
        player.moveTo(pos.getX(), pos.getY(), pos.getZ());
        return player;
    }

    @GameTest(template = "empty")
    public static void combCutterSuccessfulHarvestOnly(GameTestHelper h) {
        var hive = hive(h, 1);
        var player = serverPlayer(h);
        var tool = enchantedShears(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        h.useBlock(HIVE, player);
        var box = new net.minecraft.world.phys.AABB(hive.getBlockPos()).inflate(3);
        int drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                .filter(e -> e.getItem().is(Items.HONEYCOMB)).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(drops >= 7 && drops <= 9, "base 1-3 plus exactly six Comb Cutter combs");
        h.assertValueEqual(HiveHoneyService.get(hive), 0, "single honey cost");
        h.assertValueEqual(tool.getDamageValue(), 1, "single durability cost");
        var advancement = h.getLevel().getServer().getAdvancements().get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("the_bumblezone", "combs_and_wax/comb_cutter_extra_drops"));
        h.assertTrue(advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "Comb Cutter grants its real advancement");
        h.useBlock(HIVE, player);
        int after = h.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                .filter(e -> e.getItem().is(Items.HONEYCOMB)).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertValueEqual(after, drops, "empty hive grants no bonus");
        h.assertValueEqual(tool.getDamageValue(), 1, "failed harvest does not damage shears");
        player.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void essenceProtectsHarvestAndRelease(GameTestHelper h) {
        var hive = hive(h, 3);
        hive.storeBee(BeehiveBlockEntity.Occupant.of(VersionHooks.createBee(h.getLevel())));
        var player = serverPlayer(h);
        try {
            EssenceOfTheBees.setEssence(player, true);
            h.assertTrue(BeeCompatibility.protectedPlayer(player), "essence attachment recognized");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
            h.useBlock(HIVE, player);
            h.assertValueEqual(hive.getOccupantCount(), 1, "unsmoked essence harvesting keeps bees");
            h.assertValueEqual(HiveHoneyService.get(hive), 2, "essence does not waive honey cost");
            hive.emptyAllLivingFromHive(player, hive.getBlockState(), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            h.assertValueEqual(hive.getOccupantCount(), 0, "explicit emergency release still executes");
            h.assertTrue(h.getLevel().getEntitiesOfClass(Bee.class,
                    new net.minecraft.world.phys.AABB(hive.getBlockPos()).inflate(4)).stream()
                    .noneMatch(b -> b.getTarget() == player), "Bumblezone release hook retains protection");
        } finally {
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void headwearPriorityAndInvalidation(GameTestHelper h) {
        var bee = variant(h, 4);
        var wearer = EntityType.ARMOR_STAND.create(h.getLevel());
        wearer.moveTo(bee.getX() + 8, bee.getY(), bee.getZ());
        wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(BzItems.FLOWER_HEADWEAR.get()));
        h.getLevel().addFreshEntity(wearer);
        bee.tickCount = 1;
        BeeCompatibility.beforeBrain(bee);
        h.assertTrue(((HeadwearState)bee).betterbees$headwearTarget() == wearer, "quiet bee finds non-player headwear independently");
        h.assertTrue(bee.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.ADMIRE_ITEM), "Brain owns attraction");
        bee.getBrain().setMemory(ModMemoryTypes.WANTS_HIVE.get(), true);
        BeeCompatibility.beforeBrain(bee);
        h.assertTrue(((HeadwearState)bee).betterbees$headwearTarget() == null, "home intent wins");
        bee.getBrain().eraseMemory(ModMemoryTypes.WANTS_HIVE.get());
        bee.setInLove(null);
        BeeCompatibility.beforeBrain(bee);
        h.assertTrue(((HeadwearState)bee).betterbees$headwearTarget() == null, "breeding wins");
        bee.resetLove();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        bee.getBrain().setMemory(MemoryModuleType.TEMPTING_PLAYER, player);
        BeeCompatibility.beforeBrain(bee);
        h.assertTrue(((HeadwearState)bee).betterbees$headwearTarget() == null, "food temptation wins");
        bee.getBrain().eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        wearer.addEffect(new MobEffectInstance(BzEffects.WRATH_OF_THE_HIVE.holder(), 100));
        BeeCompatibility.beforeBrain(bee);
        h.assertTrue(((HeadwearState)bee).betterbees$headwearTarget() == null, "wrath wearer excluded");
        wearer.removeEffect(BzEffects.WRATH_OF_THE_HIVE.holder());
        BeeCompatibility.beforeBrain(bee);
        h.assertTrue(((HeadwearState)bee).betterbees$headwearTarget() == wearer, "attraction resumes");
        wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        bee.tickCount = 2;
        BeeCompatibility.beforeBrain(bee);
        h.assertTrue(((HeadwearState)bee).betterbees$headwearTarget() == null, "unequipping clears target without waiting for scan");
        bee.discard(); wearer.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void wrathAndHiddenSynchronizeBrain(GameTestHelper h) {
        var bee = variant(h, 4);
        var target = EntityType.PIG.create(h.getLevel());
        target.moveTo(bee.getX()+1, bee.getY(), bee.getZ());
        h.getLevel().addFreshEntity(target);
        bee.setNoAi(false);
        com.telepathicgrunt.the_bumblezone.effects.WrathOfTheHiveEffect.unBEElievablyHighAggression(h.getLevel(), target);
        BeeCompatibility.beforeBrain(bee);
        h.assertTrue(bee.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == target, "wrath enters Brain combat");
        target.addEffect(new MobEffectInstance(BzEffects.HIDDEN.holder(), 100, 1));
        com.telepathicgrunt.the_bumblezone.effects.WrathOfTheHiveEffect.unBEElievablyHighAggression(h.getLevel(), target);
        BeeCompatibility.beforeBrain(bee);
        h.assertTrue(!bee.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET), "Hidden clears stale Brain attack");
        target.removeEffect(BzEffects.HIDDEN.holder());
        com.telepathicgrunt.the_bumblezone.effects.WrathOfTheHiveEffect.unBEElievablyHighAggression(h.getLevel(), target);
        BeeCompatibility.beforeBrain(bee);
        com.telepathicgrunt.the_bumblezone.effects.WrathOfTheHiveEffect.calmTheBees(h.getLevel(), target);
        BeeCompatibility.beforeBrain(bee);
        h.assertTrue(!bee.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET), "calming clears Brain attack");
        bee.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void variantsBreedWithBrain(GameTestHelper h) { breed(h, false); }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void mixedBeesBreedWithBrain(GameTestHelper h) { breed(h, true); }

    private static void breed(GameTestHelper h, boolean mixed) {
        // Keep autonomous navigation inside the fixture and exclude outside mate visibility.
        for (int x = 2; x <= 7; x++) for (int y = 1; y <= 5; y++) for (int z = 2; z <= 7; z++) {
            if (x == 2 || x == 7 || y == 1 || y == 5 || z == 2 || z == 7) {
                h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        Bee first = variant(h, 4);
        Bee second = mixed ? VersionHooks.createBee(h.getLevel()) : variant(h, 5);
        second.moveTo(first.getX()+0.3, first.getY(), first.getZ());
        if (mixed) h.getLevel().addFreshEntity(second);
        for (Bee bee : List.of(first, second)) {
            bee.setNoAi(false);
            bee.setInLove(null);
            bee.getBrain().setMemory(ModMemoryTypes.POLLINATING_COOLDOWN.get(), 400);
        }
        var bounds = new net.minecraft.world.phys.AABB(
                net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(2, 1, 2))),
                net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(8, 6, 8))));
        h.succeedWhen(() -> {
            var babies = h.getLevel().getEntitiesOfClass(Bee.class, bounds, Bee::isBaby);
            h.assertTrue(!babies.isEmpty(), "Brain produces a child; first=" + first.position()
                    + ", second=" + second.position() + ", love=" + first.isInLove() + "/" + second.isInLove()
                    + ", home=" + first.getBrain().getMemory(ModMemoryTypes.WANTS_HIVE.get())
                    + ", breed=" + first.getBrain().getMemory(MemoryModuleType.BREED_TARGET));
            if (!mixed) h.assertTrue(babies.stream().allMatch(b -> b instanceof VariantBeeEntity), "variant pair retains entity type");
            h.assertTrue(babies.stream().allMatch(b -> b.getBrain().hasMemoryValue(ModMemoryTypes.POLLINATING_COOLDOWN.get())), "child memories initialized");
            first.discard(); second.discard(); babies.forEach(Entity::discard);
        });
    }

    @GameTest(template = "empty")
    public static void variantsSurviveIndoorBreedingAndHiveItem(GameTestHelper h) {
        var hive = hive(h, 13);
        for (int i=0; i<2; i++) {
            var bee = variant(h, 4+i);
            hive.storeBee(BeehiveBlockEntity.Occupant.of(bee));
            bee.discard();
        }
        h.assertTrue(HiveBreedingService.tryBreed(h.getLevel(), hive.getBlockPos(), hive, h.getLevel().random, 1), "indoor variant breeding succeeds");
        var item = new ItemStack(Items.BEEHIVE);
        VersionHooks.copyHiveToItem(hive, item, h.getLevel().registryAccess());
        var restored = new BeehiveBlockEntity(hive.getBlockPos().east(3), Blocks.BEEHIVE.defaultBlockState());
        restored.applyComponentsFromItemStack(item);
        h.assertValueEqual(HiveHoneyService.get(restored), 13, "exact honey survives hive item");
        h.assertValueEqual(restored.getOccupantCount(), 3, "all variants survive hive item");
        int babies=0;
        for (var occupant : ((BeehiveAccessor)restored).betterbees$getBees()) {
            var bee = (Bee)occupant.createEntity(h.getLevel(), restored.getBlockPos());
            h.assertTrue(bee instanceof VariantBeeEntity && ((VariantBeeEntity)bee).getVariant().equals("blue_bee"), "variant inherited and serialized");
            h.assertTrue(((HiveMemory)bee).betterbees$getMemorizedHome().equals(restored.getBlockPos()), "home relocated");
            if (bee.isBaby()) babies++;
            bee.discard();
        }
        h.assertValueEqual(babies, 1, "one stored baby");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void jadeProviderReportsAuthoritativeStorage(GameTestHelper h) throws Exception {
        try { Class.forName("snownee.jade.api.BlockAccessor"); }
        catch (ClassNotFoundException absent) { h.succeed(); return; }
        var hive = hive(h, 13);
        hive.storeBee(BeehiveBlockEntity.Occupant.of(VersionHooks.createBee(h.getLevel())));
        var providerClass = Class.forName("com.betterbees.compat.jade.BetterBeesHiveDataProvider");
        var accessorClass = Class.forName("snownee.jade.api.BlockAccessor");
        Object accessor = java.lang.reflect.Proxy.newProxyInstance(accessorClass.getClassLoader(), new Class<?>[]{accessorClass},
                (proxy, method, args) -> method.getName().equals("getBlockEntity") ? hive : null);
        var method = providerClass.getDeclaredMethod("appendServerData", CompoundTag.class, accessorClass);
        method.setAccessible(true);
        for (int honey : new int[]{13, 12, 0}) {
            HiveHoneyService.set(hive, honey);
            var data = new CompoundTag();
            method.invoke(providerClass.getEnumConstants()[0], data, accessor);
            var payload = data.getCompound("BetterBeesHive");
            h.assertValueEqual(payload.getInt("Honey"), honey, "Jade provider uses authoritative honey");
            h.assertValueEqual(payload.getInt("Bees"), 1, "Jade provider counts occupants");
            h.assertValueEqual(payload.getInt("HoneyCapacity"), 20, "Jade provider uses configured capacity");
        }
        h.succeed();
    }
}
