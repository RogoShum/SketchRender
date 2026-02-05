#version 150

uniform sampler2D Sampler0;

in vec3 normal;
in vec2 uv;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, uv);
    tex.rgb += normal;
    tex.a *= 0.5f;
    fragColor = tex;
}