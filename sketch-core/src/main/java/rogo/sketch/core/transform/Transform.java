package rogo.sketch.core.transform;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import rogo.sketch.core.data.builder.UnsafeBatchBuilder;
import rogo.sketch.core.data.builder.UnsafeHelper;
import rogo.sketch.core.util.TripleBuffer;
import sun.misc.Unsafe;

/**
 * Transform component for GPU-driven rendering.
 * Stores local transform data with dirty tracking and supports hierarchical transforms.
 * Uses triple-buffering for thread-safe async updates.
 * <p>
 * Rotation uses Euler angles in radians with ZYX convention (Roll-Yaw-Pitch).
 *
 * <p>Memory layout for SSBO (std430, 128 bytes per instance):
 * <pre>
 * Offset  Size   Field
 * 0       16     prevPos (vec4, w = padding)
 * 16      16     prevRot (vec4, xyz = euler angles, w = padding)
 * 32      16     prevScale (vec4, w = padding)
 * 48      16     currPos (vec4, w = padding)
 * 64      16     currRot (vec4, xyz = euler angles, w = padding)
 * 80      16     currScale (vec4, w = padding)
 * 96      16     pivot (vec4, w = flags)
 * 112     4      parentID (int)
 * 116     12     padding (int[3])
 * </pre>
 */
public class Transform {
    public static final int SSBO_STRIDE = 128; // bytes per instance

    // Triple buffer for thread-safe async updates
    private final TripleBuffer<TransformData> tripleBuffer = new TripleBuffer<>(TransformData::new);
    private final boolean asyncTick;
    // Hierarchy
    private Transform parent;
    private int registeredId = -1; // ID assigned by MatrixManager
    private int depth = 0; // Depth in hierarchy (0 = root)

    // Dirty tracking
    private boolean isDirty = true;

    // Cached world matrix for CPU-side calculations (e.g., physics)
    private final Matrix4f cachedWorldMatrix = new Matrix4f();
    private final Matrix4f cachedLocalMatrix = new Matrix4f();
    private boolean worldMatrixDirty = true;

    // Temporary matrices for calculations
    private static final ThreadLocal<Matrix4f> TEMP_MATRIX = ThreadLocal.withInitial(Matrix4f::new);
    private static final ThreadLocal<Vector3f> TEMP_VEC = ThreadLocal.withInitial(Vector3f::new);

    public Transform(boolean asyncTick) {
        this.asyncTick = asyncTick;
    }

    /**
     * Set the local position.
     */
    public Transform setLocalPosition(float x, float y, float z) {
        tripleBuffer.getWrite().setPosition(x, y, z);
        markDirty();
        return this;
    }

    /**
     * Set the local position.
     */
    public Transform setLocalPosition(Vector3fc pos) {
        tripleBuffer.getWrite().setPosition(pos.x(), pos.y(), pos.z());
        markDirty();
        return this;
    }

    /**
     * Set the local rotation from Euler angles (in radians).
     * Uses ZYX convention (Roll-Yaw-Pitch).
     *
     * @param pitch Rotation around X axis (radians)
     * @param yaw   Rotation around Y axis (radians)
     * @param roll  Rotation around Z axis (radians)
     */
    public Transform setLocalRotation(float pitch, float yaw, float roll) {
        tripleBuffer.getWrite().setRotation(pitch, yaw, roll);
        markDirty();
        return this;
    }

    /**
     * Set the local rotation from Euler angles vector.
     *
     * @param rot Vector containing (pitch, yaw, roll) in radians
     */
    public Transform setLocalRotation(Vector3fc rot) {
        tripleBuffer.getWrite().setRotation(rot.x(), rot.y(), rot.z());
        markDirty();
        return this;
    }

    /**
     * Set the local rotation from degrees.
     *
     * @param pitchDeg Rotation around X axis (degrees)
     * @param yawDeg   Rotation around Y axis (degrees)
     * @param rollDeg  Rotation around Z axis (degrees)
     */
    public Transform setLocalRotationDegrees(float pitchDeg, float yawDeg, float rollDeg) {
        tripleBuffer.getWrite().setRotationDegrees(pitchDeg, yawDeg, rollDeg);
        markDirty();
        return this;
    }

    /**
     * Set the local scale.
     */
    public Transform setLocalScale(float x, float y, float z) {
        tripleBuffer.getWrite().setScale(x, y, z);
        markDirty();
        return this;
    }

    /**
     * Set the local scale uniformly.
     */
    public Transform setLocalScale(float scale) {
        tripleBuffer.getWrite().setScale(scale);
        markDirty();
        return this;
    }

    /**
     * Set the local scale.
     */
    public Transform setLocalScale(Vector3fc scale) {
        tripleBuffer.getWrite().setScale(scale.x(), scale.y(), scale.z());
        markDirty();
        return this;
    }

    /**
     * Set the pivot point for rotation.
     */
    public Transform setPivot(float x, float y, float z) {
        tripleBuffer.getWrite().setPivot(x, y, z);
        markDirty();
        return this;
    }

    /**
     * Set the pivot point for rotation.
     */
    public Transform setPivot(Vector3fc pivot) {
        tripleBuffer.getWrite().setPivot(pivot.x(), pivot.y(), pivot.z());
        markDirty();
        return this;
    }

    /**
     * Set all local transform components at once.
     *
     * @param pos   Position
     * @param rot   Euler angles (pitch, yaw, roll) in radians
     * @param scale Scale
     * @param pivot Pivot point (nullable)
     */
    public Transform setLocal(Vector3fc pos, Vector3fc rot, Vector3fc scale, Vector3fc pivot) {
        TransformData data = tripleBuffer.getWrite();
        data.setPosition(pos.x(), pos.y(), pos.z());
        data.setRotation(rot.x(), rot.y(), rot.z());
        data.setScale(scale.x(), scale.y(), scale.z());
        if (pivot != null) {
            data.setPivot(pivot.x(), pivot.y(), pivot.z());
        }
        markDirty();
        return this;
    }

    /**
     * Set the parent transform.
     * Updates the depth based on parent's depth.
     */
    public Transform setParent(Transform parent) {
        this.parent = parent;
        this.depth = parent != null ? parent.depth + 1 : 0;
        markDirty();
        return this;
    }

    /**
     * Get the parent transform.
     */
    public Transform getParent() {
        return parent;
    }

    /**
     * Get the depth in hierarchy (0 = root).
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Get the registered ID assigned by MatrixManager.
     */
    public int getRegisteredId() {
        return registeredId;
    }

    /**
     * Set the registered ID. Should only be called by MatrixManager.
     */
    public void setRegisteredId(int id) {
        this.registeredId = id;
    }

    /**
     * Mark this transform as dirty, requiring recalculation.
     */
    public void markDirty() {
        isDirty = true;
        worldMatrixDirty = true;
    }

    /**
     * Check if this transform is dirty.
     */
    public boolean isDirty() {
        return isDirty;
    }

    /**
     * Clear the dirty flag.
     */
    public void clearDirty() {
        isDirty = false;
    }

    /**
     * Swap the triple buffer. Should be called from the main thread.
     */
    public void swapData() {
        tripleBuffer.swap();
    }

    /**
     * Get the world matrix for CPU-side calculations.
     * Uses lazy evaluation with caching.
     * Properly accumulates all parent matrices in the hierarchy.
     *
     * @return The world matrix
     */
    public Matrix4f getWorldMatrix() {
        if (worldMatrixDirty || (parent != null && parent.worldMatrixDirty)) {
            calculateWorldMatrix();
            worldMatrixDirty = false;
        }
        return cachedWorldMatrix;
    }

    /**
     * Get the local matrix without parent transformation.
     *
     * @return The local matrix
     */
    public Matrix4f getLocalMatrix() {
        if (worldMatrixDirty) {
            calculateLocalMatrix();
        }
        return cachedLocalMatrix;
    }

    /**
     * Calculate the local matrix from current transform data.
     */
    private void calculateLocalMatrix() {
        TransformData data = tripleBuffer.getRead();
        Vector3f tempVec = TEMP_VEC.get();

        // Get euler angles (pitch, yaw, roll)
        float pitch = data.rot.x;
        float yaw = data.rot.y;
        float roll = data.rot.z;

        // Build local matrix: T(pos) * T(pivot) * R(ZYX) * T(-pivot) * S(scale)
        cachedLocalMatrix.identity()
                .translate(data.pivot)
                .translate(data.pivot)
                .rotateZYX(roll, yaw, pitch) // ZYX convention
                .translate(tempVec.set(data.pivot).negate())
                .scale(data.scale);
    }

    /**
     * Calculate the world matrix from local transform and all parent transforms.
     */
    private void calculateWorldMatrix() {
        calculateLocalMatrix();

        // Copy local to world
        cachedWorldMatrix.set(cachedLocalMatrix);

        // Recursively multiply by all parent matrices
        if (parent != null) {
            Matrix4f parentWorld = parent.getWorldMatrix();
            Matrix4f temp = TEMP_MATRIX.get();
            temp.set(parentWorld);
            temp.mul(cachedWorldMatrix, cachedWorldMatrix);
        }
    }

    /**
     * Write transform data to buffer for GPU upload.
     *
     * @param ptr  The buffer ptr
     * @param parentId The parent's registered ID (-1 if no parent)
     */
    public void writeToBuffer(long ptr, int parentId) {
        TransformData read = tripleBuffer.getRead();
        TransformData prev = tripleBuffer.getPrev();
        Unsafe unsafe = UnsafeHelper.getUnsafe();

        long base = ptr;

        // Byte offset 0-15: prevPos (vec4)
        unsafe.putFloat(base + 0, prev.pos.x);
        unsafe.putFloat(base + 4, prev.pos.y);
        unsafe.putFloat(base + 8, prev.pos.z);
        // base + 12 是padding

        // Byte offset 16-31: prevRot (vec4)
        unsafe.putFloat(base + 16, prev.rot.x);
        unsafe.putFloat(base + 20, prev.rot.y);
        unsafe.putFloat(base + 24, prev.rot.z);
        // base + 28 是padding

        // Byte offset 32-47: prevScale (vec4)
        unsafe.putFloat(base + 32, prev.scale.x);
        unsafe.putFloat(base + 36, prev.scale.y);
        unsafe.putFloat(base + 40, prev.scale.z);
        // base + 44 是padding

        // Byte offset 48-63: currPos (vec4)
        unsafe.putFloat(base + 48, read.pos.x);
        unsafe.putFloat(base + 52, read.pos.y);
        unsafe.putFloat(base + 56, read.pos.z);
        // base + 60 是padding

        // Byte offset 64-79: currRot (vec4)
        unsafe.putFloat(base + 64, read.rot.x);
        unsafe.putFloat(base + 68, read.rot.y);
        unsafe.putFloat(base + 72, read.rot.z);
        // base + 76 是padding

        // Byte offset 80-95: currScale (vec4)
        unsafe.putFloat(base + 80, read.scale.x);
        unsafe.putFloat(base + 84, read.scale.y);
        unsafe.putFloat(base + 88, read.scale.z);
        // base + 92 是padding

        // Byte offset 96-111: pivot (vec4)
        unsafe.putFloat(base + 96, read.pivot.x);
        unsafe.putFloat(base + 100, read.pivot.y);
        unsafe.putFloat(base + 104, read.pivot.z);
        // base + 108 是padding

        // Byte offset 112-115: parentID (int)
        unsafe.putInt(base + 112, parentId);

        // Byte offset 116-127: padding (3 ints)
        unsafe.putInt(base + 116, 0);
        unsafe.putInt(base + 120, 0);
        unsafe.putInt(base + 124, 0);
    }

    /**
     * Get current position (read buffer).
     */
    public Vector3f getCurrentPosition() {
        return tripleBuffer.getRead().pos;
    }

    /**
     * Get current rotation (read buffer) as Euler angles (pitch, yaw, roll).
     */
    public Vector3f getCurrentRotation() {
        return tripleBuffer.getRead().rot;
    }

    /**
     * Get current scale (read buffer).
     */
    public Vector3f getCurrentScale() {
        return tripleBuffer.getRead().scale;
    }

    /**
     * Get the pivot point (read buffer).
     */
    public Vector3f getPivot() {
        return tripleBuffer.getRead().pivot;
    }

    public boolean asyncTick() {
        return asyncTick;
    }

    /**
     * Reset transform to identity.
     */
    public void reset() {
        tripleBuffer.getWrite().reset();
        markDirty();
    }

    /**
     * Get the triple buffer for direct access if needed.
     */
    public TripleBuffer<TransformData> getTripleBuffer() {
        return tripleBuffer;
    }
}