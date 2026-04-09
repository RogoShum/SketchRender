package rogo.sketch.vanilla.backend.opengl;

import rogo.sketch.backend.opengl.OpenGLStateAccess;
import rogo.sketch.core.backend.BackendStageScope;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.backend.opengl.state.snapshot.GLStateSnapshot;

final class LegacyMinecraftStageScope implements BackendStageScope {
    private final GLStateSnapshot snapshot;
    private final OpenGLStateAccess stateAccess;
    private final GraphicsAPI api;

    LegacyMinecraftStageScope(GLStateSnapshot snapshot, OpenGLStateAccess stateAccess, GraphicsAPI api) {
        this.snapshot = snapshot;
        this.stateAccess = stateAccess;
        this.api = api;
    }

    @Override
    public void close() {
        snapshot.restore(stateAccess, api);
    }
}
