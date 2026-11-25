package com.ssakuracotero.umbralib.api.client.geometry;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class VisualPulseRenderer {
    public record Profile(float radius, float width, int durationTicks, int rgb, int edgeRgb) {
        public Profile {
            if (!Float.isFinite(radius) || !Float.isFinite(width) || width <= 0f
                    || radius < width * 3f || radius > 32f || durationTicks < 8 || durationTicks > 200) {
                throw new IllegalArgumentException("Invalid pulse radius, width or duration");
            }
        }
    }

    private VisualPulseRenderer() { }

    public static void paint(VertexConsumer vertices, Matrix4f pose, Vec3 normal,
                             Profile profile, float ageTicks, float detail) {
        if (!Float.isFinite(ageTicks) || ageTicks < 0f || ageTicks >= profile.durationTicks()
                || !Float.isFinite(detail) || detail <= 0f || !Double.isFinite(normal.lengthSqr())) return;
        Vec3 axis = normal.normalize();
        if (axis.lengthSqr() < 0.5d) return;
        Vec3 reference = Math.abs(axis.y) > 0.9d ? new Vec3(1d, 0d, 0d) : new Vec3(0d, 1d, 0d);
        Vec3 tangent = reference.cross(axis).normalize();
        Vec3 bitangent = axis.cross(tangent);
        float progress = ageTicks / profile.durationTicks();
        int segments = detail >= 0.75f ? 48 : detail >= 0.6f ? 32 : 20;
        wave(vertices, pose, profile, tangent, bitangent, progress, 1f, segments);
        if (detail >= 0.75f) {
            wave(vertices, pose, profile, tangent, bitangent, (progress - 0.22f) / 0.78f, 0.29f, 32);
        }
    }

    private static void wave(VertexConsumer vertices, Matrix4f pose, Profile profile, Vec3 tangent, Vec3 bitangent,
                             float progress, float opacity, int segments) {
        if (progress <= 0f || progress >= 1f) return;
        float remaining = 1f - progress;
        float strength = opacity * smooth(progress / 0.14f) * (1f - smooth((progress - 0.42f) / 0.58f));
        float radius = Mth.lerp(1f - remaining * remaining, profile.width() * 1.25f, profile.radius());
        float thickness = profile.width() * Mth.lerp(progress, 1f, 0.45f);
        PathRibbon.emit(vertices, pose, segments, 1d, 0d, fraction -> {
            double angle = fraction == 1d ? 0d : fraction * Math.PI * 2d;
            Vec3 radial = tangent.scale(Math.cos(angle)).add(bitangent.scale(Math.sin(angle)));
            return new PathRibbon.Section(
                    new PathRibbon.Edge(radial.scale(radius - thickness), profile.rgb(), strength * 0.24f),
                    new PathRibbon.Edge(radial.scale(radius), profile.edgeRgb(), strength * 0.9f));
        });
    }

    private static float smooth(float progress) {
        float clamped = Mth.clamp(progress, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }
}
