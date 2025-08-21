#version 430 core

// Import common utilities - these will be processed by the preprocessor
#import <common/vertex_common>
#import <lighting/vertex_lighting>
#import "materials/vertex_materials"

// Vertex attributes
layout (location = 0) in vec3 aPosition;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aTexCoord;

#ifdef ENABLE_NORMAL_MAPPING
layout (location = 3) in vec3 aTangent;
#endif

// Uniforms
uniform mat4 modelMatrix;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat3 normalMatrix;

#ifdef SHADOW_MAPPING
uniform mat4 shadowMatrices[MAX_DIRECTIONAL_LIGHTS];
#endif

// Output to fragment shader
out VertexData {
    vec3 worldPos;
    vec3 normal;
    vec2 texCoord;
    
    #ifdef ENABLE_NORMAL_MAPPING
    vec3 tangent;
    vec3 bitangent;
    #endif
    
    #ifdef SHADOW_MAPPING
    vec4 shadowCoords[MAX_DIRECTIONAL_LIGHTS];
    #endif
} vs_out;

void main() {
    // Transform vertex position
    vec4 worldPos = modelMatrix * vec4(aPosition, 1.0);
    vs_out.worldPos = worldPos.xyz;
    
    // Transform normal
    vs_out.normal = normalize(normalMatrix * aNormal);
    vs_out.texCoord = aTexCoord;
    
    #ifdef ENABLE_NORMAL_MAPPING
    // Calculate tangent space vectors
    vs_out.tangent = normalize(normalMatrix * aTangent);
    vs_out.bitangent = cross(vs_out.normal, vs_out.tangent);
    #endif
    
    #ifdef SHADOW_MAPPING
    // Calculate shadow coordinates for each directional light
    for (int i = 0; i < MAX_DIRECTIONAL_LIGHTS; i++) {
        vs_out.shadowCoords[i] = shadowMatrices[i] * worldPos;
    }
    #endif
    
    // Final position
    gl_Position = projectionMatrix * viewMatrix * worldPos;
}
