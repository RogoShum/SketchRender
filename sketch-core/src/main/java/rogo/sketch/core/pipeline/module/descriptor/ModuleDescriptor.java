package rogo.sketch.core.pipeline.module.descriptor;

import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;

/**
 * Static module declaration. Descriptors publish settings/macros/metrics and
 * construct process-lifetime runtimes.
 */
public interface ModuleDescriptor {
    String id();

    default int priority() {
        return 1000;
    }

    default void describe(ModuleDescriptorContext context) {
    }

    ModuleRuntime createRuntime();
}
