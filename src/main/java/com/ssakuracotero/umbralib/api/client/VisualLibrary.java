package com.ssakuracotero.umbralib.api.client;

import com.ssakuracotero.umbralib.api.VisualIds;
import net.minecraft.resources.ResourceLocation;

/**
 * Client entry point of the visual library.
 *
 * <p>{@link #NAMESPACE} owns the library's own shaders and materials. Mods register content shaders, materials,
 * post effects and particle budget channels under their own namespaces. Settings come from the library config
 * ({@link VisualConfig}); it is a compile-time constant so it can be used in {@code @Mod.EventBusSubscriber}.
 */
public final class VisualLibrary {
    public static final String NAMESPACE = VisualIds.NAMESPACE;

    private VisualLibrary() { }

    /** Live library settings; {@link VisualSettings#VANILLA_FALLBACK} until {@code umbralib-client.toml} is loaded. */
    public static VisualSettings settings() {
        return VisualConfig.SPEC.isLoaded() ? VisualConfig.SETTINGS : VisualSettings.VANILLA_FALLBACK;
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(NAMESPACE, path);
    }
}
