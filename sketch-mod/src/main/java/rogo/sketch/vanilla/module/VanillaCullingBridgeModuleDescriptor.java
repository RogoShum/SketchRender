package rogo.sketch.vanilla.module;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;

public class VanillaCullingBridgeModuleDescriptor implements ModuleDescriptor {
    public static final String MODULE_ID = "vanilla_culling_bridge";

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public int priority() {
        return 220;
    }

    @Override
    public void describe(ModuleDescriptorContext context) {
    }

    @Override
    public ModuleRuntime createRuntime() {
        return new VanillaCullingBridgeModuleRuntime();
    }
}
