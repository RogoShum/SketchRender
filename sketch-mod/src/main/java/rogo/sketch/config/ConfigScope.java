package rogo.sketch.config;

import org.jetbrains.annotations.Nullable;

public record ConfigScope(String namespace, @Nullable String moduleId) {
}
