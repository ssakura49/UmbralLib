package com.ssakuracotero.umbralib.api.client.post;

import com.ssakuracotero.umbralib.api.client.ClientShaderCompatibility;
import com.ssakuracotero.umbralib.api.client.VisualLibrary;
import com.ssakuracotero.umbralib.api.client.VisualSettings;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL13;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Comparator;
import java.util.stream.Stream;

/** Scene color/depth lens pass used to bend the gravity field and entities within it. */
@Mod.EventBusSubscriber(modid = VisualLibrary.NAMESPACE, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GravitySceneLensRenderer {
    public static final ResourceLocation SHADER_ID = new ResourceLocation(VisualLibrary.NAMESPACE, "gravity_lens");
    private static final int MAX_RADIUS_PIXELS = 768;
    private static ShaderInstance shader;
    private static RenderTarget sceneCopy;
    private static volatile boolean reloadPending;
    private static volatile boolean releasePending;
    private static boolean failed;
    private static int sceneDepthTexture;
    private static FrameCamera frameCamera;

    private record DepthFormat(int internalFormat, int format, int type, int attachment) { }
    private record FrameCamera(Vec3 position, Matrix4f view, Matrix4f projection, Vector3f right,
                               int width, int height) { }

    public record Field(Vec3 position, float radius, float strength, float age) { }
    private record Projection(float u, float v, float radiusUv, float depth) { }
    private record TextureBindings(int active, int unit0, int unit1, int activeBinding) { }

    private GravitySceneLensRenderer() { }

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) {
        try {
            ShaderInstance instance = new ShaderInstance(event.getResourceProvider(), SHADER_ID, DefaultVertexFormat.POSITION_TEX);
            for (String uniform : new String[]{"umbralib_LensCenter", "umbralib_LensRadius", "umbralib_LensDepth",
                    "umbralib_LensStrength", "umbralib_LensAge", "umbralib_Aspect", "umbralib_DepthBias"}) {
                Uniform value = instance.getUniform(uniform);
                if (value == null) throw new IllegalArgumentException("Missing gravity lens uniform: " + uniform);
            }
            event.registerShader(instance, loaded -> {
                shader = loaded;
                failed = false;
                reloadPending = true;
                LogUtils.getLogger().info("Loaded gravity scene lens shader");
            });
        } catch (IOException | RuntimeException exception) {
            shader = null;
            failed = true;
            LogUtils.getLogger().warn("Gravity scene lens shader unavailable; using opaque geometry fallback", exception);
        }
    }

    public static void onResourceReload() {
        reloadPending = true;
    }

    public static void requestRelease() {
        if (RenderSystem.isOnRenderThread()) {
            destroyTarget();
            releasePending = false;
        } else {
            releasePending = true;
        }
    }

    public static void captureFrameCamera(RenderLevelStageEvent frame) {
        if (frame.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0) {
            frameCamera = null;
            return;
        }
        Vector3f right = frame.getCamera().rotation().transform(1, 0, 0, new Vector3f());
        frameCamera = new FrameCamera(frame.getCamera().getPosition(),
                new Matrix4f(frame.getPoseStack().last().pose()),
                new Matrix4f(frame.getProjectionMatrix()), right, main.width, main.height);
    }

    public static void drawNearest(RenderLevelStageEvent frame, Stream<Field> fields) {
        if (frame.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (reloadPending || releasePending) {
            reloadPending = false;
            releasePending = false;
            destroyTarget();
        }
        Field field = fields.filter(candidate -> candidate != null && candidate.strength() > .001f && candidate.radius() > .01f)
                .min(Comparator.comparingDouble(candidate -> candidate.position().distanceToSqr(frame.getCamera().getPosition())))
                .orElse(null);
        if (field == null || !allowed()) {
            destroyTarget();
            return;
        }
        if (frameCamera == null) return;
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return;
        if (frameCamera.width() != main.width || frameCamera.height() != main.height) return;
        Projection projection = project(frameCamera, field);
        if (projection == null) return;
        try {
            ensureTarget(main);
            copyScene(main, sceneCopy);
            composite(main, sceneCopy, projection, field, frame);
        } catch (RuntimeException | LinkageError exception) {
            failed = true;
            destroyTarget();
            LogUtils.getLogger().warn("Gravity scene lens failed; retaining the vanilla opaque scene", exception);
        }
    }

    private static boolean allowed() {
        VisualSettings settings = VisualLibrary.settings();
        return !failed && shader != null && settings.gravitySceneLens()
                && settings.energyShaders() && ClientShaderCompatibility.allowsCustomPrograms();
    }

    private static Projection project(FrameCamera camera, Field field) {
        Vec3 relative = field.position().subtract(camera.position());
        Vector4f centerView = camera.view().transform(new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 0));
        Vector4f clip = camera.projection().transform(new Vector4f(centerView.x, centerView.y, centerView.z, 1));
        if (!(clip.w > 1.0e-4f)) return null;
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (ndcX < -1.4f || ndcX > 1.4f || ndcY < -1.4f || ndcY > 1.4f) return null;
        Vector3f edge = new Vector3f(camera.right()).mul(field.radius());
        Vector4f edgeView = camera.view().transform(new Vector4f(edge.x, edge.y, edge.z, 0));
        Vector4f edgeClip = camera.projection().transform(new Vector4f(centerView.x + edgeView.x,
                centerView.y + edgeView.y, centerView.z + edgeView.z, 1));
        float radiusNdc = edgeClip.w > 1.0e-4f ? Math.abs(edgeClip.x / edgeClip.w - ndcX) : 0;
        float radiusUv = radiusNdc * (float) camera.height() / camera.width() * .5f;
        if (!(radiusUv > 1.0e-5f) || !Float.isFinite(radiusUv)) return null;
        float depth = clip.z / clip.w * .5f + .5f;
        if (!Float.isFinite(depth) || depth < 0 || depth >= 1) return null;
        return new Projection(ndcX * .5f + .5f, ndcY * .5f + .5f, radiusUv, depth);
    }

    private static void ensureTarget(RenderTarget main) {
        int width = main.width, height = main.height;
        if (sceneCopy != null && sceneCopy.width == width && sceneCopy.height == height && sceneDepthTexture != 0) return;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        TextureBindings textures = captureTextureBindings();
        try {
            destroyTarget();
            sceneCopy = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            sceneCopy.setFilterMode(GL11.GL_LINEAR);
            sceneDepthTexture = createDepthCopyTexture(main.getDepthTextureId(), width, height, sceneCopy.frameBufferId);
        } finally {
            restoreFramebuffers(read, draw, viewport);
            restoreTextureBindings(textures);
        }
    }

    private static void copyScene(RenderTarget main, RenderTarget copy) {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height,
                    GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    private static int createDepthCopyTexture(int sourceDepthTexture, int width, int height, int targetFramebuffer) {
        int sourceFormat = textureInternalFormat(sourceDepthTexture);
        DepthFormat format = switch (sourceFormat) {
            case 0x8CAD -> new DepthFormat(0x8CAD, 0x84F9, 0x8DAD, GL30.GL_DEPTH_STENCIL_ATTACHMENT);
            case 0x88F0 -> new DepthFormat(0x88F0, 0x84F9, 0x84FA, GL30.GL_DEPTH_STENCIL_ATTACHMENT);
            case 0x8CAC -> new DepthFormat(0x8CAC, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, GL30.GL_DEPTH_ATTACHMENT);
            case 0x81A6 -> new DepthFormat(0x81A6, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, GL30.GL_DEPTH_ATTACHMENT);
            case 0x81A5 -> new DepthFormat(0x81A5, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_SHORT, GL30.GL_DEPTH_ATTACHMENT);
            default -> throw new IllegalStateException("Unsupported main depth texture format: 0x" + Integer.toHexString(sourceFormat));
        };
        TextureBindings bindings = captureTextureBindings();
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int texture = GlStateManager._genTexture();
        try {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format.internalFormat(), width, height, 0,
                    format.format(), format.type(), (java.nio.ByteBuffer) null);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, format.attachment(), GL11.GL_TEXTURE_2D, texture, 0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Gravity lens framebuffer incomplete after depth attachment: 0x" + Integer.toHexString(status));
            return texture;
        } catch (RuntimeException | LinkageError exception) {
            GlStateManager._deleteTexture(texture);
            throw exception;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            restoreTextureBindings(bindings);
        }
    }

    private static int textureInternalFormat(int texture) {
        TextureBindings bindings = captureTextureBindings();
        try {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(texture);
            return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        } finally {
            restoreTextureBindings(bindings);
        }
    }

    private static void composite(RenderTarget main, RenderTarget copy, Projection projection,
                                  Field field, RenderLevelStageEvent frame) {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        TextureBindings oldTextures = captureTextureBindings();
        ShaderInstance oldShader = RenderSystem.getShader();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        IntBuffer oldScissor = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, oldScissor);
        try {
            main.bindWrite(false);
            int pixelRadius = Math.min(MAX_RADIUS_PIXELS,
                    Math.max(2, (int) Math.ceil(projection.radiusUv() * main.height * 1.55f)));
            int centerX = Math.round(projection.u() * main.width);
            int centerY = Math.round(projection.v() * main.height);
            int scissorX = Math.max(0, centerX - pixelRadius);
            int scissorY = Math.max(0, centerY - pixelRadius);
            int scissorRight = Math.min(main.width, centerX + pixelRadius);
            int scissorTop = Math.min(main.height, centerY + pixelRadius);
            if (scissorRight <= scissorX || scissorTop <= scissorY) return;
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(scissorX, scissorY, scissorRight - scissorX, scissorTop - scissorY);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, copy.getColorTextureId());
            RenderSystem.setShaderTexture(1, sceneDepthTexture);
            uniform("umbralib_LensCenter").set(projection.u(), projection.v());
            uniform("umbralib_LensRadius").set(projection.radiusUv());
            uniform("umbralib_LensDepth").set(projection.depth());
            uniform("umbralib_LensStrength").set(field.strength());
            uniform("umbralib_LensAge").set(field.age());
            uniform("umbralib_Aspect").set((float) main.width / main.height);
            uniform("umbralib_DepthBias").set(.00035f);
            drawQuad();
        } finally {
            if (oldShader != null) {
                RenderSystem.setShader(() -> oldShader);
                oldShader.apply();
            }
            restoreTextureBindings(oldTextures);
            if (depthMask) RenderSystem.depthMask(true); else RenderSystem.depthMask(false);
            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (scissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(oldScissor.get(0), oldScissor.get(1), oldScissor.get(2), oldScissor.get(3));
            } else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            restoreFramebuffers(read, draw, viewport);
        }
    }

    private static Uniform uniform(String name) {
        Uniform uniform = shader.getUniform(name);
        if (uniform == null) throw new IllegalStateException("Gravity lens shader lost uniform " + name);
        return uniform;
    }

    private static void drawQuad() {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertex(builder, -1, -1, 0, 0);
        vertex(builder, 1, -1, 1, 0);
        vertex(builder, 1, 1, 1, 1);
        vertex(builder, -1, 1, 0, 1);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, float x, float y, float u, float v) {
        builder.vertex(x, y, 0).uv(u, v).endVertex();
    }

    private static void restoreFramebuffers(int read, int draw, IntBuffer viewport) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
    }

    private static TextureBindings captureTextureBindings() {
        int active = GlStateManager._getActiveTexture();
        int activeBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int unit0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        int unit1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(active);
        return new TextureBindings(active, unit0, unit1, activeBinding);
    }

    private static void restoreTextureBindings(TextureBindings bindings) {
        RenderSystem.setShaderTexture(0, bindings.unit0());
        RenderSystem.setShaderTexture(1, bindings.unit1());
        if (bindings.active() != GL13.GL_TEXTURE0 && bindings.active() != GL13.GL_TEXTURE1) {
            GlStateManager._activeTexture(bindings.active());
            GlStateManager._bindTexture(bindings.activeBinding());
        }
        GlStateManager._activeTexture(bindings.active());
    }

    private static void destroyTarget() {
        if (sceneCopy == null) return;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        TextureBindings textures = captureTextureBindings();
        RenderTarget target = sceneCopy;
        sceneCopy = null;
        int depthTexture = sceneDepthTexture;
        sceneDepthTexture = 0;
        try {
            target.destroyBuffers();
            if (depthTexture != 0) GlStateManager._deleteTexture(depthTexture);
        } finally {
            restoreFramebuffers(read, draw, viewport);
            restoreTextureBindings(textures);
        }
    }
}
