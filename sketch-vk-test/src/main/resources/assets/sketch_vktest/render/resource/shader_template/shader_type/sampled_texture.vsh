#version 450 core
in vec2 position;
out vec2 vUv;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    vUv = position * 0.5 + 0.5;
}
