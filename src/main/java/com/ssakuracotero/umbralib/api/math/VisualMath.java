package com.ssakuracotero.umbralib.api.math;

import net.minecraft.util.Mth;

/**
 * Content-neutral scalar helpers shared by the visual library.
 *
 * <p>{@link #mixRgb} is a truncating linear RGB blend and {@link #smoothstep} the cubic Hermite ease. Both are
 * kept bit-stable across library versions so callers' colours and easing never shift on upgrade.
 * {@link VisualColor#mix} rounds differently and is intentionally not substituted.
 */
public final class VisualMath {
    private VisualMath() { }

    /** Linear RGB blend; non-finite factors use {@code a}. Alpha bits are discarded. */
    public static int mixRgb(int a, int b, float factor) {
        float t = Float.isFinite(factor) ? Math.max(0f, Math.min(1f, factor)) : 0f;
        int r = Math.round(((a >> 16) & 255) * (1f - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1f - t) + ((b >> 8) & 255) * t);
        int blue = Math.round((a & 255) * (1f - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | blue;
    }

    /** Cubic smoothstep on {@code [0, 1]}; inputs outside the range are clamped. */
    public static float smoothstep(float value) {
        float clamped = Mth.clamp(value, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }
}
