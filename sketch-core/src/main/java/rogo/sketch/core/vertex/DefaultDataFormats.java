package rogo.sketch.core.vertex;

import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.data.type.ValueType;

public class DefaultDataFormats {
    public static final StructLayout POSITION = StructLayout.builder("position").vec3Field("position").build();
    public static final StructLayout POSITION_LERP = StructLayout.builder("position").vec3Field("prev").vec3Field("current").build();
    public static final StructLayout TRANSFORM = StructLayout.builder("transform").vec3Field("translate").vec3Field("scale").vec3Field("rotation").build();
    public static final StructLayout POSITION_UV_NORMAL = StructLayout.builder("position_uv_normal").vec3Field("position").add("uv", ValueType.VEC2F, true, false, false).add("normal", ValueType.VEC3S, true, false, false).build();
    public static final StructLayout SCREEN_SPACE = StructLayout.builder("screen").vec3Field("position").add("uv", ValueType.VEC2F, true, false, false).build();
    public static final StructLayout OBJ = StructLayout.builder("obj").vec3Field("position").add("uv", ValueType.VEC2F, true, false, false).add("normal", ValueType.VEC3S, true, false, false).build();
    public static final StructLayout INT = StructLayout.builder("int").add("value", ValueType.INT, false, false, false).build();
}

