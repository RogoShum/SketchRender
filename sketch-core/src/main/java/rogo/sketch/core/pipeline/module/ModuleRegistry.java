package rogo.sketch.core.pipeline.module;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.pipeline.module.session.ModuleSession;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Compatibility facade for the new descriptor/runtime/session module host.
 */
public final class ModuleRegistry {
    private final List<ModuleDescriptor> pendingDescriptors = new ArrayList<>();
    private final Map<String, Object> namedModules = new LinkedHashMap<>();
    private final ModuleBindingIndex bindingIndex = new ModuleBindingIndex();
    private ModuleRuntimeHost runtimeHost;
    private boolean initialized = false;

    public void register(PipelineModule module) {
        registerDescriptor(new LegacyModuleDescriptor(module));
        namedModules.put(module.name(), module);
    }

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

    public void onGraphicsAdded(Graphics graphics, RenderParameter renderParameter, KeyId containerType) {
        if (runtimeHost != null) {
            runtimeHost.onGraphicsAdded(graphics, renderParameter, containerType);
        }
    }

    public void onGraphicsRemoved(Graphics graphics) {
        if (runtimeHost != null) {
            runtimeHost.onGraphicsRemoved(graphics);
        }
    }

    public void cleanup() {
        if (runtimeHost != null) {
            runtimeHost.shutdown();
        }
        bindingIndex.clear();
        namedModules.clear();
        pendingDescriptors.clear();
        initialized = false;
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

    public ModuleBindingIndex bindingIndex() {
        return bindingIndex;
    }

    public boolean isInitialized() {
        return initialized;
    }

    private static final class LegacyModuleDescriptor implements ModuleDescriptor {
        private final PipelineModule delegate;

        private LegacyModuleDescriptor(PipelineModule delegate) {
            this.delegate = delegate;
        }

        @Override
        public String id() {
            return delegate.name();
        }

        @Override
        public int priority() {
            return delegate.priority();
        }

        @Override
        public void describe(ModuleDescriptorContext context) {
        }

        @Override
        public ModuleRuntime createRuntime() {
            return new LegacyModuleRuntime(delegate);
        }
    }

    private static final class LegacyModuleRuntime implements ModuleRuntime {
        private final PipelineModule delegate;

        private LegacyModuleRuntime(PipelineModule delegate) {
            this.delegate = delegate;
        }

        @Override
        public String id() {
            return delegate.name();
        }

        @Override
        public void onKernelInit(ModuleRuntimeContext context) {
            delegate.initialize(context.pipeline());
        }

        @Override
        public boolean supports(Graphics graphics) {
            return delegate instanceof GraphicsModule graphicsModule && graphicsModule.supports(graphics);
        }

        @Override
        public void onGraphicsAttached(Graphics graphics, RenderParameter renderParameter, KeyId containerType, ModuleRuntimeContext context) {
            if (delegate instanceof GraphicsModule graphicsModule) {
                graphicsModule.onAttach(graphics, renderParameter, containerType);
            }
        }

        @Override
        public void onGraphicsDetached(Graphics graphics, ModuleRuntimeContext context) {
            if (delegate instanceof GraphicsModule graphicsModule) {
                graphicsModule.onDetach(graphics);
            }
        }

        @Override
        public <C extends RenderContext> void contributeToTickGraph(TickGraphBuilder<C> builder) {
            delegate.contributeToTickGraph(builder);
        }

        @Override
        public <C extends RenderContext> void contributeToFrameGraph(RenderGraphBuilder<C> builder) {
            delegate.contributeToFrameGraph(builder);
        }

        @Override
        public void onShutdown(ModuleRuntimeContext context) {
            delegate.cleanup();
        }

        @Override
        public ModuleSession createSession() {
            return ModuleSession.NOOP;
        }
    }
}