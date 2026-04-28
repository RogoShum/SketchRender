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

void main() {
    float heightShade = clamp(0.62 + vWorld.y * 0.018, 0.30, 1.0);
    vec4 texel = texture(u_DiffuseTexture, vUv);
    vec3 color = texel.rgb * u_BaseColor.rgb * heightShade;
    outColor = vec4(color, texel.a);
}
