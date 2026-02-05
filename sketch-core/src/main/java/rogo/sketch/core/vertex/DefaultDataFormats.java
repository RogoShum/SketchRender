package rogo.sketch.core.vertex;

import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.data.DataType;

public class DefaultDataFormats {
    public static final DataFormat POSITION = DataFormat.builder("position").vec3Attribute("position").build();
    public static final DataFormat POSITION_LERP = DataFormat.builder("position").vec3Attribute("prev").vec3Attribute("current").build();
    public static final DataFormat TRANSFORM = DataFormat.builder("transform").vec3Attribute("translate").vec3Attribute("scale").vec3Attribute("rotation").build();
    public static final DataFormat POSITION_UV_NORMAL = DataFormat.builder("position_uv_normal").vec3Attribute("position").add("uv", DataType.VEC2F, true, false, false).add("normal", DataType.VEC3S, true, false, false).build();
    public static final DataFormat SCREEN_SPACE = DataFormat.builder("screen").vec3Attribute("position").add("uv", DataType.VEC2F, true, false, false).build();
    public static final DataFormat OBJ = DataFormat.builder("obj").vec3Attribute("position").add("uv", DataType.VEC2F, true, false, false).add("normal", DataType.VEC3S, true, false, false).build();
    public static final DataFormat INT = DataFormat.builder("int").add("value", DataType.INT, false, false, false).build();
}