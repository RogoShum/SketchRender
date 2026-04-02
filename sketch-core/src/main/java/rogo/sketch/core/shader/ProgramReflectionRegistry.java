package rogo.sketch.core.shader;

import java.util.Objects;

public final class ProgramReflectionRegistry {
    private static volatile ProgramReflectionService service = ProgramReflectionService.NO_OP;

    private ProgramReflectionRegistry() {
    }

    public static void register(ProgramReflectionService reflectionService) {
        service = Objects.requireNonNull(reflectionService, "reflectionService");
    }

    public static ProgramReflectionService get() {
        return service;
    }
}
