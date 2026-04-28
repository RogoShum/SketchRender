#version 450 core

in vec2 vUv;
out vec4 outColor;

uniform sampler2D u_ShadowMap;

void main() {
    float depth = texture(u_ShadowMap, vUv).r;
    outColor = vec4(vec3(depth), 1.0);
}
