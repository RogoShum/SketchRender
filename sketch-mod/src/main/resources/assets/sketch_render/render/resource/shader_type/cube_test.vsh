#version 330

uniform mat4 sketch_cullingViewMat;
uniform mat4 sketch_cullingProjMat;
uniform float partialTicks;

layout (location=0) in vec3 Position;
layout (location=1) in vec2 UV;
layout (location=2) in vec3 Normal;

layout (location=3) in vec3 InstancedPosPrev;
layout (location=4) in vec3 InstancedPosCurrent;

layout (location=5) in vec3 InstancedTranslate;
layout (location=6) in vec3 InstancedScale;
layout (location=7) in vec3 InstancedRotation;

out vec3 normal;
out vec2 uv;

void main() {
    // 1. Scale
    vec3 scaledPos = Position * InstancedScale;

    // 2. 构建旋转矩阵（由方向向量）
    vec3 forward = normalize(InstancedRotation);
    vec3 up = vec3(0.0, 1.0, 0.0);

    if (abs(dot(forward, up)) > 0.999) {
        up = vec3(1.0, 0.0, 0.0);
    }

    vec3 right = normalize(cross(up, forward));
    up = cross(forward, right);

    mat3 rot = mat3(right, up, forward);

    // 3. Rotate position
    vec3 rotatedPos = rot * scaledPos;

    // 4. Translate（沿旋转方向，不受 scale 影响）
    vec3 translatedPos = rot * InstancedTranslate;

    vec3 worldPos = rotatedPos + translatedPos +  mix(InstancedPosPrev, InstancedPosCurrent, partialTicks);

    gl_Position = sketch_cullingProjMat *
    sketch_cullingViewMat *
    vec4(worldPos, 1.0);

    normal = rot * Normal; // 法线也要旋转
    uv = UV;
}