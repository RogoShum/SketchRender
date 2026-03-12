package rogo.sketch.core.pipeline.graph.scheduler;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SimpleTracer {
    private static final ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
    private static final long START_TIME = System.nanoTime();

    // 记录事件开始 (Ph: B)
    public static void begin(String name, String threadName) {
        long time = (System.nanoTime() - START_TIME) / 1000; // microsecond
        events.add(String.format("{\"name\":\"%s\",\"cat\":\"PERF\",\"ph\":\"B\",\"pid\":1,\"tid\":\"%s\",\"ts\":%d}",
            name, threadName, time));
    }

    // 记录事件结束 (Ph: E)
    public static void end(String name, String threadName) {
        long time = (System.nanoTime() - START_TIME) / 1000;
        events.add(String.format("{\"name\":\"%s\",\"cat\":\"PERF\",\"ph\":\"E\",\"pid\":1,\"tid\":\"%s\",\"ts\":%d}",
            name, threadName, time));
    }

    // 导出到文件 (在游戏关闭或特定时刻调用)
    public static void dump(String path) {
        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            out.println("[");
            boolean first = true;
            for (String event : events) {
                if (!first) out.println(",");
                out.print(event);
                first = false;
            }
            out.println("]");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}