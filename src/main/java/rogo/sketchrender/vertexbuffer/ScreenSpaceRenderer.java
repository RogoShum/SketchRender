package rogo.sketchrender.vertexbuffer;

import com.mojang.blaze3d.vertex.VertexFormat;
import rogo.sketchrender.vertexbuffer.attribute.FloatVertex;
import rogo.sketchrender.vertexbuffer.attribute.GLVertex;
import rogo.sketchrender.vertexbuffer.component.VertexBufferRenderer;

public class ScreenSpaceRenderer extends VertexBufferRenderer {

    public ScreenSpaceRenderer() {
        super(VertexFormat.Mode.TRIANGLES, DrawMode.NORMAL, new GLVertex[]{FloatVertex.size2(0, "Position")}
                , ScreenSpaceRenderer::init
                , new GLVertex[]{});
    }

    private static void init(BufferBuilder<?> buffer) {
        buffer.put(-1.0f);
        buffer.put(1.0f);

        buffer.put(-1.0f);
        buffer.put(-3.0f);

        buffer.put(3.0f);
        buffer.put(1.0f);
    }
}
