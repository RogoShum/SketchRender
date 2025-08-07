package rogo.sketchrender.vertexbuffer;

import com.mojang.blaze3d.vertex.VertexFormat;
import rogo.sketchrender.vertexbuffer.attribute.FloatVertex;
import rogo.sketchrender.vertexbuffer.attribute.Vertex;
import rogo.sketchrender.vertexbuffer.component.VertexBufferResource;

public class ScreenSpaceResource extends VertexBufferResource {

    public ScreenSpaceResource() {
        super(VertexFormat.Mode.TRIANGLES, DrawMode.NORMAL, new Vertex[]{FloatVertex.size2(0, "Position")}
                , ScreenSpaceResource::init
                , new Vertex[]{});
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