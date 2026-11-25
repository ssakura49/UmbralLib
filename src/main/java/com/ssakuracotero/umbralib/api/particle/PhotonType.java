package com.ssakuracotero.umbralib.api.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Short-lived highlight particle type. Each mod registers its own instance under its own ID; the instance carries
 * that mod's local spawn gate.
 */
public final class PhotonType extends ParticleType<PhotonOptions> {
    private final Codec<PhotonOptions> codec = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("variant").forGetter(PhotonOptions::variant),
            Codec.INT.fieldOf("rgb").forGetter(PhotonOptions::rgb),
            Codec.INT.fieldOf("style").forGetter(PhotonOptions::packedStyle)
    ).apply(instance, (variant, rgb, style) -> new PhotonOptions(this, variant, rgb, style)));
    private volatile VisualParticles.LocalGate localGate = VisualParticles.LocalGate.ADMIT_ALL;

    public PhotonType() {
        super(false, PhotonOptions.DESERIALIZER);
    }

    @Override
    public Codec<PhotonOptions> codec() {
        return codec;
    }

    public PhotonOptions options(int variant, int rgb, float size, float speed) {
        return PhotonOptions.of(this, variant, rgb, size, speed);
    }

    /** Client setup installs a decoration budget here; the default admits everything. */
    public void setLocalGate(VisualParticles.LocalGate gate) {
        localGate = Objects.requireNonNull(gate, "gate");
    }

    public boolean allowLocal(Level level, double x, double y, double z) {
        return level.isClientSide && localGate.allow(level, x, y, z);
    }
}
