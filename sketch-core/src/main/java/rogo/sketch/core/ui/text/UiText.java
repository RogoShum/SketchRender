package rogo.sketch.core.ui.text;

import java.util.Objects;

public record UiText(String value, boolean translatable) {
    public UiText {
        value = Objects.requireNonNull(value, "value");
    }

    public static UiText literal(String value) {
        return new UiText(value, false);
    }

    public static UiText key(String value) {
        return new UiText(value, true);
    }
}
