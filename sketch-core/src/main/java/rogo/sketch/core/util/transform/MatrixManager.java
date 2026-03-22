package rogo.sketch.core.util.transform;

import org.lwjgl.opengl.GL15;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.graphics.AsyncTickTransformSource;
import rogo.sketch.core.api.graphics.FrameTransformSource;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.StaticTransformSource;
import rogo.sketch.core.api.graphics.SyncTickTransformSource;
import rogo.sketch.core.api.graphics.TransformParentSource;
import rogo.sketch.core.resource.buffer.ShaderStorageBuffer;
import rogo.sketch.core.transform.TransformData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mixed tick/frame transform manager.
 * <p>
 * Tick lifecycle owns interpolation timing through previous/current/write buffers.
 * Frame lifecycle may contribute additional {@link TransformUpdateDomain#SYNC_FRAME}
 * samples before the final SSBO upload.
 */
public class MatrixManager {
    private static final int OUTPUT_STRIDE = 64;

    private final SharedIdRegistry idRegistry = new SharedIdRegistry();
    private final ShaderStorageBuffer outputSSBO;
    private final Map<Integer, TransformBinding> bindingsById = new HashMap<>();
    private final IdentityHashMap<Graphics, TransformBinding> bindingsByGraphics = new IdentityHashMap<>();
    private final TransformPipeline syncPipeline;
    private final TransformPipeline asyncPipeline;

    private int currentOutputCapacity = 64;

    public MatrixManager() {
        this.syncPipeline = new TransformPipeline(64);
        this.asyncPipeline = new TransformPipeline(1024);
        this.outputSSBO = new ShaderStorageBuffer(currentOutputCapacity, OUTPUT_STRIDE, GL15.GL_DYNAMIC_DRAW);
    }

    public TransformBinding registerBinding(Graphics graphics, TransformUpdateDomain updateDomain) {
        if (graphics == null) {
            throw new NullPointerException();
        }

        TransformBinding existing = bindingsByGraphics.get(graphics);
        if (existing != null) {
            return existing;
        }

        int id = idRegistry.allocate();
        TransformBinding binding = new TransformBinding(
                graphics,
                id,
                updateDomain,
                graphics instanceof TransformParentSource parentSource ? parentSource : null);

        bindingsById.put(id, binding);
        bindingsByGraphics.put(graphics, binding);

        if (updateDomain == TransformUpdateDomain.STATIC && graphics instanceof StaticTransformSource staticSource) {
            TransformData initial = new TransformData();
            initial.reset();
            staticSource.writeStaticTransform(initial);
            binding.seedAllTickBuffers(initial);
        }

        if (updateDomain == TransformUpdateDomain.SYNC_FRAME) {
            binding.frameData().reset();
        }

        if (updateDomain == TransformUpdateDomain.ASYNC_TICK) {
            asyncPipeline.add(binding);
        } else {
            syncPipeline.add(binding);
        }

        return binding;
    }

    public void unregisterBinding(TransformBinding binding) {
        if (binding == null) {
            return;
        }

        bindingsById.remove(binding.transformId());
        bindingsByGraphics.remove(binding.graphics());
        idRegistry.recycle(binding.transformId());

        if (binding.updateDomain() == TransformUpdateDomain.ASYNC_TICK) {
            asyncPipeline.remove(binding);
        } else {
            syncPipeline.remove(binding);
        }
    }

    public TransformBinding bindingFor(Graphics graphics) {
        return bindingsByGraphics.get(graphics);
    }

    public boolean isRegistered(Graphics graphics) {
        return bindingsByGraphics.containsKey(graphics);
    }

    public TransformBinding bindingById(int id) {
        return bindingsById.get(id);
    }

    public int getActiveCount() {
        return bindingsById.size();
    }

    /**
     * Prepare CPU-side interpolation builders for the upcoming render frames.
     * Called at tick start after previous async transform collection is finished.
     */
    public void prepareTickBuffers() {
        resolveAllHierarchy();
        ensureOutputCapacity();
        syncPipeline.prepareStructureBuffers();
        asyncPipeline.prepareStructureBuffers();
        syncPipeline.prepareInterpolationData(false);
        asyncPipeline.prepareInterpolationData(false);
    }

    /**
     * Rotate tick buffers after interpolation builders have been prepared.
     * After rotation the previously pending write buffer becomes the new current tick state.
     */
    public void swapTickBuffers() {
        for (TransformBinding binding : bindingsById.values()) {
            if (binding.updateDomain() != TransformUpdateDomain.SYNC_FRAME) {
                binding.swapTickBuffers();
            }
        }
    }

    /**
     * Collect main-thread transform updates after the game tick has updated entity state.
     */
    public void collectSyncTickTransforms() {
        for (TransformBinding binding : bindingsById.values()) {
            if (binding.updateDomain() == TransformUpdateDomain.SYNC_TICK &&
                    binding.graphics() instanceof SyncTickTransformSource source) {
                source.writeSyncTickTransform(binding.pendingTickData());
            }
        }
    }

    /**
     * Collect worker-thread transform updates in the remaining time before the next tick.
     */
    public void collectAsyncTickTransforms() {
        for (TransformBinding binding : bindingsById.values()) {
            if (binding.updateDomain() == TransformUpdateDomain.ASYNC_TICK &&
                    binding.graphics() instanceof AsyncTickTransformSource source) {
                source.writeAsyncTickTransform(binding.pendingTickData());
            }
        }
    }

    /**
     * Collect frame-authored transforms after SyncPreparePass.
     */
    public void collectFrameTransforms() {
        for (TransformBinding binding : bindingsById.values()) {
            if (binding.updateDomain() == TransformUpdateDomain.SYNC_FRAME && binding.graphics() instanceof FrameTransformSource source) {
                source.writeFrameTransform(binding.frameData());
            }
        }
    }

    public void prepareFrameBuffer() {
        syncPipeline.prepareInterpolationData(true);
    }

    /**
     * Upload the prepared transform builders after both tick-owned and frame-owned
     * CPU buffers are up to date.
     */
    public void uploadFrameBuffers() {
        syncPipeline.upload();
        asyncPipeline.upload();
    }

    public ResourceObject getOutputSSBO() {
        return outputSSBO;
    }

    public TransformPipeline getSyncPipeline() {
        return syncPipeline;
    }

    public TransformPipeline getAsyncPipeline() {
        return asyncPipeline;
    }

    private void ensureOutputCapacity() {
        int maxId = idRegistry.getMaxId();
        if (maxId > currentOutputCapacity) {
            int newCap = Math.max(maxId, (int) (currentOutputCapacity * 1.5));
            newCap = ((newCap + 63) / 64) * 64;
            outputSSBO.ensureCapacity(newCap, false);
            currentOutputCapacity = newCap;
        }
    }

    private void resolveAllHierarchy() {
        for (TransformBinding binding : bindingsById.values()) {
            resolveParentAndDepth(binding, new HashSet<>());
        }
    }

    private int resolveParentAndDepth(TransformBinding binding, Set<Integer> visited) {
        if (!visited.add(binding.transformId())) {
            int oldDepth = binding.depth();
            binding.setParentTransformId(-1);
            binding.setDepth(0);
            updatePipelineDepth(binding, oldDepth);
            return binding.depth();
        }

        Graphics parentGraphics = binding.parentSource() != null ? binding.parentSource().getTransformParent() : null;
        if (parentGraphics == null) {
            int oldDepth = binding.depth();
            binding.setParentTransformId(-1);
            binding.setDepth(0);
            updatePipelineDepth(binding, oldDepth);
            return 0;
        }

        TransformBinding parentBinding = bindingsByGraphics.get(parentGraphics);
        if (parentBinding == null) {
            int oldDepth = binding.depth();
            binding.setParentTransformId(-1);
            binding.setDepth(0);
            updatePipelineDepth(binding, oldDepth);
            return 0;
        }

        int parentDepth = resolveParentAndDepth(parentBinding, visited);
        binding.setParentTransformId(parentBinding.transformId());
        int oldDepth = binding.depth();
        binding.setDepth(parentDepth + 1);
        updatePipelineDepth(binding, oldDepth);
        return binding.depth();
    }

    private void updatePipelineDepth(TransformBinding binding, int oldDepth) {
        TransformPipeline targetPipeline =
                binding.updateDomain() == TransformUpdateDomain.ASYNC_TICK ? asyncPipeline : syncPipeline;
        if (oldDepth != binding.depth()) {
            targetPipeline.onDepthChanged(binding, oldDepth);
        }
    }

    public void cleanup() {
        bindingsById.clear();
        bindingsByGraphics.clear();
        syncPipeline.cleanup();
        asyncPipeline.cleanup();
        outputSSBO.dispose();
        idRegistry.clear();
    }
}