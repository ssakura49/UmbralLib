package com.ssakuracotero.umbralib.mixin.client;

import com.ssakuracotero.umbralib.api.client.swing.WeaponSwingRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reports the start of a swing (the write that resets {@code swingTime}) for client-side entities. */
@Mixin(LivingEntity.class)
abstract class LivingEntitySwingMixin {
    @Shadow
    private int getCurrentSwingDuration() {
        throw new AssertionError();
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;swingTime:I", opcode = Opcodes.PUTFIELD,
            shift = At.Shift.BEFORE))
    private void umbralib$swingStarted(InteractionHand hand, boolean broadcast, CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) WeaponSwingRenderer.swingStarted(self, hand, getCurrentSwingDuration());
    }
}
