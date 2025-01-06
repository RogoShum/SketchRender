#version 330

layout (location=0) in vec2 Position;

uniform vec2 ScreenSize;
uniform float[12] DepthSize;

flat out float xMult;
flat out float yMult;
flat out int xStep;
flat out int yStep;

void main() {
    xMult = ScreenSize.x/DepthSize[0];
    yMult = ScreenSize.y/DepthSize[1];

    xStep = int(ceil(xMult));
    yStep = int(ceil(yMult));

    gl_Position = vec4(Position, 1.0, 1.0);
}
