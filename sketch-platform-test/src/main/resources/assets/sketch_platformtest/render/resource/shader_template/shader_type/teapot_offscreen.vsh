#version 450 core

in vec3 position;
out vec3 vWorld;

mat3 rotateX(float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat3(
        1.0, 0.0, 0.0,
        0.0, c, -s,
        0.0, s, c);
}

mat3 rotateY(float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat3(
        c, 0.0, s,
        0.0, 1.0, 0.0,
        -s, 0.0, c);
}

void main() {
    vec3 centered = position - vec3(0.0, 27.5, 0.0);
    centered /= 60.0;
    mat3 rotation = rotateY(0.65) * rotateX(-0.55);
    vec3 world = rotation * centered;
    vWorld = world;
    gl_Position = vec4(world.xy * 0.54 + vec2(0.01, -0.08), 0.0, 1.0);
}
