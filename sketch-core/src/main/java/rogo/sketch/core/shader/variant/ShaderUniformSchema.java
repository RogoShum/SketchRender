package rogo.sketch.core.shader.variant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Declaration-level uniform schema for a processed shader variant.
 */
public final class ShaderUniformSchema {
    private static final ShaderUniformSchema EMPTY = new ShaderUniformSchema(List.of());

    private final List<ShaderUniformSpec> uniforms;

    public ShaderUniformSchema(List<ShaderUniformSpec> uniforms) {
        if (uniforms == null || uniforms.isEmpty()) {
            this.uniforms = List.of();
            return;
        }
        this.uniforms = Collections.unmodifiableList(new ArrayList<>(uniforms));
    }

    public static ShaderUniformSchema empty() {
        return EMPTY;
    }

    public List<ShaderUniformSpec> uniforms() {
        return uniforms;
    }

    public boolean isEmpty() {
        return uniforms.isEmpty();
    }
}

