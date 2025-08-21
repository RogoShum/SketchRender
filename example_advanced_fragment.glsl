#version 430 core

// Import lighting and material systems
#import <lighting/pbr_lighting>
#import <materials/material_sampling>

#ifdef SHADOW_MAPPING
#import <lighting/shadow_sampling>
#endif

#ifdef SCREEN_SPACE_REFLECTIONS
#import <post_process/ssr>
#endif

// Input from vertex shader
in VertexData {
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
} fs_in;

// Material textures
uniform sampler2D albedoMap;

#ifdef ENABLE_NORMAL_MAPPING
uniform sampler2D normalMap;
#endif

uniform sampler2D metallicRoughnessMap;

#ifdef AMBIENT_OCCLUSION
uniform sampler2D aoMap;
#endif

// Lighting uniforms
uniform vec3 cameraPos;
uniform int numPointLights;
uniform int numSpotLights;
uniform int numDirectionalLights;

// Light data (using UBOs for efficiency)
layout (std140, binding = 0) uniform PointLights {
    PointLight pointLights[MAX_POINT_LIGHTS];
};

layout (std140, binding = 1) uniform SpotLights {
    SpotLight spotLights[MAX_SPOT_LIGHTS];
};

layout (std140, binding = 2) uniform DirectionalLights {
    DirectionalLight directionalLights[MAX_DIRECTIONAL_LIGHTS];
};

#ifdef SHADOW_MAPPING
uniform sampler2DArray shadowMaps;

#ifdef USE_PCF_FILTERING
#define SHADOW_FILTER_SIZE 3
#else
#define SHADOW_FILTER_SIZE 1
#endif

#endif

// Output
out vec4 fragColor;

void main() {
    // Sample material properties
    MaterialData material;
    material.albedo = texture(albedoMap, fs_in.texCoord).rgb;
    
    vec2 metallicRoughness = texture(metallicRoughnessMap, fs_in.texCoord).rg;
    material.metallic = metallicRoughness.r;
    material.roughness = metallicRoughness.g;
    
    #ifdef AMBIENT_OCCLUSION
    material.ao = texture(aoMap, fs_in.texCoord).r;
    #else
    material.ao = 1.0;
    #endif
    
    // Calculate normal
    vec3 normal = normalize(fs_in.normal);
    
    #ifdef ENABLE_NORMAL_MAPPING
    vec3 normalSample = texture(normalMap, fs_in.texCoord).rgb * 2.0 - 1.0;
    mat3 tbn = mat3(
        normalize(fs_in.tangent),
        normalize(fs_in.bitangent),
        normal
    );
    normal = normalize(tbn * normalSample);
    #endif
    
    material.normal = normal;
    
    // Calculate lighting
    vec3 viewDir = normalize(cameraPos - fs_in.worldPos);
    vec3 finalColor = vec3(0.0);
    
    // Point lights
    for (int i = 0; i < min(numPointLights, MAX_POINT_LIGHTS); i++) {
        finalColor += calculatePBRPointLight(
            pointLights[i], material, fs_in.worldPos, viewDir
        );
    }
    
    // Spot lights
    for (int i = 0; i < min(numSpotLights, MAX_SPOT_LIGHTS); i++) {
        finalColor += calculatePBRSpotLight(
            spotLights[i], material, fs_in.worldPos, viewDir
        );
    }
    
    // Directional lights with shadows
    for (int i = 0; i < min(numDirectionalLights, MAX_DIRECTIONAL_LIGHTS); i++) {
        float shadow = 1.0;
        
        #ifdef SHADOW_MAPPING
        shadow = sampleShadowPCF(
            shadowMaps, fs_in.shadowCoords[i], i, SHADOW_FILTER_SIZE
        );
        #endif
        
        finalColor += shadow * calculatePBRDirectionalLight(
            directionalLights[i], material, viewDir
        );
    }
    
    // Apply ambient occlusion
    finalColor *= material.ao;
    
    #ifdef SCREEN_SPACE_REFLECTIONS
    // Add screen space reflections for metallic surfaces
    if (material.metallic > 0.1) {
        vec3 reflectionColor = calculateSSR(fs_in.worldPos, normal, viewDir);
        finalColor = mix(finalColor, reflectionColor, material.metallic * 0.5);
    }
    #endif
    
    fragColor = vec4(finalColor, 1.0);
}
