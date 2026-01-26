package rogo.sketch.core.data;

import org.lwjgl.opengl.GL15;

/**
 * OpenGL buffer usage patterns enumeration
 */
public enum Usage {
    // Static usage patterns - data is set once and used many times
    STATIC_DRAW(GL15.GL_STATIC_DRAW, "Data is modified once and used many times for drawing"),
    STATIC_READ(GL15.GL_STATIC_READ, "Data is modified once and used many times for reading back from GL"),
    STATIC_COPY(GL15.GL_STATIC_COPY, "Data is modified once and used many times for copying"),
    
    // Dynamic usage patterns - data is modified repeatedly and used many times
    DYNAMIC_DRAW(GL15.GL_DYNAMIC_DRAW, "Data is modified repeatedly and used many times for drawing"),
    DYNAMIC_READ(GL15.GL_DYNAMIC_READ, "Data is modified repeatedly and used many times for reading back from GL"),
    DYNAMIC_COPY(GL15.GL_DYNAMIC_COPY, "Data is modified repeatedly and used many times for copying"),
    
    // Stream usage patterns - data is modified once and used at most a few times
    STREAM_DRAW(GL15.GL_STREAM_DRAW, "Data is modified once and used at most a few times for drawing"),
    STREAM_READ(GL15.GL_STREAM_READ, "Data is modified once and used at most a few times for reading back from GL"),
    STREAM_COPY(GL15.GL_STREAM_COPY, "Data is modified once and used at most a few times for copying");
    
    private final int glConstant;
    private final String description;
    
    Usage(int glConstant, String description) {
        this.glConstant = glConstant;
        this.description = description;
    }
    
    /**
     * Get the OpenGL constant for this usage pattern
     */
    public int getGLConstant() {
        return glConstant;
    }
    
    /**
     * Get a human-readable description of this usage pattern
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this is a static usage pattern
     */
    public boolean isStatic() {
        return this == STATIC_DRAW || this == STATIC_READ || this == STATIC_COPY;
    }
    
    /**
     * Check if this is a dynamic usage pattern
     */
    public boolean isDynamic() {
        return this == DYNAMIC_DRAW || this == DYNAMIC_READ || this == DYNAMIC_COPY;
    }
    
    /**
     * Check if this is a stream usage pattern
     */
    public boolean isStream() {
        return this == STREAM_DRAW || this == STREAM_READ || this == STREAM_COPY;
    }
    
    /**
     * Check if this usage pattern is for drawing operations
     */
    public boolean isForDrawing() {
        return this == STATIC_DRAW || this == DYNAMIC_DRAW || this == STREAM_DRAW;
    }
    
    /**
     * Check if this usage pattern is for reading operations
     */
    public boolean isForReading() {
        return this == STATIC_READ || this == DYNAMIC_READ || this == STREAM_READ;
    }
    
    /**
     * Check if this usage pattern is for copying operations
     */
    public boolean isForCopying() {
        return this == STATIC_COPY || this == DYNAMIC_COPY || this == STREAM_COPY;
    }
    
    /**
     * Get the most commonly used usage pattern for vertex data
     */
    public static Usage getDefault() {
        return STATIC_DRAW;
    }
    
    /**
     * Get usage pattern based on update frequency and access pattern
     * @param updateFrequently true if data will be updated frequently
     * @param forDrawing true if data is primarily for drawing operations
     * @return appropriate usage pattern
     */
    public static Usage getRecommended(boolean updateFrequently, boolean forDrawing) {
        if (updateFrequently) {
            return forDrawing ? DYNAMIC_DRAW : DYNAMIC_READ;
        } else {
            return forDrawing ? STATIC_DRAW : STATIC_READ;
        }
    }
    
    @Override
    public String toString() {
        return name() + " (GL_" + name() + ")";
    }
}

