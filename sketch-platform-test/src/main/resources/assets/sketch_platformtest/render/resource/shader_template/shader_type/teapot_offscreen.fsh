#version 450 core

in vec3 vWorld;
out vec4 outColor;

void main() {
    float diffuse = clamp(0.55 + vWorld.z * 0.65, 0.0, 1.0);
    vec3 base = vec3(0.82, 0.56, 0.24);
    vec3 color = base * (0.25 + 0.75 * diffuse);
    outColor = vec4(color, 1.0);
}
