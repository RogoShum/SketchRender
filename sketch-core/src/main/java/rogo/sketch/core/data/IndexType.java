package rogo.sketch.core.data;

public enum IndexType {
    U_BYTE(1),
    U_SHORT(2),
    U_INT(4);

    private final int bytes;

    IndexType(int bytes) {
        this.bytes = bytes;
    }

    public int bytes() {
        return bytes;
    }

    public static IndexType getOptimalType(int maxIndex) {
        if (maxIndex < 256) {
            return U_BYTE;
        } else if (maxIndex < 65536) {
            return U_SHORT;
        } else {
            return U_INT;
        }
    }
}

