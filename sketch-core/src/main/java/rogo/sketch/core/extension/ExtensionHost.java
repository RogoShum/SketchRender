package rogo.sketch.core.extension;

import rogo.sketch.core.extension.event.HostEventRegistrar;
import rogo.sketch.core.extension.event.ObjectLifecycleEventBus;
import rogo.sketch.core.object.ObjectGraphicsRegistry;
import rogo.sketch.core.pipeline.GraphicsPipeline;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Process-scoped extension services owned by a graphics pipeline instance.
 */
public final class ExtensionHost {
    private final GraphicsPipeline<?> pipeline;
    private final ObjectLifecycleEventBus objectLifecycleEventBus = new ObjectLifecycleEventBus();
    private final ObjectGraphicsRegistry objectGraphicsRegistry;
    private final PluginApiFacade pluginApiFacade;
    private final Map<String, ExtensionContract> contracts = new LinkedHashMap<>();

    public ExtensionHost(GraphicsPipeline<?> pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.objectGraphicsRegistry = new ObjectGraphicsRegistry(pipeline, objectLifecycleEventBus);
        this.pluginApiFacade = new PluginApiFacade(this);
    }

    public GraphicsPipeline<?> pipeline() {
        return pipeline;
    }

    public ObjectGraphicsRegistry objectGraphicsRegistry() {
        return objectGraphicsRegistry;
    }

    public ObjectLifecycleEventBus objectLifecycleEventBus() {
        return objectLifecycleEventBus;
    }

    public HostEventRegistrar hostEvents() {
        return objectLifecycleEventBus;
    }

    public PluginApiFacade pluginApiFacade() {
        return pluginApiFacade;
    }

    public synchronized void registerContract(ExtensionContract contract) {
        Objects.requireNonNull(contract, "contract");
        if (contracts.containsKey(contract.id())) {
            throw new IllegalStateException("Duplicate extension contract id: " + contract.id());
        }
        contracts.put(contract.id(), contract);
        contract.register(pluginApiFacade);
    }
}
