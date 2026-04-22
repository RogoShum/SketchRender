package rogo.sketch.core.backend;

public record GpuHandle(long value) {
    public static final long NULL = 0L;
    public static final GpuHandle NONE = new GpuHandle(NULL);

    public boolean isValid() {
        return value != NULL;
    }

    /**
     * Compatibility bridge for OpenGL-only callers. New backend-neutral code
     * should keep the opaque long handle instead of narrowing it to a GL name.
     */
    @Deprecated
    public int asGlName() {
        return Math.toIntExact(value);
    }

    public static GpuHandle of(long value) {
        return value == NULL ? NONE : new GpuHandle(value);
    }

    /**
     * Compatibility bridge for legacy GL integer names.
     */
    @Deprecated
    public static GpuHandle ofGl(int name) {
        return of((long) name);
    }
}
