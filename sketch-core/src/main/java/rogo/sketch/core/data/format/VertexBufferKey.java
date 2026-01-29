package rogo.sketch.core.data.format;

import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;

import java.util.Arrays;
import java.util.Objects;

public class VertexBufferKey {
    private final RasterizationParameter renderParameter;
    private final ComponentSpec[] components;
    private final long sourceResourceID;
    private final boolean hasInstancing;

    private VertexBufferKey(RasterizationParameter staticParam, ComponentSpec[] components, long sourceResourceID) {
        this.renderParameter = staticParam;
        this.components = components;
        this.sourceResourceID = sourceResourceID;
        this.hasInstancing = Arrays.stream(components).anyMatch(ComponentSpec::isInstanced);
    }

    /**
     * Create a VertexBufferKey from a RasterizationParameter.
     * The parameter's VertexLayoutSpec defines the components.
     */
    public static VertexBufferKey fromParameter(RasterizationParameter param) {
        return fromParameter(param, -1);
    }

    /**
     * Create a VertexBufferKey with a specific source resource ID.
     * Used for BakedMeshes where the static VBOs come from a specific source.
     */
    public static VertexBufferKey fromParameter(RasterizationParameter param, long sourceResourceID) {
        VertexLayoutSpec layout = param.getLayout();
        return new VertexBufferKey(param, layout.getComponents(), sourceResourceID);
    }

    // ===== Getters =====

    public RasterizationParameter renderParameter() {
        return renderParameter;
    }

    public ComponentSpec[] components() {
        return components;
    }

    public long sourceResourceID() {
        return sourceResourceID;
    }

    /**
     * Check if this key has instance components (any instanced component).
     */
    public boolean hasInstancing() {
        return hasInstancing;
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
                Objects.equals(renderParameter, that.renderParameter) &&
                hasInstancing == that.hasInstancing &&
                Arrays.equals(components, that.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderParameter, hasInstancing, Arrays.hashCode(components), sourceResourceID);
    }

    @Override
    public String toString() {
        return "VertexBufferKey{" +
                "renderParameter=" + renderParameter +
                ", sourceID=" + sourceResourceID +
                ", components=" + Arrays.toString(components) +
                ", hasInstancing=" + hasInstancing +
                '}';
    }
}
