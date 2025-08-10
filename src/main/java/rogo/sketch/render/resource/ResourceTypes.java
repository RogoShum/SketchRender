package rogo.sketch.render.resource;

import rogo.sketch.util.Identifier;

/**
 * Predefined resource type constants
 */
public final class ResourceTypes {
    public static final Identifier TEXTURE = Identifier.of("texture");
    public static final Identifier SHADER_STORAGE_BUFFER = Identifier.of("shader_storage_buffer");
    public static final Identifier UNIFORM_BLOCK = Identifier.of("uniform_block");
    public static final Identifier COUNTER_BUFFER = Identifier.of("counter_buffer");
    public static final Identifier RENDER_TARGET = Identifier.of("render_target");
    public static final Identifier FRAMEBUFFER = Identifier.of("framebuffer");
    public static final Identifier SHADER_PROGRAM = Identifier.of("shader_program");
    public static final Identifier RENDER_SETTING = Identifier.of("render_setting");
    public static final Identifier PARTIAL_RENDER_SETTING = Identifier.of("partial_render_setting");
    public static final Identifier VERTEX_BUFFER = Identifier.of("vertex_buffer");
    
    private ResourceTypes() {
        // Utility class
    }
}