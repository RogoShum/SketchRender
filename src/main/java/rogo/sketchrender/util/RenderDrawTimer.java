package rogo.sketchrender.util;

import java.util.HashMap;
import java.util.Map;

public class RenderDrawTimer {
    private final Map<String, Long> callTimes = new HashMap<>();
    private final Map<String, Long> callCounts = new HashMap<>();
    private long startTime;
    private String func = "";
    private Map<String, String> results = new HashMap<>();

    public void start(String functionName) {
        func = functionName;
        startTime =  System.nanoTime();
    }

    public void end() {
        long endTime = System.nanoTime();
        long duration = endTime - startTime;

        callTimes.put(func, callTimes.getOrDefault(func, 0L) + duration);
        callCounts.put(func, callCounts.getOrDefault(func, 0L) + 1);
    }

    public void calculateAverageTimes() {
        results = new HashMap<>();
        for (String functionName : callTimes.keySet()) {
            long totalDuration = callTimes.get(functionName);
            long totalCalls = callCounts.get(functionName);
            long averageTimeNs = totalCalls > 0 ? totalDuration / totalCalls : 0;
            double averageTimeMs = averageTimeNs / 1_000_000.0;
            results.put(functionName, averageTimeMs + "ms");
        }

        callTimes.clear();
        callCounts.clear();
    }

    public Map<String, String> getResults() {
        return results;
    }
}