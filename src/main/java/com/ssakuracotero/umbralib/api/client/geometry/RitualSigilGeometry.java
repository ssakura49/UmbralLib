package com.ssakuracotero.umbralib.api.client.geometry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * @deprecated Renamed to {@link SigilRingGeometry}; this alias only forwards and is removed in the next major version.
 */
@Deprecated(since = "0.3.0", forRemoval = true)
public final class RitualSigilGeometry {
    private RitualSigilGeometry() { }

    /** @deprecated use {@link SigilRingGeometry#paintRing} */
    @Deprecated(since = "0.3.0", forRemoval = true)
    public static int paintRing(PoseStack pose, VertexConsumer vertices, float radius, float halfSize,
                                int count, float progress, float alpha, int rgb, float phase,
                                boolean proceduralMaterial) {
        return SigilRingGeometry.paintRing(pose, vertices, radius, halfSize, count, progress, alpha, rgb,
                phase, proceduralMaterial);
    }
}
