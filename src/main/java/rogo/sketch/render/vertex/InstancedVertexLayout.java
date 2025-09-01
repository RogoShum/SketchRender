package rogo.sketch.render.vertex;

import rogo.sketch.render.data.format.DataFormat;

public class InstancedVertexLayout {
    private final DataFormat dataFormat;

    public InstancedVertexLayout(DataFormat dataFormat) {
        this.dataFormat = dataFormat;
    }

    public DataFormat dataFormat() {
        return dataFormat;
    }
}