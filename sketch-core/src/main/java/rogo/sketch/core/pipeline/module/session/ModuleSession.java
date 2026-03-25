package rogo.sketch.core.pipeline.module.session;

/**
 * World/session-lifetime module state.
 */
public interface ModuleSession {
    ModuleSession NOOP = new ModuleSession() {
        @Override
        public String id() {
            return "noop";
        }
    };

    String id();

    default void onWorldEnter(ModuleSessionContext context) {
    }

    default void onWorldLeave(ModuleSessionContext context) {
    }

    default void onEnable(ModuleSessionContext context) {
    }

    default void onDisable(ModuleSessionContext context) {
    }

    default void onResourceReload(ModuleSessionContext context) {
    }

    default void close() {
    }
}
