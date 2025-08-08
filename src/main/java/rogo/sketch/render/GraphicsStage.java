package rogo.sketch.render;

import rogo.sketch.util.Identifier;
import rogo.sketch.util.OrderRequirement;

public class GraphicsStage {
    private final Identifier identifier;
    private final OrderRequirement<GraphicsStage> orderRequirement;

    public GraphicsStage(String identifier, OrderRequirement<GraphicsStage> orderRequirement) {
        this(Identifier.valueOf(identifier), orderRequirement);
    }

    public GraphicsStage(Identifier identifier, OrderRequirement<GraphicsStage> orderRequirement) {
        this.identifier = identifier;
        this.orderRequirement = orderRequirement;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public OrderRequirement<GraphicsStage> getOrderRequirement() {
        return orderRequirement;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GraphicsStage that = (GraphicsStage) obj;
        return identifier.equals(that.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }
}