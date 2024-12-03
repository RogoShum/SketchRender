package rogo.sketchrender.vertexbuffer;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.opengl.GL15;
import rogo.sketchrender.vertexbuffer.attribute.*;
import rogo.sketchrender.vertexbuffer.component.VertexBufferRenderer;

import java.nio.ByteBuffer;

public class EntityCullingInstanceRenderer extends VertexBufferRenderer {

    public EntityCullingInstanceRenderer() {
        super(VertexFormat.Mode.TRIANGLES, DrawMode.INSTANCED, new GLVertex[]{FloatVertex.size2(0, "Position")}
                , EntityCullingInstanceRenderer::init
                , new GLVertex[]{FloatVertex.size1(1, "index"),
                        FloatVertex.size2(2, "Size"),
                        FloatVertex.size3(3, "EntityCenter")});
    }

    private static void init(BufferBuilder<?> buffer) {
        buffer.put(-1f);
        buffer.put(-1f);
        buffer.put(1f);
        buffer.put(-1f);
        buffer.put(1f);
        buffer.put(1f);
    }
}
