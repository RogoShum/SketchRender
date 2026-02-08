package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.api.BufferResourceObject;
import rogo.sketch.core.data.DataType;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.builder.VertexStreamBuilder;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.DataElement;
import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.data.format.VBOComponent;
import rogo.sketch.core.driver.GraphicsAPI;
import rogo.sketch.core.driver.GraphicsDriver;
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

    private static GraphicsAPI api() {
        return GraphicsDriver.getCurrentAPI();
    }

    public VertexResource(PrimitiveType primitiveType, boolean useIndexBuffer) {
        this(primitiveType, useIndexBuffer, false);
    }

    public VertexResource(PrimitiveType primitiveType, boolean useIndexBuffer, boolean independentIndexBuffer) {
        this.primitiveType = primitiveType;

        this.vao = api().createVertexArray();

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
     * Uses unified API that handles DSA/Legacy internally.
     */
    protected void setupVBOComponent(VBOComponent component) {
        // Always use DSA-style API, strategy will handle legacy if needed
        setupComponentUnified(component);
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
     * Setup VBO component using unified API (strategy handles DSA/Legacy internally).
     */
    protected void setupComponentUnified(VBOComponent component) {
        GraphicsAPI api = api();
        int bindingPoint = component.getBindingPoint();
        DataFormat format = component.getFormat();
        int vboHandle = component.getVboHandle();

        // For sub-allocated VBOs (external with offset), we need to pass the offset to
        // glBindVertexBuffer.
        // The vertexOffset is in 'vertices', so multiply by stride to get bytes.
        long offset = (long) component.getVertexOffset() * format.getStride();

        bind();
        api().bindVertexBuffer(vboHandle);
        // Enable and format attributes
        for (DataElement element : format.getElements()) {
            int attribLocation = element.getIndex(); // Explicit attribute location from DataFormat

            api.enableVertexAttribArray(vao, attribLocation);

            DataType dataType = element.getDataType();
            // Format: type, normalized, and RELATIVE offset within the struct

            if (dataType.isDoubleType()) {
                // Use strategy directly for LFormat (not exposed in GraphicsAPI yet)
                api.getVertexArrayStrategy().vertexAttribLFormat(vao, attribLocation,
                        element.getComponentCount(),
                        element.getGLType(),
                        element.getOffset());
            } else if (dataType.isIntegerType() && !element.isNormalized()) {
                // Use strategy directly for IFormat (not exposed in GraphicsAPI yet)
                api.getVertexArrayStrategy().vertexAttribIFormat(vao, attribLocation,
                        element.getComponentCount(),
                        element.getGLType(),
                        element.getOffset());
            } else {
                api.vertexAttribFormat(vao, attribLocation,
                        element.getComponentCount(),
                        element.getGLType(),
                        element.isNormalized(),
                        element.getOffset());
            }

            // Link attribute location to binding point
            api.vertexAttribBinding(vao, attribLocation, bindingPoint);

            if (component.isInstanced()) {
                // Divisor is set PER BINDING POINT, not per attribute in DSA (actually it's per
                // binding)
                api.getVertexArrayStrategy().vertexBindingDivisor(vao, bindingPoint, 1);
            } else {
                api.getVertexArrayStrategy().vertexBindingDivisor(vao, bindingPoint, 0);
            }
        }

        // Bind VBO to binding point with the global offset
        api.vertexArrayVertexBuffer(vao, bindingPoint, vboHandle, offset, format.getStride());
        api().bindVertexBuffer(0);
        unbind();
    }


    protected void attachIndexBuffer() {
        if (indexBuffer == null)
            return;

        api().vertexArrayElementBuffer(vao, indexBuffer.getHandle());
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
        api().bindVertexArray(vao);
    }

    @Override
    public void unbind() {
        api().bindVertexArray(0);
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

        api().deleteVertexArray(vao);
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