package rogo.sketch.core.pipeline.data;

import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.util.KeyId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GeometryFrameData implements RenderPipelineData {
    public static final KeyId KEY = KeyId.of("geometry_frame_data");

    private final Map<GeometryHandleKey, GeometryBinding> bindings = new ConcurrentHashMap<>();

    public void register(
            GeometryHandleKey key,
            BackendGeometryBinding geometryBinding,
            BackendInstalledBuffer indirectBuffer) {
        registerNeutral(
                key,
                null,
                null,
                indirectBuffer instanceof BackendIndirectBuffer indirectCommandBuffer
                        ? new IndirectSlice(
                                0L,
                                0L,
                                indirectCommandBuffer.commandCount(),
                                (int) indirectCommandBuffer.strideBytes())
                        : null,
                SourceKind.BACKEND_NATIVE,
                geometryBinding,
                indirectBuffer);
    }

    public void registerNeutral(
            GeometryHandleKey key,
            BufferSlice vertexSlice,
            BufferSlice indexSlice,
            IndirectSlice indirectSlice,
            SourceKind sourceKind,
            BackendGeometryBinding geometryBinding,
            BackendInstalledBuffer indirectBuffer) {
        if (key == null) {
            return;
        }
        GeometryBinding nextBinding = new GeometryBinding(
                vertexSlice,
                indexSlice,
                indirectSlice,
                sourceKind != null ? sourceKind : SourceKind.BACKEND_NATIVE,
                geometryBinding,
                indirectBuffer);
        GeometryBinding previous = bindings.put(key, nextBinding);
        if (previous != null
                && previous.geometryBinding() != geometryBinding
                && previous.geometryBinding() != null
                && geometryBinding != null) {
            SketchDiagnostics.get().warn(
                    "geometry-frame-data",
                    "geometry binding overwritten for handle=" + key);
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
            BackendGeometryBinding geometryBinding,
            BackendInstalledBuffer indirectBuffer
    ) {
    }
}

