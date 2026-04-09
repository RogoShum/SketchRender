package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL15;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.backend.BackendGeometryMetadata;
import rogo.sketch.core.backend.BackendMutableGeometryBinding;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.IndexType;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.TopologyIndexGenerator;
import rogo.sketch.core.data.builder.VertexRecordWriter;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.data.layout.FieldSpec;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.backend.opengl.internal.OpenGLRuntimeSupport;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OpenGLGeometryBinding implements AutoCloseable, BackendGeometryBinding, BackendGeometryMetadata, BackendMutableGeometryBinding {
    protected static final Map<PrimitiveType, OpenGLIndexBuffer> SHARED_INDEX_BUFFER_MAP = new ConcurrentHashMap<>();
    protected final PrimitiveType primitiveType;
    protected final MeshIndexMode indexMode;

    // Components: KeyId -> VBO component
    protected final Map<KeyId, OpenGLVertexComponent> components = new LinkedHashMap<>();
    private final Map<rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout, OpenGLVertexInputLayout> vertexInputLayouts = new ConcurrentHashMap<>();
    protected OpenGLIndexBuffer indexBuffer;
    protected boolean disposed = false;

    public OpenGLGeometryBinding(PrimitiveType primitiveType, MeshIndexMode indexMode) {
        this(primitiveType, indexMode, false);
    }

    public OpenGLGeometryBinding(PrimitiveType primitiveType, MeshIndexMode indexMode, boolean independentIndexBuffer) {
        OpenGLRuntimeSupport.assertMainThread("OpenGLGeometryBinding.<init>");
        this.primitiveType = primitiveType;
        this.indexMode = indexMode != null ? indexMode : MeshIndexMode.NONE;

        if (this.indexMode.usesIndexBuffer()) {
            if (independentIndexBuffer) {
                this.indexBuffer = new OpenGLIndexBuffer(false);
            } else {
                this.indexBuffer = SHARED_INDEX_BUFFER_MAP.computeIfAbsent(primitiveType, k -> new OpenGLIndexBuffer(true));
            }
        }
    }

    public static OpenGLGeometryBinding materialize(VertexBufferKey key, OpenGLGeometryBinding sourceProvider) {
        if (key == null) {
            return null;
        }
        RasterizationParameter parameter = key.renderParameter();
        PrimitiveType primitiveType = parameter.primitiveType();
        MeshIndexMode indexMode = parameter.indexMode() != null ? parameter.indexMode() : MeshIndexMode.NONE;
        OpenGLGeometryBinding geometryBinding = new OpenGLGeometryBinding(primitiveType, indexMode);

        if (key.sourceResourceID() > 0L && sourceProvider != null) {
            geometryBinding.shareComponentsFrom(sourceProvider);
            geometryBinding.setIndexBuffer(sourceProvider.getIndexBuffer());
        }

        for (ComponentSpec spec : key.components()) {
            if (geometryBinding.hasComponent(spec.getId())) {
                continue;
            }
            if (spec.isImmutable()) {
                geometryBinding.attachVBO(spec, new OpenGLVertexBufferObject(parameter.updatePolicy()));
            } else {
                BufferUpdatePolicy updatePolicy = spec.isInstanced()
                        ? BufferUpdatePolicy.DYNAMIC
                        : parameter.updatePolicy();
                geometryBinding.attachVBO(spec, new OpenGLVertexBufferObject(updatePolicy));
            }
        }
        return geometryBinding;
    }

    // ===== VBO Attachment =====

    /**
     * Attach a VBO component.
     *
     * @param spec The component spec (containing KeyId and binding)
     * @param vbo  The vertex buffer object (owned by this resource)
     */
    public void attachVBO(ComponentSpec spec, OpenGLVertexBufferObject vbo) {
        OpenGLRuntimeSupport.assertMainThread("OpenGLGeometryBinding.attachVBO");
        OpenGLVertexComponent component = new OpenGLVertexComponent(vbo, spec);
        components.put(spec.getId(), component);
        invalidateVertexInputLayouts();
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
        OpenGLRuntimeSupport.assertMainThread("OpenGLGeometryBinding.attachExternalVBO");
        OpenGLVertexComponent component = new OpenGLVertexComponent(externalVBOHandle, spec.getId(), spec.getFormat(),
                spec.getBindingPoint(), spec.isInstanced(), vertexOffset);
        components.put(spec.getId(), component);
        invalidateVertexInputLayouts();
    }

    /**
     * Share components from another OpenGLGeometryBinding.
     * This creates external VBOComponents referencing the other resource's VBOs.
     * Useful for extending a BakedMesh's static VBOs with new dynamic ones.
     */
    public void shareComponentsFrom(OpenGLGeometryBinding other) {
        OpenGLRuntimeSupport.assertMainThread("OpenGLGeometryBinding.shareComponentsFrom");
        for (OpenGLVertexComponent otherComp : other.getAllComponents().values()) {
            OpenGLVertexComponent shared = OpenGLVertexComponent.external(otherComp);
            components.put(shared.getSpec().getId(), shared);
        }
        invalidateVertexInputLayouts();
    }

    // ===== Data Upload =====

    /**
     * Upload data from a VertexDataBuilder to the specified VBO.
     *
     * @param id      The KeyId of the component
     * @param builder The vertex data builder
     */
    public void upload(KeyId id, VertexRecordWriter writer) {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLGeometryBinding.upload");
        OpenGLVertexComponent component = components.get(id);
        if (component == null) {
            throw new IllegalStateException("No VBO component attached for id " + id);
        }

        if (component.isExternal()) {
            throw new IllegalStateException("Cannot upload to external VBO at id " + id);
        }

        OpenGLVertexBufferObject vbo = component.getVBO();
        if (vbo == null) {
            throw new IllegalStateException("No VBO available for id " + id);
        }

        vbo.upload(writer.getBaseAddress(), writer.getWriteOffset(), writer.getCapacity());
        if (!component.isInstanced() && indexBuffer != null && getIndexMode().isGenerated()) {
            generateIndices(writer);
        }

        writer.reset();
    }

    protected void generateIndices(VertexRecordWriter writer) {
        int vertexCount = writer.getVertexCount();
        int indexCount = TopologyIndexGenerator.calculateIndexCount(getPrimitiveType(), vertexCount);
        if (indexCount > indexBuffer.getIndexCount()) {
            int[] indices = TopologyIndexGenerator.generateIndices(getPrimitiveType(), vertexCount);
            indexBuffer.setIndices(indices);
        }
    }

    public OpenGLVertexInputLayout resolveVertexInputLayout(
            rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout shaderLayout) {
        OpenGLRuntimeSupport.assertMainThread("OpenGLGeometryBinding.resolveVertexInputLayout");
        rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout resolvedLayout =
                shaderLayout != null ? shaderLayout : rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout.empty();
        return vertexInputLayouts.computeIfAbsent(
                resolvedLayout,
                ignored -> OpenGLVertexInputAssembler.assemble(this, resolvedLayout));
    }

    @Override
    public void dispose() {
        OpenGLRuntimeSupport.assertMainThread("OpenGLGeometryBinding.dispose");
        if (disposed)
            return;

        // Dispose owned VBOs
        for (OpenGLVertexComponent component : components.values()) {
            component.dispose();
        }
        components.clear();

        if (indexBuffer != null && !this.indexBuffer.isShared()) {
            this.indexBuffer.dispose();
        }

        invalidateVertexInputLayouts();
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

    @Override
    public PrimitiveType primitiveType() {
        return primitiveType;
    }

    public MeshIndexMode getIndexMode() {
        return indexMode;
    }

    public OpenGLIndexBuffer getIndexBuffer() {
        return indexBuffer;
    }

    public void setIndexBuffer(OpenGLIndexBuffer indexBuffer) {
        OpenGLRuntimeSupport.assertMainThread("OpenGLGeometryBinding.setIndexBuffer");
        if (this.indexBuffer != null && !this.indexBuffer.isShared()) {
            this.indexBuffer.dispose();
        }

        this.indexBuffer = indexBuffer;
        invalidateVertexInputLayouts();
    }

    public boolean hasIndices() {
        return indexBuffer != null && indexBuffer.getIndexCount() > 0;
    }

    @Override
    public IndexType indexType() {
        return indexBuffer != null ? indexBuffer.currentIndexType() : IndexType.U_INT;
    }

    /**
     * Get the VBO component for a key.
     *
     * @param id The KeyId
     * @return The VBO component, or null if not attached
     */
    public OpenGLVertexComponent getComponent(KeyId id) {
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
    public Map<KeyId, OpenGLVertexComponent> getAllComponents() {
        return new LinkedHashMap<>(components);
    }

    // ===== Legacy Compatibility =====

    /**
     * Legacy: Get static format (format at binding 0).
     *
     * @return The format at binding 0, or null if not attached
     */
    @Deprecated
    public StructLayout getStaticFormat() {
        for (OpenGLVertexComponent comp : components.values()) {
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
        for (OpenGLVertexComponent comp : components.values()) {
            if (comp.getBindingPoint() == 0) {
                return comp.getVboHandle();
            }
        }
        return 0;
    }

    @Override
    public StructLayout vertexFormat() {
        return getStaticFormat();
    }

    @Override
    public void uploadVertexComponent(KeyId componentId, byte[] data) {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLGeometryBinding.uploadVertexComponent");
        if (data == null || data.length == 0) {
            return;
        }
        OpenGLVertexComponent component = components.get(componentId);
        if (component == null || component.getVBO() == null || component.isExternal()) {
            return;
        }
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(data.length);
        buffer.put(data);
        buffer.flip();
        component.getVBO().upload(buffer);
    }

    @Override
    public void uploadIndices(int[] indices) {
        OpenGLRuntimeSupport.assertRenderContext("OpenGLGeometryBinding.uploadIndices");
        if (indexBuffer == null || indices == null || indices.length == 0) {
            return;
        }
        indexBuffer.setIndices(indices);
    }

    private void invalidateVertexInputLayouts() {
        for (OpenGLVertexInputLayout layout : vertexInputLayouts.values()) {
            if (layout != null) {
                layout.close();
            }
        }
        vertexInputLayouts.clear();
    }
}

