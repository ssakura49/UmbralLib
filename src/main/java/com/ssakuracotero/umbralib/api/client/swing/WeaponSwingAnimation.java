package com.ssakuracotero.umbralib.api.client.swing;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Client-side weapon swing behaviour, implemented by other mods and bound to items with
 * {@link WeaponSwingRenderer#register(net.minecraft.world.item.Item, WeaponSwingAnimation)}.
 *
 * <p>UmbralLib decides <b>when</b> each stage runs; the implementation decides <b>what</b> happens: arm poses,
 * item transforms, trail shape, colours and render types. Every method has a do-nothing default, so implement
 * only the stages you need. All calls happen on the client render thread.
 *
 * <p>An implementation is consulted for an entity while <b>either</b> hand holds an item bound to it, and at most
 * once per event even when both hands hold its items. Hand-specific stages pass the hand the event is about, whose
 * stack may be empty or bound to nothing; this lets an implementation also hide or hold back the other hand.
 *
 * <p>An exception thrown from a stage is caught and logged once per implementation and stage; that call then
 * falls back to vanilla. Implementations must still keep {@link PoseStack} pushes and pops balanced.
 *
 * <p>Context records may gain components in later minor versions; read them through accessors and do not
 * construct them yourself.
 */
public interface WeaponSwingAnimation {
    /**
     * A swing has just started for {@code hand}; {@code stack} is what that hand holds and need not be bound to this
     * implementation. {@code durationTicks} is the entity's current swing duration.
     */
    record SwingStart(LivingEntity entity, InteractionHand hand, ItemStack stack, int durationTicks) { }

    /**
     * End of {@code HumanoidModel#setupAnim}, after vanilla posing and before player sleeves copy the arms.
     * {@code hand} and {@code stack} are the hand holding an item bound to this implementation (main hand first).
     *
     * @param attackProgress the vanilla attack progress 0..1 of {@code entity.swingingArm}, restored even when
     *                       {@link #replacesVanillaSwing} suppressed the vanilla arm motion
     */
    record ModelPose(LivingEntity entity, HumanoidModel<?> model, InteractionHand hand, ItemStack stack,
                     float attackProgress, float partialTick) { }

    /**
     * Start of {@code ItemInHandLayer#renderArmWithItem}. {@code pose} is in body model space; vanilla would
     * push, call {@code model.translateToHand(arm, pose)} and render the item.
     */
    record HeldItem(LivingEntity entity, ArmedModel model, InteractionHand hand, HumanoidArm arm, ItemStack stack,
                    ItemDisplayContext display, PoseStack pose, MultiBufferSource buffers, int packedLight) { }

    /** Forge {@code RenderHandEvent} for one hand of the local player. {@code pose} is in camera space. */
    record FirstPerson(AbstractClientPlayer player, InteractionHand hand, ItemStack stack, float swingProgress,
                       float equipProgress, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight) { }

    /**
     * World pass after particles, once per frame per registered implementation. {@code pose} has the camera
     * rotation applied and is centred on the camera position; flush the render types you use on {@code buffers}.
     */
    record WorldFrame(ClientLevel level, Camera camera, PoseStack pose, Matrix4f projection, float partialTick,
                      MultiBufferSource.BufferSource buffers) { }

    default void onSwingStart(SwingStart swing) { }

    /**
     * {@code hand} and {@code stack} are the swinging arm's hand and item, which need not be bound to this
     * implementation.
     *
     * @return {@code true} to zero the vanilla attack motion of the swinging arm for this model setup, so
     * {@link #poseModel} starts from an un-swung pose
     */
    default boolean replacesVanillaSwing(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return false;
    }

    /** Called once per model setup while either hand holds an item bound to this implementation. */
    default void poseModel(ModelPose pose) { }

    /**
     * Called for each arm with a non-empty item while either hand holds an item bound to this implementation.
     *
     * @return {@code true} if the item was rendered here (or deliberately hidden) and vanilla rendering for this
     * arm must be skipped
     */
    default boolean renderHeldItem(HeldItem item) {
        return false;
    }

    /**
     * Called for each hand, including an empty one, while either hand holds an item bound to this implementation.
     *
     * @return {@code true} if this hand was rendered here (or deliberately hidden) and vanilla hand rendering must
     * be skipped
     */
    default boolean renderFirstPerson(FirstPerson hand) {
        return false;
    }

    default void renderWorld(WorldFrame frame) { }

    /** Drop all per-entity state; called when the local player leaves a world. */
    default void reset() { }
}
