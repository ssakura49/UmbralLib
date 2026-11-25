package com.ssakuracotero.umbralib.api.client.render;

import com.ssakuracotero.umbralib.api.client.ClientVisualBudget;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class VisualFxRenderer {
    private VisualFxRenderer() { }

    public static RenderType material(ResourceLocation materialId, ResourceLocation texture) {
        return VisualRenderTypes.material(materialId, texture);
    }

    public static boolean shaderEnabled(ResourceLocation shaderId) {
        return ClientVisualShaders.available(shaderId);
    }

    public static float detailScale(ClientLevel level, Vec3 position) {
        return ClientVisualBudget.detailScale(level, position);
    }
}
