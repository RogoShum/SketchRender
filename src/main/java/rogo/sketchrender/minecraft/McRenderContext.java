package rogo.sketchrender.minecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import rogo.sketchrender.render.RenderContext;

public class McRenderContext extends RenderContext {
    private final PoseStack viewMatrixStack = new PoseStack();
    private final PoseStack modelMatrixStack = new PoseStack();
    private final PoseStack vanillaModelView;
    private Matrix4f projectionMatrix;

    private int renderTick;
    private float partialTicks;
    private Camera camera;
    private Frustum frustum;
    private LevelRenderer levelRenderer;

    public McRenderContext(LevelRenderer levelRenderer, PoseStack vanillaModelView, Matrix4f projectionMatrix, Camera camera, Frustum frustum, int renderTick, float partialTicks) {
        this.levelRenderer = levelRenderer;
        this.vanillaModelView = vanillaModelView;
        this.projectionMatrix = projectionMatrix;
        this.camera = camera;
        this.frustum = frustum;
        this.renderTick = renderTick;
        this.partialTicks = partialTicks;
    }

    public PoseStack vanillaModelView() {
        return vanillaModelView;
    }

    public PoseStack viewMatrixStack() {
        return viewMatrixStack;
    }

    public PoseStack modelMatrixStack() {
        return modelMatrixStack;
    }

    public Matrix4f projectionMatrix() {
        return projectionMatrix;
    }

    public void setProjectionMatrix(Matrix4f projectionMatrix) {
        this.projectionMatrix = projectionMatrix;
    }

    public int renderTick() {
        return renderTick;
    }

    public void setRenderTick(int renderTick) {
        this.renderTick = renderTick;
    }

    public float partialTicks() {
        return partialTicks;
    }

    public void setPartialTicks(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public Camera camera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public Frustum frustum() {
        return frustum;
    }

    public void setFrustum(Frustum frustum) {
        this.frustum = frustum;
    }

    public LevelRenderer levelRenderer() {
        return levelRenderer;
    }

    public void setLevelRenderer(LevelRenderer levelRenderer) {
        this.levelRenderer = levelRenderer;
    }
}