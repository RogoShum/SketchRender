package rogo.sketch.core.ui.input;

import java.util.Objects;

public record InputActionId(String value) {
    public InputActionId {
        value = Objects.requireNonNull(value, "value");
    }

    public static InputActionId of(String value) {
        return new InputActionId(value);
    }
}
