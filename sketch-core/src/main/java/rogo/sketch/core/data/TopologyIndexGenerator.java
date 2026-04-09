package rogo.sketch.core.data;

/**
 * Generates canonical local indices for authoring topologies that expand into
 * indexed triangle submission.
 */
public final class TopologyIndexGenerator {
    private TopologyIndexGenerator() {
    }

    public static boolean supportsGeneratedIndices(PrimitiveType primitiveType) {
        return primitiveType == PrimitiveType.QUADS;
    }

    public static int calculateIndexCount(PrimitiveType primitiveType, int vertexCount) {
        if (primitiveType == null || vertexCount <= 0) {
            return 0;
        }
        return switch (primitiveType) {
            case QUADS -> (vertexCount / 4) * 6;
            default -> 0;
        };
    }

    public static int[] generateIndices(PrimitiveType primitiveType, int vertexCount) {
        if (!supportsGeneratedIndices(primitiveType) || vertexCount <= 0) {
            return new int[0];
        }
        return switch (primitiveType) {
            case QUADS -> generateQuadIndices(vertexCount);
            default -> new int[0];
        };
    }

    private static int[] generateQuadIndices(int vertexCount) {
        int quadCount = vertexCount / 4;
        int[] indices = new int[quadCount * 6];
        for (int i = 0; i < quadCount; i++) {
            int quadStart = i * 4;
            int indexStart = i * 6;

            indices[indexStart] = quadStart;
            indices[indexStart + 1] = quadStart + 1;
            indices[indexStart + 2] = quadStart + 2;

            indices[indexStart + 3] = quadStart + 2;
            indices[indexStart + 4] = quadStart + 3;
            indices[indexStart + 5] = quadStart;
        }
        return indices;
    }
}

