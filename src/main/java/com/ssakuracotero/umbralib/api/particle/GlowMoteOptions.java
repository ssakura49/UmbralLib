package com.ssakuracotero.umbralib.api.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * Glow mote parameters. All values are stored as integers so they survive the network and command paths unchanged.
 *
 * @param variant  sprite index in the type's sprite list (wrapped by the sprite count)
 * @param rgb      tint, 0xRRGGBB; additive blending brightens it, so keep it below pure white
 * @param sizeMilli quad half-size in 1/1000 block
 * @param life     lifetime in ticks (at least 2)
 * @param dragMilli per-tick velocity multiplier in 1/1000 (1000 = no drag)
 * @param gravityMicro downward acceleration in 1/1,000,000 block/tick² (negative rises)
 * @param flags    {@link #TWINKLE}, {@link #NO_SHRINK}
 */
public record GlowMoteOptions(GlowMoteType type, int variant, int rgb, int sizeMilli, int life,
                              int dragMilli, int gravityMicro, int flags) implements ParticleOptions {
    /** Brightness flickers over the lifetime. */
    public static final int TWINKLE = 1;
    /** Keeps the full size instead of shrinking towards the end. */
    public static final int NO_SHRINK = 2;

    public static final ParticleOptions.Deserializer<GlowMoteOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public GlowMoteOptions fromNetwork(ParticleType<GlowMoteOptions> type, FriendlyByteBuf buf) {
            return new GlowMoteOptions((GlowMoteType) type, buf.readVarInt(), buf.readInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readVarInt());
        }

        @Override
        public GlowMoteOptions fromCommand(ParticleType<GlowMoteOptions> type, StringReader reader)
                throws CommandSyntaxException {
            reader.expect(' ');
            int variant = reader.readInt();
            reader.expect(' ');
            int rgb = reader.readInt();
            reader.expect(' ');
            float size = reader.readFloat();
            reader.expect(' ');
            int life = reader.readInt();
            return of((GlowMoteType) type, variant, rgb, size, life, 1f, 0f, 0);
        }
    };

    public GlowMoteOptions {
        Objects.requireNonNull(type, "type");
        rgb &= 0xFFFFFF;
        sizeMilli = Math.max(1, Math.min(4000, sizeMilli));
        life = Math.max(2, Math.min(600, life));
        dragMilli = Math.max(0, Math.min(1000, dragMilli));
        gravityMicro = Math.max(-100_000, Math.min(100_000, gravityMicro));
    }

    /**
     * @param size    quad half-size in blocks
     * @param drag    per-tick velocity multiplier, 0..1
     * @param gravity downward acceleration in block/tick² (negative rises)
     */
    public static GlowMoteOptions of(GlowMoteType type, int variant, int rgb, float size, int life,
                                     float drag, float gravity, int flags) {
        return new GlowMoteOptions(type, variant, rgb, Math.round(size * 1000f), life,
                Math.round(drag * 1000f), Math.round(gravity * 1_000_000f), flags);
    }

    public float size() {
        return sizeMilli / 1000f;
    }

    public float drag() {
        return dragMilli / 1000f;
    }

    public float gravity() {
        return gravityMicro / 1_000_000f;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public ParticleType<?> getType() {
        return type;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(variant);
        buf.writeInt(rgb);
        buf.writeVarInt(sizeMilli);
        buf.writeVarInt(life);
        buf.writeVarInt(dragMilli);
        buf.writeInt(gravityMicro);
        buf.writeVarInt(flags);
    }

    @Override
    public String writeToString() {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(type) + " " + variant + " " + rgb + " " + size() + " " + life;
    }
}