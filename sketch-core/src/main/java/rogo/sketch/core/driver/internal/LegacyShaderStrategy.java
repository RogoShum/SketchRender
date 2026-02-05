package rogo.sketch.core.driver.internal;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Legacy implementation of shader operations.
 * Uses traditional bind-then-operate pattern for uniforms.
 */
public class LegacyShaderStrategy implements IGLShaderStrategy {
    
    // ==================== Program Operations ====================
    
    @Override
    public int createProgram() {
        return GL20.glCreateProgram();
    }
    
    @Override
    public void deleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }
    
    @Override
    public void useProgram(int program) {
        GL20.glUseProgram(program);
    }
    
    @Override
    public void linkProgram(int program) {
        GL20.glLinkProgram(program);
    }
    
    @Override
    public int getProgrami(int program, int pname) {
        return GL20.glGetProgrami(program, pname);
    }
    
    @Override
    public String getProgramInfoLog(int program) {
        return GL20.glGetProgramInfoLog(program);
    }
    
    // ==================== Shader Operations ====================
    
    @Override
    public int createShader(int type) {
        return GL20.glCreateShader(type);
    }
    
    @Override
    public void deleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }
    
    @Override
    public void shaderSource(int shader, String source) {
        GL20.glShaderSource(shader, source);
    }
    
    @Override
    public void compileShader(int shader) {
        GL20.glCompileShader(shader);
    }
    
    @Override
    public int getShaderi(int shader, int pname) {
        return GL20.glGetShaderi(shader, pname);
    }
    
    @Override
    public String getShaderInfoLog(int shader) {
        return GL20.glGetShaderInfoLog(shader);
    }
    
    @Override
    public void attachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }
    
    @Override
    public void detachShader(int program, int shader) {
        GL20.glDetachShader(program, shader);
    }
    
    // ==================== Uniform Operations (Legacy - requires program to be bound) ====================
    
    @Override
    public int getUniformLocation(int program, String name) {
        return GL20.glGetUniformLocation(program, name);
    }
    
    private void ensureProgramBound(int program) {
        int currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (currentProgram != program) {
            GL20.glUseProgram(program);
        }
    }
    
    @Override
    public void uniform1i(int program, int location, int value) {
        // Legacy: program parameter is ignored, must be current program
        GL20.glUniform1i(location, value);
    }
    
    @Override
    public void uniform1f(int program, int location, float value) {
        GL20.glUniform1f(location, value);
    }
    
    @Override
    public void uniform2f(int program, int location, float x, float y) {
        GL20.glUniform2f(location, x, y);
    }
    
    @Override
    public void uniform3f(int program, int location, float x, float y, float z) {
        GL20.glUniform3f(location, x, y, z);
    }
    
    @Override
    public void uniform4f(int program, int location, float x, float y, float z, float w) {
        GL20.glUniform4f(location, x, y, z, w);
    }
    
    @Override
    public void uniform2i(int program, int location, int x, int y) {
        GL20.glUniform2i(location, x, y);
    }
    
    @Override
    public void uniform3i(int program, int location, int x, int y, int z) {
        GL20.glUniform3i(location, x, y, z);
    }
    
    @Override
    public void uniform4i(int program, int location, int x, int y, int z, int w) {
        GL20.glUniform4i(location, x, y, z, w);
    }
    
    @Override
    public void uniform1fv(int program, int location, FloatBuffer values) {
        GL20.glUniform1fv(location, values);
    }
    
    @Override
    public void uniform2fv(int program, int location, FloatBuffer values) {
        GL20.glUniform2fv(location, values);
    }
    
    @Override
    public void uniform3fv(int program, int location, FloatBuffer values) {
        GL20.glUniform3fv(location, values);
    }
    
    @Override
    public void uniform4fv(int program, int location, FloatBuffer values) {
        GL20.glUniform4fv(location, values);
    }
    
    @Override
    public void uniform1iv(int program, int location, IntBuffer values) {
        GL20.glUniform1iv(location, values);
    }
    
    @Override
    public void uniform2iv(int program, int location, IntBuffer values) {
        GL20.glUniform2iv(location, values);
    }
    
    @Override
    public void uniform3iv(int program, int location, IntBuffer values) {
        GL20.glUniform3iv(location, values);
    }
    
    @Override
    public void uniform4iv(int program, int location, IntBuffer values) {
        GL20.glUniform4iv(location, values);
    }
    
    @Override
    public void uniformMatrix2fv(int program, int location, boolean transpose, FloatBuffer values) {
        GL20.glUniformMatrix2fv(location, transpose, values);
    }
    
    @Override
    public void uniformMatrix3fv(int program, int location, boolean transpose, FloatBuffer values) {
        GL20.glUniformMatrix3fv(location, transpose, values);
    }
    
    @Override
    public void uniformMatrix4fv(int program, int location, boolean transpose, FloatBuffer values) {
        GL20.glUniformMatrix4fv(location, transpose, values);
    }
    
    @Override
    public void uniformMatrix2fv(int program, int location, boolean transpose, float[] values) {
        GL20.glUniformMatrix2fv(location, transpose, values);
    }
    
    @Override
    public void uniformMatrix3fv(int program, int location, boolean transpose, float[] values) {
        GL20.glUniformMatrix3fv(location, transpose, values);
    }
    
    @Override
    public void uniformMatrix4fv(int program, int location, boolean transpose, float[] values) {
        GL20.glUniformMatrix4fv(location, transpose, values);
    }
    
    // ==================== Uniform Block Operations ====================
    
    @Override
    public int getUniformBlockIndex(int program, String blockName) {
        return GL31.glGetUniformBlockIndex(program, blockName);
    }
    
    @Override
    public void uniformBlockBinding(int program, int blockIndex, int bindingPoint) {
        GL31.glUniformBlockBinding(program, blockIndex, bindingPoint);
    }
    
    // ==================== Attribute Operations ====================
    
    @Override
    public int getAttribLocation(int program, String name) {
        return GL20.glGetAttribLocation(program, name);
    }
    
    @Override
    public String getActiveAttrib(int program, int index, IntBuffer size, IntBuffer type) {
        return GL20.glGetActiveAttrib(program, index, size, type);
    }
    
    @Override
    public String getActiveUniform(int program, int index, IntBuffer size, IntBuffer type) {
        return GL20.glGetActiveUniform(program, index, size, type);
    }
}


