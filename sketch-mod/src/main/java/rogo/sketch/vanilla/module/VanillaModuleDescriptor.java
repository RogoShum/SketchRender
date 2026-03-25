package rogo.sketch.vanilla.module;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;

public class VanillaModuleDescriptor implements ModuleDescriptor {
    public static final String MODULE_ID = "vanilla";

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public void describe(ModuleDescriptorContext context) {
    }

    @Override
    public ModuleRuntime createRuntime() {
        return new VanillaModuleRuntime();
    }
}
