package com.ssakuracotero.umbralib;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Umbralib.MODID)
public class Umbralib {
    public static final String MODID = "umbralib";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Umbralib() {
        IEventBus modEventBus = getEventBus();
        modEventBus.addListener(this::commonSetup);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.ssakuracotero.umbralib.api.client.VisualConfig.SPEC, "umbralib-client.toml");
        MinecraftForge.EVENT_BUS.register(this);
    }


    @SuppressWarnings("removal")
    public static IEventBus getEventBus() {
        return FMLJavaModLoadingContext.get().getModEventBus();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

}
