package rogo.sketch.core.event;

import rogo.sketch.core.event.bridge.RegistryEvent;
import rogo.sketch.core.pipeline.flow.RenderFlowStrategy;

import java.util.function.Consumer;

/**
 * Event fired during initialization to allow registration of render flow
 * strategies.
 * <p>
 * This event is fired once during application startup. After the event
 * completes,
 * the render flow registry is frozen and no more strategies can be registered.
 * </p>
 * <p>
 * Third-party mods should listen to this event to register custom flow
 * strategies:
 *
 * <pre>{@code
 * @SubscribeEvent
 * public void onRenderFlowRegister(ProxyModEvent<RenderFlowRegisterEvent> event) {
 *     event.getEvent().register(new MyCustomFlowStrategy());
 * }
 * }</pre>
 * </p>
 */
public class RenderFlowRegisterEvent implements RegistryEvent {
    private final Consumer<RenderFlowStrategy> registrar;

    public RenderFlowRegisterEvent(Consumer<RenderFlowStrategy> registrar) {
        this.registrar = registrar;
    }

    /**
     * Register a render flow strategy.
     *
     * @param strategy The strategy to register
     * @throws IllegalStateException if called after the event has completed
     */
    public void register(RenderFlowStrategy strategy) {
        registrar.accept(strategy);
    }
}