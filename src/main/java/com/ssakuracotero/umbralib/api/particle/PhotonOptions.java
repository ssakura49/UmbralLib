package com.ssakuracotero.umbralib.api.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/** Photon parameters: its type, full RGB; size and speed are multipliers and do not change the lifetime. */
public record PhotonOptions(PhotonType type, int variant, int rgb, int packedStyle) implements ParticleOptions {
    public static final ParticleOptions.Deserializer<PhotonOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public PhotonOptions fromNetwork(ParticleType<PhotonOptions> type, FriendlyByteBuf buf) {
            return new PhotonOptions((PhotonType) type, buf.readInt(), buf.readInt(), buf.readInt());
        }

        @Override
        public PhotonOptions fromCommand(ParticleType<PhotonOptions> type, StringReader reader)
                throws CommandSyntaxException {
            reader.expect(' ');
            int variant = reader.readInt();
            reader.expect(' ');
            int rgb = reader.readInt();
            reader.expect(' ');
            float size = reader.readFloat();
            reader.expect(' ');
            float speed = reader.readFloat();
            return of((PhotonType) type, variant, rgb, size, speed);
        }
    };

    public PhotonOptions {
        Objects.requireNonNull(type, "type");
    }

    /** Both multipliers are stored at 0.01 precision, range 0.01..2.55. Command RGB is a decimal 0xRRGGBB value. */
    public static PhotonOptions of(PhotonType type, int variant, int rgb, float size, float speed) {
        int sizeByte = Math.max(1, Math.min(255, Math.round(size * 100f)));
        int speedByte = Math.max(1, Math.min(255, Math.round(speed * 100f)));
        return new PhotonOptions(type, variant, rgb & 0xFFFFFF, sizeByte << 8 | speedByte);
    }

    public float sizeMul() {
        return 0.01f * (packedStyle >>> 8 & 0xFF);
    }

    public float speedMul() {
        return 0.01f * (packedStyle & 0xFF);
    }

    @Override
    public ParticleType<?> getType() {
        return type;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(variant);
        buf.writeInt(rgb);
        buf.writeInt(packedStyle);
    }

    @Override
    public String writeToString() {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(type) + " " + variant + " " + rgb + " " + sizeMul() + " " + speedMul();
    }
}
