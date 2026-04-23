#version 450 core
in vec3 position;
in vec3 color;
out vec3 vColor;

void main() {
    gl_Position = vec4(position, 1.0);
    vColor = color;
}
