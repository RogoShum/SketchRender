package rogo.sketch.vanilla.backend;

import rogo.sketch.core.backend.PresentationController;
import rogo.sketch.core.backend.WindowService;

public final class MinecraftPresentationController implements PresentationController {
    @Override
    public void notifyFramebufferResized(int width, int height) {
    }

    @Override
    public void applyWindowSettings(WindowService windowService) {
        if (windowService != null) {
            windowService.setVSync(windowService.vSyncEnabled());
        }
    }
}
