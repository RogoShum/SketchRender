#version 450 core

in vec2 vUv;
out vec4 outColor;

uniform sampler2D test_texture;

float near = 0.05;
float far = 16.0;

float LinearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (near * far) / (far + near - z * (far - near));
}

void main() {
    float depth = texture(test_texture, vUv).r;
    float linearDepth = clamp(LinearizeDepth(depth) / far, 0.0, 1.0);
    outColor = vec4(linearDepth, linearDepth, linearDepth, 1.0);
}
