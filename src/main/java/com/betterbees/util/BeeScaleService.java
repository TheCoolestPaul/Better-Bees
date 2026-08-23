package com.betterbees.util;

import com.betterbees.BetterBees;
import com.betterbees.config.BetterBeesConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;

import java.util.UUID;

public final class BeeScaleService {
    public static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(BetterBees.MOD_ID, "individual_bee_scale");

    private static final double TWO_TO_NEGATIVE_53 = 0x1.0p-53;

    private BeeScaleService() {}

    public static double percentile(UUID uuid) {
        long mixed = uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 32);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * TWO_TO_NEGATIVE_53;
    }

    public static float scale(UUID uuid, double minimum, double maximum) {
        double effectiveMinimum = Math.min(minimum, maximum);
        double effectiveMaximum = Math.max(minimum, maximum);
        return (float) (effectiveMinimum + percentile(uuid) * (effectiveMaximum - effectiveMinimum));
    }

    public static float configuredScale(UUID uuid) {
        return scale(uuid, BetterBeesConfig.minimumBeeScale(), BetterBeesConfig.maximumBeeScale());
    }

    public static boolean apply(Bee bee) {
        if (bee.level().isClientSide()) return false;
        AttributeInstance scale = bee.getAttribute(Attributes.SCALE);
        if (scale == null) return false;

        float individualScale = configuredScale(bee.getUUID());
        scale.addOrUpdateTransientModifier(new AttributeModifier(
                MODIFIER_ID,
                individualScale - 1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
        bee.refreshDimensions();
        return true;
    }
}
