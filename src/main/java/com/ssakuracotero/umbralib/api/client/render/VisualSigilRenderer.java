package com.ssakuracotero.umbralib.api.client.render;

import com.ssakuracotero.umbralib.api.client.geometry.EnergyGeometry;
import com.ssakuracotero.umbralib.api.client.geometry.SigilRingGeometry;
import com.ssakuracotero.umbralib.api.material.VisualMaterial;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;

public final class VisualSigilRenderer {
    private VisualSigilRenderer() { }

    public static RenderType ringType() {
        return VisualFxRenderer.material(VisualMaterials.SIGIL_WEAVE, EnergyGeometry.WHITE);
    }

    public static int paintRing(PoseStack pose, VertexConsumer vertices, float radius, float halfSize,
                                int count, float progress, float alpha, int rgb, float phase) {
        VisualMaterial material = VisualMaterials.get(VisualMaterials.SIGIL_WEAVE);
        boolean procedural = material.pipeline() == VisualMaterial.Pipeline.ENERGY_ADDITIVE
                && material.shader().equals(ClientVisualShaders.SIGIL_WEAVE)
                && ClientVisualShaders.available(ClientVisualShaders.SIGIL_WEAVE);
        return SigilRingGeometry.paintRing(pose, vertices, radius, halfSize, count,
                progress, alpha, rgb, phase, procedural);
    }
}
