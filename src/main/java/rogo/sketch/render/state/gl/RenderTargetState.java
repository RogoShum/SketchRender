package rogo.sketch.render.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import rogo.sketch.api.RenderStateComponent;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.RenderTarget;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.util.Identifier;

import java.util.Optional;

public class RenderTargetState implements RenderStateComponent {
    public static final Identifier TYPE = Identifier.of("render_target");

    private Identifier renderTarget;

    public RenderTargetState() {
        this.renderTarget = Identifier.of("minecraft:main");
    }

    public RenderTargetState(Identifier identifier) {
        this.renderTarget = identifier;
    }

    public static RenderTargetState defaultFramebuffer() {
        return new RenderTargetState(Identifier.of("minecraft:main"));
    }

    @Override
    public Identifier getIdentifier() {
        return TYPE;
    }

    @Override
    public void apply(RenderContext context) {
        Optional<RenderTarget> rt = GraphicsResourceManager.getInstance().getResource(ResourceTypes.RENDER_TARGET, renderTarget);
        if (rt.isPresent()) {
            GL31.glBindFramebuffer(GL30.GL_FRAMEBUFFER, rt.get().getHandle());
        }
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        String id = json.get("identifier").getAsString();

        renderTarget = Identifier.of(id);
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
        return java.util.Objects.equals(renderTarget, that.renderTarget);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(renderTarget);
    }
}