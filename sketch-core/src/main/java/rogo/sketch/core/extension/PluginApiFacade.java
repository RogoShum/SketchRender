package rogo.sketch.core.extension;

import rogo.sketch.core.extension.event.HostEventRegistrar;
import rogo.sketch.core.object.ObjectGraphicsRegistry;
import rogo.sketch.core.pipeline.GraphicsPipeline;

/**
 * Stable plugin-facing facade exposed by the extension host.
 */
public final class PluginApiFacade {
    private final ExtensionHost extensionHost;

    PluginApiFacade(ExtensionHost extensionHost) {
        this.extensionHost = extensionHost;
    }

    public GraphicsPipeline<?> pipeline() {
        return extensionHost.pipeline();
    }

    public ObjectGraphicsRegistry objectGraphicsRegistry() {
        return extensionHost.objectGraphicsRegistry();
    }

    public HostEventRegistrar hostEvents() {
        return extensionHost.hostEvents();
    }

    public void registerExtensionContract(ExtensionContract contract) {
        extensionHost.registerContract(contract);
    }
}
