package rogo.sketchrender.util;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class RenderDrawTimer {
    private final Map<String, Long> totalDurations = new HashMap<>();
    private final Map<String, Long> callCounts = new HashMap<>();
    private final Map<String, Long> startTimes = new HashMap<>();
    private Map<String, String> results = new HashMap<>();

    public void start(String taskName) {
        startTimes.put(taskName, System.nanoTime());
    }

    public void end(String taskName) {
        Long startTime = startTimes.remove(taskName);
        if (startTime != null) {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;

            totalDurations.put(taskName, totalDurations.getOrDefault(taskName, 0L) + duration);
            callCounts.put(taskName, callCounts.getOrDefault(taskName, 0L) + 1);
        }
    }

    public void calculateAverageTimes(int frameCount) {
        results = new HashMap<>();
        DecimalFormat df = new DecimalFormat("#.############");

        for (String taskName : totalDurations.keySet()) {
            long totalDuration = totalDurations.get(taskName);
            long totalCalls = callCounts.getOrDefault(taskName, 0L);
            long averageTimeNs = totalCalls > 0 ? totalDuration / totalCalls : 0;

            double totalDurationTimeMs = totalDuration / 1_000_000.0;
            double averageTimeMs = averageTimeNs / 1_000_000.0;
            double perFrameTimeMs = frameCount > 0 ? (totalDuration / (double) frameCount) / 1_000_000.0 : 0;

            results.put(taskName, "Total: " + df.format(totalDurationTimeMs) + "ms, Average: " + df.format(averageTimeMs) + "ms, Per Frame: " + df.format(perFrameTimeMs) + "ms");
        }

        totalDurations.clear();
        callCounts.clear();
    }

    public Map<String, String> getResults() {
        return results;
    }
}
