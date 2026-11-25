package com.ssakuracotero.umbralib.internal.client.swing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.ssakuracotero.umbralib.api.client.swing.WeaponSwingAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Registry and stage dispatch behind {@code WeaponSwingRenderer}. Internal: not API, may change in any release. */
public final class WeaponSwingDispatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Item, WeaponSwingAnimation> BY_ITEM = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    /** Distinct implementations, for once-per-frame and reset stages. Replaced on every registration. */
    private static volatile List<WeaponSwingAnimation> distinct = List.of();

    private WeaponSwingDispatcher() { }

    public static synchronized void register(Item item, WeaponSwingAnimation animation) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(animation, "animation");
        WeaponSwingAnimation previous = BY_ITEM.put(item, animation);
        if (previous != null && previous != animation) {
            LOGGER.warn("Weapon swing animation for {} replaced: {} -> {}", item,
                    previous.getClass().getName(), animation.getClass().getName());
        }
        Set<WeaponSwingAnimation> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        unique.addAll(BY_ITEM.values());
        distinct = List.copyOf(new ArrayList<>(unique));
    }

    @Nullable
    public static WeaponSwingAnimation find(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : BY_ITEM.get(stack.getItem());
    }

    public static void swingStarted(LivingEntity entity, InteractionHand hand, int durationTicks) {
        if (entity == null || hand == null) return;
        ItemStack stack = entity.getItemInHand(hand);
        WeaponSwingAnimation animation = find(stack);
        if (animation == null) return;
        run(animation, "onSwingStart", () -> animation.onSwingStart(
                new WeaponSwingAnimation.SwingStart(entity, hand, stack, durationTicks)));
    }

    public static boolean suppressesVanillaSwing(LivingEntity entity) {
        if (entity == null) return false;
        InteractionHand hand = swingingHand(entity);
        ItemStack stack = entity.getItemInHand(hand);
        WeaponSwingAnimation animation = find(stack);
        return animation != null
                && test(animation, "replacesVanillaSwing", () -> animation.replacesVanillaSwing(entity, hand, stack));
    }

    public static boolean poseModel(LivingEntity entity, HumanoidModel<?> model, float attackProgress) {
        if (entity == null || model == null || BY_ITEM.isEmpty()) return false;
        float partial = Minecraft.getInstance().getFrameTime();
        boolean posed = false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = entity.getItemInHand(hand);
            WeaponSwingAnimation animation = find(stack);
            if (animation == null) continue;
            posed |= run(animation, "poseModel", () -> animation.poseModel(
                    new WeaponSwingAnimation.ModelPose(entity, model, hand, stack, attackProgress, partial)));
        }
        return posed;
    }

    public static boolean renderHeldItem(LivingEntity entity, ArmedModel model, ItemStack stack,
                                         ItemDisplayContext display, HumanoidArm arm, PoseStack pose,
                                         MultiBufferSource buffers, int packedLight) {
        if (entity == null || arm == null || pose == null || buffers == null) return false;
        WeaponSwingAnimation animation = find(stack);
        if (animation == null) return false;
        InteractionHand hand = arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        return test(animation, "renderHeldItem", () -> animation.renderHeldItem(new WeaponSwingAnimation.HeldItem(
                entity, model, hand, arm, stack, display, pose, buffers, packedLight)));
    }

    public static boolean firstPerson(AbstractClientPlayer player, InteractionHand hand, ItemStack stack,
                                      float swingProgress, float equipProgress, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, int packedLight) {
        if (player == null || hand == null || pose == null || buffers == null) return false;
        WeaponSwingAnimation animation = find(stack);
        if (animation == null) return false;
        return test(animation, "renderFirstPerson", () -> animation.renderFirstPerson(
                new WeaponSwingAnimation.FirstPerson(player, hand, stack, swingProgress, equipProgress, partialTick,
                        pose, buffers, packedLight)));
    }

    public static void renderWorld(RenderLevelStageEvent event) {
        List<WeaponSwingAnimation> animations = distinct;
        if (animations.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) return;
        WeaponSwingAnimation.WorldFrame frame = new WeaponSwingAnimation.WorldFrame(level, event.getCamera(),
                event.getPoseStack(), event.getProjectionMatrix(), event.getPartialTick(),
                client.renderBuffers().bufferSource());
        for (WeaponSwingAnimation animation : animations) {
            run(animation, "renderWorld", () -> animation.renderWorld(frame));
        }
    }

    public static void reset() {
        for (WeaponSwingAnimation animation : distinct) run(animation, "reset", animation::reset);
    }

    private static InteractionHand swingingHand(LivingEntity entity) {
        return entity.swingingArm == null ? InteractionHand.MAIN_HAND : entity.swingingArm;
    }

    private static boolean run(WeaponSwingAnimation animation, String stage, Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException failure) {
            report(animation, stage, failure);
            return false;
        }
    }

    private static boolean test(WeaponSwingAnimation animation, String stage, BooleanSupplier action) {
        try {
            return action.getAsBoolean();
        } catch (RuntimeException failure) {
            report(animation, stage, failure);
            return false;
        }
    }

    private static void report(WeaponSwingAnimation animation, String stage, RuntimeException failure) {
        String name = animation.getClass().getName();
        if (REPORTED.add(name + '#' + stage)) {
            LOGGER.error("Weapon swing animation {} failed in {}; this call falls back to vanilla "
                    + "(further failures of this stage are not logged)", name, stage, failure);
        }
    }
}
