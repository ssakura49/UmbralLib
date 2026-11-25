package com.ssakuracotero.umbralib.api.client.render;

import com.ssakuracotero.umbralib.api.client.geometry.EnergyGeometry;
import com.ssakuracotero.umbralib.api.material.VisualMaterial;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Render-type entry points for energy geometry: glow, flow, volume and registered materials. */
public final class EnergyRenderTypes {
    private EnergyRenderTypes() { }

    public static RenderType glow(ResourceLocation texture) {
        return material(VisualMaterials.GLOW, texture);
    }

    public static RenderType flow(ResourceLocation texture) {
        return material(VisualMaterials.FLOW, texture);
    }

    public static RenderType volume(boolean glow) {
        return glow ? RenderType.entityTranslucentEmissive(EnergyGeometry.WHITE)
                : RenderType.entityTranslucent(EnergyGeometry.WHITE);
    }

    public static RenderType material(ResourceLocation materialId, ResourceLocation texture) {
        return VisualRenderTypes.material(materialId, texture);
    }

    public static boolean flowEnabled() {
        VisualMaterial material = VisualMaterials.get(VisualMaterials.FLOW);
        return material.pipeline() == VisualMaterial.Pipeline.ENERGY_ADDITIVE
                && ClientVisualShaders.available(material.shader())
                && Math.abs(ClientVisualShaders.scalarValue(material, "FlowSpeed")) > 0.0001f;
    }
}
