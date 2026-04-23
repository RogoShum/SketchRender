#version 450 core
in vec3 color;
in vec2 position;
#ifdef ENABLE_INSTANCE_OFFSET
in vec2 instanceOffset;
#endif
out vec3 vColor;

void main() {
    vec2 offset = vec2(0.0);
#ifdef ENABLE_INSTANCE_OFFSET
    offset = instanceOffset;
#endif
    gl_Position = vec4(position + offset, 0.0, 1.0);
    vColor = color;
}
