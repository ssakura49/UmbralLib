package com.ssakuracotero.umbralib.api.client.swing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssakuracotero.umbralib.internal.client.swing.WeaponSwingDispatcher;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Public entry point for weapon swing animations. UmbralLib provides the registry and the call timing; the
 * behaviour itself comes from a {@link WeaponSwingAnimation} implemented by the registering mod.
 *
 * <p><b>Client only.</b> Call {@link #register} from {@code FMLClientSetupEvent} or later on the client; never from
 * common or dedicated-server code. Registering an item again replaces its previous animation and logs a warning.
 *
 * <p>The hook methods below are already called by UmbralLib's own mixins and event listeners. Call them yourself
 * only from a custom model or renderer that bypasses the vanilla path named in each method.
 *
 * <p>This class and {@link WeaponSwingAnimation} are the stable API. Everything under
 * {@code com.ssakuracotero.umbralib.internal} may change in any release.
 */
public final class WeaponSwingRenderer {
    private WeaponSwingRenderer() { }

    /** Binds {@code animation} to every stack of {@code item}. */
    public static void register(Item item, WeaponSwingAnimation animation) {
        WeaponSwingDispatcher.register(item, animation);
    }

    /** @return the animation bound to {@code stack}'s item, or {@code null} */
    @Nullable
    public static WeaponSwingAnimation get(ItemStack stack) {
        return WeaponSwingDispatcher.find(stack);
    }

    /** Hook for {@code LivingEntity#swing}, at the point a new swing starts. Client-side entities only. */
    public static void swingStarted(LivingEntity entity, InteractionHand hand, int durationTicks) {
        WeaponSwingDispatcher.swingStarted(entity, hand, durationTicks);
    }

    /** Hook for the start of {@code HumanoidModel#setupAnim}. @return {@code true} to zero the vanilla attack time */
    public static boolean suppressesVanillaSwing(LivingEntity entity) {
        return WeaponSwingDispatcher.suppressesVanillaSwing(entity);
    }

    /**
     * Hook for the end of {@code HumanoidModel#setupAnim}, with the original attack time.
     *
     * @return {@code true} if at least one registered animation posed the model
     */
    public static boolean poseModel(LivingEntity entity, HumanoidModel<?> model, float attackProgress) {
        return WeaponSwingDispatcher.poseModel(entity, model, attackProgress);
    }

    /** Hook for the start of {@code ItemInHandLayer#renderArmWithItem}. @return {@code true} to skip vanilla */
    public static boolean renderHeldItem(LivingEntity entity, ArmedModel model, ItemStack stack,
                                         ItemDisplayContext display, HumanoidArm arm, PoseStack pose,
                                         MultiBufferSource buffers, int packedLight) {
        return WeaponSwingDispatcher.renderHeldItem(entity, model, stack, display, arm, pose, buffers, packedLight);
    }

    /** Hook for Forge {@code RenderHandEvent}. @return {@code true} to cancel vanilla rendering of this hand */
    public static boolean firstPerson(AbstractClientPlayer player, InteractionHand hand, ItemStack stack,
                                      float swingProgress, float equipProgress, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, int packedLight) {
        return WeaponSwingDispatcher.firstPerson(player, hand, stack, swingProgress, equipProgress, partialTick,
                pose, buffers, packedLight);
    }
}
