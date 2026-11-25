package com.ssakuracotero.umbralib.api.client;

/**
 * Shared visual settings the library reads at rendering and budget boundaries.
 *
 * <p>They come from the library's own client config {@code umbralib-client.toml} ({@link VisualConfig}), so every mod
 * using the library sees the same values. {@link #VANILLA_FALLBACK} applies until that config is loaded, and on a
 * dedicated server: custom programs, post effects and the scene lens stay off and rendering uses vanilla paths.
 */
public interface VisualSettings {
    /** Manual compatibility switch; when true only vanilla render types and no local post effects are used. */
    boolean forceVanillaRendering();

    /** Allow local core shaders when no shader pack is active. */
    boolean energyShaders();

    /** Allow the scene refraction lens pass. */
    boolean gravitySceneLens();

    /** Allow locally owned full-screen post chains. */
    boolean postEffects();

    /** Post-effect intensity multiplier in {@code [0, 1]}; zero disables local post effects. */
    double postStrength();

    /** Multiplier for the {@code GlowStrength} material uniform. */
    double glowStrength();

    /** Multiplier for the {@code FlowSpeed} material uniform; zero freezes UV flow. */
    double flowSpeed();

    /** Blocks beyond which optional effects are not drawn. */
    int effectDistance();

    /** Blocks beyond which optional rings and glyph detail begin to reduce. */
    int detailDistance();

    /** Global cap on dispatcher-managed active effects across all mods; zero means unlimited. */
    int maxActiveEffects();

    VisualSettings VANILLA_FALLBACK = new VisualSettings() {
        @Override public boolean forceVanillaRendering() { return true; }
        @Override public boolean energyShaders() { return false; }
        @Override public boolean gravitySceneLens() { return false; }
        @Override public boolean postEffects() { return false; }
        @Override public double postStrength() { return 0d; }
        @Override public double glowStrength() { return 1.15d; }
        @Override public double flowSpeed() { return 1d; }
        @Override public int effectDistance() { return 64; }
        @Override public int detailDistance() { return 48; }
        @Override public int maxActiveEffects() { return 0; }
    };
}
