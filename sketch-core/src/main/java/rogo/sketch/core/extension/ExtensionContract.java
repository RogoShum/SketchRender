package rogo.sketch.core.extension;

/**
 * Stable extension contract entry registered against an {@link ExtensionHost}.
 */
public interface ExtensionContract {
    String id();

    default void register(PluginApiFacade facade) {
    }
}
