package com.ssakuracotero.umbralib.api.client;

import com.ssakuracotero.umbralib.api.math.FxEnvelope;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Objects;
import java.util.UUID;

public enum FxAnchor {
    OUTLET, EYE, SERVER_ORIGIN, HIT, CHEST, FEET, HEAD, FIELD;

    /**
     * Host-supplied sample of a held item's actual emission point (e.g. a rendered wand tip) in world space.
     * Returning {@code null} means no sample this frame; {@link #outlet} then uses the body-relative fallback.
     */
    @FunctionalInterface
    public interface OutletSampler {
        Vec3 sample(ClientLevel level, UUID owner, InteractionHand hand, PoseStack view,
                    Matrix4f projection, Vec3 camera, float partial);
    }

    private static volatile OutletSampler outletSampler = (level, owner, hand, view, projection, camera, partial) -> null;

    /** Client setup, before world rendering. */
    public static void setOutletSampler(OutletSampler sampler) {
        outletSampler = Objects.requireNonNull(sampler, "sampler");
    }

    public static Vec3 outlet(ClientLevel level, LivingEntity caster, InteractionHand hand, PoseStack view,
                              Matrix4f projection, Vec3 camera, float partial) {
        Vec3 sampled = outletSampler.sample(level, caster.getUUID(), hand, view, projection, camera, partial);
        if (sampled != null) return sampled;
        Vec3 look = caster.getViewVector(partial);
        Vec3 up = caster.getUpVector(partial);
        Vec3 right = look.cross(up).normalize();
        boolean rightHand = (hand == InteractionHand.MAIN_HAND) == (caster.getMainArm() == HumanoidArm.RIGHT);
        return caster.getEyePosition(partial).add(look.scale(.55)).add(right.scale(rightHand ? .32 : -.32)).add(up.scale(-.22));
    }

    public static Vec3 bridge(Vec3 outlet, Vec3 origin, float ageTicks) {
        return outlet.lerp(origin, FxEnvelope.smooth(ageTicks / 3f));
    }

    public static Vec3 hit(Vec3 point, Vec3 normal) {
        return point.add(normal.normalize().scale(.02));
    }
}
