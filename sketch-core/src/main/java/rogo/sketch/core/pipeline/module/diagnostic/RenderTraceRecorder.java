package rogo.sketch.core.pipeline.module.diagnostic;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.util.KeyId;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates trace events for selected graphics and writes one compact summary
 * line per frame/stage/graphics into {@link SketchDiagnostics}.
 */
public final class RenderTraceRecorder {
    private static final String MODULE_ID = "render-trace";

    private final RenderTraceConfig config;
    private final Map<TraceKey, TraceState> traces = new LinkedHashMap<>();
    private long activeFrame = -1L;

    public RenderTraceRecorder(RenderTraceConfig config) {
        this.config = config;
    }

    public synchronized void beginFrame(long frameNumber) {
        if (activeFrame >= 0L && frameNumber != activeFrame) {
            flushBefore(frameNumber);
        }
        activeFrame = frameNumber;
    }

    public synchronized void flushAll() {
        flushBefore(Long.MAX_VALUE);
        activeFrame = -1L;
    }

    public synchronized long activeFrame() {
        return activeFrame;
    }

    public void recordPrepare(
            KeyId stageId,
            Graphics graphics,
            boolean descriptorDirty,
            boolean geometryDirty,
            boolean boundsDirty) {
        TraceState trace = trace(stageId, graphics);
        if (trace == null) {
            return;
        }
        trace.prepared = true;
        trace.prepareDetail = "prepare(desc=" + dirtyToken(descriptorDirty)
                + ",geom=" + dirtyToken(geometryDirty)
                + ",bounds=" + dirtyToken(boundsDirty) + ")";
    }

    public void recordVisible(KeyId stageId, Graphics graphics) {
        TraceState trace = trace(stageId, graphics);
        if (trace != null) {
            trace.visible = true;
        }
    }

    public void recordPacketBuilt(KeyId stageId, Graphics graphics, PipelineStateKey stateKey) {
        TraceState trace = trace(stageId, graphics);
        if (trace == null) {
            return;
        }
        trace.packetBuilt = true;
        if (stateKey != null) {
            trace.lastShaderId = stateKey.shaderId();
        }
    }

    public void recordStagePlanned(KeyId stageId, Graphics graphics) {
        TraceState trace = trace(stageId, graphics);
        if (trace != null) {
            trace.stagePlanned = true;
        }
    }

    public void recordQueueInstalled(KeyId stageId, Graphics graphics) {
        TraceState trace = trace(stageId, graphics);
        if (trace != null) {
            trace.queueInstalled = true;
        }
    }

    public void recordBackendExecuted(KeyId stageId, Graphics graphics) {
        TraceState trace = trace(stageId, graphics);
        if (trace != null) {
            trace.backendExecuted = true;
            trace.dropReason = null;
        }
    }

    public void recordDrop(KeyId stageId, Graphics graphics, String reason) {
        TraceState trace = trace(stageId, graphics);
        if (trace == null) {
            return;
        }
        if (trace.dropReason == null || trace.dropReason.isBlank()) {
            trace.dropReason = reason;
        } else if (reason != null && !reason.isBlank() && !trace.dropReason.contains(reason)) {
            trace.dropReason = trace.dropReason + "|" + reason;
        }
    }

    private synchronized TraceState trace(KeyId stageId, Graphics graphics) {
        if (!config.matches(graphics)) {
            return null;
        }
        if (activeFrame < 0L) {
            activeFrame = 0L;
        }
        TraceKey key = new TraceKey(
                activeFrame,
                stageId != null ? stageId : KeyId.of("sketch:unknown_stage"),
                graphics.getIdentifier(),
                graphics.getClass().getName());
        return traces.computeIfAbsent(key, ignored -> new TraceState());
    }

    private void flushBefore(long nextFrame) {
        Iterator<Map.Entry<TraceKey, TraceState>> iterator = traces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TraceKey, TraceState> entry = iterator.next();
            if (entry.getKey().frameNumber >= nextFrame) {
                continue;
            }
            SketchDiagnostics.get().debug(MODULE_ID, summary(entry.getKey(), entry.getValue()));
            iterator.remove();
        }
    }

    private String summary(TraceKey key, TraceState state) {
        StringBuilder builder = new StringBuilder();
        builder.append("frame=").append(key.frameNumber)
                .append(" stage=").append(key.stageId)
                .append(" graphics=").append(key.graphicsId)
                .append(" class=").append(key.graphicsClass);
        if (state.prepareDetail != null && !state.prepareDetail.isBlank()) {
            builder.append(" ").append(state.prepareDetail);
        }
        if (state.visible) {
            builder.append(" visible");
        }
        if (state.packetBuilt) {
            builder.append(" packet");
        }
        if (state.stagePlanned) {
            builder.append(" stagePlan");
        }
        if (state.queueInstalled) {
            builder.append(" queued");
        }
        if (state.backendExecuted) {
            builder.append(" executed");
        }
        if (state.lastShaderId != null) {
            builder.append(" shader=").append(state.lastShaderId);
        }
        if (state.dropReason != null && !state.dropReason.isBlank()) {
            builder.append(" drop=").append(state.dropReason);
        }
        return builder.toString();
    }

    private String dirtyToken(boolean dirty) {
        return dirty ? "dirty" : "clean";
    }

    private record TraceKey(
            long frameNumber,
            KeyId stageId,
            KeyId graphicsId,
            String graphicsClass
    ) {
    }

    private static final class TraceState {
        private boolean prepared;
        private boolean visible;
        private boolean packetBuilt;
        private boolean stagePlanned;
        private boolean queueInstalled;
        private boolean backendExecuted;
        private String prepareDetail;
        private String dropReason;
        private KeyId lastShaderId;
    }
}
