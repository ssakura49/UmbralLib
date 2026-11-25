package com.ssakuracotero.umbralib.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssakuracotero.umbralib.api.client.swing.WeaponSwingRenderer;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets registered swing animations take over third-person held item rendering for one arm. */
@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerSwingMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void umbralib$renderHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext display,
                                         HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int packedLight,
                                         CallbackInfo callback) {
        if (stack.isEmpty()) return;
        ArmedModel model = (ArmedModel) ((ItemInHandLayer<?, ?>) (Object) this).getParentModel();
        if (WeaponSwingRenderer.renderHeldItem(entity, model, stack, display, arm, pose, buffers, packedLight)) {
            callback.cancel();
        }
    }
}
