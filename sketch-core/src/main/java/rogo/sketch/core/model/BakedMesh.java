package rogo.sketch.core.model;

import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.SharedGeometrySourceSnapshot;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.backend.BackendGeometryMetadata;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.util.KeyId;

/**
 * Implementation of a static mesh backed by either a shared geometry snapshot
 * or an installed backend geometry binding.
 */
public class BakedMesh implements BakedTypeMesh {
    private final BackendGeometryBinding sourceGeometryBinding;
    private final StructLayout format;
    private final PrimitiveType primitiveType;
    private final KeyId keyId;
    private final SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot;

    // Source offsets and counts
    private final int srcVertexOffset; // in vertices
    private final int srcIndexOffset;  // in indices
    private final int vertexCount;
    private final int indexCount;

    public BakedMesh(
            BackendGeometryBinding sourceGeometryBinding,
            KeyId keyId,
            int srcVertexOffset,
            int srcIndexOffset,
            int vertexCount,
            int indexCount,
            SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot) {
        this.sourceGeometryBinding = sourceGeometryBinding;
        this.srcVertexOffset = srcVertexOffset;
        this.srcIndexOffset = srcIndexOffset;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.sharedGeometrySourceSnapshot = sharedGeometrySourceSnapshot;

        if (sourceGeometryBinding instanceof BackendGeometryMetadata metadata) {
            this.format = metadata.vertexFormat();
            this.primitiveType = metadata.primitiveType();
        } else if (sharedGeometrySourceSnapshot != null) {
            this.format = sharedGeometrySourceSnapshot.format();
            this.primitiveType = sharedGeometrySourceSnapshot.primitiveType();
        } else {
            throw new IllegalArgumentException("BakedMesh requires either a source geometry binding or a SharedGeometrySourceSnapshot");
        }
        this.keyId = keyId;
    }

    public BakedMesh(BackendGeometryBinding sourceGeometryBinding, KeyId keyId, int srcVertexOffset, int srcIndexOffset, int vertexCount, int indexCount) {
        this(sourceGeometryBinding, keyId, srcVertexOffset, srcIndexOffset, vertexCount, indexCount, null);
    }

    @Override
    public KeyId getKetId() {
        return keyId;
    }

    @Override
    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    @Override
    public StructLayout getVertexFormat() {
        return format;
    }

    @Override
    public int getVertexCount() {
        return vertexCount;
    }

    @Override
    public int getIndicesCount() {
        return indexCount;
    }

    public BackendGeometryBinding sourceGeometryBinding() {
        return sourceGeometryBinding;
    }

    @Override
    public SharedGeometrySourceSnapshot sharedGeometrySourceSnapshot() {
        return sharedGeometrySourceSnapshot;
    }

    @Override
    public int getVertexOffset() {
        return srcVertexOffset;
    }

    @Override
    public int getIndexOffset() {
        return srcIndexOffset;
    }
}

