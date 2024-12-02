package rogo.sketchrender.shader.uniform;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.glTexImage3D;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;

public class ArrayTextureManager {
    private int arrayTextureId;
    private int layerCount;
    private int width;
    private int height;

    public ArrayTextureManager(int width, int height) {
        this.width = width;
        this.height = height;

        arrayTextureId = GL11.glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, arrayTextureId);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        glTexParameteri(GL_TEXTURE_2D, GL_DEPTH_TEXTURE_MODE, GL_INTENSITY);
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    public void reset(int numTextures) {
        this.layerCount = 0;
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, arrayTextureId);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_DEPTH_COMPONENT32, width, height, numTextures, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (FloatBuffer) null);
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    public void bindArrayTexture() {
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, arrayTextureId);
    }

    public int getTextureId() {
        return arrayTextureId;
    }

    public int bindToRenderTarget() {
        int layer = layerCount++;
        bindArrayTexture();
        GL30.glFramebufferTextureLayer(GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, arrayTextureId, 0, layer);
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        return layer;
    }

    public void destroy() {
        GL11.glDeleteTextures(arrayTextureId);
    }
}
