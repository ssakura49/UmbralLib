package com.ssakuracotero.umbralib.api.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ssakuracotero.umbralib.api.particle.GlowMoteOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;

/**
 * Additive glow mote (client). Drawn with {@code SRC_ALPHA, ONE} blending on the vanilla particle shader and atlas,
 * so it only ever brightens what is behind it: it reads as light on a daytime sky as well as at night, and never as
 * a dark speck. Depth writes are off, so overlapping motes add up instead of cutting each other.
 *
 * <p>Brightness is carried by the colour, not the alpha: additive output is {@code rgb * alpha}, so fading the
 * colour keeps the sprite's stepped alpha (pixel-cluster edges) intact and avoids the shader's alpha discard popping
 * the mote out early. Fade: quick linear rise over the first 15% of the life, then {@code (1-p)^1.6} ease-out;
 * {@link GlowMoteOptions#TWINKLE} multiplies a flicker. Size shrinks to 55% unless {@link GlowMoteOptions#NO_SHRINK}.
 *
 * <p>Register with {@code event.registerSpriteSet(type, sprites -> new GlowMoteParticle.Provider(sprites, count))},
 * where {@code count} is the number of textures in the particle definition JSON.
 */
public final class GlowMoteParticle extends TextureSheetParticle {
    /** Additive particle sheet: particle atlas, {@code SRC_ALPHA, ONE}, no depth write. Restores defaults on end. */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "UMBRALIB_GLOW_ADDITIVE";
        }
    };

    private static final float FADE_IN = 0.15f;
    private static final float FADE_OUT_POWER = 1.6f;
    private static final float SHRINK_TO = 0.55f;
    /** Vanilla {@code Particle#tick} applies {@code 0.04 * gravity} per tick. */
    private static final float VANILLA_GRAVITY_SCALE = 0.04f;

    private final float baseSize;
    private final float baseR;
    private final float baseG;
    private final float baseB;
    private final float phase;
    private final boolean twinkle;
    private final boolean shrink;

    private GlowMoteParticle(ClientLevel level, double x, double y, double z,
                             double vx, double vy, double vz, TextureAtlasSprite sprite, GlowMoteOptions options) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = options.life();
        this.baseSize = options.size();
        this.quadSize = this.baseSize;
        this.friction = options.drag();
        this.gravity = options.gravity() / VANILLA_GRAVITY_SCALE;
        this.hasPhysics = false;
        this.roll = 0f;
        this.oRoll = 0f;
        int rgb = options.rgb();
        this.baseR = (rgb >> 16 & 255) / 255f;
        this.baseG = (rgb >> 8 & 255) / 255f;
        this.baseB = (rgb & 255) / 255f;
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        this.twinkle = options.has(GlowMoteOptions.TWINKLE);
        this.shrink = !options.has(GlowMoteOptions.NO_SHRINK);
        this.alpha = 1f;
        this.setSprite(sprite);
        this.applyIntensity(0f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ADDITIVE;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public float getQuadSize(float partialTick) {
        if (!this.shrink) return this.baseSize;
        float p = Mth.clamp((this.age + partialTick) / Math.max(1, this.lifetime), 0f, 1f);
        return this.baseSize * (1f - (1f - SHRINK_TO) * p);
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        this.applyIntensity(partialTick);
        super.render(consumer, camera, partialTick);
    }

    private void applyIntensity(float partialTick) {
        float t = this.age + partialTick;
        float p = Mth.clamp(t / Math.max(1, this.lifetime), 0f, 1f);
        float k = p < FADE_IN
                ? Math.max(0.2f, p / FADE_IN)
                : (float) Math.pow((1f - p) / (1f - FADE_IN), FADE_OUT_POWER);
        if (this.twinkle) {
            k *= 0.75f + 0.25f * Mth.sin(t * 2.1f + this.phase);
        }
        this.rCol = this.baseR * k;
        this.gCol = this.baseG * k;
        this.bCol = this.baseB * k;
    }

    public static final class Provider implements ParticleProvider<GlowMoteOptions> {
        /**
         * {@link SpriteSet} has no indexed lookup in 1.20.1; {@code get(age, lifetime)} maps to
         * {@code age * (size - 1) / lifetime}, so {@code get(i, count - 1)} is sprite {@code i}.
         */
        private final SpriteSet sprites;
        private final int spriteCount;

        public Provider(SpriteSet sprites, int spriteCount) {
            this.sprites = sprites;
            this.spriteCount = Math.max(1, spriteCount);
        }

        @Override
        public Particle createParticle(GlowMoteOptions options, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            int variant = Math.floorMod(options.variant(), this.spriteCount);
            TextureAtlasSprite sprite = this.sprites.get(variant, Math.max(1, this.spriteCount - 1));
            return new GlowMoteParticle(level, x, y, z, vx, vy, vz, sprite, options);
        }
    }
}