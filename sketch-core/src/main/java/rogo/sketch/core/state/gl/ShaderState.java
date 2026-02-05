package rogo.sketch.core.state.gl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Render state component for shader program binding.
 * Supports both legacy ShaderProvider references and new ShaderTemplate with variants.
 */
public class ShaderState implements RenderStateComponent {
    public static final KeyId TYPE = ResourceTypes.SHADER_TEMPLATE;

    // For new system - ShaderTemplate with variant support
    private ResourceReference<ShaderTemplate> template;
    private ShaderVariantKey variantKey = ShaderVariantKey.EMPTY;

    private KeyId shaderId;

    public ShaderState() {
        this.template = GraphicsResourceManager.getInstance().getReference(ResourceTypes.SHADER_TEMPLATE, KeyId.of("empty"));
        this.shaderId = KeyId.of("empty");
    }

    /**
     * Create a ShaderState with template and variant support.
     *
     * @param keyId The shader template ID
     * @param flags Variant flags
     */
    public ShaderState(KeyId keyId, String... flags) {
        this.shaderId = keyId;
        this.template = GraphicsResourceManager.getInstance().getReference(ResourceTypes.SHADER_TEMPLATE, keyId);
        this.variantKey = ShaderVariantKey.of(flags);
    }

    @Override
    public KeyId getIdentifier() {
        return TYPE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShaderState that = (ShaderState) o;
        return Objects.equals(shaderId, that.shaderId) &&
                Objects.equals(variantKey, that.variantKey);
    }

    @Override
    public void apply(RenderContext context) {
        applyTemplate(context);
    }

    private void applyTemplate(RenderContext context) {
        if (template == null) return;

        ShaderTemplate tmpl = template.get();
        if (tmpl == null) return;

        try {
            // Set the active variant and get the shader
            tmpl.setActiveVariantKey(variantKey);
            ShaderTemplate.ShaderVariantInfo variantInfo = tmpl.getVariantInfo(variantKey);

            GraphicsDriver.getCurrentAPI().getShaderStrategy().useProgram(variantInfo.getShader().getHandle());
            context.setShaderProvider(variantInfo.getShader()); // ShaderTemplate implements ShaderProvider

        } catch (IOException e) {
            System.err.println("Failed to get shader variant: " + e.getMessage());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(shaderId, variantKey);
    }

    @Override
    public void deserializeFromJson(JsonObject json, Gson gson) {
        if (json.has("identifier")) {
            String shaderIdStr = json.get("identifier").getAsString();
            this.shaderId = KeyId.of(shaderIdStr);

            // Check if this is a template (new system) or legacy shader
            // If "flags" or "template" is present, use template mode
            boolean hasFlags = json.has("flags");

            this.template = GraphicsResourceManager.getInstance()
                    .getReference(ResourceTypes.SHADER_TEMPLATE, shaderId);

            // Parse flags
            if (hasFlags && json.get("flags").isJsonArray()) {
                Set<String> flags = new HashSet<>();
                JsonArray flagsArray = json.getAsJsonArray("flags");
                for (JsonElement element : flagsArray) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        flags.add(element.getAsString());
                    }
                }
                this.variantKey = ShaderVariantKey.of(flags);
            }
        }
    }

    @Override
    public RenderStateComponent createInstance() {
        return new ShaderState();
    }

    /**
     * Get the shader template reference.
     */
    public ResourceReference<ShaderTemplate> getTemplate() {
        return template;
    }

    /**
     * Get the current variant key.
     */
    public ShaderVariantKey getVariantKey() {
        return variantKey;
    }

    /**
     * Get the shader ID.
     */
    public KeyId getShaderId() {
        return shaderId;
    }
}