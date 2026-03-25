package rogo.sketch.core.pipeline.module.setting;

/**
 * Describes the scope of work required when a setting changes.
 */
public enum ChangeImpact {
    RUNTIME_ONLY,
    UPDATE_UNIFORMS,
    RECREATE_SESSION_RESOURCES,
    REATTACH_SESSION_GRAPHICS,
    RECOMPILE_SHADERS,
    REBUILD_GRAPHS
}
