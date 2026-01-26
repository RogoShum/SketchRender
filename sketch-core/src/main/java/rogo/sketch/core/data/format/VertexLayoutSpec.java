package rogo.sketch.core.data.format;

import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Defines the complete layout specification for a Vertex Resource.
 * It is a collection of ComponentSpecs, defining what attributes exist at what
 * binding points.
 */
public class VertexLayoutSpec {
    private final List<ComponentSpec> components;
    private final List<ComponentSpec> staticComponents;
    private final List<ComponentSpec> dynamicComponents;

    private final int hash;

    private VertexLayoutSpec(List<ComponentSpec> components) {
        this.components = Collections.unmodifiableList(new ArrayList<>(components));
        this.staticComponents = components.stream().filter(ComponentSpec::isImmutable).collect(Collectors.toList());
        this.dynamicComponents = components.stream().filter(ComponentSpec::isMutable).collect(Collectors.toList());
        this.hash = Objects.hash(components);
    }

    public List<ComponentSpec> getComponents() {
        return components;
    }

    /**
     * Get specifications for static components (usually provided by Mesh).
     */
    public List<ComponentSpec> getStaticSpecs() {
        return staticComponents;
    }

    /**
     * Get specifications for dynamic components (fillable at runtime).
     */
    public List<ComponentSpec> getDynamicSpecs() {
        return dynamicComponents;
    }

    /**
     * Validate if a Mesh provides all the required static attributes defined in
     * this layout.
     *
     * @param mesh The mesh to validate
     * @return true if compatible
     */
    public boolean validateMesh(PreparedMesh mesh) {
        DataFormat meshFormat = mesh.getVertexFormat();
        for (ComponentSpec spec : getStaticSpecs()) {
            // Each static component's format must be present in the mesh
            // Note: In the new design, Mesh might provide ONE big format (flat attributes)
            // or specific bindings.
            // Assumption: Mesh provides attributes matching the Spec's format.
            if (!meshFormat.contains(spec.getFormat())) {
                return false;
            }
        }
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<ComponentSpec> specs = new ArrayList<>();
        private final List<KeyId> usedKeys = new ArrayList<>();
        private int nextAttributeIndex = 0;
        private int nextBindingIndex = 0;

        /**
         * Add a component spec.
         * Automatically assigns binding point and adjusts attribute indices.
         * Throws IllegalArgumentException if KeyId is already used.
         */
        public Builder add(KeyId id, DataFormat format, boolean instanced, boolean mutable) {
            // 1. Validation: Check Key Collision
            if (usedKeys.contains(id)) {
                throw new IllegalArgumentException(
                        "KeyId " + id + " is already used in this layout.");
            }
            usedKeys.add(id);

            // 2. Auto-Indexing: Calculate shift
            // Find the lowest index in the new format
            int minIndex = Integer.MAX_VALUE;
            int maxIndex = Integer.MIN_VALUE;

            boolean hasElements = format.getElementCount() > 0;

            if (hasElements) {
                for (DataElement element : format.getElements()) {
                    minIndex = Math.min(minIndex, element.getIndex());
                    maxIndex = Math.max(maxIndex, element.getIndex());
                }

                // Calculate shift needed to place minIndex at nextAttributeIndex
                int shift = nextAttributeIndex - minIndex;

                if (shift != 0) {
                    format = format.withAttributeIndexOffset(shift);
                    // Update maxIndex for tracking
                    maxIndex += shift;
                }

                nextAttributeIndex = maxIndex + 1;
            }

            int bindingPoint = nextBindingIndex++;
            ComponentSpec spec = new ComponentSpec(id, bindingPoint, format, instanced, mutable);
            specs.add(spec);

            return this;
        }

        public Builder addStatic(KeyId id, DataFormat format) {
            return add(id, format, false, false);
        }

        public Builder addDynamic(KeyId id, DataFormat format) {
            return add(id, format, false, true);
        }

        public Builder addStaticInstanced(KeyId id, DataFormat format) {
            return add(id, format, true, false);
        }

        public Builder addDynamicInstanced(KeyId id, DataFormat format) {
            return add(id, format, true, true);
        }

        public VertexLayoutSpec build() {
            return new VertexLayoutSpec(specs);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        VertexLayoutSpec that = (VertexLayoutSpec) o;
        return components.equals(that.components);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "VertexLayoutSpec{" +
                "components=" + components +
                '}';
    }
}