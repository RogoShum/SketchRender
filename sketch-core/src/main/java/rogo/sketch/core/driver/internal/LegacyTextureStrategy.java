package rogo.sketch.core.driver.internal;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;

import java.nio.ByteBuffer;

/**
 * Legacy implementation of texture operations.
 * Uses traditional bind-then-operate pattern with state save/restore.
 */
public class LegacyTextureStrategy implements IGLTextureStrategy {
    
    @Override
    public int createTexture(int target) {
        return GL11.glGenTextures();
    }
    
    @Override
    public void deleteTexture(int id) {
        GL11.glDeleteTextures(id);
    }
    
    @Override
    public void texImage2D(int id, int level, int internalFormat, int width, int height,
                           int format, int type, ByteBuffer data) {
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, internalFormat, width, height, 0, format, type, data);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
    }
    
    @Override
    public void texSubImage2D(int id, int level, int xOffset, int yOffset, int width, int height,
                              int format, int type, ByteBuffer data) {
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, xOffset, yOffset, width, height, format, type, data);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
    }
    
    @Override
    public void texImage3D(int id, int level, int internalFormat, int width, int height, int depth,
                           int format, int type, ByteBuffer data) {
        int previousBinding = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, id);
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, level, internalFormat, width, height, depth, 0, format, type, data);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, previousBinding);
    }
    
    @Override
    public void texParameteri(int id, int pname, int param) {
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, pname, param);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
    }
    
    @Override
    public void texParameterf(int id, int pname, float param) {
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, pname, param);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
    }
    
    @Override
    public void generateMipmap(int id) {
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
    }
    
    @Override
    public void bindTexture(int target, int id) {
        GL11.glBindTexture(target, id);
    }
    
    @Override
    public void bindTextureUnit(int unit, int id) {
        // Legacy: must use active texture + bind
        int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL13.glActiveTexture(previousUnit);
    }
    
    @Override
    public void activeTexture(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
    }
    
    @Override
    public void bindImageTexture(int unit, int id, int level, boolean layered, int layer, int access, int format) {
        GL42.glBindImageTexture(unit, id, level, layered, layer, access, format);
    }
    
    @Override
    public void texStorage2D(int id, int levels, int internalFormat, int width, int height) {
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, levels, internalFormat, width, height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
    }
    
    @Override
    public void texStorage3D(int id, int levels, int internalFormat, int width, int height, int depth) {
        int previousBinding = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, id);
        GL42.glTexStorage3D(GL12.GL_TEXTURE_3D, levels, internalFormat, width, height, depth);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, previousBinding);
    }
}


