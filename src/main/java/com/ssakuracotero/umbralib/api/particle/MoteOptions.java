package com.ssakuracotero.umbralib.api.particle;

import com.mojang.brigadier.StringReader;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * Parameters of a mote particle: its type, a sprite variant and a tint. Create through {@link MoteType#options}.
 *
 * <p>Variant and tint travel as one int (high 8 bits variant, low 24 bits RGB). What each variant index means is
 * defined by the {@link MoteVariants} table installed on the type.
 */
public record MoteOptions(MoteType type, int variant, int rgb) implements ParticleOptions {
    /** Required by the 1.20.1 {@link ParticleType} constructor; network and command entry points. */
    public static final ParticleOptions.Deserializer<MoteOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public MoteOptions fromNetwork(ParticleType<MoteOptions> type, FriendlyByteBuf buf) {
            return unpack((MoteType) type, buf.readInt());
        }

        @Override
        public MoteOptions fromCommand(ParticleType<MoteOptions> type, StringReader reader) {
            return new MoteOptions((MoteType) type, 0, 0xFFFFFF);
        }
    };

    public MoteOptions {
        Objects.requireNonNull(type, "type");
    }

    public static MoteOptions unpack(MoteType type, int packed) {
        return new MoteOptions(type, packed >>> 24 & 0xFF, packed & 0xFFFFFF);
    }

    public int packed() {
        return (variant & 0xFF) << 24 | rgb & 0xFFFFFF;
    }

    @Override
    public ParticleType<?> getType() {
        return type;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(packed());
    }

    @Override
    public String writeToString() {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(type) + "[" + variant + "," + Integer.toHexString(rgb) + "]";
    }
}
