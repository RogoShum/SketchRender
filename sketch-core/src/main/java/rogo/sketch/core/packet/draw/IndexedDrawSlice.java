package rogo.sketch.core.packet.draw;

/**
 * Canonical indexed-draw slice description.
 * <p>
 * This record only describes draw addressing:
 * <ul>
 *     <li>{@code baseVertex}: base vertex added to local index values</li>
 *     <li>{@code firstIndexByteOffset}: byte offset into the active index buffer</li>
 *     <li>{@code indexCount}: number of indices to draw</li>
 * </ul>
 * Any backend-specific installed buffer ownership lives elsewhere.
 */
public record IndexedDrawSlice(
        long sourceToken,
        int baseVertex,
        int indexCount,
        long firstIndexByteOffset
) {
    public IndexedDrawSlice {
        indexCount = Math.max(indexCount, 0);
        firstIndexByteOffset = Math.max(firstIndexByteOffset, 0L);
    }

    public static IndexedDrawSlice indexed(int baseVertex, int indexCount, long firstIndexByteOffset) {
        return new IndexedDrawSlice(0L, baseVertex, indexCount, firstIndexByteOffset);
    }
}

