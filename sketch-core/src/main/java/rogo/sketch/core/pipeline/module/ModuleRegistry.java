package rogo.sketch.core.pipeline.module;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Registry for all {@link PipelineModule}s and {@link GraphicsModule}s.
 * <p>
 * Manages module lifecycle (init, cleanup) and dispatches graphics
 * attach/detach events to the appropriate modules via the
 * {@link ModuleBindingIndex}.
 * </p>
 */
public final class ModuleRegistry {
    private final List<PipelineModule> modules = new ArrayList<>();
    private final Map<String, PipelineModule> nameToModules = new HashMap<>();
    private final List<GraphicsModule> graphicsModules = new ArrayList<>();
    private final ModuleBindingIndex bindingIndex = new ModuleBindingIndex();
    private boolean initialized = false;

    /**
     * Register a module before initialization.
     */
    public void register(PipelineModule module) {
        if (initialized) {
            throw new IllegalStateException("Cannot register modules after initialization");
        }

        if (nameToModules.containsKey(module.name())) {
            throw new IllegalStateException("Duplicate module name: " + module.name());
        }

        nameToModules.put(module.name(), module);
        modules.add(module);
        if (module instanceof GraphicsModule gm) {
            graphicsModules.add(gm);
        }
    }

    /**
     * Initialize all modules in priority order.
     */
    public void initialize(GraphicsPipeline<?> pipeline) {
        modules.sort(Comparator.comparingInt(PipelineModule::priority));
        for (PipelineModule module : modules) {
            module.initialize(pipeline);
        }
        initialized = true;
    }

    /**
     * Let all modules contribute passes to the render graph builder.
     */
    public <C extends RenderContext> void contributeToGraph(RenderGraphBuilder<C> builder) {
        for (PipelineModule module : modules) {
            module.contributeToGraph(builder);
        }
    }

    /**
     * Notify modules that a graphics instance has been added.
     * Each interested GraphicsModule will claim the instance.
     */
    public void onGraphicsAdded(Graphics graphics, RenderParameter renderParameter, KeyId containerType) {
        for (GraphicsModule module : graphicsModules) {
            if (module.supports(graphics)) {
                module.onAttach(graphics, renderParameter, containerType);
                bindingIndex.bind(graphics, module);
            }
        }
    }

    /**
     * Notify modules that a graphics instance has been removed.
     * All modules that claimed it will be notified.
     */
    public void onGraphicsRemoved(Graphics graphics) {
        Set<GraphicsModule> bound = bindingIndex.unbindAll(graphics);
        for (GraphicsModule module : bound) {
            module.onDetach(graphics);
        }
    }

    /**
     * Cleanup all modules.
     */
    public void cleanup() {
        for (PipelineModule module : modules) {
            module.cleanup();
        }
        bindingIndex.clear();
    }

    /**
     * Get all registered modules.
     */
    public List<PipelineModule> allModules() {
        return Collections.unmodifiableList(modules);
    }

    public boolean containsModule(String name) {
        return nameToModules.containsKey(name);
    }

    public PipelineModule moduleByName(String name) {
        return nameToModules.get(name);
    }

    /**
     * Get the binding index for inspection.
     */
    public ModuleBindingIndex bindingIndex() {
        return bindingIndex;
    }

    public boolean isInitialized() {
        return initialized;
    }
}