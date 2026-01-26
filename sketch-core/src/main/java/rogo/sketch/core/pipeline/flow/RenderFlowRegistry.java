package rogo.sketch.core.pipeline.flow;

import rogo.sketch.core.event.RenderFlowRegisterEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;

import java.util.*;

/**
 * Registry for render flow strategies.
 * <p>
 * This registry manages the available render flow strategies and provides
 * lookup by {@link RenderFlowType}. Strategies must be registered during
 * the initialization phase - registration is locked after initialization
 * completes.
 * </p>
 * <p>
 * Third-party mods should register custom strategies by listening to
 * {@link RenderFlowRegisterEvent}.
 * </p>
 */
public class RenderFlowRegistry {
    private static RenderFlowRegistry instance;

    private final Map<RenderFlowType, RenderFlowStrategy> strategies = new HashMap<>();
    private volatile boolean frozen = false;
    private boolean initialized = false;

    private RenderFlowRegistry() {
    }

    /**
     * Get the singleton registry instance.
     *
     * @return The render flow registry
     */
    public static RenderFlowRegistry getInstance() {
        if (instance == null) {
            instance = new RenderFlowRegistry();
        }
        return instance;
    }

    /**
     * Initialize the registry and fire the registration event.
     * This method should be called once during application startup.
     * After initialization, no more strategies can be registered.
     */
    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // Post registration event through event bus
        EventBusBridge.post(new RenderFlowRegisterEvent(this::registerInternal));

        // Freeze the registry
        freeze();
    }

    private void registerInternal(RenderFlowStrategy strategy) {
        Objects.requireNonNull(strategy, "Strategy cannot be null");
        Objects.requireNonNull(strategy.getFlowType(), "Strategy flow type cannot be null");

        RenderFlowType flowType = strategy.getFlowType();
        RenderFlowStrategy existing = strategies.get(flowType);

        if (existing != null) {
            throw new IllegalArgumentException("Flow type " + flowType + " is already registered");
        } else {
            strategies.put(flowType, strategy);
        }
    }

    /**
     * Freeze the registry to prevent further modifications.
     */
    public void freeze() {
        this.frozen = true;
    }

    /**
     * Check if the registry is frozen.
     *
     * @return true if frozen
     */
    public boolean isFrozen() {
        return frozen;
    }

    private void checkNotFrozen() {
        if (frozen) {
            throw new IllegalStateException(
                    "RenderFlowRegistry is frozen. Strategies can only be registered during initialization.");
        }
    }

    /**
     * Get the strategy for a specific flow type.
     *
     * @param flowType The flow type to find a strategy for
     * @return Optional containing the strategy, or empty if not found
     */
    public Optional<RenderFlowStrategy> getStrategy(RenderFlowType flowType) {
        return Optional.ofNullable(strategies.get(flowType));
    }

    /**
     * Get all registered strategies.
     *
     * @return Unmodifiable collection of all strategies
     */
    public Collection<RenderFlowStrategy> getAllStrategies() {
        return Collections.unmodifiableCollection(strategies.values());
    }

    /**
     * Check if a strategy exists for the given flow type.
     *
     * @param flowType The flow type to check
     * @return true if a strategy is registered
     */
    public boolean hasStrategy(RenderFlowType flowType) {
        return strategies.containsKey(flowType);
    }

    /**
     * Get the number of registered strategies.
     *
     * @return Strategy count
     */
    public int getStrategyCount() {
        return strategies.size();
    }
}