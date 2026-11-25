package com.ssakuracotero.umbralib.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.lang.ref.WeakReference;

/** Animation clocks do not advance gameplay state when read. */
public final class ClientVisualClock {
    private static final long START = System.nanoTime();
    private static WeakReference<ClientLevel> clockLevel = new WeakReference<>(null);
    private static long worldStartTick;

    private ClientVisualClock() { }

    /** Continuous seconds since client start as a float. Precision drops in long sessions; use {@link #realSecondsExact()} for phase maths. */
    public static float realSeconds() {
        return (float) realSecondsExact();
    }

    /** Continuous seconds since client start in double precision. Reduce with a modulo before handing a phase to a float or shader. */
    public static double realSecondsExact() {
        return (System.nanoTime() - START) / 1.0E9d;
    }

    public static float worldSeconds() {
        return (float) worldSecondsExact();
    }

    /** Seconds since this client level was first observed, in double precision. Same timeline as {@link #worldSeconds()}. */
    public static double worldSecondsExact() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return realSecondsExact();
        }
        long gameTick = level.getGameTime();
        if (clockLevel.get() != level) {
            clockLevel = new WeakReference<>(level);
            worldStartTick = gameTick;
        }
        return elapsedWorldSecondsExact(worldStartTick, gameTick, minecraft.getFrameTime());
    }

    public static float elapsedWorldSeconds(long startTick, long gameTick, float partialTick) {
        return (float) elapsedWorldSecondsExact(startTick, gameTick, partialTick);
    }

    public static double elapsedWorldSecondsExact(long startTick, long gameTick, float partialTick) {
        return (gameTick - startTick + (double) partialTick) / 20d;
    }
}
