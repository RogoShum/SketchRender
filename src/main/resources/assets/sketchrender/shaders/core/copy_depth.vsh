#version 150

in vec3 Position;

uniform vec2 ScreenSize;
uniform float[10] DepthSize;

flat out float xMult;
flat out float yMult;
flat out ivec2 ParentSize;

void main() {
    xMult = ScreenSize.x/DepthSize[0];
    yMult = ScreenSize.y/DepthSize[1];
    ParentSize = ivec2(int(ScreenSize.x), int(ScreenSize.y));
    gl_Position = vec4(Position, 1.0);
}
