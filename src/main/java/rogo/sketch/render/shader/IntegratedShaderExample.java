package rogo.sketch.render.shader;

import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.util.Identifier;

import java.util.Set;

/**
 * Complete integration example showing the full shader preprocessing system
 */
public class IntegratedShaderExample {
    
    public static void demonstrateFullSystem(ResourceProvider resourceProvider) {
        try {
            // Initialize the advanced shader manager
            AdvancedShaderManager.initialize(resourceProvider);
            AdvancedShaderManager manager = AdvancedShaderManager.getInstance();
            
            // Example 1: Create a complex lighting shader with imports and macros
            demonstrateLightingShader(manager);
            
            // Example 2: Create compute shader for post-processing
            demonstrateComputeShader(manager);
            
            // Example 3: Dynamic configuration changes
            demonstrateConfigurationChanges(manager);
            
            // Example 4: Dependency tracking and hot-reload simulation
            demonstrateDependencyTracking(manager);
            
            // Example 5: Performance monitoring
            demonstratePerformanceMonitoring(manager);
            
            // Clean up
            manager.dispose();
            
        } catch (Exception e) {
            System.err.println("Integrated shader example failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void demonstrateLightingShader(AdvancedShaderManager manager) throws Exception {
        System.out.println("=== Demonstrating Lighting Shader ===");
        
        Identifier lightingId = Identifier.of("example:advanced_lighting");
        
        // Set up configuration for advanced lighting
        manager.setShaderConfiguration(lightingId, ShaderConfiguration.builder()
            .define("MAX_POINT_LIGHTS", 32)
            .define("MAX_SPOT_LIGHTS", 16)
            .define("MAX_DIRECTIONAL_LIGHTS", 4)
            .enableFeature("pbr_lighting")
            .enableFeature("shadow_mapping")
            .enableFeature("ambient_occlusion")
            .setProperty("shadow_quality", "high")
            .build());
        
        String vertexSource = """
            #version 430 core
            
            // Import common vertex utilities
            #import <common/vertex_common>
            #import <lighting/vertex_lighting>
            
            layout (location = 0) in vec3 aPosition;
            layout (location = 1) in vec3 aNormal;
            layout (location = 2) in vec2 aTexCoord;
            layout (location = 3) in vec3 aTangent;
            
            uniform mat4 modelMatrix;
            uniform mat4 viewMatrix;
            uniform mat4 projectionMatrix;
            uniform mat3 normalMatrix;
            
            out VertexData {
                vec3 worldPos;
                vec3 normal;
                vec2 texCoord;
                vec3 tangent;
                vec3 bitangent;
                #ifdef SHADOW_MAPPING
                vec4 shadowCoords[MAX_DIRECTIONAL_LIGHTS];
                #endif
            } vs_out;
            
            void main() {
                vec4 worldPos = modelMatrix * vec4(aPosition, 1.0);
                vs_out.worldPos = worldPos.xyz;
                vs_out.normal = normalize(normalMatrix * aNormal);
                vs_out.texCoord = aTexCoord;
                vs_out.tangent = normalize(normalMatrix * aTangent);
                vs_out.bitangent = cross(vs_out.normal, vs_out.tangent);
                
                #ifdef SHADOW_MAPPING
                calculateShadowCoords(worldPos, vs_out.shadowCoords);
                #endif
                
                gl_Position = projectionMatrix * viewMatrix * worldPos;
            }
            """;
        
        String fragmentSource = """
            #version 430 core
            
            // Import lighting and material systems
            #import <lighting/pbr_lighting>
            #import <lighting/shadow_sampling>
            #import <materials/material_common>
            
            in VertexData {
                vec3 worldPos;
                vec3 normal;
                vec2 texCoord;
                vec3 tangent;
                vec3 bitangent;
                #ifdef SHADOW_MAPPING
                vec4 shadowCoords[MAX_DIRECTIONAL_LIGHTS];
                #endif
            } fs_in;
            
            // Material textures
            uniform sampler2D albedoMap;
            uniform sampler2D normalMap;
            uniform sampler2D metallicRoughnessMap;
            uniform sampler2D aoMap;
            
            // Lighting uniforms
            uniform vec3 cameraPos;
            uniform int numPointLights;
            uniform int numSpotLights;
            uniform int numDirectionalLights;
            
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
            #endif
            
            out vec4 fragColor;
            
            void main() {
                // Sample material properties
                MaterialData material = sampleMaterial(
                    albedoMap, normalMap, metallicRoughnessMap, aoMap, fs_in.texCoord
                );
                
                // Calculate normal from normal map
                vec3 normal = calculateNormalFromMap(
                    material.normal, fs_in.normal, fs_in.tangent, fs_in.bitangent
                );
                
                vec3 viewDir = normalize(cameraPos - fs_in.worldPos);
                vec3 finalColor = vec3(0.0);
                
                #ifdef PBR_LIGHTING
                // PBR lighting calculation
                for (int i = 0; i < min(numPointLights, MAX_POINT_LIGHTS); i++) {
                    finalColor += calculatePBRPointLight(
                        pointLights[i], material, fs_in.worldPos, normal, viewDir
                    );
                }
                
                for (int i = 0; i < min(numSpotLights, MAX_SPOT_LIGHTS); i++) {
                    finalColor += calculatePBRSpotLight(
                        spotLights[i], material, fs_in.worldPos, normal, viewDir
                    );
                }
                
                for (int i = 0; i < min(numDirectionalLights, MAX_DIRECTIONAL_LIGHTS); i++) {
                    float shadow = 1.0;
                    #ifdef SHADOW_MAPPING
                    shadow = sampleShadow(shadowMaps, fs_in.shadowCoords[i], i);
                    #endif
                    
                    finalColor += shadow * calculatePBRDirectionalLight(
                        directionalLights[i], material, normal, viewDir
                    );
                }
                #else
                // Simplified lighting
                finalColor = material.albedo * 0.5; // Ambient
                #endif
                
                #ifdef AMBIENT_OCCLUSION
                finalColor *= material.ao;
                #endif
                
                fragColor = vec4(finalColor, material.alpha);
            }
            """;
        
        RecompilableGraphicsShader lightingShader = manager.createGraphicsShader(
            lightingId, vertexSource, fragmentSource
        );
        
        System.out.println("Created lighting shader with " + lightingShader.getDependencies().size() + " dependencies");
        System.out.println("Dependencies: " + lightingShader.getDependencies());
    }
    
    private static void demonstrateComputeShader(AdvancedShaderManager manager) throws Exception {
        System.out.println("\n=== Demonstrating Compute Shader ===");
        
        Identifier computeId = Identifier.of("example:post_process");
        
        // Configure compute shader
        manager.setShaderConfiguration(computeId, ShaderConfiguration.builder()
            .define("WORK_GROUP_SIZE", 16)
            .define("USE_SHARED_MEMORY")
            .enableFeature("tone_mapping")
            .enableFeature("bloom")
            .build());
        
        String computeSource = """
            #version 430
            
            #import <post_process/tone_mapping>
            #import <post_process/bloom>
            
            layout (local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;
            
            layout (binding = 0, rgba16f) uniform readonly image2D inputImage;
            layout (binding = 1, rgba8) uniform writeonly image2D outputImage;
            
            #ifdef USE_SHARED_MEMORY
            shared vec3 sharedData[WORK_GROUP_SIZE][WORK_GROUP_SIZE];
            #endif
            
            uniform float exposure;
            uniform float bloomStrength;
            
            void main() {
                ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
                ivec2 imageSize = imageSize(inputImage);
                
                if (coord.x >= imageSize.x || coord.y >= imageSize.y) return;
                
                vec3 hdrColor = imageLoad(inputImage, coord).rgb;
                
                #ifdef BLOOM
                vec3 bloomColor = sampleBloom(inputImage, coord);
                hdrColor += bloomColor * bloomStrength;
                #endif
                
                #ifdef TONE_MAPPING
                vec3 ldrColor = toneMap(hdrColor, exposure);
                #else
                vec3 ldrColor = hdrColor;
                #endif
                
                imageStore(outputImage, coord, vec4(ldrColor, 1.0));
            }
            """;
        
        RecompilableComputeShader computeShader = manager.createComputeShader(computeId, computeSource);
        System.out.println("Created compute shader with " + computeShader.getDependencies().size() + " dependencies");
    }
    
    private static void demonstrateConfigurationChanges(AdvancedShaderManager manager) {
        System.out.println("\n=== Demonstrating Configuration Changes ===");
        
        Identifier lightingId = Identifier.of("example:advanced_lighting");
        
        // Get current stats
        AdvancedShaderManager.ShaderManagerStats initialStats = manager.getStats();
        System.out.println("Initial stats: " + initialStats);
        
        // Change lighting quality
        System.out.println("Switching to performance mode...");
        manager.updateShaderConfiguration(lightingId, config -> {
            config.define("MAX_POINT_LIGHTS", 8);
            config.define("MAX_SPOT_LIGHTS", 4);
            config.disableFeature("ambient_occlusion");
            config.setProperty("shadow_quality", "medium");
        });
        
        // Check if recompilation is needed
        manager.recompileIfNeeded();
        
        System.out.println("Switching to debug mode...");
        manager.applyPreset(lightingId, "debug");
        
        AdvancedShaderManager.ShaderManagerStats finalStats = manager.getStats();
        System.out.println("Final stats: " + finalStats);
    }
    
    private static void demonstrateDependencyTracking(AdvancedShaderManager manager) {
        System.out.println("\n=== Demonstrating Dependency Tracking ===");
        
        // Simulate a file change
        Identifier changedFile = Identifier.of("lighting/pbr_lighting");
        Set<Identifier> dependentShaders = manager.getShadersUsingFile(changedFile);
        
        System.out.println("File " + changedFile + " would affect " + dependentShaders.size() + " shaders:");
        dependentShaders.forEach(id -> System.out.println("  - " + id));
        
        // Simulate hot-reload
        System.out.println("Simulating hot-reload...");
        manager.recompileDependentShaders(changedFile);
    }
    
    private static void demonstratePerformanceMonitoring(AdvancedShaderManager manager) {
        System.out.println("\n=== Performance Monitoring ===");
        
        AdvancedShaderManager.ShaderManagerStats stats = manager.getStats();
        System.out.println("Total shaders managed: " + stats.totalShaders());
        System.out.println("Graphics shaders: " + stats.graphicsShaders());
        System.out.println("Compute shaders: " + stats.computeShaders());
        System.out.println("Shaders needing recompilation: " + stats.shadersNeedingRecompilation());
        System.out.println("Total dependencies tracked: " + stats.totalDependencies());
        
        // Memory usage estimation
        long estimatedMemory = stats.totalShaders() * 1024; // Rough estimate
        System.out.println("Estimated memory usage: " + estimatedMemory + " bytes");
        
        if (stats.shadersNeedingRecompilation() > 0) {
            System.out.println("Warning: Some shaders need recompilation!");
        }
    }
}
