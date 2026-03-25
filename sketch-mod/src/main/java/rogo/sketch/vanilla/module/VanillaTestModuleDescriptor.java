package rogo.sketch.vanilla.module;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;

public class VanillaTestModuleDescriptor implements ModuleDescriptor {
    public static final String MODULE_ID = "vanilla_test";

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public int priority() {
        return 900;
    }

    @Override
    public void describe(ModuleDescriptorContext context) {
    }

    @Override
    public ModuleRuntime createRuntime() {
        return new VanillaTestModuleRuntime();
    }
}
