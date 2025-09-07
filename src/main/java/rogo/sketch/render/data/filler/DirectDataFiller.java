package rogo.sketch.render.data.filler;

import rogo.sketch.render.data.format.DataFormat;

/**
 * Abstract base class for data fillers that write directly to storage backends
 * Provides common implementation for write operations using a WriteStrategy
 */
public abstract class DirectDataFiller extends DataFiller {
    protected final WriteStrategy writeStrategy;

    public DirectDataFiller(DataFormat format, WriteStrategy writeStrategy) {
        super(format);
        this.writeStrategy = writeStrategy;
    }

    // ===== Implement core scalar writing using strategy =====

    @Override
    public DataFiller putFloat(float value) {
        writeStrategy.writeFloat(value);
        return this;
    }

    @Override
    public DataFiller putInt(int value) {
        writeStrategy.writeInt(value);
        return this;
    }

    @Override
    public DataFiller putUInt(int value) {
        writeStrategy.writeInt(value); // Same as signed int in memory
        return this;
    }

    @Override
    public DataFiller putByte(byte value) {
        writeStrategy.writeByte(value);
        return this;
    }

    @Override
    public DataFiller putUByte(byte value) {
        writeStrategy.writeByte(value); // Same as signed byte in memory
        return this;
    }

    @Override
    public DataFiller putShort(short value) {
        writeStrategy.writeShort(value);
        return this;
    }

    @Override
    public DataFiller putUShort(short value) {
        writeStrategy.writeShort(value); // Same as signed short in memory
        return this;
    }

    @Override
    public DataFiller putDouble(double value) {
        writeStrategy.writeDouble(value);
        return this;
    }

    // ===== Random access support (if strategy supports it) =====

    @Override
    public void putFloatAt(long byteOffset, float value) {
        if (!writeStrategy.supportsRandomAccess()) {
            super.putFloatAt(byteOffset, value); // Throws UnsupportedOperationException
        }
        writeStrategy.writeFloatAt(byteOffset, value);
    }

    @Override
    public void putIntAt(long byteOffset, int value) {
        if (!writeStrategy.supportsRandomAccess()) {
            super.putIntAt(byteOffset, value);
        }
        writeStrategy.writeIntAt(byteOffset, value);
    }

    @Override
    public void putUIntAt(long byteOffset, int value) {
        putIntAt(byteOffset, value); // Same as signed int
    }

    @Override
    public void putByteAt(long byteOffset, byte value) {
        if (!writeStrategy.supportsRandomAccess()) {
            super.putByteAt(byteOffset, value);
        }
        writeStrategy.writeByteAt(byteOffset, value);
    }

    @Override
    public void putUByteAt(long byteOffset, byte value) {
        putByteAt(byteOffset, value); // Same as signed byte
    }

    @Override
    public void putShortAt(long byteOffset, short value) {
        if (!writeStrategy.supportsRandomAccess()) {
            super.putShortAt(byteOffset, value);
        }
        writeStrategy.writeShortAt(byteOffset, value);
    }

    @Override
    public void putUShortAt(long byteOffset, short value) {
        putShortAt(byteOffset, value); // Same as signed short
    }

    @Override
    public void putDoubleAt(long byteOffset, double value) {
        if (!writeStrategy.supportsRandomAccess()) {
            super.putDoubleAt(byteOffset, value);
        }
        writeStrategy.writeDoubleAt(byteOffset, value);
    }

    @Override
    public boolean supportsRandomAccess() {
        return writeStrategy.supportsRandomAccess();
    }

    @Override
    public void finish() {
        writeStrategy.finish();
    }

    // ===== Utility methods =====

    /**
     * Get current position in bytes (if strategy supports position tracking)
     */
    public long getCurrentPosition() {
        if (!writeStrategy.supportsPositionTracking()) {
            throw new UnsupportedOperationException("Position tracking not supported");
        }
        return writeStrategy.getCurrentPosition();
    }

    /**
     * Set position in bytes (if strategy supports position tracking)
     */
    public void setPosition(long position) {
        if (!writeStrategy.supportsPositionTracking()) {
            throw new UnsupportedOperationException("Position tracking not supported");
        }
        writeStrategy.setPosition(position);
    }

    /**
     * Get the write strategy
     */
    public WriteStrategy getWriteStrategy() {
        return writeStrategy;
    }

    /**
     * Get capacity in bytes (if available)
     */
    public long getCapacity() {
        return writeStrategy.getCapacity();
    }

    /**
     * Reset the filler for reuse
     */
    public void reset() {
        writeStrategy.reset();
    }
}
