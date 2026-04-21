package rogo.sketch.core.pipeline.indirect;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.backend.BackendBufferFactory;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.packet.draw.IndirectCommandRange;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns persistent indirect buffers and stable stream slices per render-parameter lane.
 */
public final class PersistentIndirectBufferPool {
    private final Map<RenderParameter, LaneState> lanes = new ConcurrentHashMap<>();
    private final Set<RenderParameter> pendingCreate = ConcurrentHashMap.newKeySet();
    private final int defaultBufferSize;
    private long frameCursor = 0L;

    public PersistentIndirectBufferPool(int defaultBufferSize) {
        this.defaultBufferSize = Math.max(defaultBufferSize, 1);
    }

    @Nullable
    public BackendIndirectBuffer get(RenderParameter renderParameter) {
        LaneState laneState = renderParameter != null ? lanes.get(renderParameter) : null;
        return laneState != null ? laneState.buffer : null;
    }

    public BackendIndirectBuffer getOrCreate(RenderParameter renderParameter) {
        if (renderParameter == null) {
            return null;
        }
        return ensureLane(renderParameter).buffer;
    }

    public void planCreate(RenderParameter renderParameter) {
        if (renderParameter != null && !lanes.containsKey(renderParameter)) {
            pendingCreate.add(renderParameter);
        }
    }

    public int materializePending() {
        int created = 0;
        for (RenderParameter renderParameter : List.copyOf(pendingCreate)) {
            if (renderParameter != null && !lanes.containsKey(renderParameter)) {
                ensureLane(renderParameter);
                created++;
            }
        }
        pendingCreate.clear();
        return created;
    }

    public Map<RenderParameter, BackendIndirectBuffer> buffersView() {
        Map<RenderParameter, BackendIndirectBuffer> view = new LinkedHashMap<>();
        for (Map.Entry<RenderParameter, LaneState> entry : lanes.entrySet()) {
            view.put(entry.getKey(), entry.getValue().buffer);
        }
        return Map.copyOf(view);
    }

    public void beginFrame() {
        frameCursor++;
        for (LaneState laneState : lanes.values()) {
            laneState.beginFrame(frameCursor);
        }
    }

    public void finishFrame() {
        for (LaneState laneState : lanes.values()) {
            laneState.finishFrame();
        }
    }

    public void synchronizeLayoutsFrom(PersistentIndirectBufferPool other) {
        if (other == null || other == this) {
            return;
        }
        Map<RenderParameter, LaneState> otherLanes = other.lanes;
        lanes.keySet().removeIf(renderParameter -> {
            if (otherLanes.containsKey(renderParameter)) {
                return false;
            }
            LaneState removed = lanes.get(renderParameter);
            if (removed != null && removed.buffer != null) {
                removed.buffer.dispose();
            }
            return true;
        });
        for (Map.Entry<RenderParameter, LaneState> entry : otherLanes.entrySet()) {
            RenderParameter renderParameter = entry.getKey();
            LaneState source = entry.getValue();
            LaneState target = ensureLane(renderParameter);
            target.syncFrom(source);
        }
        pendingCreate.addAll(other.pendingCreate);
    }

    public WriteResult writeStream(
            RenderParameter renderParameter,
            IndirectStreamKey streamKey,
            IndirectCommandBatch commandBatch) {
        if (renderParameter == null || streamKey == null || commandBatch == null || commandBatch.isEmpty()) {
            return WriteResult.empty();
        }
        LaneState laneState = ensureLane(renderParameter);
        SliceState sliceState = laneState.streams.get(streamKey);
        boolean reused = false;
        boolean relocated = false;
        int requiredCount = commandBatch.commandCount();

        if (sliceState != null && sliceState.commandCapacity >= requiredCount) {
            reused = true;
        } else {
            if (sliceState != null) {
                laneState.freeSlices.add(sliceState.toPersistentSlice());
                laneState.streams.remove(streamKey);
                relocated = true;
            }
            PersistentIndirectSlice allocated = laneState.allocate(requiredCount);
            sliceState = SliceState.from(allocated, frameCursor);
            laneState.streams.put(streamKey, sliceState);
        }

        sliceState.commandCount = requiredCount;
        sliceState.activeThisFrame = true;
        sliceState.lastUsedFrame = frameCursor;
        laneState.highWaterCommandIndex = Math.max(laneState.highWaterCommandIndex, sliceState.startCommandIndex + sliceState.commandCount);

        commandBatch.writeTo(laneState.buffer, sliceState.toPersistentSlice());
        laneState.buffer.setCommandCount(laneState.highWaterCommandIndex);
        laneState.buffer.setWritePositionBytes((long) laneState.highWaterCommandIndex * laneState.buffer.strideBytes());
        laneState.buffer.uploadRange(
                (long) sliceState.startCommandIndex * laneState.buffer.strideBytes(),
                (long) sliceState.commandCount * laneState.buffer.strideBytes());

        return new WriteResult(
                laneState.buffer,
                new IndirectCommandRange(sliceState.startCommandIndex, sliceState.commandCount),
                reused,
                relocated);
    }

    public IndirectPoolStats stats() {
        int streamCount = 0;
        int freeSliceCount = 0;
        for (LaneState laneState : lanes.values()) {
            streamCount += laneState.streams.size();
            freeSliceCount += laneState.freeSlices.size();
        }
        return new IndirectPoolStats(lanes.size(), streamCount, freeSliceCount);
    }

    public void clear() {
        for (LaneState laneState : lanes.values()) {
            if (laneState.buffer != null) {
                laneState.buffer.clear();
            }
            laneState.streams.clear();
            laneState.freeSlices.clear();
            laneState.highWaterCommandIndex = 0;
            laneState.nextCommandIndex = 0;
        }
    }

    private LaneState ensureLane(RenderParameter renderParameter) {
        return lanes.computeIfAbsent(renderParameter, this::createLane);
    }

    private LaneState createLane(RenderParameter renderParameter) {
        KeyId resourceId = KeyId.of("indirect_" + Integer.toUnsignedLong(renderParameter != null ? renderParameter.hashCode() : 0));
        ResolvedBufferResource descriptor = new ResolvedBufferResource(
                resourceId,
                BufferRole.INDIRECT,
                BufferUpdatePolicy.DYNAMIC,
                defaultBufferSize,
                BackendIndirectBuffer.COMMAND_STRIDE_BYTES,
                (long) defaultBufferSize * BackendIndirectBuffer.COMMAND_STRIDE_BYTES);
        BackendIndirectBuffer buffer = BackendBufferFactory.createIndirectBuffer(resourceId, descriptor, defaultBufferSize);
        buffer.ensureCommandCapacity(defaultBufferSize);
        return new LaneState(renderParameter, buffer);
    }

    public record WriteResult(
            @Nullable BackendIndirectBuffer buffer,
            @Nullable IndirectCommandRange range,
            boolean sliceReused,
            boolean sliceRelocated
    ) {
        static WriteResult empty() {
            return new WriteResult(null, null, false, false);
        }
    }

    private static final class LaneState {
        private final RenderParameter renderParameter;
        private final BackendIndirectBuffer buffer;
        private final Map<IndirectStreamKey, SliceState> streams = new LinkedHashMap<>();
        private final List<PersistentIndirectSlice> freeSlices = new ArrayList<>();
        private int nextCommandIndex = 0;
        private int highWaterCommandIndex = 0;

        private LaneState(RenderParameter renderParameter, BackendIndirectBuffer buffer) {
            this.renderParameter = renderParameter;
            this.buffer = buffer;
        }

        private void beginFrame(long frameCursor) {
            highWaterCommandIndex = 0;
            for (SliceState sliceState : streams.values()) {
                sliceState.activeThisFrame = false;
            }
            buffer.setCommandCount(0);
            buffer.setWritePositionBytes(0L);
        }

        private void finishFrame() {
            List<Map.Entry<IndirectStreamKey, SliceState>> staleEntries = new ArrayList<>();
            for (Map.Entry<IndirectStreamKey, SliceState> entry : streams.entrySet()) {
                if (!entry.getValue().activeThisFrame) {
                    staleEntries.add(entry);
                } else {
                    highWaterCommandIndex = Math.max(highWaterCommandIndex, entry.getValue().startCommandIndex + entry.getValue().commandCount);
                }
            }
            for (Map.Entry<IndirectStreamKey, SliceState> staleEntry : staleEntries) {
                freeSlices.add(staleEntry.getValue().toPersistentSlice());
                streams.remove(staleEntry.getKey());
            }
            nextCommandIndex = Math.max(nextCommandIndex, highWaterCommandIndex);
            buffer.setCommandCount(highWaterCommandIndex);
            buffer.setWritePositionBytes((long) highWaterCommandIndex * buffer.strideBytes());
        }

        private PersistentIndirectSlice allocate(int requiredCommandCount) {
            freeSlices.sort(Comparator.comparingInt(PersistentIndirectSlice::commandCapacity));
            for (int i = 0; i < freeSlices.size(); i++) {
                PersistentIndirectSlice freeSlice = freeSlices.get(i);
                if (freeSlice.commandCapacity() >= requiredCommandCount) {
                    freeSlices.remove(i);
                    return new PersistentIndirectSlice(
                            freeSlice.startCommandIndex(),
                            freeSlice.commandCapacity(),
                            requiredCommandCount);
                }
            }
            int startIndex = nextCommandIndex;
            int endIndex = startIndex + requiredCommandCount;
            buffer.ensureCommandCapacity(endIndex);
            nextCommandIndex = endIndex;
            return new PersistentIndirectSlice(startIndex, requiredCommandCount, requiredCommandCount);
        }

        private void syncFrom(LaneState source) {
            streams.clear();
            freeSlices.clear();
            nextCommandIndex = 0;
            highWaterCommandIndex = 0;

            int requiredCommandCapacity = 0;
            for (SliceState sliceState : source.streams.values()) {
                requiredCommandCapacity = Math.max(requiredCommandCapacity, sliceState.startCommandIndex + sliceState.commandCapacity);
            }
            buffer.ensureCommandCapacity(Math.max(requiredCommandCapacity, 1));

            for (Map.Entry<IndirectStreamKey, SliceState> entry : source.streams.entrySet()) {
                SliceState sourceSlice = entry.getValue();
                streams.put(entry.getKey(), new SliceState(
                        sourceSlice.startCommandIndex,
                        sourceSlice.commandCapacity,
                        sourceSlice.commandCount,
                        false,
                        sourceSlice.lastUsedFrame));
                nextCommandIndex = Math.max(nextCommandIndex, sourceSlice.startCommandIndex + sourceSlice.commandCapacity);
                highWaterCommandIndex = Math.max(highWaterCommandIndex, sourceSlice.startCommandIndex + sourceSlice.commandCount);
            }
            buffer.setCommandCount(highWaterCommandIndex);
            buffer.setWritePositionBytes((long) highWaterCommandIndex * buffer.strideBytes());
        }
    }

    private static final class SliceState {
        private final int startCommandIndex;
        private final int commandCapacity;
        private int commandCount;
        private boolean activeThisFrame;
        private long lastUsedFrame;

        private SliceState(
                int startCommandIndex,
                int commandCapacity,
                int commandCount,
                boolean activeThisFrame,
                long lastUsedFrame) {
            this.startCommandIndex = startCommandIndex;
            this.commandCapacity = commandCapacity;
            this.commandCount = commandCount;
            this.activeThisFrame = activeThisFrame;
            this.lastUsedFrame = lastUsedFrame;
        }

        private static SliceState from(PersistentIndirectSlice slice, long frameCursor) {
            return new SliceState(
                    slice.startCommandIndex(),
                    slice.commandCapacity(),
                    slice.commandCount(),
                    true,
                    frameCursor);
        }

        private PersistentIndirectSlice toPersistentSlice() {
            return new PersistentIndirectSlice(startCommandIndex, commandCapacity, commandCount);
        }
    }
}
