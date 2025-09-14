package rogo.sketch.api;

import rogo.sketch.render.data.IndexType;
import rogo.sketch.render.data.PrimitiveType;

public interface DrawCall {
    void execute(PrimitiveType primitiveType, IndexType indexType);
}