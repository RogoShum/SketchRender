#version 450 core

in vec3 vWorld;
in vec2 vUv;

out vec4 outColor;

uniform RealSceneParams {
    mat4 u_ViewProjection;
    mat4 u_Model;
    mat4 u_LightViewProjection;
    vec4 u_BaseColor;
    vec4 u_ShadowParams;
};
uniform sampler2D u_DiffuseTexture;
uniform sampler2D u_ShadowMap;

#include <sketch_render:sketch_shadow_sampling.glsl>

void main() {
    float heightShade = clamp(0.62 + vWorld.y * 0.018, 0.30, 1.0);
    vec4 texel = texture(u_DiffuseTexture, vUv);
    float shadow = sketch_projected_shadow(vWorld, u_LightViewProjection, u_ShadowMap, u_ShadowParams);
    vec3 color = texel.rgb * u_BaseColor.rgb * heightShade * (1.0 - shadow);
    outColor = vec4(color, texel.a);
}
