package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL45;
import rogo.sketch.core.api.BufferResourceObject;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.DataElement;
import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.data.format.VBOComponent;
import rogo.sketch.core.util.GLFeatureChecker;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VertexResource implements BufferResourceObject, AutoCloseable {
    protected static final Map<PrimitiveType, IndexBufferResource> SHARED_INDEX_BUFFER_MAP = new ConcurrentHashMap<>();
    protected final int vao;
    protected final PrimitiveType primitiveType;

    // Components: KeyId -> VBO component
    protected final Map<KeyId, VBOComponent> components = new LinkedHashMap<>();
    protected IndexBufferResource indexBuffer;
    protected boolean disposed = false;

    public VertexResource(PrimitiveType primitiveType, boolean useIndexBuffer) {
        this(primitiveType, useIndexBuffer, false);
    }

    public VertexResource(PrimitiveType primitiveType, boolean useIndexBuffer, boolean independentIndexBuffer) {
        this.primitiveType = primitiveType;

        if (GLFeatureChecker.supportsDSA()) {
            this.vao = GL45.glCreateVertexArrays();
        } else {
            this.vao = GL30.glGenVertexArrays();
        }

        if (useIndexBuffer) {
            if (independentIndexBuffer) {
                this.indexBuffer = new IndexBufferResource(false);
            } else {
                this.indexBuffer = SHARED_INDEX_BUFFER_MAP.computeIfAbsent(primitiveType, k -> new IndexBufferResource(true));
            }

            attachIndexBuffer();
        }
    }

    // ===== VBO Attachment =====

    /**
     * Attach a VBO component.
     *
     * @param spec The component spec (containing KeyId and binding)
     * @param vbo  The vertex buffer object (owned by this resource)
     */
    public void attachVBO(ComponentSpec spec, VertexBufferObject vbo) {
        VBOComponent component = new VBOComponent(vbo, spec);
        components.put(spec.getId(), component);
        setupVBOComponent(component);
    }

    /**
     * Attach an external VBO by reference (zero-copy).
     * The VBO is not owned by this resource and won't be disposed.
     *
     * @param spec              The component spec
     * @param externalVBOHandle The external VBO handle
     * @param vertexOffset      The offset in vertices
     */
    public void attachExternalVBO(ComponentSpec spec, int externalVBOHandle, int vertexOffset) {
        VBOComponent component = new VBOComponent(externalVBOHandle, spec.getId(), spec.getFormat(),
                spec.getBindingPoint(), spec.isInstanced(), vertexOffset);
        components.put(spec.getId(), component);
        setupVBOComponent(component);
    }

    /**
     * Setup VBO component attributes and binding.
     */
    protected void setupVBOComponent(VBOComponent component) {
        if (GLFeatureChecker.supportsDSA()) {
            setupComponentDSA(component);
        } else {
            setupComponentLegacy(component);
        }
    }

    /**
     * Share components from another VertexResource.
     * This creates external VBOComponents referencing the other resource's VBOs.
     * Useful for extending a BakedMesh's static VBOs with new dynamic ones.
     */
    public void shareComponentsFrom(VertexResource other) {
        for (VBOComponent otherComp : other.getAllComponents().values()) {
            VBOComponent shared = VBOComponent.external(otherComp);
            components.put(shared.getSpec().getId(), shared);
            setupVBOComponent(shared);
        }
    }

    /**
     * Setup VBO component using DSA (Direct State Access).
     */
    protected void setupComponentDSA(VBOComponent component) {
        int bindingPoint = component.getBindingPoint();
        DataFormat format = component.getFormat();
        int vboHandle = component.getVboHandle();

        // For sub-allocated VBOs (external with offset), we need to pass the offset to
        // glBindVertexBuffer.
        // The vertexOffset is in 'vertices', so multiply by stride to get bytes.
        long offset = (long) component.getVertexOffset() * format.getStride();

        // Enable and format attributes
        for (DataElement element : format.getElements()) {
            int attribLocation = element.getIndex(); // Explicit attribute location from DataFormat

            GL45.glEnableVertexArrayAttrib(vao, attribLocation);

            // Format: type, normalized, and RELATIVE offset within the struct
            GL45.glVertexArrayAttribFormat(vao, attribLocation,
                    element.getComponentCount(),
                    element.getGLType(),
                    element.isNormalized(),
                    element.getOffset());

            // Link attribute location to binding point
            GL45.glVertexArrayAttribBinding(vao, attribLocation, bindingPoint);

            if (component.isInstanced()) {
                // Divisor is set PER BINDING POINT, not per attribute in DSA (actually it's per
                // binding)
                GL45.glVertexArrayBindingDivisor(vao, bindingPoint, 1);
            } else {
                GL45.glVertexArrayBindingDivisor(vao, bindingPoint, 0);
            }
        }

        // Bind VBO to binding point with the global offset
        GL45.glVertexArrayVertexBuffer(vao, bindingPoint, vboHandle, offset, format.getStride());
    }

    /**
     * Setup VBO component using legacy OpenGL.
     */
    protected void setupComponentLegacy(VBOComponent component) {
        bind();

        DataFormat format = component.getFormat();

        // Bind VBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, component.getVboHandle());

        // Setup attributes
        for (DataElement element : format.getElements()) {
            int attribIndex = element.getIndex();
            GL30.glEnableVertexAttribArray(attribIndex);
            GL30.glVertexAttribPointer(
                    attribIndex,
                    element.getComponentCount(),
                    element.getGLType(),
                    element.isNormalized(),
                    format.getStride(),
                    element.getOffset());

            if (component.isInstanced()) {
                GL33.glVertexAttribDivisor(attribIndex, 1);
            }
        }

        // Unbind VBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        unbind();
    }

    protected void attachIndexBuffer() {
        if (indexBuffer == null)
            return;

        if (GLFeatureChecker.supportsDSA()) {
            GL45.glVertexArrayElementBuffer(vao, indexBuffer.getHandle());
        } else {
            bind();
            indexBuffer.bind();
            unbind();
            indexBuffer.unbind();
        }
    }

    // ===== Data Upload =====

    /**
     * Upload data from a VertexDataBuilder to the specified VBO.
     *
     * @param id      The KeyId of the component
     * @param builder The vertex data builder
     */
    public void upload(KeyId id, VertexStreamBuilder builder) {
        VBOComponent component = components.get(id);
        if (component == null) {
            throw new IllegalStateException("No VBO component attached for id " + id);
        }

        if (component.isExternal()) {
            throw new IllegalStateException("Cannot upload to external VBO at id " + id);
        }

        VertexBufferObject vbo = component.getVBO();
        if (vbo == null) {
            throw new IllegalStateException("No VBO available for id " + id);
        }

        vbo.upload(builder.getBaseAddress(), builder.getWriteOffset(), builder.getCapacity());
        if (!component.isInstanced() && indexBuffer != null && getPrimitiveType().requiresIndexBuffer()) {
            generateIndices(builder);
        }

        builder.reset();
    }

    protected void generateIndices(VertexStreamBuilder builder) {
        int vertexCount = builder.getVertexCount();
        int indexCount = getPrimitiveType().calculateIndexCount(vertexCount);
        if (indexCount > indexBuffer.getIndexCount()) {
            int[] indices = getPrimitiveType().generateIndices(vertexCount);
            indexBuffer.setIndices(indices);
        }
    }

    // ===== Bind/Unbind =====

    @Override
    public void bind() {
        GL30.glBindVertexArray(vao);
    }

    @Override
    public void unbind() {
        GL30.glBindVertexArray(0);
    }

    @Override
    public int getHandle() {
        return vao;
    }

    @Override
    public void dispose() {
        if (disposed)
            return;

        // Dispose owned VBOs
        for (VBOComponent component : components.values()) {
            component.dispose();
        }
        components.clear();

        if (indexBuffer != null && !this.indexBuffer.isShared()) {
            this.indexBuffer.dispose();
        }

        GL30.glDeleteVertexArrays(vao);
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void close() {
        dispose();
    }

    // ===== Getters =====

    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    public IndexBufferResource getIndexBuffer() {
        return indexBuffer;
    }

    public void setIndexBuffer(IndexBufferResource indexBuffer) {
        if (this.indexBuffer != null && !this.indexBuffer.isShared()) {
            this.indexBuffer.dispose();
        }

        this.indexBuffer = indexBuffer;
        attachIndexBuffer();
    }

    public boolean hasIndices() {
        return indexBuffer != null && indexBuffer.getIndexCount() > 0;
    }

    /**
     * Get the VBO component for a key.
     *
     * @param id The KeyId
     * @return The VBO component, or null if not attached
     */
    public VBOComponent getComponent(KeyId id) {
        return components.get(id);
    }

    /**
     * Check if a VBO component is attached for the given key.
     *
     * @param id The KeyId
     * @return true if a component is attached
     */
    public boolean hasComponent(KeyId id) {
        return components.containsKey(id);
    }

    /**
     * Get all attached components.
     *
     * @return Map of KeyId to component
     */
    public Map<KeyId, VBOComponent> getAllComponents() {
        return new LinkedHashMap<>(components);
    }

    // ===== Legacy Compatibility =====

    /**
     * Legacy: Get static format (format at binding 0).
     *
     * @return The format at binding 0, or null if not attached
     */
    @Deprecated
    public DataFormat getStaticFormat() {
        for (VBOComponent comp : components.values()) {
            if (comp.getBindingPoint() == 0) {
                return comp.getFormat();
            }
        }
        return null;
    }

    /**
     * Legacy: Get static VBO handle (VBO at binding 0).
     *
     * @return The VBO handle at binding 0, or 0 if not attached
     */
    @Deprecated
    public int getStaticVBO() {
        for (VBOComponent comp : components.values()) {
            if (comp.getBindingPoint() == 0) {
                return comp.getVboHandle();
            }
        }
        return 0;
    }
}