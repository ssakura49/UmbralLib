package com.ssakuracotero.umbralib.api.particle;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Parameterised mote particle type (variant + tint). Each mod registers its own instance under its own ID, for example
 * {@code PARTICLES.register("mote", () -> MY_MOTE)}; the instance carries that mod's variant table and local spawn gate,
 * so several mods can use motes side by side.
 *
 * <p>{@link MoteOptions#variant()} selects an index in the sprite list of this type's particle definition.
 */
public final class MoteType extends ParticleType<MoteOptions> {
    private final Codec<MoteOptions> codec = Codec.INT.xmap(packed -> MoteOptions.unpack(this, packed), MoteOptions::packed);
    private volatile MoteVariants variants = MoteVariants.SINGLE_NEUTRAL;
    private volatile VisualParticles.LocalGate localGate = VisualParticles.LocalGate.ADMIT_ALL;

    public MoteType() {
        super(false, MoteOptions.DESERIALIZER);
    }

    @Override
    public Codec<MoteOptions> codec() {
        return codec;
    }

    public MoteOptions options(int variant, int rgb) {
        return new MoteOptions(this, variant, rgb);
    }

    /** Install before particle providers are registered; {@code count()} must be at least 1. */
    public void installVariants(MoteVariants table) {
        Objects.requireNonNull(table, "table");
        if (table.count() < 1) {
            throw new IllegalArgumentException("Mote variant table must contain at least one variant");
        }
        variants = table;
    }

    public MoteVariants variants() {
        return variants;
    }

    /** Client setup installs a decoration budget here; the default admits everything. */
    public void setLocalGate(VisualParticles.LocalGate gate) {
        localGate = Objects.requireNonNull(gate, "gate");
    }

    public boolean allowLocal(Level level, double x, double y, double z) {
        return level.isClientSide && localGate.allow(level, x, y, z);
    }
}
