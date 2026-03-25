package rogo.sketch.compat.sodium;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;

public class SodiumCompatModuleDescriptor implements ModuleDescriptor {
    public static final String MODULE_ID = "sodium_compat";

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public int priority() {
        return 230;
    }

    @Override
    public void describe(ModuleDescriptorContext context) {
    }

    @Override
    public ModuleRuntime createRuntime() {
        return new SodiumCompatModuleRuntime();
    }
}
