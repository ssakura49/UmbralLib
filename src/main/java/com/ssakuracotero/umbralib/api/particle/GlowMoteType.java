package com.ssakuracotero.umbralib.api.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Additive glow mote type: light dust that brightens what is behind it instead of covering it, so it reads as
 * light on bright skies and in the dark alike. Each mod registers its own instance under its own ID together with
 * its own sprite list; the instance carries that mod's local spawn gate. The library ships no sprites or colours.
 */
public final class GlowMoteType extends ParticleType<GlowMoteOptions> {
    private final Codec<GlowMoteOptions> codec = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("variant").forGetter(GlowMoteOptions::variant),
            Codec.INT.fieldOf("rgb").forGetter(GlowMoteOptions::rgb),
            Codec.INT.fieldOf("size_milli").forGetter(GlowMoteOptions::sizeMilli),
            Codec.INT.fieldOf("life").forGetter(GlowMoteOptions::life),
            Codec.INT.fieldOf("drag_milli").forGetter(GlowMoteOptions::dragMilli),
            Codec.INT.fieldOf("gravity_micro").forGetter(GlowMoteOptions::gravityMicro),
            Codec.INT.fieldOf("flags").forGetter(GlowMoteOptions::flags)
    ).apply(instance, (variant, rgb, size, life, drag, gravity, flags) ->
            new GlowMoteOptions(this, variant, rgb, size, life, drag, gravity, flags)));
    private volatile VisualParticles.LocalGate localGate = VisualParticles.LocalGate.ADMIT_ALL;

    public GlowMoteType() {
        super(false, GlowMoteOptions.DESERIALIZER);
    }

    @Override
    public Codec<GlowMoteOptions> codec() {
        return codec;
    }

    /** Client setup installs a decoration budget here; the default admits everything. */
    public void setLocalGate(VisualParticles.LocalGate gate) {
        localGate = Objects.requireNonNull(gate, "gate");
    }

    public boolean allowLocal(Level level, double x, double y, double z) {
        return level.isClientSide && localGate.allow(level, x, y, z);
    }
}