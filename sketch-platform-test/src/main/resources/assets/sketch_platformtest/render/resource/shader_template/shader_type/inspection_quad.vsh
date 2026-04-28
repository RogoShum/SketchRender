#version 450 core

in vec2 position;
in vec3 color;

out vec2 vUv;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    vUv = color.xy;
}
