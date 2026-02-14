package rogo.sketch.profiler;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class Profiler {
    private static final Profiler INSTANCE = new Profiler();
    private ProfilerSession currentSession;
    private long autoStopConfigs; // Duration in ms
    private long recordingStartTime;
    private boolean isRecording;

    // Runtime State
    private long frameStartTime;
    private final java.util.Deque<String> stageStack = new java.util.ArrayDeque<>();
    private final List<ProfilerEvent> currentFrameEvents = new ArrayList<>();

    private String lastSessionJson = "{}";

    public static Profiler get() {
        return INSTANCE;
    }

    // ProfilerEvent is now a standalone class in the same package
    // (rogo.sketch.profiler.ProfilerEvent)
    // No inner class definition needed.

    public void startRecording(double seconds) {
        if (isRecording)
            return;

        // Initialize runtime
        this.frameStartTime = System.nanoTime();
        this.stageStack.clear();
        this.currentFrameEvents.clear();

        // Push root implicit
        this.stageStack.push("root");

        this.currentSession = new ProfilerSession();
        this.autoStopConfigs = (long) (seconds * 1000);
        this.recordingStartTime = System.currentTimeMillis();
        this.isRecording = true;

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player
                    .sendSystemMessage(Component.literal("Profiler started for " + seconds + " seconds."));
        }
    }

    public void stopRecording() {
        if (!isRecording)
            return;
        isRecording = false;
        currentSession.finish();
        processSessionJson();
        ProfilerWebServer.start();

        String url = "http://localhost:25586";
        Component message = Component.literal("Profiler finished. ")
                .append(Component.literal("[Click to View]")
                        .withStyle(style -> style
                                .withColor(net.minecraft.ChatFormatting.GREEN)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, url))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Open Profiler Report")))));

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(message);
        }
    }

    public void open() {
        if (!isRecording)
            return;

        if (System.currentTimeMillis() - recordingStartTime > autoStopConfigs) {
            stopRecording();
            return;
        }

        frameStartTime = System.nanoTime();
        stageStack.clear();
        currentFrameEvents.clear();

        // Start 'root' implicitly
        stageStack.push("root");
        currentFrameEvents.add(new ProfilerEvent(ProfilerEvent.Type.START, "root", frameStartTime));
    }

    public void start(String stageName) {
        if (!isRecording)
            return;
        stageStack.push(stageName);
        currentFrameEvents.add(new ProfilerEvent(ProfilerEvent.Type.START, stageName, System.nanoTime()));
    }

    public void end(String stageName) {
        if (!isRecording)
            return;

        if (stageStack.isEmpty()) {
            reportError("Profiler stack underflow! Attempted to end '" + stageName + "' but stack is empty.");
            return;
        }

        String top = stageStack.peek();

        stageStack.pop();

        if (top.equals(stageName)) {
            currentFrameEvents.add(new ProfilerEvent(ProfilerEvent.Type.END, stageName, System.nanoTime()));
        }
    }

    public void endStart(String stageName) {
        if (!isRecording)
            return;

        // endStart implies switching the 'current linear stage'.
        // It does NOT pop the stack in a traditional sense of closing a scope started
        // by start(),
        // UNLESS the previous stage was also a linear stage...
        // Actually, user said: "endStart means end prev and start new".
        // If we are in "root", and call endStart("Sky"), we end "root"? No.
        // Usually endStart is used for: open -> endStart(A) -> endStart(B) -> close.
        // But here we might be mixed.

        // We will record it as a special LINEAR event.
        // The frontend will interpret LINEAR as: Close current 'Linear' node, Open new
        // 'Linear' node.
        currentFrameEvents.add(new ProfilerEvent(ProfilerEvent.Type.LINEAR, stageName, System.nanoTime()));
    }

    public void close() {
        if (!isRecording)
            return;

        long now = System.nanoTime();

        // Check for unclosed stages (anything other than 'root')
        if (stageStack.size() > 1) { // 1 because 'root' is always there
            StringBuilder stackTrace = new StringBuilder();
            for (String s : stageStack)
                stackTrace.append(s).append(" -> ");
            reportError("Unclosed Profiler Stages detected at end of frame! Stack: " + stackTrace);
        }

        // Close root
        currentFrameEvents.add(new ProfilerEvent(ProfilerEvent.Type.END, "root", now));

        currentSession.addCycle(currentFrameEvents);
    }

    private void reportError(String msg) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c[Profiler Error] " + msg));
        }
    }

    private void processSessionJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"duration\":").append(currentSession.getDuration()).append(",");
        sb.append("\"cycles\":[");

        var cycles = currentSession.getCycles();
        for (int i = 0; i < cycles.size(); i++) {
            List<ProfilerEvent> events = cycles.get(i);
            sb.append("[");
            for (int j = 0; j < events.size(); j++) {
                ProfilerEvent e = events.get(j);
                sb.append("{\"t\":\"").append(e.type).append("\",\"n\":\"").append(e.name).append("\",\"ts\":")
                        .append(e.timestamp).append("}");
                if (j < events.size() - 1)
                    sb.append(",");
            }
            sb.append("]");
            if (i < cycles.size() - 1)
                sb.append(",");
        }
        sb.append("]");
        sb.append("}");
        this.lastSessionJson = sb.toString();
    }

    public String getLastSessionJson() {
        return lastSessionJson;
    }
}
