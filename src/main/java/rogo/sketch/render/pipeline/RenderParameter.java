package rogo.sketch.render.pipeline;

import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.Usage;
import rogo.sketch.render.data.format.DataFormat;

public record RenderParameter(
        DataFormat dataFormat,
        PrimitiveType primitiveType,
        Usage usage,
        boolean enableSorting
) {

    public static final RenderParameter EMPTY = new RenderParameter(
            DataFormat.builder("EMPTY").build(),
            PrimitiveType.QUADS,
            Usage.STATIC_DRAW,
            false
    );

    public static final RenderParameter INVALID = new RenderParameter(
            DataFormat.builder("INVALID").build(),
            PrimitiveType.QUADS,
            Usage.STATIC_DRAW,
            false
    );

    public boolean isInvalid() {
        return this == RenderParameter.INVALID;
    }

    /**
     * Create a RenderParameter with default usage (STATIC_DRAW)
     */
    public static RenderParameter create(DataFormat dataFormat, PrimitiveType primitiveType) {
        return new RenderParameter(dataFormat, primitiveType, Usage.STATIC_DRAW, false);
    }

    /**
     * Create a RenderParameter with specified usage
     */
    public static RenderParameter create(DataFormat dataFormat, PrimitiveType primitiveType, Usage usage) {
        return new RenderParameter(dataFormat, primitiveType, usage, false);
    }

    /**
     * Create a RenderParameter with all options specified
     */
    public static RenderParameter create(DataFormat dataFormat, PrimitiveType primitiveType,
                                         Usage usage, boolean enableSorting) {
        return new RenderParameter(dataFormat, primitiveType, usage, enableSorting);
    }
}