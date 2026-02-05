package rogo.sketch.core.driver.internal;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL45;

import java.nio.ByteBuffer;

/**
 * DSA (Direct State Access) implementation of texture operations.
 * Uses OpenGL 4.5+ DSA functions for state-less texture manipulation.
 */
public class DSATextureStrategy implements IGLTextureStrategy {
    
    @Override
    public int createTexture(int target) {
        return GL45.glCreateTextures(target);
    }
    
    @Override
    public void deleteTexture(int id) {
        GL11.glDeleteTextures(id);
    }
    
    @Override
    public void texImage2D(int id, int level, int internalFormat, int width, int height,
                           int format, int type, ByteBuffer data) {
        // DSA doesn't have a direct equivalent for texImage2D, so we use texStorage + texSubImage
        // or fall back to binding for initial allocation
        // For simplicity, we allocate storage and then upload
        if (data != null) {
            // First allocate with texStorage, then upload with texSubImage
            // Note: texStorage is immutable, so this approach only works for initial creation
            // For more flexibility, we bind temporarily
            int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, internalFormat, width, height, 0, format, type, data);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
        } else {
            // For allocation only, we can use DSA
            int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, internalFormat, width, height, 0, format, type, (ByteBuffer) null);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
        }
    }
    
    @Override
    public void texSubImage2D(int id, int level, int xOffset, int yOffset, int width, int height,
                              int format, int type, ByteBuffer data) {
        GL45.glTextureSubImage2D(id, level, xOffset, yOffset, width, height, format, type, data);
    }
    
    @Override
    public void texImage3D(int id, int level, int internalFormat, int width, int height, int depth,
                           int format, int type, ByteBuffer data) {
        // Similar to texImage2D, fall back to binding for initial allocation
        int previousBinding = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, id);
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, level, internalFormat, width, height, depth, 0, format, type, data);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, previousBinding);
    }
    
    @Override
    public void texParameteri(int id, int pname, int param) {
        GL45.glTextureParameteri(id, pname, param);
    }
    
    @Override
    public void texParameterf(int id, int pname, float param) {
        GL45.glTextureParameterf(id, pname, param);
    }
    
    @Override
    public void generateMipmap(int id) {
        GL45.glGenerateTextureMipmap(id);
    }
    
    @Override
    public void bindTexture(int target, int id) {
        // Still need regular binding for some operations and draw calls
        GL11.glBindTexture(target, id);
    }
    
    @Override
    public void bindTextureUnit(int unit, int id) {
        GL45.glBindTextureUnit(unit, id);
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
        GL45.glTextureStorage2D(id, levels, internalFormat, width, height);
    }
    
    @Override
    public void texStorage3D(int id, int levels, int internalFormat, int width, int height, int depth) {
        GL45.glTextureStorage3D(id, levels, internalFormat, width, height, depth);
    }
}


