package rogo.sketch.core.pipeline;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.shader.ShaderProgramHandle;
import rogo.sketch.core.shader.ShaderProgramResolver;
import rogo.sketch.core.util.KeyId;

import java.util.HashMap;
import java.util.Map;

public class RenderContext {
    public static final ContextKey<Boolean> RENDERED = ContextKey.of("sketch:rendered", Boolean.class);

    private final Map<ContextKey<?>, Object> contextMap = new HashMap<>();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    protected RenderStateManager renderStateManager;
    protected Vector3f cameraPosition = new Vector3f();
    protected Vector3f cameraDirection = new Vector3f();
    protected Vector3f cameraUp = new Vector3f();
    protected Vector3f cameraLeft = new Vector3f();
    protected int windowWidth;
    protected int windowHeight;
    protected int renderTick;
    protected float partialTicks;
    protected boolean nextTick = true;
    protected FrustumIntersection frustum; // JOML frustum for culling
    protected ShaderProgramHandle shaderProgramHandle;

    public Matrix4f viewMatrix() {
        return viewMatrix;
    }

    public Matrix4f modelMatrix() {
        return modelMatrix;
    }

    public Matrix4f projectionMatrix() {
        return projectionMatrix;
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

    public boolean nextTick() {
        return nextTick;
    }

    public void setNextTick(boolean nextTick) {
        this.nextTick = nextTick;
    }

    public int windowHeight() {
        return windowHeight;
    }

    public int windowWidth() {
        return windowWidth;
    }

    public Vector3f cameraPosition() {
        return cameraPosition;
    }

    public Vector3f cameraDirection() {
        return cameraDirection;
    }

    public Vector3f cameraUp() {
        return cameraUp;
    }

    public Vector3f cameraLeft() {
        return cameraLeft;
    }

    public void setShaderProgramHandle(ShaderProgramHandle shaderProgramHandle) {
        this.shaderProgramHandle = shaderProgramHandle;
    }

    public ShaderProgramHandle shaderProgramHandle() {
        return shaderProgramHandle;
    }

    public void setShaderProvider(ShaderProvider shaderProvider) {
        this.shaderProgramHandle = ShaderProgramResolver.adaptProgramHandle(shaderProvider);
    }

    public ShaderProvider shaderProvider() {
        return shaderProgramHandle;
    }

    public void setRenderStateManager(RenderStateManager renderStateManager) {
        this.renderStateManager = renderStateManager;
    }

    public RenderStateManager renderStateManager() {
        return renderStateManager;
    }

    public FrustumIntersection getFrustum() {
        return frustum;
    }

    public void setFrustum(FrustumIntersection frustum) {
        this.frustum = frustum;
    }

    public void preStage(KeyId stage) {

    }

    public void postStage(KeyId stage) {
        this.set(RENDERED, false);
    }

    public <T> void set(ContextKey<T> key, T value) {
        if (key == null) {
            return;
        }
        contextMap.put(key, value);
    }

    public <T> T get(ContextKey<T> key) {
        return key != null ? key.cast(contextMap.get(key)) : null;
    }

    public RenderContext snapshot() {
        RenderContext snapshot = new RenderContext();
        copyInto(snapshot);
        return snapshot;
    }

    protected void copyInto(RenderContext snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshot.contextMap.clear();
        snapshot.contextMap.putAll(this.contextMap);
        snapshot.viewMatrix.set(this.viewMatrix);
        snapshot.modelMatrix.set(this.modelMatrix);
        snapshot.projectionMatrix.set(this.projectionMatrix);
        snapshot.cameraPosition = new Vector3f(this.cameraPosition);
        snapshot.cameraDirection = new Vector3f(this.cameraDirection);
        snapshot.cameraUp = new Vector3f(this.cameraUp);
        snapshot.cameraLeft = new Vector3f(this.cameraLeft);
        snapshot.windowWidth = this.windowWidth;
        snapshot.windowHeight = this.windowHeight;
        snapshot.renderTick = this.renderTick;
        snapshot.partialTicks = this.partialTicks;
        snapshot.nextTick = this.nextTick;
        snapshot.frustum = this.frustum;
        snapshot.shaderProgramHandle = this.shaderProgramHandle;
    }
}

