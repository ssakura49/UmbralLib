package com.ssakuracotero.umbralib.api.client.post;

import com.ssakuracotero.umbralib.api.client.ClientShaderCompatibility;
import com.ssakuracotero.umbralib.api.client.ClientVisualClock;
import com.ssakuracotero.umbralib.mixin.client.PostChainAccess;
import com.ssakuracotero.umbralib.api.compat.PostEffectPolicy;
import com.ssakuracotero.umbralib.api.client.VisualLibrary;
import com.ssakuracotero.umbralib.api.client.VisualSettings;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * Owns at most one local post chain and never replaces a chain installed by another renderer.
 *
 * <p>The manager holds no content. Hosts register {@link Definition}s plus two hook kinds during client setup:
 * session-reset hooks run whenever the level or player changes and on logout; tick hooks run once per
 * unpaused client tick before the chain decision. Both run in registration order.
 */
public final class ClientPostEffects {
    @FunctionalInterface
    public interface UniformWriter {
        void apply(EffectInstance program, float strength);
    }

    public record Definition(ResourceLocation chain, int priority, float threshold,
                             DoubleSupplier strength, Map<ResourceLocation, UniformWriter> programs) {
        public Definition {
            Objects.requireNonNull(chain, "chain");
            Objects.requireNonNull(strength, "strength");
            if (!Float.isFinite(threshold) || threshold < 0f || threshold >= 1f) {
                throw new IllegalArgumentException("Post-effect threshold must be within [0, 1)");
            }
            programs = Map.copyOf(programs);
        }
    }

    public static final ClientPostEffects INSTANCE = new ClientPostEffects();

    private final Map<ResourceLocation, Definition> definitions = new LinkedHashMap<>();
    private final Map<ResourceLocation, Float> levels = new LinkedHashMap<>();
    private final Set<ResourceLocation> failed = new HashSet<>();
    private final List<PostEffectPolicy.Request> requests = new ArrayList<>();
    private final List<Runnable> sessionResetHooks = new ArrayList<>();
    private final List<Runnable> tickHooks = new ArrayList<>();
    private PostChain ownedChain;
    private ResourceLocation ownedId;
    private ClientLevel lastLevel;
    private LocalPlayer lastPlayer;
    private volatile boolean reloadPending;

    private ClientPostEffects() { }

    /** Call on the client thread during initialization; priorities are higher-first. */
    public void register(ResourceLocation id, Definition definition) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(id, definition) != null) {
            throw new IllegalArgumentException("Duplicate post effect: " + id);
        }
    }

    /** Runs when the client level or player changes and on logout. Call during client setup. */
    public void addSessionResetHook(Runnable hook) {
        sessionResetHooks.add(Objects.requireNonNull(hook, "hook"));
    }

    /** Runs once per unpaused in-world client tick, before strengths are sampled. Call during client setup. */
    public void addTickHook(Runnable hook) {
        tickHooks.add(Objects.requireNonNull(hook, "hook"));
    }

    /** True while the chain registered under {@code id} is the one actually installed by this manager. */
    public boolean isActive(ResourceLocation id) {
        return id != null && id.equals(ownedId) && ownedChain != null
                && Minecraft.getInstance().gameRenderer.currentEffect() == ownedChain;
    }

    public void onResourceReload() {
        reloadPending = true;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != lastLevel || minecraft.player != lastPlayer) {
            release(minecraft);
            sessionResetHooks.forEach(Runnable::run);
            lastLevel = minecraft.level;
            lastPlayer = minecraft.player;
        }
        if (reloadPending) {
            release(minecraft);
            failed.clear();
            reloadPending = false;
        }
        if (minecraft.level != null && minecraft.player != null && !minecraft.isPaused()) {
            tickHooks.forEach(Runnable::run);
        }
        PostChain current = minecraft.gameRenderer.currentEffect();
        if (current != ownedChain) {
            ownedChain = null;
            ownedId = null;
        }
        ResourceLocation wanted = requested(minecraft);
        switch (PostEffectPolicy.decide(current != null && current != ownedChain, ownedId, wanted)) {
            case LOAD -> load(minecraft, wanted);
            case RELEASE -> release(minecraft);
            case KEEP, YIELD -> { }
        }
    }

    @SubscribeEvent
    public void beforePostProcessing(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && ownedChain != null
                && !ClientShaderCompatibility.allowsCustomPrograms()) {
            release(Minecraft.getInstance());
            levels.clear();
            requests.clear();
        }
    }

    private ResourceLocation requested(Minecraft minecraft) {
        levels.clear();
        requests.clear();
        VisualSettings settings = VisualLibrary.settings();
        if (minecraft.level == null || minecraft.player == null || !settings.postEffects()
                || settings.postStrength() <= 0d || !ClientShaderCompatibility.allowsCustomPrograms()) {
            return null;
        }
        float strengthScale = (float) settings.postStrength();
        definitions.forEach((id, definition) -> {
            if (failed.contains(id)) {
                return;
            }
            try {
                double raw = definition.strength().getAsDouble();
                float value = Double.isFinite(raw) ? (float) Math.max(0d, Math.min(1d, raw)) : 0f;
                value *= strengthScale;
                if (value > definition.threshold()) {
                    levels.put(id, value);
                    requests.add(new PostEffectPolicy.Request(id, definition.priority(), value));
                }
            } catch (RuntimeException exception) {
                fail(id, exception);
            }
        });
        return PostEffectPolicy.select(requests, failed);
    }

    private void load(Minecraft minecraft, ResourceLocation id) {
        release(minecraft);
        Definition definition = definitions.get(id);
        try {
            minecraft.gameRenderer.loadEffect(definition.chain());
            PostChain loaded = minecraft.gameRenderer.currentEffect();
            if (loaded != null && definition.chain().toString().equals(loaded.getName())) {
                ownedChain = loaded;
                ownedId = id;
            } else {
                fail(id, null);
            }
        } catch (RuntimeException exception) {
            PostChain partial = minecraft.gameRenderer.currentEffect();
            if (partial != null && definition.chain().toString().equals(partial.getName())) {
                ownedChain = partial;
                ownedId = id;
                release(minecraft);
            }
            fail(id, exception);
        }
    }

    private void fail(ResourceLocation id, RuntimeException exception) {
        if (failed.add(id)) {
            LogUtils.getLogger().warn("Post effect {} disabled until resource reload", id, exception);
        }
    }

    private void release(Minecraft minecraft) {
        if (ownedChain != null && minecraft.gameRenderer.currentEffect() == ownedChain) {
            minecraft.gameRenderer.shutdownEffect();
        }
        ownedChain = null;
        ownedId = null;
    }

    /** Called by PostPassMixin; only programs declared by our actually owned chain receive uniforms. */
    public void applyUniforms(PostPass pass) {
        if (ownedChain == null || failed.contains(ownedId)
                || Minecraft.getInstance().gameRenderer.currentEffect() != ownedChain
                || !((PostChainAccess) ownedChain).umbralib$getPasses().contains(pass)) {
            return;
        }
        EffectInstance program = pass.getEffect();
        Definition definition = definitions.get(ownedId);
        for (Map.Entry<ResourceLocation, UniformWriter> entry : definition.programs().entrySet()) {
            if (entry.getKey().toString().equals(program.getName())) {
                try {
                    scalar(program, "VisualTime", ClientVisualClock.realSeconds());
                    entry.getValue().apply(program, levels.getOrDefault(ownedId, 0f));
                } catch (RuntimeException exception) {
                    fail(ownedId, exception);
                }
                return;
            }
        }
    }

    public static void scalar(EffectInstance program, String name, float value) {
        Uniform uniform = program.getUniform(name);
        if (uniform != null) {
            if (uniform.getCount() != 1 || uniform.getType() != Uniform.getTypeFromString("float")) {
                throw new IllegalArgumentException("Post-effect uniform must be a scalar float: " + name);
            }
            uniform.set(Float.isFinite(value) ? value : 0f);
        }
    }

    @SubscribeEvent
    public void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        release(Minecraft.getInstance());
        levels.clear();
        requests.clear();
        sessionResetHooks.forEach(Runnable::run);
        lastLevel = null;
        lastPlayer = null;
    }
}
