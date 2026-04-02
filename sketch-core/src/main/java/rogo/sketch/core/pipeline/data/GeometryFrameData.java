package rogo.sketch.core.pipeline.data;

import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.util.KeyId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GeometryFrameData implements RenderPipelineData {
    public static final KeyId KEY = KeyId.of("geometry_frame_data");

    private final Map<GeometryHandleKey, GeometryBinding> bindings = new ConcurrentHashMap<>();

    public void register(GeometryHandleKey key, VertexResource vertexResource, IndirectCommandBuffer indirectBuffer) {
        registerNeutral(
                key,
                vertexResource != null ? new BufferSlice(vertexResource.getHandle(), 0L, 0L, 0) : null,
                vertexResource != null && vertexResource.hasIndices()
                        ? new BufferSlice(vertexResource.getIndexBuffer().getHandle(), 0L, 0L, 0)
                        : null,
                indirectBuffer != null
                        ? new IndirectSlice(indirectBuffer.getHandle(), 0L, indirectBuffer.getCommandCount(), (int) indirectBuffer.getStride())
                        : null,
                SourceKind.OPENGL_VERTEX_RESOURCE,
                vertexResource,
                indirectBuffer);
    }

    public void registerNeutral(
            GeometryHandleKey key,
            BufferSlice vertexSlice,
            BufferSlice indexSlice,
            IndirectSlice indirectSlice,
            SourceKind sourceKind,
            VertexResource vertexResource,
            IndirectCommandBuffer indirectBuffer) {
        if (key == null) {
            return;
        }
        GeometryBinding nextBinding = new GeometryBinding(
                vertexSlice,
                indexSlice,
                indirectSlice,
                sourceKind != null ? sourceKind : SourceKind.BACKEND_NATIVE,
                vertexResource,
                indirectBuffer);
        GeometryBinding previous = bindings.put(key, nextBinding);
        if (previous != null
                && previous.vertexResource() != null
                && vertexResource != null
                && previous.vertexResource() != vertexResource
                && previous.vertexResource().getHandle() != vertexResource.getHandle()) {
            SketchDiagnostics.get().warn(
                    "geometry-frame-data",
                    "geometry binding overwritten for handle=" + key
                            + " previousVao=" + previous.vertexResource().getHandle()
                            + " nextVao=" + vertexResource.getHandle());
        }
    }

    public GeometryBinding resolve(GeometryHandleKey key) {
        return key == null ? null : bindings.get(key);
    }

    @Override
    public void reset() {
        bindings.clear();
    }

    public enum SourceKind {
        OPENGL_VERTEX_RESOURCE,
        SHARED_SOURCE,
        DYNAMIC_STAGING,
        BACKEND_NATIVE
    }

    public record BufferSlice(long handle, long offset, long size, int stride) {
    }

    public record IndirectSlice(long handle, long offset, int drawCount, int stride) {
    }

    public record GeometryBinding(
            BufferSlice vertexSlice,
            BufferSlice indexSlice,
            IndirectSlice indirectSlice,
            SourceKind sourceKind,
            VertexResource vertexResource,
            IndirectCommandBuffer indirectBuffer
    ) {
    }
}
