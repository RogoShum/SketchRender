package rogo.sketch.render.data.format;

import rogo.sketch.api.model.PreparedMesh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.format.DataElement;
import java.util.stream.Collectors;

/**
 * Defines the complete layout specification for a Vertex Resource.
 * It is a collection of ComponentSpecs, defining what attributes exist at what
 * binding points.
 */
public class VertexLayoutSpec {
    private final List<ComponentSpec> components;

    public VertexLayoutSpec(List<ComponentSpec> components) {
        this.components = Collections.unmodifiableList(new ArrayList<>(components));
    }

    public List<ComponentSpec> getComponents() {
        return components;
    }

    /**
     * Get specifications for static components (usually provided by Mesh).
     */
    public List<ComponentSpec> getStaticSpecs() {
        return components.stream()
                .filter(ComponentSpec::isImmutable)
                .collect(Collectors.toList());
    }

    /**
     * Get specifications for dynamic components (fillable at runtime).
     */
    public List<ComponentSpec> getDynamicSpecs() {
        return components.stream()
                .filter(ComponentSpec::isMutable)
                .collect(Collectors.toList());
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
        private final List<Integer> usedBindings = new ArrayList<>();
        private int nextAttributeIndex = 0;

        /**
         * Add a component spec.
         * Automatically adjusts attribute indices to avoid collisions with previous
         * components.
         * Throws IllegalArgumentException if binding point is already used.
         */
        public Builder add(ComponentSpec spec) {
            // 1. Validation: Check Binding Collision
            if (usedBindings.contains(spec.getBindingPoint())) {
                throw new IllegalArgumentException(
                        "Binding point " + spec.getBindingPoint() + " is already used in this layout.");
            }
            usedBindings.add(spec.getBindingPoint());

            DataFormat format = spec.getFormat();
            if (format.getElementCount() == 0) {
                specs.add(spec);
                return this;
            }

            // 2. Auto-Indexing: Calculate shift
            // Find the lowest index in the new format
            int minIndex = Integer.MAX_VALUE;
            int maxIndex = Integer.MIN_VALUE;

            for (DataElement element : format.getElements()) {
                minIndex = Math.min(minIndex, element.getIndex());
                maxIndex = Math.max(maxIndex, element.getIndex());
            }

            // Calculate shift needed to place minIndex at nextAttributeIndex
            int shift = nextAttributeIndex - minIndex;

            ComponentSpec finalSpec = spec;
            if (shift != 0) {
                DataFormat newFormat = format.withAttributeIndexOffset(shift);
                finalSpec = new ComponentSpec(
                        spec.getBindingPoint(),
                        newFormat,
                        spec.isInstanced(),
                        spec.isMutable());

                // Update maxIndex for tracking
                maxIndex += shift;
            }

            specs.add(finalSpec);
            nextAttributeIndex = maxIndex + 1;

            return this;
        }

        public Builder addStatic(int binding, DataFormat format) {
            return add(ComponentSpec.immutable(binding, format, false));
        }

        public Builder addDynamic(int binding, DataFormat format) {
            return add(ComponentSpec.mutable(binding, format, false));
        }

        public Builder addStaticInstanced(int binding, DataFormat format) {
            return add(ComponentSpec.immutable(binding, format, true));
        }

        public Builder addDynamicInstanced(int binding, DataFormat format) {
            return add(ComponentSpec.mutable(binding, format, true));
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
        return components.hashCode();
    }

    @Override
    public String toString() {
        return "VertexLayoutSpec{" +
                "components=" + components +
                '}';
    }
}
