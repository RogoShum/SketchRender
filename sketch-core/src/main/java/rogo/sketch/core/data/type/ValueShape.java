package rogo.sketch.core.data.type;

public enum ValueShape {
    SCALAR(1, 1),
    VEC2(1, 2),
    VEC3(1, 3),
    VEC4(1, 4),
    MAT2(2, 2),
    MAT3(3, 3),
    MAT4(4, 4),
    MAT2X3(2, 3),
    MAT2X4(2, 4),
    MAT3X2(3, 2),
    MAT3X4(3, 4),
    MAT4X2(4, 2),
    MAT4X3(4, 3);

    private final int columns;
    private final int rows;

    ValueShape(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int componentCount() {
        return columns * rows;
    }

    public boolean isScalar() {
        return this == SCALAR;
    }

    public boolean isVector() {
        return !isScalar() && !isMatrix();
    }

    public boolean isMatrix() {
        return columns > 1 && rows > 1;
    }
}

