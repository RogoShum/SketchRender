package rogo.sketch.core.api.graphics;

/**
 * Describes whether a graphics instance keeps a stable render descriptor for its
 * lifetime or may swap descriptor/material state during runtime.
 */
public enum DescriptorStability {
    /**
     * Descriptor/material layout is expected to stay stable after initial
     * compilation.
     */
    STABLE,
    /**
     * Descriptor/material state may change at runtime and should be recompiled
     * when {@link RenderDescriptorProvider#descriptorVersion()} changes.
     */
    DYNAMIC
}

