#version 450 core

in vec3 position;
in vec2 uv;

uniform RealSceneParams {
    mat4 u_ViewProjection;
    mat4 u_Model;
    mat4 u_LightViewProjection;
    vec4 u_BaseColor;
    vec4 u_ShadowParams;
};

out vec3 vWorld;
out vec2 vUv;

void main() {
    vec4 world = u_Model * vec4(position, 1.0);
    vWorld = world.xyz;
    vUv = uv;
#ifdef SKETCH_SHADOW_PASS
    gl_Position = u_LightViewProjection * world;
#else
    gl_Position = u_ViewProjection * world;
#endif
}
