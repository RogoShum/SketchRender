package rogo.sketch.core.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.resource.*;
import rogo.sketch.core.util.KeyId;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RenderTargetState implements RenderStateComponent {
    public static final KeyId TYPE = KeyId.of("render_target");
    private ResourceReference<RenderTarget> renderTarget;
    private KeyId rtId;
    private List<Object> drawBuffers;

    public RenderTargetState() {
        this(KeyId.of("minecraft:main_target"));
    }

    public RenderTargetState(KeyId keyId) {
        this(keyId, null);
    }

    public RenderTargetState(KeyId keyId, List<Object> drawBuffers) {
        this.renderTarget = GraphicsResourceManager.getInstance().getReference(ResourceTypes.RENDER_TARGET, keyId);
        this.rtId = keyId;
        this.drawBuffers = drawBuffers;
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
            renderTarget.get().bind();
            applyDrawBuffers(renderTarget.get());
        }
    }

    private void applyDrawBuffers(RenderTarget rt) {
        List<Integer> activeBuffers = new ArrayList<>();
        boolean shouldApply = false;

        if (rt instanceof StandardRenderTarget srt) {
            shouldApply = true;

            if (drawBuffers == null) {
                List<KeyId> attachments = srt.getColorAttachmentIds();
                for (int i = 0; i < attachments.size(); i++) {
                    activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + i);
                }
            } else {
                List<KeyId> attachments = srt.getColorAttachmentIds();
                for (Object comp : drawBuffers) {
                    if (comp instanceof KeyId keyId) {
                        int index = attachments.indexOf(keyId);
                        if (index >= 0) {
                            activeBuffers.add(GL30.GL_COLOR_ATTACHMENT0 + index);
                        }
                    } else if (comp instanceof Integer val) {
                        activeBuffers.add(val);
                    }
                }
            }
        } else {
            if (drawBuffers != null) {
                shouldApply = true;
                for (Object comp : drawBuffers) {
                    if (comp instanceof Integer val) {
                        activeBuffers.add(val);
                    }
                }
            }
        }

        if (shouldApply) {
            if (activeBuffers.isEmpty()) {
                GL11.glDrawBuffer(GL11.GL_NONE);
            } else {
                IntBuffer buffer = BufferUtils.createIntBuffer(activeBuffers.size());
                for (int activeBuffer : activeBuffers) {
                    buffer.put(activeBuffer);
                }
                buffer.flip();
                GL20.glDrawBuffers(buffer);
            }
        }
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        String id = json.get("identifier").getAsString();
        renderTarget = GraphicsResourceManager.getInstance().getReference(ResourceTypes.RENDER_TARGET, KeyId.of(id));
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