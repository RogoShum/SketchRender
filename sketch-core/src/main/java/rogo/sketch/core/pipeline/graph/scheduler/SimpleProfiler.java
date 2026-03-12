package rogo.sketch.core.pipeline.graph.scheduler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 纯 Java 实现的性能分析器，不依赖 Minecraft。
 */
public class SimpleProfiler {
    private static final SimpleProfiler INSTANCE = new SimpleProfiler();
    
    // 线程安全的事件队列
    private final Queue<TraceEvent> events = new ConcurrentLinkedQueue<>();
    private volatile boolean recording = false;
    private long startNanoTime;
    
    // 用于自动停止的调度器
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Profiler-Stopper");
        t.setDaemon(true);
        return t;
    });

    public static SimpleProfiler get() { return INSTANCE; }

    /**
     * 开始录制
     * @param seconds 持续时间
     * @param onFinishedCallback 录制完成后的回调（传入生成的 File 对象），用于通知上层应用
     */
    public void startRecording(double seconds, Consumer<File> onFinishedCallback) {
        if (recording) {
            return; // 防止重复启动
        }

        this.events.clear();
        this.startNanoTime = System.nanoTime();
        this.recording = true;

        // 设置定时任务：N秒后停止并导出
        scheduler.schedule(() -> {
            File savedFile = stopAndDump();
            if (onFinishedCallback != null && savedFile != null) {
                onFinishedCallback.accept(savedFile);
            }
        }, (long) (seconds * 1000), TimeUnit.MILLISECONDS);
    }

    public boolean isRecording() {
        return recording;
    }

    // 记录开始 (Begin)
    public void begin(String name, String threadName) {
        if (!recording) return;
        long ts = (System.nanoTime() - startNanoTime) / 1000; // microsecond
        events.add(new TraceEvent(name, "B", ts, threadName));
    }

    // 记录结束 (End)
    public void end(String name, String threadName) {
        if (!recording) return;
        long ts = (System.nanoTime() - startNanoTime) / 1000;
        events.add(new TraceEvent(name, "E", ts, threadName));
    }

    private synchronized File stopAndDump() {
        if (!recording) return null;
        recording = false;

        // 保存到运行目录下的 profiling 文件夹
        File dumpDir = new File("profiling");
        if (!dumpDir.exists()) dumpDir.mkdirs();

        String fileName = "trace_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".json";
        File file = new File(dumpDir, fileName);

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("[\n");
            boolean first = true;
            for (TraceEvent event : events) {
                if (!first) writer.write(",\n");
                writer.write(event.toJson());
                first = false;
            }
            writer.write("\n]");
            return file;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // 内部数据结构
    private static class TraceEvent {
        String name, ph, tid;
        long ts;

        public TraceEvent(String name, String ph, long ts, String tid) {
            this.name = name; this.ph = ph; this.ts = ts; this.tid = tid;
        }

        public String toJson() {
            return String.format(
                    "{\"name\":\"%s\",\"cat\":\"PERF\",\"ph\":\"%s\",\"ts\":%d,\"pid\":1,\"tid\":\"%s\"}",
                    name, ph, ts, tid
            );
        }
    }
}