package rogo.sketch.render.command;

import org.lwjgl.opengl.GL46;
import rogo.sketch.api.DrawCall;
import rogo.sketch.render.data.IndexType;
import rogo.sketch.render.data.PrimitiveType;

public class MultiInstancedMeshDrawCall implements DrawCall {
    private final int subMeshCount;

    public MultiInstancedMeshDrawCall(int subMeshCount) {
        this.subMeshCount = subMeshCount;
    }

    @Override
    public void execute(PrimitiveType primitiveType, IndexType indexType) {
        GL46.glMultiDrawElementsIndirect(primitiveType.glType(), indexType.glType(), 0, subMeshCount, 20);
    }
}