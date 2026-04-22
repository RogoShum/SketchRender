package rogo.sketch.core.backend;

public record GpuHandle(long value) {
    public static final long NULL = 0L;
    public static final GpuHandle NONE = new GpuHandle(NULL);

    public boolean isValid() {
        return value != NULL;
    }

    public int asGlName() {
        return Math.toIntExact(value);
    }

    public static GpuHandle of(long value) {
        return value == NULL ? NONE : new GpuHandle(value);
    }

    public static GpuHandle ofGl(int name) {
        return of((long) name);
    }
}
