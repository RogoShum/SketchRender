package rogo.sketch.core.driver.internal;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Strategy interface for OpenGL shader and program operations.
 * Implementations provide either DSA (Direct State Access) or Legacy approaches.
 */
public interface IGLShaderStrategy {
    
    // ==================== Program Operations ====================
    
    /**
     * Create a new shader program
     * @return The program handle
     */
    int createProgram();
    
    /**
     * Delete a shader program
     * @param program The program handle
     */
    void deleteProgram(int program);
    
    /**
     * Use/bind a shader program
     * @param program The program handle (0 to unbind)
     */
    void useProgram(int program);
    
    /**
     * Link a shader program
     * @param program The program handle
     */
    void linkProgram(int program);
    
    /**
     * Get program parameter
     * @param program The program handle
     * @param pname Parameter name
     * @return Parameter value
     */
    int getProgrami(int program, int pname);
    
    /**
     * Get program info log
     * @param program The program handle
     * @return The info log string
     */
    String getProgramInfoLog(int program);
    
    // ==================== Shader Operations ====================
    
    /**
     * Create a shader object
     * @param type Shader type (GL_VERTEX_SHADER, GL_FRAGMENT_SHADER, etc.)
     * @return The shader handle
     */
    int createShader(int type);
    
    /**
     * Delete a shader object
     * @param shader The shader handle
     */
    void deleteShader(int shader);
    
    /**
     * Set shader source code
     * @param shader The shader handle
     * @param source The source code
     */
    void shaderSource(int shader, String source);
    
    /**
     * Compile a shader
     * @param shader The shader handle
     */
    void compileShader(int shader);
    
    /**
     * Get shader parameter
     * @param shader The shader handle
     * @param pname Parameter name
     * @return Parameter value
     */
    int getShaderi(int shader, int pname);
    
    /**
     * Get shader info log
     * @param shader The shader handle
     * @return The info log string
     */
    String getShaderInfoLog(int shader);
    
    /**
     * Attach a shader to a program
     * @param program The program handle
     * @param shader The shader handle
     */
    void attachShader(int program, int shader);
    
    /**
     * Detach a shader from a program
     * @param program The program handle
     * @param shader The shader handle
     */
    void detachShader(int program, int shader);
    
    // ==================== Uniform Operations ====================
    
    /**
     * Get uniform location
     * @param program The program handle
     * @param name Uniform name
     * @return Uniform location, or -1 if not found
     */
    int getUniformLocation(int program, String name);
    
    /**
     * Set uniform integer value
     * @param program The program handle (used for DSA, ignored in legacy)
     * @param location Uniform location
     * @param value The value
     */
    void uniform1i(int program, int location, int value);
    
    /**
     * Set uniform float value
     * @param program The program handle
     * @param location Uniform location
     * @param value The value
     */
    void uniform1f(int program, int location, float value);
    
    /**
     * Set uniform vec2 value
     * @param program The program handle
     * @param location Uniform location
     * @param x X component
     * @param y Y component
     */
    void uniform2f(int program, int location, float x, float y);
    
    /**
     * Set uniform vec3 value
     * @param program The program handle
     * @param location Uniform location
     * @param x X component
     * @param y Y component
     * @param z Z component
     */
    void uniform3f(int program, int location, float x, float y, float z);
    
    /**
     * Set uniform vec4 value
     * @param program The program handle
     * @param location Uniform location
     * @param x X component
     * @param y Y component
     * @param z Z component
     * @param w W component
     */
    void uniform4f(int program, int location, float x, float y, float z, float w);
    
    /**
     * Set uniform vec2i value
     * @param program The program handle
     * @param location Uniform location
     * @param x X component
     * @param y Y component
     */
    void uniform2i(int program, int location, int x, int y);
    
    /**
     * Set uniform vec3i value
     * @param program The program handle
     * @param location Uniform location
     * @param x X component
     * @param y Y component
     * @param z Z component
     */
    void uniform3i(int program, int location, int x, int y, int z);
    
    /**
     * Set uniform vec4i value
     * @param program The program handle
     * @param location Uniform location
     * @param x X component
     * @param y Y component
     * @param z Z component
     * @param w W component
     */
    void uniform4i(int program, int location, int x, int y, int z, int w);
    
    /**
     * Set uniform float array
     * @param program The program handle
     * @param location Uniform location
     * @param values The values
     */
    void uniform1fv(int program, int location, FloatBuffer values);
    
    /**
     * Set uniform vec2 array
     * @param program The program handle
     * @param location Uniform location
     * @param values The values (x1,y1,x2,y2,...)
     */
    void uniform2fv(int program, int location, FloatBuffer values);
    
    /**
     * Set uniform vec3 array
     * @param program The program handle
     * @param location Uniform location
     * @param values The values
     */
    void uniform3fv(int program, int location, FloatBuffer values);
    
    /**
     * Set uniform vec4 array
     * @param program The program handle
     * @param location Uniform location
     * @param values The values
     */
    void uniform4fv(int program, int location, FloatBuffer values);
    
    /**
     * Set uniform int array
     * @param program The program handle
     * @param location Uniform location
     * @param values The values
     */
    void uniform1iv(int program, int location, IntBuffer values);
    
    /**
     * Set uniform vec2i array
     * @param program The program handle
     * @param location Uniform location
     * @param values The values
     */
    void uniform2iv(int program, int location, IntBuffer values);
    
    /**
     * Set uniform vec3i array
     * @param program The program handle
     * @param location Uniform location
     * @param values The values
     */
    void uniform3iv(int program, int location, IntBuffer values);
    
    /**
     * Set uniform vec4i array
     * @param program The program handle
     * @param location Uniform location
     * @param values The values
     */
    void uniform4iv(int program, int location, IntBuffer values);
    
    /**
     * Set uniform mat2 value
     * @param program The program handle
     * @param location Uniform location
     * @param transpose Whether to transpose the matrix
     * @param values Matrix values (column-major)
     */
    void uniformMatrix2fv(int program, int location, boolean transpose, FloatBuffer values);
    
    /**
     * Set uniform mat3 value
     * @param program The program handle
     * @param location Uniform location
     * @param transpose Whether to transpose the matrix
     * @param values Matrix values (column-major)
     */
    void uniformMatrix3fv(int program, int location, boolean transpose, FloatBuffer values);
    
    /**
     * Set uniform mat4 value
     * @param program The program handle
     * @param location Uniform location
     * @param transpose Whether to transpose the matrix
     * @param values Matrix values (column-major)
     */
    void uniformMatrix4fv(int program, int location, boolean transpose, FloatBuffer values);
    
    /**
     * Set uniform mat2 value from float array
     * @param program The program handle
     * @param location Uniform location
     * @param transpose Whether to transpose the matrix
     * @param values Matrix values (column-major)
     */
    void uniformMatrix2fv(int program, int location, boolean transpose, float[] values);
    
    /**
     * Set uniform mat3 value from float array
     * @param program The program handle
     * @param location Uniform location
     * @param transpose Whether to transpose the matrix
     * @param values Matrix values (column-major)
     */
    void uniformMatrix3fv(int program, int location, boolean transpose, float[] values);
    
    /**
     * Set uniform mat4 value from float array
     * @param program The program handle
     * @param location Uniform location
     * @param transpose Whether to transpose the matrix
     * @param values Matrix values (column-major)
     */
    void uniformMatrix4fv(int program, int location, boolean transpose, float[] values);
    
    // ==================== Uniform Block Operations ====================
    
    /**
     * Get uniform block index
     * @param program The program handle
     * @param blockName Block name
     * @return Block index, or GL_INVALID_INDEX if not found
     */
    int getUniformBlockIndex(int program, String blockName);
    
    /**
     * Set uniform block binding
     * @param program The program handle
     * @param blockIndex Block index
     * @param bindingPoint Binding point
     */
    void uniformBlockBinding(int program, int blockIndex, int bindingPoint);
    
    // ==================== Attribute Operations ====================
    
    /**
     * Get attribute location
     * @param program The program handle
     * @param name Attribute name
     * @return Attribute location, or -1 if not found
     */
    int getAttribLocation(int program, String name);
    
    /**
     * Get active attribute info
     * @param program The program handle
     * @param index Attribute index
     * @param size Output buffer for size
     * @param type Output buffer for type
     * @return Attribute name
     */
    String getActiveAttrib(int program, int index, IntBuffer size, IntBuffer type);
    
    /**
     * Get active uniform info
     * @param program The program handle
     * @param index Uniform index
     * @param size Output buffer for size
     * @param type Output buffer for type
     * @return Uniform name
     */
    String getActiveUniform(int program, int index, IntBuffer size, IntBuffer type);
}


