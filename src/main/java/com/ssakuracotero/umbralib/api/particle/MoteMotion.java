package com.ssakuracotero.umbralib.api.particle;

/**
 * Content-neutral motion of one mote variant, already combined by the registering mod.
 *
 * @param speedMul initial velocity multiplier
 * @param lifeMul  lifetime multiplier
 * @param sizeMul  quad size multiplier
 * @param gravity  vanilla particle gravity (positive sinks, negative rises)
 * @param drag     extra per-tick velocity damping on top of vanilla friction; 1 disables it
 * @param swirl    per-tick horizontal rotation in degrees; 0 disables it
 */
public record MoteMotion(float speedMul, float lifeMul, float sizeMul, float gravity, float drag, float swirl) {
    public static final MoteMotion NEUTRAL = new MoteMotion(1f, 1f, 1f, 0f, 0.98f, 0f);
}
