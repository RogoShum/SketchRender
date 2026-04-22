package rogo.sketch.core.pipeline.module.setting;

/**
 * Describes the scope of work required when a setting changes.
 */
public enum ChangeImpact {
    /**
     * The setting is consumed by runtime code only. Changing it should not rebuild
     * shader variants, graph topology, session graphics, or registered resources.
     */
    RUNTIME_ONLY,

    /**
     * The setting only changes values that are pushed through existing uniform
     * hooks or equivalent frame-live state. Pipelines and resources stay valid.
     */
    UPDATE_UNIFORMS,

    /**
     * The setting changes world/session-owned resource instances or descriptors
     * such as textures, buffers, render targets, or built-in resource handles.
     */
    RECREATE_SESSION_RESOURCES,

    /**
     * The setting changes which session graphics entities are attached, or their
     * descriptors enough that they must be detached and attached again.
     */
    REATTACH_SESSION_GRAPHICS,

    /**
     * The setting changes shader-facing macros, variants, shader interface shape,
     * or other inputs that require program/pipeline recompilation.
     */
    RECOMPILE_SHADERS,

    /**
     * The setting changes pass/stage topology, dependencies, scheduling domain,
     * or graph-visible resources, so the render/task graphs must be rebuilt.
     */
    REBUILD_GRAPHS
}
