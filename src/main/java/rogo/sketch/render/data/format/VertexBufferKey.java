package rogo.sketch.render.data.format;

import rogo.sketch.render.pipeline.RasterizationParameter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VertexBufferKey {
    private final RasterizationParameter renderParameter;
    private final List<ComponentSpec> components;
    private final long sourceResourceID;

    private VertexBufferKey(RasterizationParameter staticParam, List<ComponentSpec> components, long sourceResourceID) {
        this.renderParameter = staticParam;
        this.components = components;
        this.sourceResourceID = sourceResourceID;
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

    public List<ComponentSpec> components() {
        return components;
    }

    public long sourceResourceID() {
        return sourceResourceID;
    }

    /**
     * Check if this key has instance components (any instanced component).
     */
    public boolean hasInstancing() {
        return components.stream().anyMatch(ComponentSpec::isInstanced);
    }

    /**
     * Get only the mutable components (those that need data filling).
     */
    public List<ComponentSpec> mutableComponents() {
        return components.stream()
                .filter(ComponentSpec::isMutable)
                .collect(Collectors.toList());
    }

    /**
     * Get only the immutable components (those that are pre-baked).
     */
    public List<ComponentSpec> immutableComponents() {
        return components.stream()
                .filter(ComponentSpec::isImmutable)
                .collect(Collectors.toList());
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
                Objects.equals(components, that.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(renderParameter, components, sourceResourceID);
    }

    @Override
    public String toString() {
        return "VertexBufferKey{" +
                "renderParameter=" + renderParameter +
                ", sourceID=" + sourceResourceID +
                ", components=" + components +
                '}';
    }
}
