package rogo.sketch.core.data.type;

public enum ScalarType {
    FLOAT32(4, NumericDomain.FLOATING_POINT),
    FLOAT64(8, NumericDomain.FLOATING_POINT),
    SINT8(1, NumericDomain.SIGNED_INTEGER),
    UINT8(1, NumericDomain.UNSIGNED_INTEGER),
    SINT16(2, NumericDomain.SIGNED_INTEGER),
    UINT16(2, NumericDomain.UNSIGNED_INTEGER),
    SINT32(4, NumericDomain.SIGNED_INTEGER),
    UINT32(4, NumericDomain.UNSIGNED_INTEGER);

    public enum NumericDomain {
        FLOATING_POINT,
        SIGNED_INTEGER,
        UNSIGNED_INTEGER
    }

    private final int byteSize;
    private final NumericDomain domain;

    ScalarType(int byteSize, NumericDomain domain) {
        this.byteSize = byteSize;
        this.domain = domain;
    }

    public int byteSize() {
        return byteSize;
    }

    public NumericDomain domain() {
        return domain;
    }

    public boolean isFloatingPoint() {
        return domain == NumericDomain.FLOATING_POINT;
    }

    public boolean isSignedInteger() {
        return domain == NumericDomain.SIGNED_INTEGER;
    }

    public boolean isUnsignedInteger() {
        return domain == NumericDomain.UNSIGNED_INTEGER;
    }
}

