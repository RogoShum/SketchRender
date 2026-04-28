#version 450 core

in vec2 vUv;
out vec4 outColor;

uniform sampler2D test_texture;

void main() {
    float value = texture(test_texture, vUv).r;
    outColor = vec4(value, value, value, 1.0);
}
