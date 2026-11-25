package com.ssakuracotero.umbralib.api.client.geometry;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.function.DoubleFunction;

public final class PathRibbon {
    public record Edge(Vec3 point, int rgb, float alpha) { }
    public record Section(Edge inner, Edge outer) {
        Vec3 center() {
            return inner.point().lerp(outer.point(), 0.5);
        }
    }

    private PathRibbon() { }

    public static void emit(VertexConsumer buffer, Matrix4f pose, int segments, double extent,
                            double uvPerBlock, DoubleFunction<Section> path) {
        if (segments < 1 || segments > 64 || !Double.isFinite(extent) || extent <= 0d || extent > 1d
                || !Double.isFinite(uvPerBlock) || uvPerBlock < 0d) return;
        Section previous = path.apply(0d);
        double distance = 0d;
        for (int segment = 1; segment <= segments; segment++) {
            Section next = path.apply(extent * segment / segments);
            if (valid(previous) && valid(next)) {
                double nextDistance = distance + previous.center().distanceTo(next.center());
                float fromU = (float) (distance * uvPerBlock);
                float toU = (float) (nextDistance * uvPerBlock);
                if (Float.isFinite(fromU) && Float.isFinite(toU)) {
                    vertex(buffer, pose, previous.inner(), fromU, 0f);
                    vertex(buffer, pose, previous.outer(), fromU, 1f);
                    vertex(buffer, pose, next.outer(), toU, 1f);
                    vertex(buffer, pose, next.inner(), toU, 0f);
                    distance = nextDistance;
                }
            } else {
                distance = 0d;
            }
            previous = next;
        }
    }

    private static boolean valid(Section section) {
        return section != null && valid(section.inner()) && valid(section.outer());
    }

    private static boolean valid(Edge edge) {
        return edge != null && edge.point() != null && Float.isFinite(edge.alpha())
                && Float.isFinite((float) edge.point().x) && Float.isFinite((float) edge.point().y)
                && Float.isFinite((float) edge.point().z);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Edge edge, float u, float v) {
        Vec3 point = edge.point();
        int rgb = edge.rgb();
        EnergyGeometry.vertex(buffer, pose, (float) point.x, (float) point.y, (float) point.z, u, v,
                (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f,
                Math.max(0f, Math.min(1f, edge.alpha())));
    }
}
