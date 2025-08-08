package rogo.sketch.util;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class RenderCallTimer {
    private final Map<String, Integer> queryObjects = new HashMap<>(); // OpenGL查询对象
    private final Map<String, Long> totalDurations = new HashMap<>();  // 总耗时
    private final Map<String, Long> callCounts = new HashMap<>();      // 调用次数
    private final Map<String, String> results = new HashMap<>();       // 结果缓存

    public void start(String taskName) {
        int query = queryObjects.computeIfAbsent(taskName, k -> {
            int id = GL15.glGenQueries();
            if (id == 0) {
                throw new RuntimeException("Failed to create OpenGL query object.");
            }
            return id;
        });

        GL33.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
    }

    public void end(String taskName) {
        Integer query = queryObjects.get(taskName);
        if (query == null) {
            throw new IllegalStateException("Task '" + taskName + "' has not been started.");
        }

        GL33.glEndQuery(GL33.GL_TIME_ELAPSED);

        long elapsedTime = GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT);
        totalDurations.put(taskName, totalDurations.getOrDefault(taskName, 0L) + elapsedTime);
        callCounts.put(taskName, callCounts.getOrDefault(taskName, 0L) + 1);
    }

    public void calculateAverageTimes(int frameCount) {
        results.clear();
        DecimalFormat df = new DecimalFormat("#.######");

        for (String taskName : totalDurations.keySet()) {
            long totalDuration = totalDurations.get(taskName);
            long totalCalls = callCounts.getOrDefault(taskName, 0L);
            long averageTimeNs = totalCalls > 0 ? totalDuration / totalCalls : 0;

            double totalDurationMs = totalDuration / 1_000_000.0;
            double averageTimeMs = averageTimeNs / 1_000_000.0;
            double perFrameTimeMs = frameCount > 0 ? (totalDuration / (double) frameCount) / 1_000_000.0 : 0;

            results.put(taskName, " 每帧: " + df.format(perFrameTimeMs) + "ms, 平均: " + df.format(averageTimeMs) + "ms, 总计: " + df.format(totalDurationMs) + "ms");
        }

        totalDurations.clear();
        callCounts.clear();
    }

    public Map<String, String> getResults() {
        return results;
    }

    public void cleanup() {
        for (int query : queryObjects.values()) {
            GL15.glDeleteQueries(query);
        }
        queryObjects.clear();
    }
}
