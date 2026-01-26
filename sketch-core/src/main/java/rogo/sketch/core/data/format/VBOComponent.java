package rogo.sketch.core.data.format;

import rogo.sketch.core.resource.buffer.VertexBufferObject;
import rogo.sketch.core.util.KeyId;

/**
 * A VBO component that combines a VertexBufferObject with its DataFormat and
 * binding configuration.
 * Represents a single vertex buffer binding point in a VertexResource (VAO).
 *
 * <p>
 * This unified design replaces the separate maps for buffers and formats,
 * making the code more cohesive and easier to manage.
 * </p>
 *
 * <p>
 * VBOs can be:
 * </p>
 * <ul>
 * <li>Mutable: Needs data filling via VertexDataBuilder (e.g., dynamic
 * mesh)</li>
 * <li>Immutable: Pre-baked data that doesn't need filling (e.g., BakedMesh
 * VBO)</li>
 * <li>External: Referenced from another source (zero-copy), not owned by this
 * resource</li>
 * </ul>
 */
public class VBOComponent {
    private final VertexBufferObject vbo;
    private final ComponentSpec spec;
    private final int vertexOffset;
    private final int externalVboHandle; // For external VBOs
    private final boolean external;

    /**
     * Create a VBO component from a specification and VBO.
     */
    public VBOComponent(VertexBufferObject vbo, ComponentSpec spec) {
        this(vbo, spec, false, 0, 0);
    }

    /**
     * Create a mutable internal VBO component (owned by VertexResource, needs
     * filling).
     */
    public VBOComponent(VertexBufferObject vbo, KeyId id, DataFormat format, int bindingPoint, boolean instanced) {
        this(vbo, ComponentSpec.mutable(id, bindingPoint, format, instanced), false, 0, 0);
    }

    /**
     * Create an immutable internal VBO component (owned, pre-filled, doesn't need
     * filling).
     * Used for BakedMesh zero-copy integration.
     */
    public static VBOComponent immutable(VertexBufferObject vbo, KeyId id, DataFormat format,
            int bindingPoint, boolean instanced) {
        return new VBOComponent(vbo, ComponentSpec.immutable(id, bindingPoint, format, instanced), false, 0, 0);
    }

    /**
     * Create an external VBO component (not owned by VertexResource).
     * Used for zero-copy BakedMesh integration where the VBO is referenced from
     * elsewhere.
     */
    public VBOComponent(int externalVboHandle, KeyId id, DataFormat format, int bindingPoint,
            boolean instanced, int vertexOffset) {
        this(null, ComponentSpec.immutable(id, bindingPoint, format, instanced), true, vertexOffset, externalVboHandle);
    }

    /**
     * Create an external VBO component from a spec (shared from another resource).
     */
    public static VBOComponent external(VBOComponent other) {
        // Create a copy that is marked external
        return new VBOComponent(null, other.getSpec(), true, other.getVertexOffset(), other.getVboHandle());
    }

    private VBOComponent(VertexBufferObject vbo, ComponentSpec spec, boolean external, int vertexOffset,
            int externalVboHandle) {
        this.vbo = vbo;
        this.spec = spec;
        this.external = external;
        this.vertexOffset = vertexOffset;
        this.externalVboHandle = externalVboHandle;
    }

    public VertexBufferObject getVBO() {
        return vbo;
    }

    public ComponentSpec getSpec() {
        return spec;
    }

    public DataFormat getFormat() {
        return spec.getFormat();
    }

    public int getBindingPoint() {
        return spec.getBindingPoint();
    }

    public boolean isInstanced() {
        return spec.isInstanced();
    }

    public boolean isExternal() {
        return external;
    }

    /**
     * Check if this VBO is immutable (pre-filled, doesn't need data filling).
     * Immutable VBOs are typically from BakedMesh and don't require
     * VertexDataBuilder.
     */
    public boolean isImmutable() {
        return spec.isImmutable();
    }

    /**
     * Check if this VBO is mutable (needs data filling via VertexDataBuilder).
     */
    public boolean isMutable() {
        return spec.isMutable();
    }

    public int getVertexOffset() {
        return vertexOffset;
    }

    /**
     * Get the VBO handle.
     * For external VBOs, returns the external handle.
     * For internal VBOs, returns the VBO's handle (or 0 if VBO is null).
     */
    public int getVboHandle() {
        if (external) {
            return externalVboHandle;
        }
        return vbo != null ? vbo.getHandle() : 0;
    }

    /**
     * Check if this component owns its VBO (will be disposed).
     */
    public boolean isOwned() {
        return !external && vbo != null;
    }

    /**
     * Dispose the VBO if it's owned by this component.
     * External VBOs are not disposed.
     */
    public void dispose() {
        if (isOwned() && vbo != null) {
            vbo.dispose();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        VBOComponent that = (VBOComponent) o;
        return vertexOffset == that.vertexOffset &&
                externalVboHandle == that.externalVboHandle &&
                external == that.external &&
                // Check VBO handle for internal VBOs if both are internal
                ((vbo == null && that.vbo == null)
                        || (vbo != null && that.vbo != null && vbo.getHandle() == that.vbo.getHandle()))
                &&
                spec.equals(that.spec);
    }

    @Override
    public int hashCode() {
        int vboHandle = (vbo != null) ? vbo.getHandle() : 0;
        // Use Objects.hash for convenience
        return java.util.Objects.hash(vboHandle, spec, vertexOffset, externalVboHandle, external);
    }

    @Override
    public String toString() {
        return "VBOComponent{" +
                "spec=" + spec +
                ", offset=" + vertexOffset +
                ", external=" + external +
                ", handle=" + getVboHandle() +
                '}';
    }
}