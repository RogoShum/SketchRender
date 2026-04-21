package rogo.sketch.core.backend;

/**
 * Stable backend device-facing service boundary used by phase-3 backend
 * refactors. Runtime facades may still expose legacy getters, but the actual
 * backend service graph should hang off this contract.
 */
public interface RenderDevice {
    BackendCapabilities capabilities();

    BackendFrameExecutor frameExecutor();

    default BackendCountedIndirectDraw countedIndirectDraw() {
        return BackendCountedIndirectDraw.UNSUPPORTED;
    }

    BackendShaderProgramCache shaderProgramCache();

    BackendResourceResolver resourceResolver();

    BackendStateApplier stateApplier();

    CommandRecorderFactory commandRecorderFactory();
}
