package rogo.sketch.core.shader.variant;

import rogo.sketch.core.shader.ShaderType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Declaration-level shader uniform schema entry resolved from processed GLSL.
 */
public final class ShaderUniformSpec {
    private final String name;
    private final String glslType;
    private final int arraySize;
    private final Set<ShaderType> stageMask;
    private final boolean blockMember;
    private final boolean standaloneUniform;

    public ShaderUniformSpec(
            String name,
            String glslType,
            int arraySize,
            Set<ShaderType> stageMask,
            boolean blockMember,
            boolean standaloneUniform) {
        this.name = Objects.requireNonNull(name, "name");
        this.glslType = Objects.requireNonNull(glslType, "glslType");
        this.arraySize = Math.max(arraySize, 1);
        this.stageMask = stageMask == null || stageMask.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(stageMask));
        this.blockMember = blockMember;
        this.standaloneUniform = standaloneUniform;
    }

    public String name() {
        return name;
    }

    public String glslType() {
        return glslType;
    }

    public int arraySize() {
        return arraySize;
    }

    public Set<ShaderType> stageMask() {
        return stageMask;
    }

    public boolean blockMember() {
        return blockMember;
    }

    public boolean standaloneUniform() {
        return standaloneUniform;
    }
}

