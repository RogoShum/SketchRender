package rogo.sketch.core.util.transform;

import org.lwjgl.opengl.GL15;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.resource.buffer.ShaderStorageBuffer;
import rogo.sketch.core.transform.Transform;
import rogo.sketch.core.transform.TransformManager;

/**
 * MatrixManager - Refactored GPU Transform System
 * <p>
 * Separates Sync and Async transforms into distinct pipelines to prevent blocking the main thread.
 * Uses a shared ID registry so all results land in a common Output SSBO.
 */
public class MatrixManager implements TransformManager {

    // Shared Resources
    private final SharedIdRegistry idRegistry = new SharedIdRegistry();
    private ShaderStorageBuffer outputSSBO; // Binding 1 (Output)

    // Pipelines
    private final TransformPipeline syncPipeline;
    private final TransformPipeline asyncPipeline;

    // Output Capacity Management
    private static final int OUTPUT_STRIDE = 64; // mat4
    private int currentOutputCapacity = 64;

    public MatrixManager() {
        this.syncPipeline = new TransformPipeline(64);
        this.asyncPipeline = new TransformPipeline(1024); // Async usually has more objects

        this.outputSSBO = new ShaderStorageBuffer(currentOutputCapacity, OUTPUT_STRIDE, GL15.GL_DYNAMIC_DRAW);
    }

    // ================== Interface Implementation ==================

    @Override
    public int registerTransform(Transform transform) {
        if (transform == null) throw new NullPointerException();
        if (transform.getRegisteredId() != -1) return transform.getRegisteredId();

        // 1. Allocate Shared ID
        int id = idRegistry.allocate();
        transform.setRegisteredId(id);

        // 2. Route to correct pipeline
        if (transform.asyncTick()) {
            asyncPipeline.add(transform);
        } else {
            syncPipeline.add(transform);
        }

        return id;
    }

    @Override
    public void unregisterTransform(Transform transform) {
        int id = transform.getRegisteredId();
        if (id == -1) return;

        // 1. Recycle ID
        idRegistry.recycle(id);
        transform.setRegisteredId(-1);

        // 2. Remove from pipeline
        if (transform.asyncTick()) {
            asyncPipeline.remove(transform);
        } else {
            syncPipeline.remove(transform);
        }
    }

    public void onDepthChanged(Transform transform, int oldDepth) {
        if (transform.asyncTick()) {
            asyncPipeline.onDepthChanged(transform, oldDepth);
        } else {
            syncPipeline.onDepthChanged(transform, oldDepth);
        }
    }

    @Override
    public Transform getTransform(int id) {
        // Not implemented: Dual pipeline makes O(1) reverse lookup hard.
        // If needed, MatrixManager must maintain a separate Int2ObjectMap<Transform>.
        // For rendering, this is usually not needed.
        return null;
    }

    @Override
    public boolean isRegistered(Transform transform) {
        return transform.getRegisteredId() != -1;
    }

    @Override
    public void swapTransformData() {
        // Only for sync objects if needed
        // Async objects usually handle double buffering internally or don't need explicit swap
    }

    @Override
    public int getActiveCount() {
        // Just a metric
        return idRegistry.getMaxId(); // Approximation
    }

    // ================== Tick & Render Flow ==================

    /**
     * Main Thread Tick.
     * Processes logic for Sync objects.
     */
    public void syncTick() {
        syncPipeline.processLogic();
    }

    /**
     * Worker Thread Tick.
     * Processes logic for Async objects.
     */
    public void asyncTick() {
        asyncPipeline.processLogic();
    }

    /**
     * Pre-Render Update (Main Thread).
     * Uploads all data and resizes Output buffer if needed.
     */
    public void preRender() {
        // 1. Resize Shared Output SSBO if needed
        int maxId = idRegistry.getMaxId();
        if (maxId > currentOutputCapacity) {
            int newCap = Math.max(maxId, (int) (currentOutputCapacity * 1.5));
            newCap = ((newCap + 63) / 64) * 64;
            outputSSBO.ensureCapacity(newCap, false);
            currentOutputCapacity = newCap;
        }

        // 2. Upload Data (Sync & Async)
        // Note: Async logic must be finished before calling this!
        syncPipeline.upload();
        asyncPipeline.upload();
    }

    // ================== Getters for Compute Shader ==================

    public ResourceObject getOutputSSBO() {
        return outputSSBO;
    }

    public TransformPipeline getSyncPipeline() {
        return syncPipeline;
    }

    public TransformPipeline getAsyncPipeline() {
        return asyncPipeline;
    }

    // ================== Cleanup ==================

    public void cleanup() {
        syncPipeline.cleanup();
        asyncPipeline.cleanup();
        outputSSBO.dispose();
        idRegistry.clear();
    }
}