package rogo.sketch.core.data.format;

import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;

import java.util.Objects;

public class VertexBufferKey {
    private final RasterizationParameter renderParameter;
    private final long sourceResourceID;
    private final long bindingToken;

    private VertexBufferKey(RasterizationParameter rp, long sourceResourceID, long bindingToken) {
        this.renderParameter = rp;
        this.sourceResourceID = sourceResourceID;
        this.bindingToken = bindingToken;
    }

    /**
     * Create a VertexBufferKey from a RasterizationParameter.
     * The parameter's VertexLayoutSpec defines the components.
     */
    public static VertexBufferKey fromParameter(RasterizationParameter param) {
        return fromParameter(param, -1L, 0L);
    }

    /**
     * Create a VertexBufferKey with a specific source resource ID.
     * Used for BakedMeshes where the static VBOs come from a specific source.
     */
    public static VertexBufferKey fromParameter(RasterizationParameter param, long sourceResourceID) {
        return fromParameter(param, sourceResourceID, 0L);
    }

    public static VertexBufferKey fromParameter(RasterizationParameter param, long sourceResourceID, long bindingToken) {
        return new VertexBufferKey(param, sourceResourceID, bindingToken);
    }

    // ===== Getters =====

    public RasterizationParameter renderParameter() {
        return renderParameter;
    }

    public ComponentSpec[] components() {
        return renderParameter.getLayout().getComponents();
    }

    public ComponentSpec[] dynamicComponents() {
        return renderParameter.getLayout().getDynamicSpecs();
    }

    public ComponentSpec[] staticComponents() {
        return renderParameter.getLayout().getStaticSpecs();
    }

    public long sourceResourceID() {
        return sourceResourceID;
    }

    public long bindingToken() {
        return bindingToken;
    }

    /**
     * Check if this key has instance components (any instanced component).
     */
    public boolean hasInstancing() {
        return renderParameter.getLayout().hasInstancing();
    }

    // ===== equals & hashCode =====

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        VertexBufferKey that = (VertexBufferKey) o;
        return sourceResourceID == that.sourceResourceID &&
                bindingToken == that.bindingToken &&
                Objects.equals(renderParameter, that.renderParameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderParameter, sourceResourceID, bindingToken);
    }

    @Override
    public String toString() {
        return "VertexBufferKey{" +
                "renderParameter=" + renderParameter +
                ", sourceID=" + sourceResourceID +
                ", bindingToken=" + bindingToken +
                '}';
    }
}
