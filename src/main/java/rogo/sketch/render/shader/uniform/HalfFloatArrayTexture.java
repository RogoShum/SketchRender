package rogo.sketch.render.shader.uniform;

import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.glTexImage3D;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.GL_R16F;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;

public class HalfFloatArrayTexture {
    private int arrayTextureId;
    private int width;
    private int height;

    public HalfFloatArrayTexture(int width, int height, int count) {
        this.width = width;
        this.height = height;

        arrayTextureId = GL11.glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, arrayTextureId);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_R16F, width, height, count, 0, GL_RED, GL_FLOAT, (FloatBuffer) null);
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    public void bindArrayTexture() {
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, arrayTextureId);
    }

    public int getTextureId() {
        return arrayTextureId;
    }

    public void resize(int newWidth, int newHeight, int newCount) {
        if (newWidth == this.width && newHeight == this.height) {
            return;
        }

        int newTextureId = GL11.glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, newTextureId);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_R16F, newWidth, newHeight, newCount, 0, GL_RED, GL_FLOAT, (FloatBuffer) null);

        destroy();

        arrayTextureId = newTextureId;
        width = newWidth;
        height = newHeight;

        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    public void destroy() {
        GL11.glDeleteTextures(arrayTextureId);
    }
}