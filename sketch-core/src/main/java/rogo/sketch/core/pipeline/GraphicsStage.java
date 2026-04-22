package rogo.sketch.core.pipeline;

import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.OrderRequirement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphicsStage {
    private final KeyId keyId;
    private final OrderRequirement<GraphicsStage> orderRequirement;
    private final boolean isDedicatedTranslucentStage;
    private final Boolean translucentFollowsSolid; // null means use pipeline config default
    private final Map<String, String> stageMacros;
    private final ShaderVariantKey stageVariantKey;

    public GraphicsStage(String identifier, OrderRequirement<GraphicsStage> orderRequirement) {
        this(KeyId.valueOf(identifier), orderRequirement, false, null);
    }

    public GraphicsStage(String identifier, OrderRequirement<GraphicsStage> orderRequirement, Map<String, String> stageMacros) {
        this(KeyId.valueOf(identifier), orderRequirement, false, null, stageMacros);
    }

    public GraphicsStage(KeyId keyId, OrderRequirement<GraphicsStage> orderRequirement) {
        this(keyId, orderRequirement, false, null);
    }

    public GraphicsStage(KeyId keyId, OrderRequirement<GraphicsStage> orderRequirement, Map<String, String> stageMacros) {
        this(keyId, orderRequirement, false, null, stageMacros);
    }

    public GraphicsStage(KeyId keyId, OrderRequirement<GraphicsStage> orderRequirement,
            boolean isDedicatedTranslucentStage, Boolean translucentFollowsSolid) {
        this(keyId, orderRequirement, isDedicatedTranslucentStage, translucentFollowsSolid, Map.of());
    }

    public GraphicsStage(
            KeyId keyId,
            OrderRequirement<GraphicsStage> orderRequirement,
            boolean isDedicatedTranslucentStage,
            Boolean translucentFollowsSolid,
            Map<String, String> stageMacros) {
        this.keyId = keyId;
        this.orderRequirement = orderRequirement;
        this.isDedicatedTranslucentStage = isDedicatedTranslucentStage;
        this.translucentFollowsSolid = translucentFollowsSolid;
        this.stageMacros = normalizeStageMacros(stageMacros);
        this.stageVariantKey = ShaderVariantKey.of(this.stageMacros.keySet());
    }

    public KeyId getIdentifier() {
        return keyId;
    }

    public OrderRequirement<GraphicsStage> getOrderRequirement() {
        return orderRequirement;
    }

    /**
     * Check if this stage is a dedicated translucent flush point.
     * When true, this stage will flush accumulated translucent commands.
     * 
     * @return true if dedicated translucent stage
     */
    public boolean isDedicatedTranslucentStage() {
        return isDedicatedTranslucentStage;
    }

    /**
     * Get whether translucent rendering follows solid rendering for this stage.
     * 
     * @return true if follows immediately, false if deferred, null if using
     *         pipeline config
     */
    public Boolean getTranslucentFollowsSolid() {
        return translucentFollowsSolid;
    }

    public Map<String, String> getStageMacros() {
        return stageMacros;
    }

    public ShaderVariantKey getStageVariantKey() {
        return stageVariantKey;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        GraphicsStage that = (GraphicsStage) obj;
        return keyId.equals(that.keyId);
    }

    @Override
    public int hashCode() {
        return keyId.hashCode();
    }

    @Override
    public String toString() {
        return "GraphicsStage{" +
                "identifier=" + keyId +
                ", dedicatedTranslucent=" + isDedicatedTranslucentStage +
                ", stageMacros=" + stageMacros.keySet() +
                '}';
    }

    private static Map<String, String> normalizeStageMacros(Map<String, String> stageMacros) {
        if (stageMacros == null || stageMacros.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : stageMacros.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isBlank()) {
                continue;
            }
            String value = entry.getValue();
            String normalizedValue = value == null || value.isBlank() ? "1" : value.trim();
            if (!"1".equals(normalizedValue)) {
                throw new IllegalArgumentException("Stage macro values are not supported yet: " + name);
            }
            normalized.put(name.trim(), "1");
        }
        return normalized.isEmpty() ? Map.of() : Collections.unmodifiableMap(normalized);
    }
}
