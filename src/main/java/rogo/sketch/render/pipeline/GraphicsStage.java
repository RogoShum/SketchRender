package rogo.sketch.render.pipeline;

import rogo.sketch.util.KeyId;
import rogo.sketch.util.OrderRequirement;

public class GraphicsStage {
    private final KeyId keyId;
    private final OrderRequirement<GraphicsStage> orderRequirement;

    public GraphicsStage(String identifier, OrderRequirement<GraphicsStage> orderRequirement) {
        this(KeyId.valueOf(identifier), orderRequirement);
    }

    public GraphicsStage(KeyId keyId, OrderRequirement<GraphicsStage> orderRequirement) {
        this.keyId = keyId;
        this.orderRequirement = orderRequirement;
    }

    public KeyId getIdentifier() {
        return keyId;
    }

    public OrderRequirement<GraphicsStage> getOrderRequirement() {
        return orderRequirement;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
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
                '}';
    }
}