package com.ssakuracotero.umbralib.api.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The library's client config, {@code umbralib-client.toml}. Shared by every mod that uses the library; values are
 * read live, so tests and config screens may change them at runtime.
 */
public final class VisualConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue FORCE_VANILLA_RENDERING;
    public static final ForgeConfigSpec.BooleanValue ENERGY_SHADERS;
    public static final ForgeConfigSpec.BooleanValue GRAVITY_SCENE_LENS;
    public static final ForgeConfigSpec.BooleanValue POST_EFFECTS;
    public static final ForgeConfigSpec.DoubleValue POST_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue GLOW_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue FLOW_SPEED;
    public static final ForgeConfigSpec.IntValue EFFECT_DISTANCE;
    public static final ForgeConfigSpec.IntValue DETAIL_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_EFFECTS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("visuals");
        FORCE_VANILLA_RENDERING = builder.comment("Force vanilla visual render types and disable local post effects for unsupported shader loaders.")
                .define("forceVanillaRendering", false);
        ENERGY_SHADERS = builder.comment("Use local energy shaders when no shader pack is active; otherwise use vanilla emissive rendering.")
                .define("energyShaders", true);
        GRAVITY_SCENE_LENS = builder.comment("Refract opaque world scenery around lens fields when custom client shaders are allowed.")
                .define("gravitySceneLens", true);
        POST_EFFECTS = builder.comment("Enable post effects registered by mods; post chains installed by other renderers always take priority.")
                .define("postEffects", true);
        POST_STRENGTH = builder.comment("Post-effect intensity multiplier; zero disables local post effects.")
                .defineInRange("postStrength", 1d, 0d, 1d);
        GLOW_STRENGTH = builder.defineInRange("glowStrength", 1.15d, 0.25d, 2d);
        FLOW_SPEED = builder.comment("Multiplier applied to material flow speeds; zero freezes UV movement.")
                .defineInRange("flowSpeed", 1d, 0d, 2d);
        EFFECT_DISTANCE = builder.comment("Blocks beyond which optional effects are not drawn.")
                .defineInRange("effectDistance", 64, 16, 128);
        DETAIL_DISTANCE = builder.comment("Distance at which optional rings and glyph details begin to reduce.")
                .defineInRange("detailDistance", 48, 16, 128);
        MAX_ACTIVE_EFFECTS = builder.comment("Global cap on simultaneously active dispatcher-managed effects across all mods; 0 means unlimited (default).")
                .defineInRange("maxActiveEffects", 0, 0, 512);
        builder.pop();
        SPEC = builder.build();
    }

    static final VisualSettings SETTINGS = new VisualSettings() {
        @Override public boolean forceVanillaRendering() { return FORCE_VANILLA_RENDERING.get(); }
        @Override public boolean energyShaders() { return ENERGY_SHADERS.get(); }
        @Override public boolean gravitySceneLens() { return GRAVITY_SCENE_LENS.get(); }
        @Override public boolean postEffects() { return POST_EFFECTS.get(); }
        @Override public double postStrength() { return POST_STRENGTH.get(); }
        @Override public double glowStrength() { return GLOW_STRENGTH.get(); }
        @Override public double flowSpeed() { return FLOW_SPEED.get(); }
        @Override public int effectDistance() { return EFFECT_DISTANCE.get(); }
        @Override public int detailDistance() { return DETAIL_DISTANCE.get(); }
        @Override public int maxActiveEffects() { return MAX_ACTIVE_EFFECTS.get(); }
    };

    private VisualConfig() { }
}
