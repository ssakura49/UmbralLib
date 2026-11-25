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

/**
 * Registry and stage dispatch behind {@code WeaponSwingRenderer}. Internal: not API, may change in any release.
 *
 * <p>An implementation is consulted for an entity while either hand holds an item bound to it, and at most once per
 * event, so it can also control the other hand (hide its item, keep it out of the swing) and is never run twice when
 * both hands hold its items.
 */
public final class WeaponSwingDispatcher {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Item, WeaponSwingAnimation> BY_ITEM = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    /** Distinct implementations, for once-per-frame and reset stages. Replaced on every registration. */
    private static volatile List<WeaponSwingAnimation> distinct = List.of();

    /** An implementation found on an entity and the hand holding its item (main hand first). */
    private record Bound(WeaponSwingAnimation animation, InteractionHand hand) { }

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

    /** Implementations bound to the items in either hand, each once, main hand first. */
    private static List<Bound> bound(@Nullable LivingEntity entity) {
        if (entity == null || BY_ITEM.isEmpty()) return List.of();
        WeaponSwingAnimation main = find(entity.getMainHandItem());
        WeaponSwingAnimation off = find(entity.getOffhandItem());
        if (main == null && off == null) return List.of();
        if (main == null) return List.of(new Bound(off, InteractionHand.OFF_HAND));
        if (off == null || off == main) return List.of(new Bound(main, InteractionHand.MAIN_HAND));
        return List.of(new Bound(main, InteractionHand.MAIN_HAND), new Bound(off, InteractionHand.OFF_HAND));
    }

    public static void swingStarted(LivingEntity entity, InteractionHand hand, int durationTicks) {
        if (hand == null) return;
        List<Bound> found = bound(entity);
        if (found.isEmpty()) return;
        ItemStack stack = entity.getItemInHand(hand);
        WeaponSwingAnimation.SwingStart swing = new WeaponSwingAnimation.SwingStart(entity, hand, stack, durationTicks);
        for (Bound b : found) run(b.animation(), "onSwingStart", () -> b.animation().onSwingStart(swing));
    }

    public static boolean suppressesVanillaSwing(LivingEntity entity) {
        List<Bound> found = bound(entity);
        if (found.isEmpty()) return false;
        InteractionHand hand = swingingHand(entity);
        ItemStack stack = entity.getItemInHand(hand);
        for (Bound b : found) {
            if (test(b.animation(), "replacesVanillaSwing", () -> b.animation().replacesVanillaSwing(entity, hand, stack)))
                return true;
        }
        return false;
    }

    public static boolean poseModel(LivingEntity entity, HumanoidModel<?> model, float attackProgress) {
        if (model == null) return false;
        List<Bound> found = bound(entity);
        if (found.isEmpty()) return false;
        float partial = Minecraft.getInstance().getFrameTime();
        boolean posed = false;
        for (Bound b : found) {
            WeaponSwingAnimation.ModelPose pose = new WeaponSwingAnimation.ModelPose(entity, model, b.hand(),
                    entity.getItemInHand(b.hand()), attackProgress, partial);
            posed |= run(b.animation(), "poseModel", () -> b.animation().poseModel(pose));
        }
        return posed;
    }

    public static boolean renderHeldItem(LivingEntity entity, ArmedModel model, ItemStack stack,
                                         ItemDisplayContext display, HumanoidArm arm, PoseStack pose,
                                         MultiBufferSource buffers, int packedLight) {
        if (arm == null || pose == null || buffers == null) return false;
        List<Bound> found = bound(entity);
        if (found.isEmpty()) return false;
        InteractionHand hand = arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        WeaponSwingAnimation.HeldItem item = new WeaponSwingAnimation.HeldItem(entity, model, hand, arm, stack, display,
                pose, buffers, packedLight);
        for (Bound b : found) {
            if (test(b.animation(), "renderHeldItem", () -> b.animation().renderHeldItem(item))) return true;
        }
        return false;
    }

    public static boolean firstPerson(AbstractClientPlayer player, InteractionHand hand, ItemStack stack,
                                      float swingProgress, float equipProgress, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, int packedLight) {
        if (hand == null || pose == null || buffers == null) return false;
        List<Bound> found = bound(player);
        if (found.isEmpty()) return false;
        WeaponSwingAnimation.FirstPerson view = new WeaponSwingAnimation.FirstPerson(player, hand, stack, swingProgress,
                equipProgress, partialTick, pose, buffers, packedLight);
        for (Bound b : found) {
            if (test(b.animation(), "renderFirstPerson", () -> b.animation().renderFirstPerson(view))) return true;
        }
        return false;
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
