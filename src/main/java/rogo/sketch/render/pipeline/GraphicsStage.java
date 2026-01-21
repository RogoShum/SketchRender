package rogo.sketch.render.pipeline;

import rogo.sketch.util.KeyId;
import rogo.sketch.util.OrderRequirement;

public class GraphicsStage {
    private final KeyId keyId;
    private final OrderRequirement<GraphicsStage> orderRequirement;
    private final boolean isDedicatedTranslucentStage;
    private final Boolean translucentFollowsSolid; // null means use pipeline config default

    public GraphicsStage(String identifier, OrderRequirement<GraphicsStage> orderRequirement) {
        this(KeyId.valueOf(identifier), orderRequirement, false, null);
    }

    public GraphicsStage(KeyId keyId, OrderRequirement<GraphicsStage> orderRequirement) {
        this(keyId, orderRequirement, false, null);
    }

    public GraphicsStage(KeyId keyId, OrderRequirement<GraphicsStage> orderRequirement,
            boolean isDedicatedTranslucentStage, Boolean translucentFollowsSolid) {
        this.keyId = keyId;
        this.orderRequirement = orderRequirement;
        this.isDedicatedTranslucentStage = isDedicatedTranslucentStage;
        this.translucentFollowsSolid = translucentFollowsSolid;
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
                '}';
    }
}
