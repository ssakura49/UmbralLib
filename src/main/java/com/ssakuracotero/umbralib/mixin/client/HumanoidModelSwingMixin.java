package com.ssakuracotero.umbralib.mixin.client;

import com.ssakuracotero.umbralib.api.client.swing.WeaponSwingRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets registered swing animations suppress the vanilla attack motion and pose the model afterwards.
 * {@code PlayerModel#setupAnim} calls this method first and copies sleeves after it returns.
 */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelSwingMixin {
    @Unique
    private float umbralib$attackTime;
    @Unique
    private boolean umbralib$suppressed;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void umbralib$beforePose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                     float netHeadYaw, float headPitch, CallbackInfo callback) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        umbralib$attackTime = model.attackTime;
        umbralib$suppressed = model.attackTime > 0f && WeaponSwingRenderer.suppressesVanillaSwing(entity);
        if (umbralib$suppressed) model.attackTime = 0f;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void umbralib$afterPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                    float netHeadYaw, float headPitch, CallbackInfo callback) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (umbralib$suppressed) {
            model.attackTime = umbralib$attackTime;
            umbralib$suppressed = false;
        }
        WeaponSwingRenderer.poseModel(entity, model, umbralib$attackTime);
    }
}
