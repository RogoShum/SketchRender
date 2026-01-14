package rogo.sketch.render.resource.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL45;
import rogo.sketch.api.BufferResourceObject;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.builder.MemoryBufferWriter;
import rogo.sketch.render.data.builder.VertexDataBuilder;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.format.VBOComponent;
import rogo.sketch.render.vertex.DrawMode;
import rogo.sketch.util.GLFeatureChecker;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class VertexResource implements BufferResourceObject, AutoCloseable {
    private final int vao;
    private final PrimitiveType primitiveType;

    // Components: binding point -> VBO component
    private final Map<Integer, VBOComponent> components = new LinkedHashMap<>();
    private IndexBufferResource indexBuffer;
    private boolean disposed = false;

    public VertexResource(PrimitiveType primitiveType, boolean useIndexBuffer) {
        this.primitiveType = primitiveType;

        if (GLFeatureChecker.supportsDSA()) {
            this.vao = GL45.glCreateVertexArrays();
        } else {
            this.vao = GL30.glGenVertexArrays();
        }

        if (useIndexBuffer) {
            this.indexBuffer = new IndexBufferResource();
            attachIndexBuffer();
        }
    }

    // ===== VBO Attachment =====

    /**
     * Attach a VBO component at a specific binding point.
     *
     * @param bindingPoint The binding point (0, 1, 2, ...)
     * @param vbo          The vertex buffer object (owned by this resource)
     * @param format       The data format
     * @param instanced    Whether this is an instanced attribute
     */
    public void attachVBO(int bindingPoint, VertexBufferObject vbo, DataFormat format, boolean instanced) {
        VBOComponent component = new VBOComponent(vbo, format, bindingPoint, instanced);
        components.put(bindingPoint, component);
        setupVBOComponent(component);
    }

    /**
     * Attach an external VBO by reference (zero-copy).
     * The VBO is not owned by this resource and won't be disposed.
     *
     * @param bindingPoint      The binding point
     * @param externalVBOHandle The external VBO handle
     * @param format            The data format
     * @param instanced         Whether this is an instanced attribute
     * @param vertexOffset      The offset in vertices
     */
    public void attachExternalVBO(int bindingPoint, int externalVBOHandle,
            DataFormat format, boolean instanced, int vertexOffset) {
        VBOComponent component = new VBOComponent(externalVBOHandle, format, bindingPoint, instanced, vertexOffset);
        components.put(bindingPoint, component);
        setupVBOComponent(component);
    }

    /**
     * Setup VBO component attributes and binding.
     */
    private void setupVBOComponent(VBOComponent component) {
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
            components.put(shared.getBindingPoint(), shared);
            setupVBOComponent(shared);
        }
    }

    /**
     * Setup VBO component using DSA (Direct State Access).
     */
    private void setupComponentDSA(VBOComponent component) {
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
    private void setupComponentLegacy(VBOComponent component) {
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

    private void attachIndexBuffer() {
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
     * Upload data from a VertexDataBuilder to the specified binding point.
     *
     * @param bindingPoint The binding point
     * @param builder      The vertex data builder
     */
    public void upload(int bindingPoint, VertexDataBuilder builder) {
        builder.finish();

        VBOComponent component = components.get(bindingPoint);
        if (component == null) {
            throw new IllegalStateException("No VBO component attached at binding " + bindingPoint);
        }

        if (component.isExternal()) {
            throw new IllegalStateException("Cannot upload to external VBO at binding " + bindingPoint);
        }

        VertexBufferObject vbo = component.getVBO();
        if (vbo == null) {
            throw new IllegalStateException("No VBO available at binding " + bindingPoint);
        }

        if (builder.getWriter() instanceof MemoryBufferWriter memWriter) {
            ByteBuffer buffer = memWriter.getBuffer();
            buffer.flip();
            vbo.upload(buffer);
        } else {
            throw new UnsupportedOperationException("Only MemoryBufferWriter backed builders supported for now");
        }

        // Generate indices if needed (only for binding 0)
        if (bindingPoint == 0 && indexBuffer != null && builder.getPrimitiveType().requiresIndexBuffer()) {
            generateIndices(builder);
        }
    }

    private void generateIndices(VertexDataBuilder builder) {
        int vertexCount = builder.getVertexCount();
        int[] indices = builder.getPrimitiveType().generateIndices(vertexCount);

        indexBuffer.clear();
        for (int i : indices)
            indexBuffer.addIndex(i);
        // indexBuffer.upload();
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

        if (indexBuffer != null) {
            indexBuffer.dispose();
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

    public boolean hasIndices() {
        return indexBuffer != null && indexBuffer.getIndexCount() > 0;
    }

    /**
     * Get the VBO component at a binding point.
     *
     * @param bindingPoint The binding point
     * @return The VBO component, or null if not attached
     */
    public VBOComponent getComponent(int bindingPoint) {
        return components.get(bindingPoint);
    }

    /**
     * Check if a VBO component is attached at the given binding point.
     *
     * @param bindingPoint The binding point
     * @return true if a component is attached
     */
    public boolean hasComponent(int bindingPoint) {
        return components.containsKey(bindingPoint);
    }

    /**
     * Get all attached components.
     *
     * @return Map of binding point to component
     */
    public Map<Integer, VBOComponent> getAllComponents() {
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
        VBOComponent component = components.get(0);
        return component != null ? component.getFormat() : null;
    }

    /**
     * Legacy: Get static VBO handle (VBO at binding 0).
     *
     * @return The VBO handle at binding 0, or 0 if not attached
     */
    @Deprecated
    public int getStaticVBO() {
        VBOComponent component = components.get(0);
        return component != null ? component.getVboHandle() : 0;
    }

    // ===== Constants for Legacy Compatibility =====

    /**
     * @deprecated Use explicit binding points instead.
     */
    @Deprecated
    public static final int BINDING_MAIN = 0;

    /**
     * @deprecated Use explicit binding points instead.
     */
    @Deprecated
    public static final int BINDING_INSTANCE = 1;
}
