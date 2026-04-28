#version 450 core

in vec2 vUv;
out vec4 outColor;

uniform sampler2D u_ShadowMap;

void main() {
    outColor = texture(u_ShadowMap, vUv);
}
