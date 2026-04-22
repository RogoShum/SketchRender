package rogo.sketch.core.driver.state.component;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.pipeline.PipelineConfig;
import rogo.sketch.core.pipeline.TargetBinding;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RenderTargetState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("render_target");
    private KeyId rtId;
    private List<Object> drawBuffers;

    public RenderTargetState() {
        this(PipelineConfig.DEFAULT_RENDER_TARGET_ID);
    }

    public RenderTargetState(KeyId keyId) {
        this(keyId, null);
    }

    public RenderTargetState(KeyId keyId, List<Object> drawBuffers) {
        this.rtId = keyId;
        this.drawBuffers = drawBuffers;
    }

    public static RenderTargetState defaultFramebuffer() {
        return new RenderTargetState(PipelineConfig.DEFAULT_RENDER_TARGET_ID);
    }

    public KeyId renderTargetId() {
        return rtId;
    }

    public List<Object> drawBuffers() {
        return drawBuffers != null ? List.copyOf(drawBuffers) : List.of();
    }

    public TargetBinding toTargetBinding() {
        return new TargetBinding(rtId, drawBuffers(), null, null);
    }

    @Override
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson, rogo.sketch.core.resource.GraphicsResourceManager resourceManager) {
        String id = json.get("identifier").getAsString();
        this.rtId = KeyId.of(id);

        if (json.has("drawBuffers")) {
            this.drawBuffers = new ArrayList<>();
            JsonArray compArray = json.getAsJsonArray("drawBuffers");

            for (JsonElement element : compArray) {
                if (element.getAsJsonPrimitive().isNumber()) {
                    this.drawBuffers.add(element.getAsInt());
                } else {
                    this.drawBuffers.add(KeyId.of(element.getAsString()));
                }
            }
        } else {
            this.drawBuffers = null;
        }
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
        return Objects.equals(rtId, that.rtId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rtId);
    }
}


