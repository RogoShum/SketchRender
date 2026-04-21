package rogo.sketch.core.driver.state;

import rogo.sketch.core.driver.state.component.RenderTargetState;

public record AttachmentBindingState(
        RenderTargetState renderTargetState
) {
}
