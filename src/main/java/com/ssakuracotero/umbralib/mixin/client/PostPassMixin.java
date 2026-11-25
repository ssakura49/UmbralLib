package com.ssakuracotero.umbralib.mixin.client;

import com.ssakuracotero.umbralib.api.client.post.ClientPostEffects;

import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies uniforms only to programs belonging to the post chain owned by the library. */
@Mixin(PostPass.class)
public class PostPassMixin {
    @Inject(method = "process", at = @At("HEAD"))
    private void umbralib$applyPostUniforms(float partialTicks, CallbackInfo ci) {
        ClientPostEffects.INSTANCE.applyUniforms((PostPass) (Object) this);
    }
}
