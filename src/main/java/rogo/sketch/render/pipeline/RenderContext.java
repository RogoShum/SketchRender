package rogo.sketch.render.pipeline;

import org.joml.Matrix4f;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class RenderContext {
    private final Map<Identifier, Object> contextMap = new HashMap<>();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    protected int windowWidth;
    protected int windowHeight;
    protected int renderTick;
    protected float partialTicks;

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

    public void preStage(Identifier stage) {

    }

    public void postStage(Identifier stage) {
        this.set(Identifier.of("rendered"), false);
    }

    public void set(Identifier key, Object value) {
        contextMap.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Identifier key) {
        return (T) contextMap.get(key);
    }
}