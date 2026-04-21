package rogo.sketch.core.backend;

import java.util.Objects;

public record BackendBootstrapContext(
        String entryPoint,
        long mainWindowHandle,
        WindowService windowService,
        PresentationController presentationController
) {
    public BackendBootstrapContext {
        entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
        windowService = Objects.requireNonNull(windowService, "windowService");
        presentationController = Objects.requireNonNull(presentationController, "presentationController");
        if (mainWindowHandle == 0L) {
            throw new IllegalArgumentException("mainWindowHandle must not be zero");
        }
        long declaredHandle = windowService.windowHandle();
        if (declaredHandle != 0L && declaredHandle != mainWindowHandle) {
            throw new IllegalArgumentException(
                    "WindowService handle " + declaredHandle + " does not match bootstrap handle " + mainWindowHandle);
        }
    }
}
