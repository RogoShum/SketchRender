package rogo.sketch.core.pipeline.module;

import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import java.util.*;

/**
 * Compatibility facade for the new descriptor/runtime/session module host.
 */
public final class ModuleRegistry {
    private final List<ModuleDescriptor> pendingDescriptors = new ArrayList<>();
    private final Map<String, Object> namedModules = new LinkedHashMap<>();
    private ModuleRuntimeHost runtimeHost;
    private boolean initialized = false;

    public void registerDescriptor(ModuleDescriptor descriptor) {
        if (initialized) {
            throw new IllegalStateException("Cannot register modules after initialization");
        }
        for (ModuleDescriptor existing : pendingDescriptors) {
            if (existing.id().equals(descriptor.id())) {
                throw new IllegalStateException("Duplicate module name: " + descriptor.id());
            }
        }
        pendingDescriptors.add(descriptor);
    }

    public void processInitialize(GraphicsPipeline<?> pipeline) {
        if (initialized) {
            return;
        }
        runtimeHost = new ModuleRuntimeHost(pipeline);
        for (ModuleDescriptor descriptor : pendingDescriptors) {
            runtimeHost.registerDescriptor(descriptor);
        }
        runtimeHost.processInitialize();
        for (ModuleRuntime runtime : runtimeHost.allRuntimes()) {
            namedModules.put(runtime.id(), runtime);
        }
        initialized = true;
    }

    public void initializeKernel(rogo.sketch.core.pipeline.kernel.PipelineKernel<?> kernel) {
        if (runtimeHost == null) {
            processInitialize(kernel.pipeline());
        }
        runtimeHost.initializeKernel(kernel);
    }

    public void enterWorld() {
        if (runtimeHost != null) {
            runtimeHost.enterWorld();
        }
    }

    public void leaveWorld() {
        if (runtimeHost != null) {
            runtimeHost.leaveWorld();
        }
    }

    public void onResourceReload() {
        if (runtimeHost != null) {
            runtimeHost.onResourceReload();
        }
    }

    public <C extends RenderContext> void contributeToTickGraph(TickGraphBuilder<C> builder) {
        if (runtimeHost != null) {
            runtimeHost.contributeToTickGraph(builder);
        }
    }

    public <C extends RenderContext> void contributeToFrameGraph(RenderGraphBuilder<C> builder) {
        if (runtimeHost != null) {
            runtimeHost.contributeToFrameGraph(builder);
        }
    }

    public void onEntitySpawned(GraphicsEntityId entityId) {
        if (runtimeHost != null) {
            runtimeHost.onEntitySpawned(entityId);
        }
    }

    public void onEntityDestroyed(GraphicsEntityId entityId) {
        if (runtimeHost != null) {
            runtimeHost.onEntityDestroyed(entityId);
        }
    }

    public void onEntityShapeChanged(GraphicsEntityId entityId) {
        if (runtimeHost != null) {
            runtimeHost.onEntityShapeChanged(entityId);
        }
    }

    public Collection<ModuleRuntime> allRuntimes() {
        return runtimeHost != null ? runtimeHost.allRuntimes() : Collections.emptyList();
    }

    public boolean containsModule(String name) {
        return namedModules.containsKey(name);
    }

    public Object moduleByName(String name) {
        return namedModules.get(name);
    }

    public ModuleRuntimeHost runtimeHost() {
        return runtimeHost;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void cleanup() {
        if (runtimeHost != null) {
            runtimeHost.shutdown();
        }
        namedModules.clear();
        pendingDescriptors.clear();
        initialized = false;
    }
}
