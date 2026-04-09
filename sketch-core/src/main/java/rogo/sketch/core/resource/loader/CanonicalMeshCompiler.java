package rogo.sketch.core.resource.loader;

import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.SharedGeometrySourceSnapshot;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.TopologyIndexGenerator;
import rogo.sketch.core.data.builder.VertexRecordWriter;
import rogo.sketch.core.data.format.ComponentSpec;
import rogo.sketch.core.data.layout.FieldSpec;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.core.model.BakedMesh;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class CanonicalMeshCompiler {
    private CanonicalMeshCompiler() {
    }

    static void compile(KeyId resourceId, MeshGroup meshGroup, MeshIndexMode indexMode, List<CanonicalSubMesh> subMeshes) {
        if (meshGroup == null || indexMode == null || subMeshes == null || subMeshes.isEmpty()) {
            return;
        }

        StructLayout groupFormat = meshGroup.getVertexFormat();
        PrimitiveType primitiveType = meshGroup.getPrimitiveType();
        List<Float> allVertices = new ArrayList<>();
        List<Integer> allIndices = new ArrayList<>();
        List<SubMeshEntry> entries = new ArrayList<>(subMeshes.size());

        int currentVertexOffset = 0;
        int currentIndexOffset = 0;
        for (CanonicalSubMesh subMesh : subMeshes) {
            List<Float> vertices = subMesh.vertices() != null ? subMesh.vertices() : List.of();
            int sourceFloatsPerVertex = sourceFloatsPerVertex(groupFormat);
            if (vertices.size() % sourceFloatsPerVertex != 0) {
                throw new IllegalArgumentException(
                        "SubMesh " + subMesh.name() + " vertex payload does not match vertex format stride");
            }

            int subVertexCount = vertices.size() / sourceFloatsPerVertex;
            List<Integer> indices = switch (indexMode) {
                case NONE -> List.of();
                case EXPLICIT_LOCAL -> validateExplicitLocalIndices(subMesh.name(), subMesh.indices(), subVertexCount);
                case GENERATED -> generateIndices(subMesh.name(), primitiveType, subVertexCount, subMesh.indices());
            };

            entries.add(new SubMeshEntry(subMesh, currentVertexOffset, currentIndexOffset, subVertexCount, indices.size()));
            allVertices.addAll(vertices);
            allIndices.addAll(indices);
            currentVertexOffset += subVertexCount;
            currentIndexOffset += indices.size();
        }

        GeometryResourceCoordinator vrm = GeometryResourceCoordinator.globalInstance();
        VertexRecordWriter builder = vrm.createBuilder(groupFormat, primitiveType, false);
        fillVertices(builder, toFloatArray(allVertices), groupFormat);
        byte[] vertexBytes = copyBuilderBytes(builder);
        byte[] indexBytes = toIndexBytes(allIndices);
        long sharedSourceRef = Integer.toUnsignedLong(resourceId.hashCode());

        SharedGeometrySourceSnapshot sharedSnapshot = new SharedGeometrySourceSnapshot(
                sharedSourceRef,
                BakedTypeMesh.BAKED_MESH,
                groupFormat,
                primitiveType,
                vertexBytes,
                currentVertexOffset,
                indexBytes,
                currentIndexOffset);

        for (SubMeshEntry entry : entries) {
            BakedMesh bakedMesh = new BakedMesh(
                    null,
                    KeyId.of(entry.subMesh().name()),
                    entry.vertexOffset(),
                    entry.indexOffset(),
                    entry.vertexCount(),
                    entry.indexCount(),
                    sharedSnapshot);
            meshGroup.addMesh(KeyId.of(entry.subMesh().name()), bakedMesh);
            applyMetadata(meshGroup, entry.subMesh());
        }
    }

    private static void applyMetadata(MeshGroup meshGroup, CanonicalSubMesh subMesh) {
        if (meshGroup == null || subMesh == null || subMesh.metadata() == null || subMesh.metadata().isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : subMesh.metadata().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            meshGroup.setMetadata(entry.getKey() + "_" + subMesh.name(), entry.getValue());
        }
    }

    private static List<Integer> validateExplicitLocalIndices(String subMeshName, List<Integer> rawIndices, int subVertexCount) {
        if (rawIndices == null || rawIndices.isEmpty()) {
            throw new IllegalArgumentException("SubMesh " + subMeshName + " requires explicit local indices");
        }
        List<Integer> normalized = new ArrayList<>(rawIndices.size());
        for (Integer rawIndex : rawIndices) {
            if (rawIndex == null || rawIndex < 0 || rawIndex >= subVertexCount) {
                throw new IllegalArgumentException(
                        "SubMesh " + subMeshName + " contains out-of-range local index " + rawIndex);
            }
            normalized.add(rawIndex);
        }
        return Collections.unmodifiableList(normalized);
    }

    private static List<Integer> generateIndices(
            String subMeshName,
            PrimitiveType primitiveType,
            int subVertexCount,
            List<Integer> providedIndices) {
        if (providedIndices != null && !providedIndices.isEmpty()) {
            throw new IllegalArgumentException(
                    "SubMesh " + subMeshName + " uses generated indices and must not declare explicit indices");
        }
        if (!TopologyIndexGenerator.supportsGeneratedIndices(primitiveType)) {
            throw new IllegalArgumentException(
                    "Primitive topology " + primitiveType + " does not support generated indices");
        }
        int[] generated = TopologyIndexGenerator.generateIndices(primitiveType, subVertexCount);
        List<Integer> indices = new ArrayList<>(generated.length);
        for (int index : generated) {
            indices.add(index);
        }
        return Collections.unmodifiableList(indices);
    }

    private static int sourceFloatsPerVertex(StructLayout format) {
        int sourceFloatsPerVertex = 0;
        for (FieldSpec field : format.getFields()) {
            sourceFloatsPerVertex += field.getComponentCount();
        }
        return sourceFloatsPerVertex;
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] data = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            data[i] = values.get(i);
        }
        return data;
    }

    static void fillVertices(VertexRecordWriter filler, float[] data, StructLayout format) {
        int totalFloats = data.length;
        int sourceFloatsPerVertex = sourceFloatsPerVertex(format);
        if (totalFloats % sourceFloatsPerVertex != 0) {
            throw new IllegalArgumentException(
                    "Vertex data size does not match vertex format. Data length: " + totalFloats
                            + ", Floats per vertex: " + sourceFloatsPerVertex);
        }

        int vertexCount = totalFloats / sourceFloatsPerVertex;
        int readIndex = 0;
        FieldSpec[] elements = format.getFields();

        for (int i = 0; i < vertexCount; i++) {
            for (FieldSpec field : elements) {
                int count = field.getComponentCount();
                ValueType type = field.getValueType();
                boolean normalized = field.isNormalized();
                boolean useIntegerWrite = !normalized && !type.isFloatingPoint();

                if (useIntegerWrite) {
                    switch (count) {
                        case 1 -> filler.put((int) data[readIndex++]);
                        case 2 -> filler.put((int) data[readIndex++], (int) data[readIndex++]);
                        case 3 -> filler.put((int) data[readIndex++], (int) data[readIndex++], (int) data[readIndex++]);
                        case 4 -> filler.put((int) data[readIndex++], (int) data[readIndex++], (int) data[readIndex++], (int) data[readIndex++]);
                        default -> throw new UnsupportedOperationException("Unsupported integer component count: " + count);
                    }
                } else {
                    switch (count) {
                        case 1 -> filler.put(data[readIndex++]);
                        case 2 -> filler.put(data[readIndex++], data[readIndex++]);
                        case 3 -> filler.put(data[readIndex++], data[readIndex++], data[readIndex++]);
                        case 4 -> filler.put(data[readIndex++], data[readIndex++], data[readIndex++], data[readIndex++]);
                        default -> throw new UnsupportedOperationException("Unsupported float component count: " + count);
                    }
                }
            }
        }
    }

    static byte[] copyBuilderBytes(VertexRecordWriter builder) {
        if (builder == null || builder.getWriteOffset() <= 0L) {
            return new byte[0];
        }
        ByteBuffer source = builder.asReadOnlyBuffer();
        byte[] bytes = new byte[source.remaining()];
        source.get(bytes);
        return bytes;
    }

    static byte[] toIndexBytes(List<Integer> indices) {
        if (indices == null || indices.isEmpty()) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(indices.size() * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (Integer index : indices) {
            buffer.putInt(index != null ? index : 0);
        }
        return buffer.array();
    }

    record CanonicalSubMesh(String name, List<Float> vertices, List<Integer> indices, Map<String, Object> metadata) {
        CanonicalSubMesh {
            name = name != null ? name : "main";
            vertices = vertices != null ? List.copyOf(vertices) : List.of();
            indices = indices != null ? List.copyOf(indices) : List.of();
            metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        }
    }

    private record SubMeshEntry(
            CanonicalSubMesh subMesh,
            int vertexOffset,
            int indexOffset,
            int vertexCount,
            int indexCount) {
    }
}

