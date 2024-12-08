package rogo.sketchrender.api;

import rogo.sketchrender.culling.ChunkDataStorage;

public interface ChunkDataStorageSupplier {
    ChunkDataStorage getChunkDataStorage();
}
