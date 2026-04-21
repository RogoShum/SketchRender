package rogo.sketch.core.data.builder;

import org.joml.*;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.memory.MemoryDomain;
import rogo.sketch.core.memory.MemoryLease;
import rogo.sketch.core.memory.UnifiedMemoryFabric;

import java.nio.ByteBuffer;

/**
 * [Core] Native memory-backed write base.
 * 适用于 SSBO、UBO 或无结构的 Raw Data 填充。
 * 支持堆外内存自动扩容 (Auto-Grow) 和 显存映射回调 (Remap)。
 */
class NativeBufferWriterBase implements RawWriteBuffer, RecordWriteOps {
    protected long baseAddress;
    protected long currentAddr;
    protected long capacity;
    protected long limitAddr;

    // 内存管理策略
    private boolean isExternalMemory; // true=显存映射/外部指针(不可直接free), false=内部分配(可free/realloc)
    private ResizeCallback resizeCallback;
    private MemoryLease memoryLease;

    public static boolean DEBUG_MODE = true;

    // ===== 初始化 =====

    public NativeBufferWriterBase(ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Direct buffer required");
        }

        init(MemoryUtil.memAddress(buffer), buffer.capacity(), true);
    }

    public static NativeBufferWriterBase createInternal(long capacity) {
        return new NativeBufferWriterBase(MemoryUtil.nmemAlloc(capacity), capacity, false);
    }

    public static NativeBufferWriterBase getExternal(long address, long capacity) {
        return new NativeBufferWriterBase(address, capacity, true);
    }

    public static NativeBufferWriterBase getExternal(ByteBuffer buffer) {
        return new NativeBufferWriterBase(buffer);
    }

    public NativeBufferWriterBase(long address, long capacity, boolean isExternalMemory) {
        init(address, capacity, isExternalMemory);
    }

    protected void init(long address, long capacity, boolean isExternal) {
        if (capacity < 0L) {
            throw new IllegalArgumentException("Capacity cannot be negative: " + capacity);
        }
        if (capacity > 0L && address == MemoryUtil.NULL) {
            if (isExternal) {
                throw new IllegalArgumentException("External memory builder requires a non-null base address");
            }
            throw new OutOfMemoryError("Failed to allocate native builder memory (capacity=" + capacity + ")");
        }
        this.baseAddress = address;
        this.currentAddr = address;
        this.capacity = capacity;
        this.limitAddr = address + capacity;
        this.isExternalMemory = isExternal;
        if (!isExternal && memoryLease == null) {
            memoryLease = UnifiedMemoryFabric.get()
                    .openLease(MemoryDomain.CPU_NATIVE, "native-buffer-writer")
                    .bindSuppliers(this::trackedReservedBytes, this::trackedLiveBytes);
        }
    }

    public void setResizeCallback(ResizeCallback callback) {
        this.resizeCallback = callback;
    }

    // ===== 核心写入 API (Raw Type) =====

    @Override
    public NativeBufferWriterBase put(float val) {
        ensureCapacity(4);
        MemoryUtil.memPutFloat(currentAddr, val);
        currentAddr += 4;

        return this;
    }

    @Override
    public NativeBufferWriterBase put(int val) {
        ensureCapacity(4);
        MemoryUtil.memPutInt(currentAddr, val);
        currentAddr += 4;

        return this;
    }

    @Override
    public NativeBufferWriterBase put(short val) {
        ensureCapacity(2);
        MemoryUtil.memPutShort(currentAddr, val);
        currentAddr += 2;

        return this;
    }

    @Override
    public NativeBufferWriterBase put(byte val) {
        ensureCapacity(1);
        MemoryUtil.memPutByte(currentAddr, val);
        currentAddr += 1;

        return this;
    }

    @Override
    public NativeBufferWriterBase put(long val) {
        ensureCapacity(8);
        MemoryUtil.memPutLong(currentAddr, val);
        currentAddr += 8;

        return this;
    }

    // ===== 向量写入 (Vectorized Put) =====

    @Override
    public NativeBufferWriterBase put(float x, float y) {
        ensureCapacity(8);
        MemoryUtil.memPutFloat(currentAddr, x);
        MemoryUtil.memPutFloat(currentAddr + 4, y);
        currentAddr += 8;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(float x, float y, float z) {
        ensureCapacity(12);
        MemoryUtil.memPutFloat(currentAddr, x);
        MemoryUtil.memPutFloat(currentAddr + 4, y);
        MemoryUtil.memPutFloat(currentAddr + 8, z);
        currentAddr += 12;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(float x, float y, float z, float w) {
        ensureCapacity(16);
        MemoryUtil.memPutFloat(currentAddr, x);
        MemoryUtil.memPutFloat(currentAddr + 4, y);
        MemoryUtil.memPutFloat(currentAddr + 8, z);
        MemoryUtil.memPutFloat(currentAddr + 12, w);
        currentAddr += 16;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(int x, int y) {
        ensureCapacity(8);
        MemoryUtil.memPutInt(currentAddr, x);
        MemoryUtil.memPutInt(currentAddr + 4, y);
        currentAddr += 8;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(int x, int y, int z) {
        ensureCapacity(12);
        MemoryUtil.memPutInt(currentAddr, x);
        MemoryUtil.memPutInt(currentAddr + 4, y);
        MemoryUtil.memPutInt(currentAddr + 8, z);
        currentAddr += 12;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(int x, int y, int z, int w) {
        ensureCapacity(16);
        MemoryUtil.memPutInt(currentAddr, x);
        MemoryUtil.memPutInt(currentAddr + 4, y);
        MemoryUtil.memPutInt(currentAddr + 8, z);
        MemoryUtil.memPutInt(currentAddr + 12, w);
        currentAddr += 16;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(short x, short y) {
        ensureCapacity(4);
        MemoryUtil.memPutShort(currentAddr, x);
        MemoryUtil.memPutShort(currentAddr + 2, y);
        currentAddr += 4;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(short x, short y, short z) {
        ensureCapacity(6);
        MemoryUtil.memPutShort(currentAddr, x);
        MemoryUtil.memPutShort(currentAddr + 2, y);
        MemoryUtil.memPutShort(currentAddr + 4, z);
        currentAddr += 6;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(short x, short y, short z, short w) {
        ensureCapacity(8);
        MemoryUtil.memPutShort(currentAddr, x);
        MemoryUtil.memPutShort(currentAddr + 2, y);
        MemoryUtil.memPutShort(currentAddr + 4, z);
        MemoryUtil.memPutShort(currentAddr + 6, w);
        currentAddr += 8;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(byte x, byte y) {
        ensureCapacity(2);
        MemoryUtil.memPutByte(currentAddr, x);
        MemoryUtil.memPutByte(currentAddr + 1, y);
        currentAddr += 2;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(byte x, byte y, byte z) {
        ensureCapacity(3);
        MemoryUtil.memPutByte(currentAddr, x);
        MemoryUtil.memPutByte(currentAddr + 1, y);
        MemoryUtil.memPutByte(currentAddr + 2, z);
        currentAddr += 3;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(byte x, byte y, byte z, byte w) {
        ensureCapacity(4);
        MemoryUtil.memPutInt(currentAddr, (x & 0xFF) | ((y & 0xFF) << 8) | ((z & 0xFF) << 16) | ((w & 0xFF) << 24));
        currentAddr += 4;
        return this;
    }

    // ===== JOML 对象写入 =====

    @Override
    public NativeBufferWriterBase put(Vector2fc v) {
        return put(v.x(), v.y());
    }

    @Override
    public NativeBufferWriterBase put(Vector3fc v) {
        return put(v.x(), v.y(), v.z());
    }

    @Override
    public NativeBufferWriterBase put(Vector4fc v) {
        return put(v.x(), v.y(), v.z(), v.w());
    }

    @Override
    public NativeBufferWriterBase put(Vector2ic v) {
        return put(v.x(), v.y());
    }

    @Override
    public NativeBufferWriterBase put(Vector3ic v) {
        return put(v.x(), v.y(), v.z());
    }

    @Override
    public NativeBufferWriterBase put(Vector4ic v) {
        return put(v.x(), v.y(), v.z(), v.w());
    }

    @Override
    public NativeBufferWriterBase put(Matrix4fc m) {
        ensureCapacity(64);
        m.get(MemoryUtil.memFloatBuffer(currentAddr, 16));
        currentAddr += 64;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(Matrix3fc m) {
        ensureCapacity(36);
        m.get(MemoryUtil.memFloatBuffer(currentAddr, 9));
        currentAddr += 36;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(Quaternionfc q) {
        ensureCapacity(16);
        MemoryUtil.memPutFloat(currentAddr, q.x());
        MemoryUtil.memPutFloat(currentAddr + 4, q.y());
        MemoryUtil.memPutFloat(currentAddr + 8, q.z());
        MemoryUtil.memPutFloat(currentAddr + 12, q.w());
        currentAddr += 16;
        return this;
    }

    // ===== 批量写入 (Bulk Ops) =====

    public void putData(long srcAddress, int bytes) {
        ensureCapacity(bytes);
        MemoryUtil.memCopy(srcAddress, currentAddr, bytes);
        currentAddr += bytes;
    }

    @Override
    public NativeBufferWriterBase putFloatAt(long byteOffset, float value) {
        checkAbsoluteWrite(byteOffset, Float.BYTES);
        MemoryUtil.memPutFloat(baseAddress + byteOffset, value);
        return this;
    }

    @Override
    public NativeBufferWriterBase putIntAt(long byteOffset, int value) {
        checkAbsoluteWrite(byteOffset, Integer.BYTES);
        MemoryUtil.memPutInt(baseAddress + byteOffset, value);
        return this;
    }

    @Override
    public NativeBufferWriterBase putShortAt(long byteOffset, short value) {
        checkAbsoluteWrite(byteOffset, Short.BYTES);
        MemoryUtil.memPutShort(baseAddress + byteOffset, value);
        return this;
    }

    @Override
    public NativeBufferWriterBase putByteAt(long byteOffset, byte value) {
        checkAbsoluteWrite(byteOffset, Byte.BYTES);
        MemoryUtil.memPutByte(baseAddress + byteOffset, value);
        return this;
    }

    @Override
    public NativeBufferWriterBase putLongAt(long byteOffset, long value) {
        checkAbsoluteWrite(byteOffset, Long.BYTES);
        MemoryUtil.memPutLong(baseAddress + byteOffset, value);
        return this;
    }

    @Override
    public NativeBufferWriterBase put(float[] data) {
        int bytes = data.length << 2; // * 4
        ensureCapacity(bytes);
        UnsafeHelper.copyFloatArray(data, currentAddr);
        currentAddr += bytes;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(float[] data, int offset, int count) {
        int bytes = count << 2;
        ensureCapacity(bytes);
        UnsafeHelper.copyFloatArray(data, offset, currentAddr, count);
        currentAddr += bytes;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(int[] data) {
        int bytes = data.length << 2;
        ensureCapacity(bytes);
        UnsafeHelper.copyIntArray(data, currentAddr);
        currentAddr += bytes;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(int[] data, int offset, int count) {
        int bytes = count << 2;
        ensureCapacity(bytes);
        UnsafeHelper.copyIntArray(data, offset, currentAddr, count);
        currentAddr += bytes;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(short[] data) {
        int bytes = data.length << 1; // * 2
        ensureCapacity(bytes);
        UnsafeHelper.copyShortArray(data, currentAddr);
        currentAddr += bytes;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(short[] data, int offset, int count) {
        int bytes = count << 1;
        ensureCapacity(bytes);
        UnsafeHelper.copyShortArray(data, offset, currentAddr, count);
        currentAddr += bytes;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(byte[] data) {
        int bytes = data.length;
        ensureCapacity(bytes);
        UnsafeHelper.copyByteArray(data, currentAddr);
        currentAddr += bytes;
        return this;
    }

    @Override
    public NativeBufferWriterBase put(byte[] data, int offset, int count) {
        ensureCapacity(count);
        UnsafeHelper.copyByteArray(data, offset, currentAddr, count);
        currentAddr += count;
        return this;
    }

    // ===== 布局控制 (SSBO std140/430 Helper) =====

    /**
     * 填充 Padding 字节
     */
    public void pad(int bytes) {
        ensureCapacity(bytes);
        MemoryUtil.memSet(currentAddr, 0, bytes);
        currentAddr += bytes;
    }

    /**
     * 对齐当前指针 (std140 常用)
     * e.g. align(16) for vec3/vec4 in std140
     */
    public void align(int alignment) {
        long offset = currentAddr - baseAddress;
        long misalignment = offset % alignment;
        if (misalignment != 0) {
            pad((int) (alignment - misalignment));
        }
    }

    // ===== SSBO 布局对齐 (std140/std430) =====

    /**
     * std140: Vec3 实际占用 Vec4 的空间（16 字节）。
     * 调用此方法在写入 Vec3 后填充 4 字节。
     */
    public void padToStd140Vec3() {
        pad(4);
    }

    /**
     * std140: 数组元素必须按 Vec4 对齐（16 字节）。
     * 在写入数组元素前调用此方法对齐。
     */
    public void alignToStd140Array() {
        align(16);
    }

    /**
     * std140: 结构体必须按 Vec4 对齐（16 字节）。
     */
    public void alignToStd140Struct() {
        align(16);
    }

    /**
     * std430: 相对宽松，但某些情况仍需对齐。
     * Vec3 不需要额外 padding，但数组和结构体仍需对齐。
     */
    public void alignToStd430Array() {
        align(16);
    }

    // ===== 数据打包 (Packing) =====

    /**
     * 将 4 个 float 打包为 GL_INT_2_10_10_10_REV 格式并写入。
     * 常用于压缩法线、切线数据。
     */
    public NativeBufferWriterBase putPackedInt2_10_10_10(float x, float y, float z, float w) {
        ensureCapacity(4);
        int packed = UnsafeHelper.packInt2_10_10_10_REV(x, y, z, w);
        MemoryUtil.memPutInt(currentAddr, packed);
        currentAddr += 4;
        return this;
    }

    /**
     * 将归一化法线向量打包为 32 位整数。
     */
    public NativeBufferWriterBase putPackedNormal(float nx, float ny, float nz) {
        return putPackedInt2_10_10_10(nx, ny, nz, 0.0f);
    }

    /**
     * 将 RGBA 颜色打包为 32 位整数 (RGBA8)。
     */
    public NativeBufferWriterBase putPackedColor(float r, float g, float b, float a) {
        ensureCapacity(4);
        int packed = UnsafeHelper.packColor(r, g, b, a);
        MemoryUtil.memPutInt(currentAddr, packed);
        currentAddr += 4;
        return this;
    }

    /**
     * 将法线向量打包为 10-10-10-2 格式并写入。
     */
    public NativeBufferWriterBase putCompressedNormal(float nx, float ny, float nz) {
        return putPackedNormal(nx, ny, nz);
    }

    /**
     * 将切线向量和手性打包为 10-10-10-2 格式并写入。
     */
    public NativeBufferWriterBase putCompressedTangent(float tx, float ty, float tz, float handedness) {
        return putPackedTangent(tx, ty, tz, handedness);
    }

    // ===== 归一化数据写入 (Normalized Put) =====

    // --- 标量归一化 ---

    /**
     * 将 [0,1] float 转换为归一化 UBYTE 并写入。
     */
    public NativeBufferWriterBase putNormalizedUByte(float v) {
        ensureCapacity(1);
        MemoryUtil.memPutByte(currentAddr, UnsafeHelper.floatToNormalizedUByte(v));
        currentAddr += 1;
        return this;
    }

    /**
     * 将 [-1,1] float 转换为归一化 BYTE 并写入。
     */
    public NativeBufferWriterBase putNormalizedByte(float v) {
        ensureCapacity(1);
        MemoryUtil.memPutByte(currentAddr, UnsafeHelper.floatToNormalizedByte(v));
        currentAddr += 1;
        return this;
    }

    /**
     * 将 [0,1] float 转换为归一化 USHORT 并写入。
     */
    public NativeBufferWriterBase putNormalizedUShort(float v) {
        ensureCapacity(2);
        MemoryUtil.memPutShort(currentAddr, UnsafeHelper.floatToNormalizedUShort(v));
        currentAddr += 2;
        return this;
    }

    /**
     * 将 [-1,1] float 转换为归一化 SHORT 并写入。
     */
    public NativeBufferWriterBase putNormalizedShort(float v) {
        ensureCapacity(2);
        MemoryUtil.memPutShort(currentAddr, UnsafeHelper.floatToNormalizedShort(v));
        currentAddr += 2;
        return this;
    }

    // --- VEC2 归一化 ---

    public NativeBufferWriterBase putNormalizedUByte(float x, float y) {
        ensureCapacity(2);
        MemoryUtil.memPutByte(currentAddr, UnsafeHelper.floatToNormalizedUByte(x));
        MemoryUtil.memPutByte(currentAddr + 1, UnsafeHelper.floatToNormalizedUByte(y));
        currentAddr += 2;
        return this;
    }

    public NativeBufferWriterBase putNormalizedByte(float x, float y) {
        ensureCapacity(2);
        MemoryUtil.memPutByte(currentAddr, UnsafeHelper.floatToNormalizedByte(x));
        MemoryUtil.memPutByte(currentAddr + 1, UnsafeHelper.floatToNormalizedByte(y));
        currentAddr += 2;
        return this;
    }

    public NativeBufferWriterBase putNormalizedUShort(float x, float y) {
        ensureCapacity(4);
        MemoryUtil.memPutShort(currentAddr, UnsafeHelper.floatToNormalizedUShort(x));
        MemoryUtil.memPutShort(currentAddr + 2, UnsafeHelper.floatToNormalizedUShort(y));
        currentAddr += 4;
        return this;
    }

    public NativeBufferWriterBase putNormalizedShort(float x, float y) {
        ensureCapacity(4);
        MemoryUtil.memPutShort(currentAddr, UnsafeHelper.floatToNormalizedShort(x));
        MemoryUtil.memPutShort(currentAddr + 2, UnsafeHelper.floatToNormalizedShort(y));
        currentAddr += 4;
        return this;
    }

    // --- VEC3 归一化 ---

    public NativeBufferWriterBase putNormalizedUByte(float x, float y, float z) {
        ensureCapacity(3);
        MemoryUtil.memPutByte(currentAddr, UnsafeHelper.floatToNormalizedUByte(x));
        MemoryUtil.memPutByte(currentAddr + 1, UnsafeHelper.floatToNormalizedUByte(y));
        MemoryUtil.memPutByte(currentAddr + 2, UnsafeHelper.floatToNormalizedUByte(z));
        currentAddr += 3;
        return this;
    }

    public NativeBufferWriterBase putNormalizedByte(float x, float y, float z) {
        ensureCapacity(3);
        MemoryUtil.memPutByte(currentAddr, UnsafeHelper.floatToNormalizedByte(x));
        MemoryUtil.memPutByte(currentAddr + 1, UnsafeHelper.floatToNormalizedByte(y));
        MemoryUtil.memPutByte(currentAddr + 2, UnsafeHelper.floatToNormalizedByte(z));
        currentAddr += 3;
        return this;
    }

    public NativeBufferWriterBase putNormalizedUShort(float x, float y, float z) {
        ensureCapacity(6);
        MemoryUtil.memPutShort(currentAddr, UnsafeHelper.floatToNormalizedUShort(x));
        MemoryUtil.memPutShort(currentAddr + 2, UnsafeHelper.floatToNormalizedUShort(y));
        MemoryUtil.memPutShort(currentAddr + 4, UnsafeHelper.floatToNormalizedUShort(z));
        currentAddr += 6;
        return this;
    }

    public NativeBufferWriterBase putNormalizedShort(float x, float y, float z) {
        ensureCapacity(6);
        MemoryUtil.memPutShort(currentAddr, UnsafeHelper.floatToNormalizedShort(x));
        MemoryUtil.memPutShort(currentAddr + 2, UnsafeHelper.floatToNormalizedShort(y));
        MemoryUtil.memPutShort(currentAddr + 4, UnsafeHelper.floatToNormalizedShort(z));
        currentAddr += 6;
        return this;
    }

    // --- VEC4 归一化 ---

    public NativeBufferWriterBase putNormalizedUByte(float x, float y, float z, float w) {
        ensureCapacity(4);
        // 优化：合并为单个 int 写入
        int packed = (UnsafeHelper.floatToNormalizedUByte(x) & 0xFF) |
                ((UnsafeHelper.floatToNormalizedUByte(y) & 0xFF) << 8) |
                ((UnsafeHelper.floatToNormalizedUByte(z) & 0xFF) << 16) |
                ((UnsafeHelper.floatToNormalizedUByte(w) & 0xFF) << 24);
        MemoryUtil.memPutInt(currentAddr, packed);
        currentAddr += 4;
        return this;
    }

    public NativeBufferWriterBase putNormalizedByte(float x, float y, float z, float w) {
        ensureCapacity(4);
        MemoryUtil.memPutByte(currentAddr, UnsafeHelper.floatToNormalizedByte(x));
        MemoryUtil.memPutByte(currentAddr + 1, UnsafeHelper.floatToNormalizedByte(y));
        MemoryUtil.memPutByte(currentAddr + 2, UnsafeHelper.floatToNormalizedByte(z));
        MemoryUtil.memPutByte(currentAddr + 3, UnsafeHelper.floatToNormalizedByte(w));
        currentAddr += 4;
        return this;
    }

    public NativeBufferWriterBase putNormalizedUShort(float x, float y, float z, float w) {
        ensureCapacity(8);
        MemoryUtil.memPutShort(currentAddr, UnsafeHelper.floatToNormalizedUShort(x));
        MemoryUtil.memPutShort(currentAddr + 2, UnsafeHelper.floatToNormalizedUShort(y));
        MemoryUtil.memPutShort(currentAddr + 4, UnsafeHelper.floatToNormalizedUShort(z));
        MemoryUtil.memPutShort(currentAddr + 6, UnsafeHelper.floatToNormalizedUShort(w));
        currentAddr += 8;
        return this;
    }

    public NativeBufferWriterBase putNormalizedShort(float x, float y, float z, float w) {
        ensureCapacity(8);
        MemoryUtil.memPutShort(currentAddr, UnsafeHelper.floatToNormalizedShort(x));
        MemoryUtil.memPutShort(currentAddr + 2, UnsafeHelper.floatToNormalizedShort(y));
        MemoryUtil.memPutShort(currentAddr + 4, UnsafeHelper.floatToNormalizedShort(z));
        MemoryUtil.memPutShort(currentAddr + 6, UnsafeHelper.floatToNormalizedShort(w));
        currentAddr += 8;
        return this;
    }

    // ===== 常用打包包装方法 =====

    /**
     * 将归一化切线向量打包为 32 位整数 (INT_2_10_10_10_REV)。
     * W 分量用于存储手性 (handedness): 1.0 或 -1.0。
     */
    public NativeBufferWriterBase putPackedTangent(float tx, float ty, float tz, float handedness) {
        return putPackedInt2_10_10_10(tx, ty, tz, handedness);
    }

    /**
     * 将 RGB 颜色打包为 32 位整数 (RGB8，A=255)。
     */
    public NativeBufferWriterBase putPackedColorRGB(float r, float g, float b) {
        return putPackedColor(r, g, b, 1.0f);
    }

    /**
     * 将 ARGB 颜色（整数格式 0xAARRGGBB）写入。
     */
    public NativeBufferWriterBase putPackedColorARGB(int argb) {
        ensureCapacity(4);
        // 转换为 ABGR 顺序（OpenGL 小端序）
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int abgr = (a << 24) | (b << 16) | (g << 8) | r;
        MemoryUtil.memPutInt(currentAddr, abgr);
        currentAddr += 4;
        return this;
    }

    /**
     * 将 RGBA 颜色（整数格式 0xRRGGBBAA）写入。
     */
    public NativeBufferWriterBase putPackedColorRGBA(int rgba) {
        ensureCapacity(4);
        // 转换为 ABGR 顺序（OpenGL 小端序）
        int r = (rgba >> 24) & 0xFF;
        int g = (rgba >> 16) & 0xFF;
        int b = (rgba >> 8) & 0xFF;
        int a = rgba & 0xFF;
        int abgr = (a << 24) | (b << 16) | (g << 8) | r;
        MemoryUtil.memPutInt(currentAddr, abgr);
        currentAddr += 4;
        return this;
    }

    // ===== ByteBuffer 互操作 =====

    /**
     * 将当前已写入的内存区域包装为只读 ByteBuffer。
     * 注意：返回的 buffer 与原生内存共享，修改 buffer 会影响原生内存。
     */
    public ByteBuffer asReadOnlyBuffer() {
        ensureOpen();
        long size = getWriteOffset();
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException("Buffer too large for ByteBuffer");
        }
        return MemoryUtil.memByteBuffer(baseAddress, (int) size).asReadOnlyBuffer();
    }

    /**
     * 将当前已写入的内存区域包装为可写 ByteBuffer。
     * 警告：直接修改此 buffer 可能导致状态不一致。
     */
    public ByteBuffer asWritableBuffer() {
        ensureOpen();
        long size = getWriteOffset();
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException("Buffer too large for ByteBuffer");
        }
        return MemoryUtil.memByteBuffer(baseAddress, (int) size);
    }

    /**
     * 获取完整容量的 ByteBuffer 视图（包括未写入区域）。
     */
    public ByteBuffer asFullBuffer() {
        ensureOpen();
        if (capacity > Integer.MAX_VALUE) {
            throw new IllegalStateException("Buffer too large for ByteBuffer");
        }
        return MemoryUtil.memByteBuffer(baseAddress, (int) capacity);
    }

    public ParallelWritableSlice sliceView(long byteOffset, long byteCapacity) {
        return ParallelWritableSlice.of(new NativeWriteBuffer(getBaseAddress(), getCapacity(), true), byteOffset, byteCapacity);
    }

    // ===== 内存管理 =====

    protected void ensureCapacity(int bytes) {
        ensureOpen();
        if (currentAddr + bytes > limitAddr) {
            grow(bytes);
        }
    }

    private void grow(int requiredExtra) {
        long used = currentAddr - baseAddress;
        long newCapacity = java.lang.Math.max(capacity * 2, capacity + requiredExtra);

        if (isExternalMemory) {
            if (resizeCallback != null) {
                // 回调上层：Orphan 旧 buffer，映射新 buffer，返回新地址
                long newBase = resizeCallback.onResize(newCapacity);
                // 假设回调处理了数据拷贝（或者不需要拷贝），更新指针
                this.baseAddress = newBase;
                this.currentAddr = newBase + used;
                this.capacity = newCapacity;
                this.limitAddr = newBase + newCapacity;
            } else {
                throw new IndexOutOfBoundsException(
                        "External memory overflow (limit: " + capacity + ") and no ResizeCallback set.");
            }
        } else {
            // 堆外内存自动扩容
            long newBase = MemoryUtil.nmemRealloc(baseAddress, newCapacity);
            if (newBase == MemoryUtil.NULL) {
                throw new OutOfMemoryError("Failed to grow native builder memory to " + newCapacity + " bytes");
            }
            this.baseAddress = newBase;
            this.currentAddr = newBase + used;
            this.capacity = newCapacity;
            this.limitAddr = newBase + newCapacity;
        }
    }

    // ===== 辅助方法 =====

    public long getWriteOffset() {
        ensureOpen();
        return currentAddr - baseAddress;
    }

    public void setWriteOffset(long writeOffset) {
        ensureOpen();
        if (writeOffset < 0) {
            throw new IllegalArgumentException("Write offset cannot be negative!!!!!");
        }
        this.currentAddr = this.baseAddress + writeOffset;
    }

    public long getBaseAddress() {
        return baseAddress;
    }

    public long getCapacity() {
        return capacity;
    }

    public void reset() {
        ensureOpen();
        currentAddr = baseAddress;
    }

    private void checkAbsoluteWrite(long byteOffset, int bytes) {
        ensureOpen();
        if (byteOffset < 0L) {
            throw new IllegalArgumentException("Write offset cannot be negative");
        }
        long required = byteOffset + bytes;
        if (required > capacity) {
            ensureAbsoluteCapacity(required);
        }
    }

    private void ensureAbsoluteCapacity(long requiredSize) {
        if (requiredSize <= capacity) {
            return;
        }
        long used = currentAddr - baseAddress;
        long newCapacity = java.lang.Math.max(capacity * 2, requiredSize);

        if (isExternalMemory) {
            if (resizeCallback == null) {
                throw new IndexOutOfBoundsException(
                        "External memory overflow (limit: " + capacity + ") and no ResizeCallback set.");
            }
            long newBase = resizeCallback.onResize(newCapacity);
            this.baseAddress = newBase;
            this.currentAddr = newBase + used;
            this.capacity = newCapacity;
            this.limitAddr = newBase + newCapacity;
            return;
        }

        long newBase = MemoryUtil.nmemRealloc(baseAddress, newCapacity);
        if (newBase == MemoryUtil.NULL) {
            throw new OutOfMemoryError("Failed to grow native builder memory to " + newCapacity + " bytes");
        }
        this.baseAddress = newBase;
        this.currentAddr = newBase + used;
        this.capacity = newCapacity;
        this.limitAddr = newBase + newCapacity;
    }

    protected final void ensureOpen() {
        if (baseAddress == MemoryUtil.NULL) {
            throw new IllegalStateException("Builder memory has been released");
        }
    }

    @Override
    public void close() {
        if (memoryLease != null) {
            memoryLease.close();
            memoryLease = null;
        }
        if (!isExternalMemory && baseAddress != MemoryUtil.NULL) {
            MemoryUtil.nmemFree(baseAddress);
        }
        baseAddress = MemoryUtil.NULL;
        currentAddr = MemoryUtil.NULL;
        limitAddr = MemoryUtil.NULL;
        capacity = 0L;
    }

    private long trackedReservedBytes() {
        return isExternalMemory ? 0L : capacity;
    }

    private long trackedLiveBytes() {
        if (isExternalMemory || baseAddress == MemoryUtil.NULL || currentAddr == MemoryUtil.NULL) {
            return 0L;
        }
        return java.lang.Math.max(0L, currentAddr - baseAddress);
    }

    @FunctionalInterface
    public interface ResizeCallback {
        /**
         * 请求调整内存大小。
         * 实现者需要：1. Orphan/Realloc Buffer 2. Map 新区域 3. 返回新地址
         */
        long onResize(long newSize);
    }
}

