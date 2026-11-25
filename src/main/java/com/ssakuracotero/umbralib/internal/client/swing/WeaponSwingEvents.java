package com.ssakuracotero.umbralib.internal.client.swing;

import com.ssakuracotero.umbralib.Umbralib;
import com.ssakuracotero.umbralib.api.client.swing.WeaponSwingRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client event wiring for weapon swing animations. Internal: not API. */
@Mod.EventBusSubscriber(modid = Umbralib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WeaponSwingEvents {
    private WeaponSwingEvents() { }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (WeaponSwingRenderer.firstPerson(player, event.getHand(), event.getItemStack(), event.getSwingProgress(),
                event.getEquipProgress(), event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) WeaponSwingDispatcher.renderWorld(event);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WeaponSwingDispatcher.reset();
    }
}
