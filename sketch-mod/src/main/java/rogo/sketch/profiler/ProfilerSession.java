package rogo.sketch.profiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfilerSession {
    private final List<List<ProfilerEvent>> cycles = new ArrayList<>();
    private final long startTime;
    private long endTime;

    public ProfilerSession() {
        this.startTime = System.currentTimeMillis();
    }

    public void addCycle(List<ProfilerEvent> events) {
        cycles.add(new ArrayList<>(events));
    }

    public void finish() {
        this.endTime = System.currentTimeMillis();
    }

    public List<List<ProfilerEvent>> getCycles() {
        return cycles;
    }

    public long getDuration() {
        return endTime - startTime;
    }
}