package com.ssakuracotero.umbralib.api.client.geometry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class RitualSigilGeometry {
    private RitualSigilGeometry() { }

    public static int paintRing(PoseStack pose, VertexConsumer vertices, float radius, float halfSize,
                                int count, float progress, float alpha, int rgb, float phase,
                                boolean proceduralMaterial) {
        if (vertices == null || !Float.isFinite(radius) || radius <= 0f || !Float.isFinite(halfSize)
                || halfSize <= 0f || count < 1 || count > 16 || !Float.isFinite(progress)
                || !Float.isFinite(alpha) || !Float.isFinite(phase)) return 0;
        float reveal = clamp(progress, 0f, 1f);
        float opacity = clamp(alpha, 0f, 1f);
        int active = Math.min(count, (int) Math.ceil(reveal * count));
        for (int rune = 0; rune < count; rune++) {
            double angle = Math.PI * 2d * rune / count + phase;
            Vec3 center = new Vec3(Math.cos(angle) * radius, 0.025d, Math.sin(angle) * radius);
            float lit = rune < active ? 1f : 0.3f;
            pose.pushPose();
            try {
                pose.translate(center.x, center.y, center.z);
                pose.mulPose(Axis.XP.rotationDegrees(90f));
                pose.mulPose(Axis.YP.rotation((float) -angle));
                float red = (rgb >> 16 & 255) / 255f;
                float green = (rgb >> 8 & 255) / 255f;
                float blue = (rgb & 255) / 255f;
                if (proceduralMaterial) {
                    EnergyGeometry.texturedQuad(vertices, pose.last().pose(), halfSize, opacity * lit,
                            red, green, blue);
                } else {
                    float scaleX = halfSize * 2f;
                    drawLine(vertices, pose.last().pose(), -0.30f * scaleX, 0f, 0f, 0.64f * halfSize,
                            0.014f * halfSize, opacity * lit, red, green, blue);
                    drawLine(vertices, pose.last().pose(), 0f, 0.64f * halfSize, 0.30f * scaleX, 0f,
                            0.014f * halfSize, opacity * lit, red, green, blue);
                    drawLine(vertices, pose.last().pose(), 0.30f * scaleX, 0f, 0f, -0.64f * halfSize,
                            0.014f * halfSize, opacity * lit, red, green, blue);
                    drawLine(vertices, pose.last().pose(), 0f, -0.64f * halfSize, -0.30f * scaleX, 0f,
                            0.014f * halfSize, opacity * lit, red, green, blue);
                    drawLine(vertices, pose.last().pose(), 0f, -0.42f * halfSize, 0f, 0.42f * halfSize,
                            0.018f * halfSize, opacity * lit, red, green, blue);
                    drawLine(vertices, pose.last().pose(), -0.16f * scaleX, 0f, 0.16f * scaleX, 0f,
                            0.018f * halfSize, opacity * lit, red, green, blue);
                }
            } finally {
                pose.popPose();
            }
        }
        return count * (proceduralMaterial ? 4 : 24);
    }

    private static void drawLine(VertexConsumer vertices, Matrix4f pose, float startX, float startY,
                                 float endX, float endY, float halfWidth, float alpha,
                                 float red, float green, float blue) {
        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0e-5f) return;
        float offsetX = -dy / length * halfWidth;
        float offsetY = dx / length * halfWidth;
        EnergyGeometry.vertex(vertices, pose, startX + offsetX, startY + offsetY, 0f, 0f, 0f, red, green, blue, alpha);
        EnergyGeometry.vertex(vertices, pose, endX + offsetX, endY + offsetY, 0f, 1f, 0f, red, green, blue, alpha);
        EnergyGeometry.vertex(vertices, pose, endX - offsetX, endY - offsetY, 0f, 1f, 1f, red, green, blue, alpha);
        EnergyGeometry.vertex(vertices, pose, startX - offsetX, startY - offsetY, 0f, 0f, 1f, red, green, blue, alpha);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
