package rogo.sketch.core.pipeline.module.runtime;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.kernel.FrameResourceHandle;
import rogo.sketch.core.pipeline.kernel.ModulePassDefinition;
import rogo.sketch.core.util.KeyId;

/**
 * Module-facing collector used to assemble graph metadata snapshots.
 */
public interface ModuleGraphAssemblyContext {
    String moduleId();

    <T> FrameResourceHandle<T> registerFrameResourceHandle(FrameResourceHandle<T> handle);

    @Nullable
    <T> FrameResourceHandle<T> frameResourceHandle(KeyId handleId, Class<T> valueType);

    void registerModulePass(ModulePassDefinition definition);
}
