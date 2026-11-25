package com.ssakuracotero.umbralib.internal.client;

import com.ssakuracotero.umbralib.api.client.post.ClientPostEffects;
import com.ssakuracotero.umbralib.api.client.post.GravitySceneLensRenderer;
import com.ssakuracotero.umbralib.api.client.ClientShaderCompatibility;
import com.ssakuracotero.umbralib.api.client.VisualLibrary;
import com.ssakuracotero.umbralib.api.client.render.VisualMaterials;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Library-owned client wiring: resource reload hooks and the post-effect manager. Internal: not API. */
@Mod.EventBusSubscriber(modid = VisualLibrary.NAMESPACE, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientVisualResources {
    private ClientVisualResources() { }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(VisualMaterials.INSTANCE);
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            ClientPostEffects.INSTANCE.onResourceReload();
            ClientShaderCompatibility.onResourceReload();
            GravitySceneLensRenderer.onResourceReload();
        });
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(ClientPostEffects.INSTANCE);
    }
}
