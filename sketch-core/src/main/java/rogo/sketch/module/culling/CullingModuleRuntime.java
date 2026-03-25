package rogo.sketch.module.culling;

import rogo.sketch.core.pipeline.module.macro.ModuleMacroProjector;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.pipeline.module.setting.SettingChangeEvent;

import java.util.function.Consumer;

public class CullingModuleRuntime implements ModuleRuntime {
    private final ModuleMacroProjector macroProjector = new ModuleMacroProjector()
            .projectFlag(CullingModuleDescriptor.CULL_CHUNK, "SKETCH_CULL_CHUNK")
            .projectFlag(CullingModuleDescriptor.CULL_ENTITY, "SKETCH_CULL_ENTITY")
            .projectFlag(CullingModuleDescriptor.CULL_BLOCK_ENTITY, "SKETCH_CULL_BLOCK_ENTITY");

    private Consumer<SettingChangeEvent> settingListener;

    @Override
    public String id() {
        return CullingModuleDescriptor.MODULE_ID;
    }

    @Override
    public void onProcessInit(ModuleRuntimeContext context) {
        settingListener = event -> {
            if (id().equals(event.moduleId())) {
                macroProjector.apply(context.ownerId(), context.settings().snapshot(), context.macros());
            }
        };
        context.settings().addListener(settingListener);
        macroProjector.apply(context.ownerId(), context.settings().snapshot(), context.macros());
    }

    @Override
    public void onEnable(ModuleRuntimeContext context) {
        macroProjector.apply(context.ownerId(), context.settings().snapshot(), context.macros());
    }

    @Override
    public void onDisable(ModuleRuntimeContext context) {
        context.clearOwnedMacros();
    }

    @Override
    public void onShutdown(ModuleRuntimeContext context) {
        if (settingListener != null) {
            context.settings().removeListener(settingListener);
            settingListener = null;
        }
        context.clearOwnedMacros();
    }
}
