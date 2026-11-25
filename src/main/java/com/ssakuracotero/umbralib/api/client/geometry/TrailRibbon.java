package com.ssakuracotero.umbralib.api.client.geometry;

import com.ssakuracotero.umbralib.api.trail.TrailPath;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Submits a prepared {@link TrailPath.Result} as quads: left edge at V=0, right edge at V=1, U from arc length.
 *
 * <p>Positions are used as given; the caller prepares the path in the same (usually camera-relative) space as
 * {@code pose} and must not transform twice. No buffer flush, budget, lifetime or shader fallback is handled
 * here: obtain the buffer through {@code VisualFxRenderer.material(...)} as for any other library geometry.
 */
public final class TrailRibbon {
    /** Per-sample edge colour and opacity; {@code right} selects the V=1 edge. */
    public interface EdgeStyle {
        int rgb(TrailPath.Sample sample, boolean right);

        float alpha(TrailPath.Sample sample, boolean right);
    }

    private TrailRibbon() { }

    /** @return number of quads submitted */
    public static int emit(VertexConsumer buffer, Matrix4f pose, TrailPath.Result path, EdgeStyle style) {
        if (buffer == null || pose == null || path == null || style == null) return 0;
        List<TrailPath.Sample> samples = path.samples();
        int quads = 0;
        for (TrailPath.Segment segment : path.segments()) {
            if (!segment.drawable()) continue;
            int end = segment.start() + segment.count() - 1;
            for (int i = segment.start(); i < end; i++) {
                TrailPath.Sample from = samples.get(i);
                TrailPath.Sample to = samples.get(i + 1);
                float fromU = (float) from.u();
                float toU = (float) to.u();
                if (!Float.isFinite(fromU) || !Float.isFinite(toU)) continue;
                vertex(buffer, pose, from.left(), fromU, 0f, style.rgb(from, false), style.alpha(from, false));
                vertex(buffer, pose, from.right(), fromU, 1f, style.rgb(from, true), style.alpha(from, true));
                vertex(buffer, pose, to.right(), toU, 1f, style.rgb(to, true), style.alpha(to, true));
                vertex(buffer, pose, to.left(), toU, 0f, style.rgb(to, false), style.alpha(to, false));
                quads++;
            }
        }
        return quads;
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 point, float u, float v, int rgb, float alpha) {
        float a = Float.isFinite(alpha) ? Math.max(0f, Math.min(1f, alpha)) : 0f;
        EnergyGeometry.vertex(buffer, pose, (float) point.x, (float) point.y, (float) point.z, u, v,
                (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, a);
    }
}
