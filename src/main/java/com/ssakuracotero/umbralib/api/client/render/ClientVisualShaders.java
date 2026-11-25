package com.ssakuracotero.umbralib.api.client.render;

import com.ssakuracotero.umbralib.api.client.ClientShaderCompatibility;
import com.ssakuracotero.umbralib.api.client.ClientVisualClock;
import com.ssakuracotero.umbralib.api.client.VisualLibrary;
import com.ssakuracotero.umbralib.api.material.VisualMaterial;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registers NEW_ENTITY visual programs and resets their scalar parameters at batch submission.
 *
 * <p>The library ships {@link #ENERGY} and {@link #SIGIL_WEAVE}. Other mods register their own programs with
 * {@link #register} during mod construction; program files live under the registering mod namespace.
 */
@Mod.EventBusSubscriber(modid = VisualLibrary.NAMESPACE, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientVisualShaders {
    public static final ResourceLocation ENERGY = new ResourceLocation(VisualLibrary.NAMESPACE, "energy");
    public static final ResourceLocation SIGIL_WEAVE = new ResourceLocation(VisualLibrary.NAMESPACE, "sigil_weave");
    private static final Map<ResourceLocation, Map<String, Float>> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, MaterialShader> LOADED = new LinkedHashMap<>();
    private static boolean registrationClosed;

    static {
        register(ENERGY, Map.of("FlowSpeed", 0f, "GlowStrength", 1f,
                "PulseStrength", 0f, "PulseSpeed", 1.5f, "EdgeSoftness", 0f, "AlphaCutoff", 0.003f,
                "umbralib_FlowHighlight", 0f, "umbralib_FlowHighlightScale", 4f,
                "umbralib_FlowHighlightWidth", 0.08f));
        register(SIGIL_WEAVE, Map.of("SigilScale", 4f, "FlowSpeed", .72f,
                "PulseStrength", .22f, "GlowStrength", 1f, "EdgeSoftness", .06f, "AlphaCutoff", .004f));
    }

    private ClientVisualShaders() { }

    private static final class MaterialShader extends ShaderInstance {
        private VisualMaterial.Pipeline pipeline = VisualMaterial.Pipeline.SHADER_ALPHA;

        private MaterialShader(ResourceProvider resources, ResourceLocation id) throws IOException {
            super(resources, id, DefaultVertexFormat.NEW_ENTITY);
        }

        @Override
        public void apply() {
            super.apply();
            VisualRenderTypes.applyMaterialTransparency(pipeline);
        }
    }

    /** Call during client initialization, before the first shader reload. */
    public static void register(ResourceLocation id, Map<String, Float> defaults) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(defaults, "defaults");
        if (registrationClosed) {
            throw new IllegalStateException("Visual shaders must be registered before the first shader reload");
        }
        if (defaults.size() > 32) {
            throw new IllegalArgumentException("A visual shader supports at most 32 scalar defaults");
        }
        defaults.forEach(VisualMaterial::validateUniform);
        if (DEFINITIONS.putIfAbsent(id, Map.copyOf(defaults)) != null) {
            throw new IllegalArgumentException("Duplicate visual shader: " + id);
        }
    }

    public static boolean registered(ResourceLocation id) {
        return DEFINITIONS.containsKey(id);
    }

    public static void validate(VisualMaterial material) {
        Map<String, Float> defaults = DEFINITIONS.get(material.shader());
        if (defaults == null || !defaults.keySet().containsAll(material.uniforms().keySet())) {
            throw new IllegalArgumentException("Unknown shader or scalar uniform in material: " + material.shader());
        }
    }

    public static boolean available(ResourceLocation id) {
        return VisualLibrary.settings().energyShaders() && LOADED.containsKey(id)
                && ClientShaderCompatibility.allowsCustomPrograms();
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        registrationClosed = true;
        LOADED.clear();
        VisualRenderTypes.clearCache();
        for (ResourceLocation id : DEFINITIONS.keySet()) {
            try {
                MaterialShader shader = new MaterialShader(event.getResourceProvider(), id);
                try {
                    for (String name : DEFINITIONS.get(id).keySet()) {
                        Uniform uniform = shader.getUniform(name);
                        if (uniform == null || uniform.getCount() != 1 || uniform.getType() != Uniform.getTypeFromString("float")) {
                            throw new IllegalArgumentException("Missing or non-scalar float uniform: " + name);
                        }
                    }
                    event.registerShader(shader, loaded -> {
                        LOADED.put(id, shader);
                        LogUtils.getLogger().info("Loaded visual shader {}", id);
                    });
                } catch (RuntimeException exception) {
                    shader.close();
                    throw exception;
                }
            } catch (IOException | RuntimeException exception) {
                LogUtils.getLogger().warn("Visual shader {} unavailable; using vanilla emissive rendering", id, exception);
            }
        }
    }

    public static float scalarValue(VisualMaterial material, String name) {
        Map<String, Float> defaults = DEFINITIONS.getOrDefault(material.shader(), Map.of());
        float value = material.uniforms().getOrDefault(name, defaults.getOrDefault(name, 0f));
        if (name.equals("GlowStrength")) {
            value *= (float) VisualLibrary.settings().glowStrength();
        } else if (name.equals("FlowSpeed")) {
            value *= (float) VisualLibrary.settings().flowSpeed();
        }
        return value;
    }

    /** Binds the material program with its scalar uniforms reset; vanilla emissive program when unavailable. */
    public static ShaderInstance bind(VisualMaterial material) {
        MaterialShader shader = LOADED.get(material.shader());
        if (shader == null || !available(material.shader())) {
            return GameRenderer.getRendertypeEntityTranslucentEmissiveShader();
        }
        shader.pipeline = material.pipeline();
        // Shared shaders must receive complete defaults for every batch, not while vertices are queued.
        for (String name : DEFINITIONS.get(material.shader()).keySet()) {
            Uniform uniform = shader.getUniform(name);
            if (uniform != null) {
                uniform.set(scalarValue(material, name));
            }
        }
        Uniform time = shader.getUniform("VisualTime");
        if (time != null) {
            time.set(ClientVisualClock.worldSeconds());
        }
        return shader;
    }
}
