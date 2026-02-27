package rogo.sketch.profiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Profiler {
    private static final Profiler INSTANCE = new Profiler();

    // 配置与状态
    private boolean isRecording = false;
    private long recordingStartTime;
    private long autoStopMs;

    // 运行时数据 (当前帧)
    private long frameStartNano;
    // 正在运行的片段：Name -> Segment
    private final Map<String, Segment> activeSegments = new ConcurrentHashMap<>();
    // 当前帧已完成的片段列表
    private final List<Segment> finishedSegments = new ArrayList<>();
    // 上一次操作的片段 (用于 popPush 的衔接逻辑)
    private Segment lastTouchedSegment = null;

    // 统计数据 (所有帧累加)
    // Key = 路径 (e.g. "Root/GameLoop/Render")，用于区分不同层级的同名方法
    private final Map<String, StatData> globalStats = new ConcurrentHashMap<>();

    // 输出缓存
    private String lastSessionJson = "{}";
    private FrameNode lastFrameRoot = null; // 用于可视化的最后一帧树

    public static Profiler get() { return INSTANCE; }

    // ==================================================
    //  控制方法
    // ==================================================

    public void startRecording(double seconds) {
        if (isRecording) return;
        clear();
        this.autoStopMs = (long) (seconds * 1000);
        this.recordingStartTime = System.currentTimeMillis();
        this.isRecording = true;

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Profiler started (" + seconds + "s)"));
        }
    }

    public void stopRecording() {
        if (!isRecording) return;
        isRecording = false;

        // 生成最终 JSON
        generateJson();
        ProfilerWebServer.start();

        // 发送链接
        String url = "http://localhost:25586";
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Profiler finished. ")
                    .append(Component.literal("[Click View Report]")
                            .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, url)))));
        }
    }

    private void clear() {
        activeSegments.clear();
        finishedSegments.clear();
        globalStats.clear();
        lastTouchedSegment = null;
        lastFrameRoot = null;
    }

    // ==================================================
    //  核心埋点 API
    // ==================================================

    // 每一帧开始时调用
    public void open() {
        if (!isRecording) return;
        if (System.currentTimeMillis() - recordingStartTime > autoStopMs) {
            stopRecording();
            return;
        }

        // 重置帧数据
        activeSegments.clear();
        finishedSegments.clear();
        lastTouchedSegment = null;
        frameStartNano = System.nanoTime();

        // 隐式启动 Root
        push("Root");
    }

    // 每一帧结束时调用
    public void close() {
        if (!isRecording) return;

        long frameEndNano = System.nanoTime();

        // 1. 强制关闭所有未关闭的片段 (容错)
        for (Segment seg : activeSegments.values()) {
            seg.end(frameEndNano);
            finishedSegments.add(seg);
        }

        // 2. 构建树并统计
        processFrame(frameEndNano);
    }

    public void push(String name) {
        if (!isRecording) return;

        Segment seg = new Segment(name, System.nanoTime());
        activeSegments.put(name, seg);
        lastTouchedSegment = seg;
    }

    public void pop(String name) {
        if (!isRecording) return;

        Segment seg = activeSegments.remove(name);
        if (seg != null) {
            seg.end(System.nanoTime());
            finishedSegments.add(seg);
            lastTouchedSegment = seg;
        }
    }

    /**
     * 智能衔接：
     * 如果上一个操作的片段还在运行，将其 pop。
     * 然后 push 新的。
     */
    public void popPush(String name) {
        if (!isRecording) return;

        // 逻辑：如果 lastTouched 还是 Active 状态，说明它是我们要“衔接”的前驱
        if (lastTouchedSegment != null && activeSegments.containsKey(lastTouchedSegment.name)) {
            pop(lastTouchedSegment.name);
        }

        push(name);
    }

    // ==================================================
    //  数据处理逻辑 (在 Mod 侧计算)
    // ==================================================

    private void processFrame(long frameEndNano) {
        // 1. 排序：这是正确推断关系的关键
        // 规则 A: 开始时间早的在前
        // 规则 B: 如果开始时间相同，持续时间长的在前 (确保容器先被处理，从而包裹住短的)
        finishedSegments.sort((a, b) -> {
            int startComp = Long.compare(a.startTime, b.startTime);
            if (startComp != 0) return startComp;
            return Long.compare(b.endTime, a.endTime); // 持续时间降序 (end大 - start小 = 时长)
        });

        // 2. 构建逻辑树 (Logical Containment Tree)
        // 根节点覆盖全帧
        FrameNode root = new FrameNode("Root", frameStartNano, frameEndNano);

        for (Segment seg : finishedSegments) {
            if (seg.name.equals("Root")) continue;
            // 将每个片段递归插入到最合适的父节点下
            insertNodeRecursive(root, new FrameNode(seg.name, seg.startTime, seg.endTime));
        }

        this.lastFrameRoot = root;

        // 3. 统计数据 (递归)
        updateStatsRecursive(root, "Root");
    }

    /**
     * 递归插入节点
     * 核心逻辑：只有当 parent "严格包含" child 时，才将其作为子节点。
     * 否则（即使有重叠但不包含），视为 parent 的兄弟节点（由上一层递归处理）。
     */
    private void insertNodeRecursive(FrameNode parent, FrameNode newNode) {
        // 尝试放入 parent 的现有的子节点中（看是否属于某个子节点的更深层级）
        // 我们只需要检查最后一个子节点，因为数据是按时间排序的，
        // 如果属于子节点，必然属于时间轴上最近的那个。
        // 但为了处理并行情况（多个兄弟同时运行），我们需要遍历所有可能的“活跃”兄弟？
        // 不，由于排序保证了包含者先入，我们只需要从后往前找，或者遍历所有子节点。
        // 考虑到性能和逻辑，遍历当前所有子节点寻找容器是准确的。

        for (FrameNode child : parent.children) {
            if (isContained(child, newNode)) {
                insertNodeRecursive(child, newNode);
                return; // 找到了更深层的父节点，插入后退出
            }
        }

        // 如果没有子节点能包含它（比如它是与现有子节点并行的，或者是衔接在后面的）
        // 直接作为当前 parent 的子节点（即成为其他子节点的兄弟）
        parent.addChild(newNode);
    }

    // 判断 container 是否包含 item
    private boolean isContained(FrameNode container, FrameNode item) {
        return container.start <= item.start && container.end >= item.end;
    }

    private void updateStatsRecursive(FrameNode node, String path) {
        long duration = node.end - node.start;

        // 更新统计
        globalStats.computeIfAbsent(path, k -> new StatData()).add(duration);

        for (FrameNode child : node.children) {
            updateStatsRecursive(child, path + "/" + child.name);
        }
    }

    private void generateJson() {
        Gson gson = new GsonBuilder().create();
        Map<String, Object> output = new HashMap<>();

        // 1. Stats 数据转换 (纳秒转毫秒，方便阅读，但也可以保留纳秒)
        Map<String, Object> statsOut = new HashMap<>();
        globalStats.forEach((path, data) -> {
            Map<String, Object> d = new HashMap<>();
            d.put("min", data.min);
            d.put("max", data.max);
            d.put("avg", data.getAvg());
            d.put("total", data.total);
            d.put("count", data.count);
            statsOut.put(path, d);
        });

        output.put("stats", statsOut);

        // 2. 最后一帧的树结构
        output.put("lastFrame", this.lastFrameRoot);

        this.lastSessionJson = gson.toJson(output);
    }

    public String getLastSessionJson() {
        return lastSessionJson;
    }

    // ==================================================
    //  内部数据类
    // ==================================================

    // 运行时临时片段
    private static class Segment {
        String name;
        long startTime;
        long endTime = -1;

        Segment(String name, long start) { this.name = name; this.startTime = start; }
        void end(long time) { this.endTime = time; }
    }

    // 用于生成 JSON 树的节点
    private static class FrameNode {
        String name;
        long start; // Nano relative to frame start (optional, or absolute)
        long end;
        List<FrameNode> children = new ArrayList<>();

        FrameNode(String name, long s, long e) {
            this.name = name;
            // 为了前端方便，这里存相对 Root 的偏移量
            this.start = s;
            this.end = e;
        }

        void addChild(FrameNode child) {
            children.add(child);
        }
    }

    // 统计累加器
    private static class StatData {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long total = 0;
        long count = 0;

        void add(long val) {
            if (val < min) min = val;
            if (val > max) max = val;
            total += val;
            count++;
        }

        double getAvg() {
            return count == 0 ? 0 : total / (double) count;
        }
    }
}