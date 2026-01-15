#version 330

uniform mat4 sketch_cullingViewMat;
uniform mat4 sketch_cullingProjMat;

layout (location=0) in vec3 Position;
layout (location=1) in vec2 UV;
layout (location=2) in vec3 Normal;

layout (location=3) in vec3 InstancedPos;

out vec3 normal;
out vec2 uv;

void main() {
    gl_Position = sketch_cullingProjMat * sketch_cullingViewMat * vec4(Position + InstancedPos, 1.0);
    normal = Normal;
    uv = UV;
}