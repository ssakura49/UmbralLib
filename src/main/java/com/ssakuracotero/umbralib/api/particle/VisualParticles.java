package com.ssakuracotero.umbralib.api.particle;

import net.minecraft.world.level.Level;

/**
 * Shared particle helpers. The library registers no particle IDs: each mod registers its own {@link MoteType} and
 * {@link PhotonType} instances, which carry that mod's variant table and local spawn gate.
 */
public final class VisualParticles {
    @FunctionalInterface
    public interface LocalGate {
        LocalGate ADMIT_ALL = (level, x, y, z) -> true;

        boolean allow(Level level, double x, double y, double z);
    }

    private VisualParticles() { }

    /** Client-side highlight through {@code type}'s local gate; vanilla non-forced particle path. */
    public static void photon(Level level, PhotonType type, int rgb, double x, double y, double z,
                              double vx, double vy, double vz, float size, float speed) {
        if (!level.isClientSide || !type.allowLocal(level, x, y, z)) return;
        level.addParticle(type.options(0, photonRgb(rgb), size, speed), x, y, z, vx, vy, vz);
    }

    /** Mixes in some white so dark colours still read as highlights. */
    public static int photonRgb(int rgb) {
        int r = (rgb >> 16 & 255) * 3 / 5 + 102;
        int g = (rgb >> 8 & 255) * 3 / 5 + 102;
        int b = (rgb & 255) * 3 / 5 + 102;
        return r << 16 | g << 8 | b;
    }
}
