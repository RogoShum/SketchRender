package rogo.sketch.profiler;

public class ProfilerEvent {
    public enum Type {
        START, END, LINEAR
    }

    public Type type;
    public String name;
    public long timestamp;

    public ProfilerEvent(Type type, String name, long timestamp) {
        this.type = type;
        this.name = name;
        this.timestamp = timestamp;
    }
}
