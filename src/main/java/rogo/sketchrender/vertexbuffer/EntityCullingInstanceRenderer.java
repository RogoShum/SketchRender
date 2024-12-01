package rogo.sketchrender.vertexbuffer;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.opengl.GL15;
import rogo.sketchrender.vertexbuffer.attribute.GLFloatVertex;
import rogo.sketchrender.vertexbuffer.attribute.GLVertex;
import rogo.sketchrender.vertexbuffer.component.VertexBufferRenderer;

import java.nio.FloatBuffer;

public class EntityCullingInstanceRenderer extends VertexBufferRenderer {

    public EntityCullingInstanceRenderer() {
        super(VertexFormat.Mode.QUADS, DrawMode.INSTANCED, new GLVertex[]{GLFloatVertex.createF2(0, "Position")}
                , EntityCullingInstanceRenderer::init
                , new GLVertex[]{GLFloatVertex.createF1(1, "index"),
                        GLFloatVertex.createF2(2, "Size"),
                        GLFloatVertex.createF3(3, "EntityCenter")});
    }

    private static void init(FloatBuffer buffer) {
        buffer.position(0);
        buffer.put(-1f);
        buffer.put(-1f);
        buffer.put(1f);
        buffer.put(-1f);
        buffer.put(1f);
        buffer.put(1f);
        buffer.put(-1f);
        buffer.put(1f);
        buffer.flip();
        GL15.glBufferData(34962, buffer, 35044);
    }
}
