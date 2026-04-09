package rogo.sketch.core.data.builder;

import org.joml.*;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.data.type.ValueType;

import java.lang.Runtime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * [Specialist] 顶点流式构建器。
 * 特性：
 * 1. 格式自动化：根据 StructLayout 自动步进字段。
 * 2. 数据归一化：自动将 putFloat(0-1) 转为 UBYTE/SHORT。
 * 3. 自动索引：支持自动生成 Quads -> Triangles 索引。
 * 4. 多线程切片：slice() 方法。
 */
class StructuredRecordWriter extends VertexWriteCursor {
    // ===== 静态渲染线程池 =====

    /**
     * 默认的渲染专用线程池。
     * 使用有界队列防止内存溢出，核心线程数为 CPU 核心数。
     * 与 ForkJoinPool.commonPool() 分离，避免与主游戏逻辑争抢 CPU。
     */
    private static final ExecutorService DEFAULT_RENDER_EXECUTOR;

    static {
        int coreCount = Runtime.getRuntime().availableProcessors();
        DEFAULT_RENDER_EXECUTOR = new ThreadPoolExecutor(
                coreCount, // core pool size
                coreCount, // max pool size
                60L, TimeUnit.SECONDS, // keep alive time
                new LinkedBlockingQueue<>(256), // 有界队列
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "VertexWriter-" + threadNumber.getAndIncrement());
                        t.setDaemon(true); // 守护线程，不阻止 JVM 退出
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由调用者执行
        );
    }

    // ===== 构造 =====
    public StructuredRecordWriter(StructLayout format, PrimitiveType primitiveType) {
        this(2097152, format, primitiveType);
    }

    public StructuredRecordWriter(long capacity, StructLayout format, PrimitiveType primitiveType) {
        super(capacity, format, primitiveType);
    }

    public StructuredRecordWriter(long address, long capacity, StructLayout format, PrimitiveType primitiveType) {
        super(address, capacity, format, primitiveType);
    }

    @Override
    public StructuredRecordWriter put(float x) {
        if (DEBUG_MODE)
            checkMatch(1);

        ValueType type = elementTypes[elementIndex];

        // 严格标量检查：单个 float 只能写入标量类型
        if (type == ValueType.FLOAT) {
            super.put(x);
        } else if (elementNormalized[elementIndex]) {
            // 归一化标量转换
            switch (type) {
                case UBYTE -> super.putNormalizedUByte(x);
                case BYTE -> super.putNormalizedByte(x);
                case USHORT -> super.putNormalizedUShort(x);
                case SHORT -> super.putNormalizedShort(x);
                default -> throw new IllegalArgumentException(
                        "put(float): Cannot write scalar float to type " + type +
                                ". Use vectorized put() for vector types.");
            }
        } else {
            // 非归一化标量转换
            switch (type) {
                case INT, UINT -> super.put((int) x);
                case SHORT, USHORT -> super.put((short) x);
                case BYTE, UBYTE -> super.put((byte) x);
                default -> throw new IllegalArgumentException(
                        "put(float): Cannot write scalar float to type " + type +
                                ". Use vectorized put() for vector types.");
            }
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(float x, float y) {
        if (DEBUG_MODE)
            checkMatch(2);

        ValueType type = elementTypes[elementIndex];

        // Fast Path: VEC2F
        if (type == ValueType.VEC2F) {
            super.put(x, y);
        } else if (elementNormalized[elementIndex]) {
            // 归一化 VEC2 转换
            switch (type) {
                case VEC2UB -> super.putNormalizedUByte(x, y);
                case VEC2B -> super.putNormalizedByte(x, y);
                case VEC2US -> super.putNormalizedUShort(x, y);
                case VEC2S -> super.putNormalizedShort(x, y);
                default -> throw new IllegalArgumentException(
                        "put(float,float): Cannot write VEC2 float to type " + type);
            }
        } else {
            // 非归一化 VEC2 转换
            switch (type) {
                case VEC2I, VEC2UI -> super.put((int) x, (int) y);
                case VEC2S, VEC2US -> super.put((short) x, (short) y);
                case VEC2B, VEC2UB -> super.put((byte) x, (byte) y);
                default -> throw new IllegalArgumentException(
                        "put(float,float): Cannot write VEC2 float to type " + type);
            }
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(float x, float y, float z) {
        if (DEBUG_MODE)
            checkMatch(3);

        ValueType type = elementTypes[elementIndex];

        // Fast Path: VEC3F
        if (type == ValueType.VEC3F) {
            super.put(x, y, z);
        } else if (elementNormalized[elementIndex]) {
            // 归一化 VEC3 转换
            switch (type) {
                case VEC3UB -> super.putNormalizedUByte(x, y, z);
                case VEC3B -> super.putNormalizedByte(x, y, z);
                case VEC3US -> super.putNormalizedUShort(x, y, z);
                case VEC3S -> super.putNormalizedShort(x, y, z);
                default -> throw new IllegalArgumentException(
                        "put(float,float,float): Cannot write VEC3 float to type " + type);
            }
        } else {
            // 非归一化 VEC3 转换
            switch (type) {
                case VEC3I, VEC3UI -> super.put((int) x, (int) y, (int) z);
                case VEC3S, VEC3US -> super.put((short) x, (short) y, (short) z);
                case VEC3B, VEC3UB -> super.put((byte) x, (byte) y, (byte) z);
                default -> throw new IllegalArgumentException(
                        "put(float,float,float): Cannot write VEC3 float to type " + type);
            }
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(float x, float y, float z, float w) {
        if (DEBUG_MODE)
            checkMatch(4);

        ValueType type = elementTypes[elementIndex];

        // Fast Path: VEC4F
        if (type == ValueType.VEC4F) {
            super.put(x, y, z, w);
        }
        // 打包颜色 (VEC4UB normalized -> 单个 int 写入)
        else if (type == ValueType.VEC4UB && elementNormalized[elementIndex]) {
            super.putPackedColor(x, y, z, w);
        }
        // 打包法线/切线 (INT normalized -> INT_2_10_10_10_REV)
        else if (type == ValueType.INT && elementNormalized[elementIndex]) {
            super.putPackedInt2_10_10_10(x, y, z, w);
        } else if (elementNormalized[elementIndex]) {
            // 其他归一化 VEC4 转换
            switch (type) {
                case VEC4UB -> super.putNormalizedUByte(x, y, z, w);
                case VEC4B -> super.putNormalizedByte(x, y, z, w);
                case VEC4US -> super.putNormalizedUShort(x, y, z, w);
                case VEC4S -> super.putNormalizedShort(x, y, z, w);
                default -> throw new IllegalArgumentException(
                        "put(float x4): Cannot write VEC4 float to type " + type);
            }
        } else {
            // 非归一化 VEC4 转换
            switch (type) {
                case VEC4I, VEC4UI -> super.put((int) x, (int) y, (int) z, (int) w);
                case VEC4S, VEC4US -> super.put((short) x, (short) y, (short) z, (short) w);
                case VEC4B, VEC4UB -> super.put((byte) x, (byte) y, (byte) z, (byte) w);
                default -> throw new IllegalArgumentException(
                        "put(float x4): Cannot write VEC4 float to type " + type);
            }
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(int x) {
        if (DEBUG_MODE)
            checkMatch(1);

        ValueType type = elementTypes[elementIndex];

        // 严格标量检查
        switch (type) {
            case INT, UINT -> super.put(x);
            case FLOAT -> super.put((float) x);
            case SHORT, USHORT -> super.put((short) x);
            case BYTE, UBYTE -> super.put((byte) x);
            default -> throw new IllegalArgumentException(
                    "put(int): Cannot write scalar int to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(int x, int y) {
        if (DEBUG_MODE)
            checkMatch(2);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case VEC2I, VEC2UI -> super.put(x, y);
            case VEC2F -> super.put((float) x, (float) y);
            case VEC2S, VEC2US -> super.put((short) x, (short) y);
            case VEC2B, VEC2UB -> super.put((byte) x, (byte) y);
            default -> throw new IllegalArgumentException(
                    "put(int,int): Cannot write VEC2 int to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(int x, int y, int z) {
        if (DEBUG_MODE)
            checkMatch(3);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case VEC3I, VEC3UI -> super.put(x, y, z);
            case VEC3F -> super.put((float) x, (float) y, (float) z);
            case VEC3S, VEC3US -> super.put((short) x, (short) y, (short) z);
            case VEC3B, VEC3UB -> super.put((byte) x, (byte) y, (byte) z);
            default -> throw new IllegalArgumentException(
                    "put(int,int,int): Cannot write VEC3 int to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(int x, int y, int z, int w) {
        if (DEBUG_MODE)
            checkMatch(4);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case VEC4I, VEC4UI -> super.put(x, y, z, w);
            case VEC4F -> super.put((float) x, (float) y, (float) z, (float) w);
            case VEC4S, VEC4US -> super.put((short) x, (short) y, (short) z, (short) w);
            case VEC4B, VEC4UB -> super.put((byte) x, (byte) y, (byte) z, (byte) w);
            default -> throw new IllegalArgumentException(
                    "put(int x4): Cannot write VEC4 int to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(short x) {
        if (DEBUG_MODE)
            checkMatch(1);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case SHORT, USHORT -> super.put(x);
            case BYTE, UBYTE -> super.put((byte) x);
            default -> throw new IllegalArgumentException(
                    "put(short): Cannot write scalar short to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(short x, short y) {
        if (DEBUG_MODE)
            checkMatch(2);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case VEC2S, VEC2US -> super.put(x, y);
            case VEC2B, VEC2UB -> super.put((byte) x, (byte) y);
            default -> throw new IllegalArgumentException(
                    "put(short,short): Cannot write VEC2 short to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(short x, short y, short z) {
        if (DEBUG_MODE)
            checkMatch(3);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case VEC3S, VEC3US -> super.put(x, y, z);
            case VEC3B, VEC3UB -> super.put((byte) x, (byte) y, (byte) z);
            default -> throw new IllegalArgumentException(
                    "put(short,short,short): Cannot write VEC3 short to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(short x, short y, short z, short w) {
        if (DEBUG_MODE)
            checkMatch(4);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case VEC4S, VEC4US -> super.put(x, y, z, w);
            case VEC4B, VEC4UB -> super.put((byte) x, (byte) y, (byte) z, (byte) w);
            default -> throw new IllegalArgumentException(
                    "put(short x4): Cannot write VEC4 short to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(byte x) {
        if (DEBUG_MODE)
            checkMatch(1);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case BYTE, UBYTE -> super.put(x);
            default -> throw new IllegalArgumentException(
                    "put(byte): Cannot write scalar byte to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(byte x, byte y) {
        if (DEBUG_MODE)
            checkMatch(2);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case VEC2B, VEC2UB -> super.put(x, y);
            default -> throw new IllegalArgumentException(
                    "put(byte,byte): Cannot write VEC2 byte to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(byte x, byte y, byte z) {
        if (DEBUG_MODE)
            checkMatch(3);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case VEC3B, VEC3UB -> super.put(x, y, z);
            default -> throw new IllegalArgumentException(
                    "put(byte,byte,byte): Cannot write VEC3 byte to type " + type);
        }

        advance();
        return this;
    }

    @Override
    public StructuredRecordWriter put(byte x, byte y, byte z, byte w) {
        if (DEBUG_MODE)
            checkMatch(4);

        ValueType type = elementTypes[elementIndex];

        switch (type) {
            case VEC4B, VEC4UB -> super.put(x, y, z, w);
            default -> throw new IllegalArgumentException(
                    "put(byte x4): Cannot write VEC4 byte to type " + type);
        }

        advance();
        return this;
    }

    // ===== JOML 对象写入 =====

    @Override
    public StructuredRecordWriter put(Vector2fc v) {
        return put(v.x(), v.y());
    }

    @Override
    public StructuredRecordWriter put(Vector3fc v) {
        return put(v.x(), v.y(), v.z());
    }

    @Override
    public StructuredRecordWriter put(Vector4fc v) {
        return put(v.x(), v.y(), v.z(), v.w());
    }

    @Override
    public StructuredRecordWriter put(Vector2ic v) {
        return put(v.x(), v.y());
    }

    @Override
    public StructuredRecordWriter put(Vector3ic v) {
        return put(v.x(), v.y(), v.z());
    }

    @Override
    public StructuredRecordWriter put(Vector4ic v) {
        return put(v.x(), v.y(), v.z(), v.w());
    }

    @Override
    public StructuredRecordWriter put(Matrix4fc m) {
        if (DEBUG_MODE) {
        }
        m.get(UnsafeHelper.MAT_FLOAT_BUFFER);
        float[] data = UnsafeHelper.MAT_FLOAT_BUFFER;
        put(data[0], data[1], data[2], data[3]);
        put(data[4], data[5], data[6], data[7]);
        put(data[8], data[9], data[10], data[11]);
        put(data[12], data[13], data[14], data[15]);
        return this;
    }

    @Override
    public StructuredRecordWriter put(Matrix3fc m) {
        m.get(UnsafeHelper.MAT_FLOAT_BUFFER);
        float[] data = UnsafeHelper.MAT_FLOAT_BUFFER;
        put(data[0], data[1], data[2]);
        put(data[3], data[4], data[5]);
        put(data[6], data[7], data[8]);
        return this;
    }

    @Override
    public StructuredRecordWriter put(long val) {
        super.put(val);
        return this;
    }

    @Override
    public StructuredRecordWriter put(float[] data) {
        super.put(data);
        return this;
    }

    @Override
    public StructuredRecordWriter put(float[] data, int offset, int count) {
        super.put(data, offset, count);
        return this;
    }

    @Override
    public StructuredRecordWriter put(int[] data) {
        super.put(data);
        return this;
    }

    @Override
    public StructuredRecordWriter put(int[] data, int offset, int count) {
        super.put(data, offset, count);
        return this;
    }

    @Override
    public StructuredRecordWriter put(short[] data) {
        super.put(data);
        return this;
    }

    @Override
    public StructuredRecordWriter put(short[] data, int offset, int count) {
        super.put(data, offset, count);
        return this;
    }

    @Override
    public StructuredRecordWriter put(byte[] data) {
        super.put(data);
        return this;
    }

    @Override
    public StructuredRecordWriter put(byte[] data, int offset, int count) {
        super.put(data, offset, count);
        return this;
    }

    // ===== 打包包装方法 (Packing Wrappers) =====

    /**
     * 将归一化法线向量打包为 32 位整数 (INT_2_10_10_10_REV)。
     */
    public StructuredRecordWriter putPackedNormal(float nx, float ny, float nz) {
        if (DEBUG_MODE)
            checkMatch(ValueType.INT);
        super.putPackedNormal(nx, ny, nz);
        advance();
        return this;
    }

    /**
     * 将法线向量打包为 10-10-10-2 格式并写入。
     */
    public StructuredRecordWriter putCompressedNormal(float nx, float ny, float nz) {
        return putPackedNormal(nx, ny, nz);
    }

    /**
     * 将 RGBA 颜色打包为 32 位整数 (RGBA8)。
     */
    public StructuredRecordWriter putPackedColor(float r, float g, float b, float a) {
        if (DEBUG_MODE)
            checkMatch(ValueType.VEC4UB);
        super.putPackedColor(r, g, b, a);
        advance();
        return this;
    }

    /**
     * 将 RGB 颜色打包为 32 位整数 (RGB8，A=255)。
     */
    public StructuredRecordWriter putPackedColorRGB(float r, float g, float b) {
        if (DEBUG_MODE)
            checkMatch(ValueType.VEC4UB); // 即使只提供 RGB，目标通常也是 VEC4UB
        super.putPackedColorRGB(r, g, b);
        advance();
        return this;
    }

    /**
     * 将 4 分量浮点数打包为 32 位整数 (INT_2_10_10_10_REV)。
     */
    public StructuredRecordWriter putPackedInt2_10_10_10(float x, float y, float z, float w) {
        if (DEBUG_MODE)
            checkMatch(ValueType.INT);
        super.putPackedInt2_10_10_10(x, y, z, w);
        advance();
        return this;
    }

    /**
     * 将归一化切线向量打包为 32 位整数 (INT_2_10_10_10_REV)。
     */
    public StructuredRecordWriter putPackedTangent(float tx, float ty, float tz, float handedness) {
        if (DEBUG_MODE)
            checkMatch(ValueType.INT);
        super.putPackedTangent(tx, ty, tz, handedness);
        advance();
        return this;
    }

    /**
     * 将切线向量和手性打包为 10-10-10-2 格式并写入。
     */
    public StructuredRecordWriter putCompressedTangent(float tx, float ty, float tz, float handedness) {
        return putPackedTangent(tx, ty, tz, handedness);
    }

    /**
     * 将 ARGB 颜色（整数格式 0xAARRGGBB）写入。
     */
    public StructuredRecordWriter putPackedColorARGB(int argb) {
        if (DEBUG_MODE)
            checkMatch(ValueType.VEC4UB);
        super.putPackedColorARGB(argb);
        advance();
        return this;
    }

    /**
     * 将 RGBA 颜色（整数格式 0xRRGGBBAA）写入。
     */
    public StructuredRecordWriter putPackedColorRGBA(int rgba) {
        if (DEBUG_MODE)
            checkMatch(ValueType.VEC4UB);
        super.putPackedColorRGBA(rgba);
        advance();
        return this;
    }

    public StructuredRecordWriter padding() {
        super.padding();
        return this;
    }

    public void finish() {

    }

    // ===== 多线程切片 (Slicing) =====

    /**
     * 创建切片构建器。
     *
     * @param vertexCounts 每个切片负责的顶点数
     */
    public List<StructuredRecordWriter> slice(int... vertexCounts) {
        // 1. 全局校验：所有分片必须对齐 PrimitiveType
        for (int count : vertexCounts) {
            if (!primitiveType.isValidVertexCount(count)) {
                throw new IllegalArgumentException(
                        "Vertex count " + count + " is invalid for primitive type " + primitiveType +
                                ". Must be multiple of " + primitiveType.getVerticesPerPrimitive());
            }
        }

        List<StructuredRecordWriter> slices = new ArrayList<>();
        long offsetAccum = 0;
        int vertexIdxAccum = 0;

        for (int count : vertexCounts) {
            long sliceBytes = (long) count * stride;
            long sliceStart = this.baseAddress + offsetAccum;

            if (sliceStart + sliceBytes > this.limitAddr) {
                throw new IndexOutOfBoundsException("Slice exceeds reserved capacity.");
            }

            StructuredRecordWriter slice = new StructuredRecordWriter(
                    sliceStart,
                    sliceBytes,
                    this.format,
                    this.primitiveType);

            slices.add(slice);

            offsetAccum += sliceBytes;
            vertexIdxAccum += count;
        }

        this.vertexCount += vertexIdxAccum;
        this.recordStartAddr = this.baseAddress + offsetAccum;
        this.currentAddr = recordStartAddr;

        return slices;
    }

    /**
     * 多线程执行工具（使用默认渲染线程池）。
     */
    public static void parallelFill(List<StructuredRecordWriter> builders, List<Consumer<StructuredRecordWriter>> tasks) {
        parallelFill(builders, tasks, DEFAULT_RENDER_EXECUTOR);
    }

    /**
     * 多线程执行工具（使用自定义线程池）。
     *
     * @param builders 切片后的 Builder 列表
     * @param tasks    每个 Builder 对应的填充任务
     * @param executor 自定义线程池（例如游戏引擎的工作线程池）
     */
    public static void parallelFill(List<StructuredRecordWriter> builders, List<Consumer<StructuredRecordWriter>> tasks,
                                    ExecutorService executor) {
        if (builders.size() != tasks.size()) {
            throw new IllegalArgumentException(
                    "Builder count (" + builders.size() + ") must match task count (" + tasks.size() + ")");
        }

        List<Callable<Void>> callables = new ArrayList<>();
        for (int i = 0; i < builders.size(); i++) {
            int idx = i;
            callables.add(() -> {
                tasks.get(idx).accept(builders.get(idx));
                return null;
            });
        }

        try {
            executor.invokeAll(callables);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Parallel fill interrupted", e);
        }
    }

    /**
     * 获取默认的渲染线程池。
     * 允许用户自己管理并发，例如设置线程优先级。
     */
    public static ExecutorService getDefaultRenderExecutor() {
        return DEFAULT_RENDER_EXECUTOR;
    }

    public StructuredRecordWriter snapshotCopy() {
        long writtenBytes = java.lang.Math.max(getWriteOffset(), 1L);
        StructuredRecordWriter copy = new StructuredRecordWriter(writtenBytes, format, primitiveType);
        if (getWriteOffset() > 0L) {
            copy.putData(getBaseAddress(), (int) getWriteOffset());
        }
        copy.vertexCount = this.vertexCount;
        copy.elementIndex = this.elementIndex;
        copy.recordStartAddr = copy.getBaseAddress() + (long) copy.vertexCount * stride;
        return copy;
    }

    @Override
    public void close() {
        if (DEBUG_MODE) {
            validateComplete();
        }
        super.close();
    }
}

