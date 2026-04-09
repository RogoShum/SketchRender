#version 450 core
in vec2 vUv;
out vec4 outColor;
uniform sampler2D test_texture;

void main() {
    outColor = textureLod(test_texture, vUv, 2.0);
}
