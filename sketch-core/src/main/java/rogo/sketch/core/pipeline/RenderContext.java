package rogo.sketch.core.pipeline;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.util.KeyId;

import java.util.HashMap;
import java.util.Map;

public class RenderContext {
    private final Map<KeyId, Object> contextMap = new HashMap<>();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    protected Vector3f cameraPosition = new Vector3f();
    protected Vector3f cameraDirection = new Vector3f();
    protected Vector3f cameraUp = new Vector3f();
    protected Vector3f cameraLeft = new Vector3f();
    protected int windowWidth;
    protected int windowHeight;
    protected int renderTick;
    protected float partialTicks;
    protected FrustumIntersection frustum; // JOML frustum for culling

    protected ShaderProvider shaderProvider;

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

    public int windowHeight() {
        return windowHeight;
    }

    public int windowWidth() {
        return windowWidth;
    }

    public void setShaderProvider(ShaderProvider shaderProvider) {
        this.shaderProvider = shaderProvider;
    }

    public ShaderProvider shaderProvider() {
        return shaderProvider;
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
        this.set(KeyId.of("rendered"), false);
    }

    public void set(KeyId key, Object value) {
        contextMap.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(KeyId key) {
        return (T) contextMap.get(key);
    }
}