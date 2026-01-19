package rogo.sketch.render.vertex;

import rogo.sketch.render.data.DataType;
import rogo.sketch.render.data.format.DataFormat;

public class DefaultDataFormats {
    public static final DataFormat POSITION = DataFormat.builder("position").vec3Attribute("position").build();
    public static final DataFormat TRANSFORM = DataFormat.builder("transform").vec3Attribute("translate").vec3Attribute("scale").vec3Attribute("rotation").build();
    public static final DataFormat POSITION_UV_NORMAL = DataFormat.builder("position_uv_normal").vec3Attribute("position").add("uv", DataType.VEC2F, true).add("normal", DataType.VEC3F, true).build();
    public static final DataFormat SCREEN_SPACE = DataFormat.builder("screen").vec3Attribute("position").add("uv", DataType.VEC2F, true).build();
    public static final DataFormat OBJ = DataFormat.builder("obj").vec3Attribute("position").add("uv", DataType.VEC2F, true).add("normal", DataType.VEC3F, true).build();
}