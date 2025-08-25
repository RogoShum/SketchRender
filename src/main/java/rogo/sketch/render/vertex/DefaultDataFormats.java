package rogo.sketch.render.vertex;

import rogo.sketch.render.data.format.DataFormat;

public class DefaultDataFormats {
    public static final DataFormat POSITION = DataFormat.builder("position").vec3Attribute("position").build();
    public static final DataFormat SCREEN_SPACE = DataFormat.builder("screen").vec3Attribute("position").vec2Attribute("uv").build();
}