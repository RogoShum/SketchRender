package rogo.sketch.render.state;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL30;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.resource.RenderTarget;
import rogo.sketch.util.Identifier;

/**
 * Render state component for managing render targets
 */
public class RenderTargetState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("render_target_state");
    
    private RenderTarget renderTarget;
    private boolean shouldClear;

    // Default constructor for prototype
    public RenderTargetState() {
        this.renderTarget = null;
        this.shouldClear = false;
    }

    public RenderTargetState(RenderTarget renderTarget, boolean shouldClear) {
        this.renderTarget = renderTarget;
        this.shouldClear = shouldClear;
    }

    public RenderTargetState(RenderTarget renderTarget) {
        this(renderTarget, true);
    }

    /**
     * Default framebuffer state
     */
    public static RenderTargetState defaultFramebuffer() {
        return new RenderTargetState(null, false);
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public void apply(RenderContext context) {
        if (renderTarget != null) {
            renderTarget.bind();
            if (shouldClear) {
                renderTarget.clear();
            }
        } else {
            // Bind default framebuffer
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            // TODO: Get viewport from context
        }
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        this.shouldClear = json.has("shouldClear") ? json.get("shouldClear").getAsBoolean() : false;
        
        // TODO: Deserialize render target from JSON
        // For now, render targets are expected to be set programmatically
    }

    @Override
    public RenderStateComponent createInstance() {
        return new RenderTargetState();
    }

    public RenderTarget getRenderTarget() {
        return renderTarget;
    }

    public boolean shouldClear() {
        return shouldClear;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RenderTargetState)) return false;
        
        RenderTargetState that = (RenderTargetState) o;
        return shouldClear == that.shouldClear && 
               java.util.Objects.equals(renderTarget, that.renderTarget);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(renderTarget, shouldClear);
    }
}
