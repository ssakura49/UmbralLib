package com.ssakuracotero.umbralib.api.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

public final class GuiPrimitives {
    private GuiPrimitives() {}

    /** Shortens text to the given GUI width at the font's native size; never rescales glyphs. */
    public static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        int ellipsis = font.width("…");
        if (width <= ellipsis) return "";
        return font.plainSubstrByWidth(text, width - ellipsis) + "…";
    }

    public static void slotWell(GuiGraphics graphics, int iconX, int iconY, int edge, int well) {
        graphics.fill(iconX - 1, iconY - 1, iconX + 17, iconY + 17, edge);
        graphics.fill(iconX, iconY, iconX + 16, iconY + 16, well);
    }

    public static void recessedSlot(GuiGraphics graphics, int iconX, int iconY, int inset,
                                    int edge, int well, int shadow, int gleam) {
        int left = iconX - inset;
        int top = iconY - inset;
        int right = iconX + 16 + inset;
        int bottom = iconY + 16 + inset;
        graphics.fill(left, top, right, bottom, edge);
        graphics.fill(left, top, right - 1, bottom - 1, well);
        graphics.fill(left, top, right, top + 1, shadow);
        graphics.fill(left, top + 1, left + 1, bottom, shadow);
        if (gleam != 0) {
            graphics.fill(right - 3, bottom - 1, right, bottom, gleam);
        }
    }

    public static void progressBar(GuiGraphics graphics, int left, int top, int width, int height,
                                   int inset, int filledWidth, int track, int fill) {
        graphics.fill(left, top, left + width, top + height, track);
        if (filledWidth > 0) {
            graphics.fill(left + inset, top + inset, left + inset + filledWidth, top + height - inset, fill);
        }
    }

    public static void outlinedDisk(GuiGraphics graphics, int centerX, int centerY, int radius, int thickness,
                                    int edge, int fill) {
        int innerRadius = Math.max(0, radius - thickness);
        RenderType overlay = RenderType.guiOverlay();
        for (int row = -radius; row <= radius; row++) {
            int outer = (int) Math.sqrt(radius * radius - row * row);
            int inner = Math.abs(row) < innerRadius ? (int) Math.sqrt(innerRadius * innerRadius - row * row) : -1;
            int top = centerY + row;
            if (inner < 0) {
                graphics.fill(overlay, centerX - outer, top, centerX + outer + 1, top + 1, edge);
            } else {
                graphics.fill(overlay, centerX - outer, top, centerX - inner, top + 1, edge);
                graphics.fill(overlay, centerX - inner, top, centerX + inner + 1, top + 1, fill);
                graphics.fill(overlay, centerX + inner + 1, top, centerX + outer + 1, top + 1, edge);
            }
        }
    }
}
