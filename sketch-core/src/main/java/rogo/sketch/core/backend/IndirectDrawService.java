package rogo.sketch.core.backend;

public interface IndirectDrawService {
    IndirectDrawService UNSUPPORTED = new IndirectDrawService() {
    };

    default boolean isSupported() {
        return this != UNSUPPORTED;
    }

    default void multiDrawElementsIndirect(
            int primitiveMode,
            int indexType,
            long commandOffset,
            int drawCount,
            int stride) {
    }

    default void multiDrawElementsIndirectCount(
            long commandOffset,
            long countOffset,
            int maxDrawCount,
            int stride) {
    }
}
