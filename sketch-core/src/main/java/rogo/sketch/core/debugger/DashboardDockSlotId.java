package rogo.sketch.core.debugger;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

public record DashboardDockSlotId(String value) {
    public DashboardDockSlotId {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("slot id must not be blank");
        }
    }

    public static @Nullable DashboardDockSlotId ofNullable(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new DashboardDockSlotId(value);
    }

    public static DashboardDockSlotId of(String value) {
        return new DashboardDockSlotId(value);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }
}
