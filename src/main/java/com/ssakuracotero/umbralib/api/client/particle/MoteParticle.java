package com.ssakuracotero.umbralib.api.client.particle;

import com.ssakuracotero.umbralib.api.particle.MoteMotion;
import com.ssakuracotero.umbralib.api.particle.MoteOptions;
import com.ssakuracotero.umbralib.api.particle.MoteVariants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Mote particle (client): sprite chosen by variant, tint from the options, motion from the {@link MoteVariants}
 * table installed on the particle's type (gravity, drag, horizontal swirl, speed/life/size multipliers).
 *
 * <p>Uses its own provider rather than {@code minecraft:generic} so whole sprites are used and can be picked by
 * index. Full-bright so motes stay visible in caves and at night. Register with
 * {@code event.registerSpriteSet(type, MoteParticle.Provider::new)}.
 */
public final class MoteParticle extends TextureSheetParticle {
    private static final int LIFETIME_MIN = 12;
    private static final int LIFETIME_RANDOM = 10;
    private static final float SIZE_BASE = 0.10f;
    private static final float SIZE_RANDOM = 0.06f;
    private static final float CHANNEL_MAX = 255f;
    /** Shorter than this only flickers and no longer reads as a particle. */
    private static final int LIFETIME_FLOOR = 5;
    private final MoteMotion motion;

    private MoteParticle(ClientLevel level, double x, double y, double z,
                         double vx, double vy, double vz,
                         TextureAtlasSprite sprite, int rgb, MoteMotion motion) {
        super(level, x, y, z);
        this.xd = vx * motion.speedMul();
        this.yd = vy * motion.speedMul();
        this.zd = vz * motion.speedMul();
        this.motion = motion;
        float life = motion.lifeMul();
        this.lifetime = Math.max(LIFETIME_FLOOR,
                Math.round((LIFETIME_MIN + this.random.nextInt(LIFETIME_RANDOM)) * life));
        float size = motion.sizeMul();
        this.quadSize = Math.max(0.02f, (SIZE_BASE + this.random.nextFloat() * SIZE_RANDOM) * size);
        // positive sinks, negative rises (same meaning as Particle#gravity)
        this.gravity = motion.gravity();
        this.hasPhysics = false;
        this.setSprite(sprite);
        this.setColor((rgb >> 16 & 0xFF) / CHANNEL_MAX, (rgb >> 8 & 0xFF) / CHANNEL_MAX, (rgb & 0xFF) / CHANNEL_MAX);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();
        float drag = this.motion.drag();
        if (drag != 1f) {
            this.xd *= drag;
            this.yd *= drag;
            this.zd *= drag;
        }
        float swirl = this.motion.swirl();
        if (swirl != 0f) {
            double rad = Math.toRadians(swirl);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double nx = this.xd * cos - this.zd * sin;
            double nz = this.xd * sin + this.zd * cos;
            this.xd = nx;
            this.zd = nz;
        }
        // fade out over the lifetime so the particle does not pop
        this.alpha = 1f - (float) this.age / this.lifetime;
    }

    public static final class Provider implements ParticleProvider<MoteOptions> {
        /**
         * {@link SpriteSet} has no indexed lookup in 1.20.1; {@code get(age, lifetime)} maps to
         * {@code age * (size - 1) / lifetime}, so {@code get(i, size - 1)} is sprite {@code i}.
         */
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(MoteOptions options, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            MoteVariants variants = options.type().variants();
            int count = Math.max(1, variants.count());
            int variant = Math.floorMod(options.variant(), count);
            TextureAtlasSprite sprite = this.sprites.get(variant, Math.max(1, count - 1));
            return new MoteParticle(level, x, y, z, vx, vy, vz, sprite, options.rgb(), variants.motion(variant));
        }
    }
}
