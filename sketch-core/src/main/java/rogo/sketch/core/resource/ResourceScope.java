package rogo.sketch.core.resource;

/**
 * Formal lifecycle scope for graphics resources managed by {@link GraphicsResourceManager}.
 */
public enum ResourceScope {
    /**
     * Engine-level resource that survives normal reloads and has no owner.
     */
    PERSISTENT,
    /**
     * Resource owned by a module/runtime and released when the module detaches or disables.
     */
    MODULE_OWNED,
    /**
     * Resource owned by a session/world lifetime.
     */
    SESSION_OWNED,
    /**
     * Resource registered directly for tests or temporary construction.
     */
    EPHEMERAL_TEST
}

