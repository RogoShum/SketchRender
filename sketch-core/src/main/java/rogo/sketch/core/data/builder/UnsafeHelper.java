package rogo.sketch.core.data.builder;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Unsafe 内存操作工具类。
 * 提供高性能的数组到指针批量拷贝和数据打包功能。
 * 
 * 警告：此类使用 JDK 内部 API (sun.misc.Unsafe)。
 * 未来 JDK 版本可能移除此 API。
 */
public class UnsafeHelper {

    private static final Unsafe UNSAFE;

    // 数组基础偏移量 (用于直接访问数组内存)
    private static final long ARRAY_FLOAT_BASE_OFFSET;
    private static final long ARRAY_INT_BASE_OFFSET;
    private static final long ARRAY_SHORT_BASE_OFFSET;
    private static final long ARRAY_BYTE_BASE_OFFSET;

    // 数组元素大小
    private static final int FLOAT_ARRAY_INDEX_SCALE;
    private static final int INT_ARRAY_INDEX_SCALE;
    private static final int SHORT_ARRAY_INDEX_SCALE;
    private static final int BYTE_ARRAY_INDEX_SCALE;

    // 线程局部的临时缓冲区 (用于矩阵/向量写入)
    public static final float[] MAT_FLOAT_BUFFER = new float[16];

    static {
        try {
            // 通过反射获取 Unsafe 实例
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);

            // 获取数组内存布局信息
            ARRAY_FLOAT_BASE_OFFSET = UNSAFE.arrayBaseOffset(float[].class);
            ARRAY_INT_BASE_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
            ARRAY_SHORT_BASE_OFFSET = UNSAFE.arrayBaseOffset(short[].class);
            ARRAY_BYTE_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

            FLOAT_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(float[].class);
            INT_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(int[].class);
            SHORT_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(short[].class);
            BYTE_ARRAY_INDEX_SCALE = UNSAFE.arrayIndexScale(byte[].class);

            // 验证数组布局假设
            if (FLOAT_ARRAY_INDEX_SCALE != 4 || INT_ARRAY_INDEX_SCALE != 4 ||
                    SHORT_ARRAY_INDEX_SCALE != 2 || BYTE_ARRAY_INDEX_SCALE != 1) {
                throw new AssertionError("Unexpected array layout");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize UnsafeHelper", e);
        }
    }

    // ===== 批量数组拷贝方法 =====

    /**
     * 将 float 数组高速拷贝到原生内存地址。
     * 
     * @param src       源数组
     * @param srcOffset 源数组起始索引
     * @param destAddr  目标内存地址
     * @param count     拷贝的 float 数量
     */
    public static void copyFloatArray(float[] src, int srcOffset, long destAddr, int count) {
        if (src == null)
            throw new NullPointerException("Source array is null");
        if (srcOffset < 0 || count < 0 || srcOffset + count > src.length) {
            throw new IndexOutOfBoundsException("Invalid src offset or count");
        }

        long srcAddress = ARRAY_FLOAT_BASE_OFFSET + ((long) srcOffset * FLOAT_ARRAY_INDEX_SCALE);
        long bytes = (long) count * 4;
        UNSAFE.copyMemory(src, srcAddress, null, destAddr, bytes);
    }

    /**
     * 将整个 float 数组拷贝到原生内存地址。
     */
    public static void copyFloatArray(float[] src, long destAddr) {
        copyFloatArray(src, 0, destAddr, src.length);
    }

    /**
     * 将 int 数组高速拷贝到原生内存地址。
     */
    public static void copyIntArray(int[] src, int srcOffset, long destAddr, int count) {
        if (src == null)
            throw new NullPointerException("Source array is null");
        if (srcOffset < 0 || count < 0 || srcOffset + count > src.length) {
            throw new IndexOutOfBoundsException("Invalid src offset or count");
        }

        long srcAddress = ARRAY_INT_BASE_OFFSET + ((long) srcOffset * INT_ARRAY_INDEX_SCALE);
        long bytes = (long) count * 4;
        UNSAFE.copyMemory(src, srcAddress, null, destAddr, bytes);
    }

    public static void copyIntArray(int[] src, long destAddr) {
        copyIntArray(src, 0, destAddr, src.length);
    }

    /**
     * 将 short 数组高速拷贝到原生内存地址。
     */
    public static void copyShortArray(short[] src, int srcOffset, long destAddr, int count) {
        if (src == null)
            throw new NullPointerException("Source array is null");
        if (srcOffset < 0 || count < 0 || srcOffset + count > src.length) {
            throw new IndexOutOfBoundsException("Invalid src offset or count");
        }

        long srcAddress = ARRAY_SHORT_BASE_OFFSET + ((long) srcOffset * SHORT_ARRAY_INDEX_SCALE);
        long bytes = (long) count * 2;
        UNSAFE.copyMemory(src, srcAddress, null, destAddr, bytes);
    }

    public static void copyShortArray(short[] src, long destAddr) {
        copyShortArray(src, 0, destAddr, src.length);
    }

    /**
     * 将 byte 数组高速拷贝到原生内存地址。
     */
    public static void copyByteArray(byte[] src, int srcOffset, long destAddr, int count) {
        if (src == null)
            throw new NullPointerException("Source array is null");
        if (srcOffset < 0 || count < 0 || srcOffset + count > src.length) {
            throw new IndexOutOfBoundsException("Invalid src offset or count");
        }

        long srcAddress = ARRAY_BYTE_BASE_OFFSET + ((long) srcOffset * BYTE_ARRAY_INDEX_SCALE);
        UNSAFE.copyMemory(src, srcAddress, null, destAddr, count);
    }

    public static void copyByteArray(byte[] src, long destAddr) {
        copyByteArray(src, 0, destAddr, src.length);
    }

    // ===== 数据打包方法 =====

    /**
     * 将 4 个 float 打包为 GL_INT_2_10_10_10_REV 格式。
     * 常用于压缩法线、切线数据。
     * 
     * @param x 范围 [-1, 1] 映射到 10 位有符号整数
     * @param y 范围 [-1, 1] 映射到 10 位有符号整数
     * @param z 范围 [-1, 1] 映射到 10 位有符号整数
     * @param w 范围 [-1, 1] 映射到 2 位有符号整数
     * @return 打包后的 32 位整数
     */
    public static int packInt2_10_10_10_REV(float x, float y, float z, float w) {
        // 10-bit signed: -512 to 511
        int ix = clamp((int) (x * 511.0f), -512, 511) & 0x3FF;
        int iy = clamp((int) (y * 511.0f), -512, 511) & 0x3FF;
        int iz = clamp((int) (z * 511.0f), -512, 511) & 0x3FF;
        // 2-bit signed: -2 to 1
        int iw = clamp((int) (w * 1.0f), -2, 1) & 0x3;

        // Pack: w(2) | z(10) | y(10) | x(10)
        return (iw << 30) | (iz << 20) | (iy << 10) | ix;
    }

    /**
     * 将归一化法线向量打包为 32 位整数 (GL_INT_2_10_10_10_REV)。
     * W 分量默认设为 0。
     */
    public static int packNormal(float nx, float ny, float nz) {
        return packInt2_10_10_10_REV(nx, ny, nz, 0.0f);
    }

    /**
     * 将 4 个 float 打包为 4 个 UBYTE (32-bit RGBA8)。
     * 常用于颜色打包。
     * 
     * @param r 范围 [0, 1]
     * @param g 范围 [0, 1]
     * @param b 范围 [0, 1]
     * @param a 范围 [0, 1]
     * @return 打包后的 32 位整数 (ABGR 顺序，小端序)
     */
    public static int packColor(float r, float g, float b, float a) {
        int ir = clamp((int) (r * 255.0f), 0, 255);
        int ig = clamp((int) (g * 255.0f), 0, 255);
        int ib = clamp((int) (b * 255.0f), 0, 255);
        int ia = clamp((int) (a * 255.0f), 0, 255);

        // ABGR8 (小端序)
        return (ia << 24) | (ib << 16) | (ig << 8) | ir;
    }

    /**
     * 将 float 转换为归一化的有符号 byte (-128~127)。
     * 
     * @param value 范围 [-1, 1]
     */
    public static byte floatToNormalizedByte(float value) {
        return (byte) clamp((int) (value * 127.0f), -128, 127);
    }

    /**
     * 将 float 转换为归一化的无符号 byte (0~255)。
     * 
     * @param value 范围 [0, 1]
     */
    public static byte floatToNormalizedUByte(float value) {
        return (byte) clamp((int) (value * 255.0f), 0, 255);
    }

    /**
     * 将 float 转换为归一化的有符号 short (-32768~32767)。
     */
    public static short floatToNormalizedShort(float value) {
        return (short) clamp((int) (value * 32767.0f), -32768, 32767);
    }

    /**
     * 将 float 转换为归一化的无符号 short (0~65535)。
     */
    public static short floatToNormalizedUShort(float value) {
        return (short) clamp((int) (value * 65535.0f), 0, 65535);
    }

    // ===== 辅助方法 =====

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 获取 Unsafe 实例 (仅供内部使用)。
     * 
     * @return Unsafe 实例
     */
    public static Unsafe getUnsafe() {
        return UNSAFE;
    }
}