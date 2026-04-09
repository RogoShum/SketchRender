package rogo.sketch.core.data.builder;

import java.nio.ByteBuffer;

/**
 * Low-level writable memory contract.
 * <p>
 * This layer is backend-neutral and does not understand vertex layout,
 * padding rules, or sorting. It only exposes sequential/random writes,
 * range management, and ByteBuffer interop.
 */
public interface RawWriteBuffer extends AutoCloseable {
    long getBaseAddress();

    long getCapacity();

    long getWriteOffset();

    void setWriteOffset(long writeOffset);

    void reset();

    void putData(long srcAddress, int bytes);

    RawWriteBuffer putFloatAt(long byteOffset, float value);

    RawWriteBuffer putIntAt(long byteOffset, int value);

    RawWriteBuffer putShortAt(long byteOffset, short value);

    RawWriteBuffer putByteAt(long byteOffset, byte value);

    RawWriteBuffer putLongAt(long byteOffset, long value);

    ByteBuffer asReadOnlyBuffer();

    ByteBuffer asWritableBuffer();

    ByteBuffer asFullBuffer();
}

