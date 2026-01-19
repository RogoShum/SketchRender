package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.RenderTarget;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.util.KeyId;

public class RenderTargetState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("render_target");
    private ResourceReference<RenderTarget> renderTarget;
    private KeyId rtId;

    public RenderTargetState() {
        this.renderTarget = GraphicsResourceManager.getInstance().getReference(ResourceTypes.RENDER_TARGET, KeyId.of("minecraft:main_target"));
        this.rtId = KeyId.of("minecraft:main_target");
    }

    public RenderTargetState(KeyId keyId) {
        this.renderTarget = GraphicsResourceManager.getInstance().getReference(ResourceTypes.RENDER_TARGET, keyId);
        this.rtId = keyId;
    }

    public static RenderTargetState defaultFramebuffer() {
        return new RenderTargetState(KeyId.of("minecraft:main_target"));
    }

    @Override
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public void apply(RenderContext context) {
        if (renderTarget.isAvailable()) {
            GL31.glBindFramebuffer(GL30.GL_FRAMEBUFFER, renderTarget.get().getHandle());
        }
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        String id = json.get("identifier").getAsString();
        renderTarget = GraphicsResourceManager.getInstance().getReference(ResourceTypes.RENDER_TARGET, KeyId.of(id));
        this.rtId = KeyId.of(id);
    }

    @Override
    public RenderStateComponent createInstance() {
        return new RenderTargetState();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RenderTargetState)) return false;

        RenderTargetState that = (RenderTargetState) o;
        return java.util.Objects.equals(rtId, that.rtId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(rtId);
    }
}