package com.ssakuracotero.umbralib.api;

import net.minecraft.resources.ResourceLocation;

/** Common-side namespace of UmbralLib resources (loaded on both distributions). */
public final class VisualIds {
    public static final String NAMESPACE = "umbralib";

    private VisualIds() { }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(NAMESPACE, path);
    }
}
