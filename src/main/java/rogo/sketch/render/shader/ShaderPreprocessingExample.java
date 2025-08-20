package rogo.sketch.render.shader;

import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.config.ShaderConfigurationManager;
import rogo.sketch.util.Identifier;

import java.io.IOException;

/**
 * Example demonstrating the new shader preprocessing and recompilation system
 */
public class ShaderPreprocessingExample {
    
    public static void demonstrateShaderPreprocessing(ResourceProvider resourceProvider) {
        try {
            // Create shader factory with preprocessing support
            ShaderFactory factory = new ShaderFactory(resourceProvider);
            
            // Example 1: Create a shader with macro definitions
            Identifier shaderId = Identifier.of("example:test_shader");
            
            // Configure shader before creation
            factory.withConfiguration(shaderId, builder -> builder
                .define("MAX_LIGHTS", 8)
                .define("USE_SHADOWS")
                .enableFeature("advanced_lighting")
                .setProperty("quality_level", "high")
            );
            
            // Create recompilable graphics shader
            String vertexSource = """
                #version 330 core
                
                #ifdef USE_SHADOWS
                    #import "common/shadow_utils"
                #endif
                
                #import <common/vertex_common>
                
                layout (location = 0) in vec3 aPos;
                layout (location = 1) in vec2 aTexCoord;
                
                uniform mat4 mvpMatrix;
                
                out vec2 texCoord;
                #ifdef USE_SHADOWS
                out vec4 shadowCoord;
                #endif
                
                void main() {
                    gl_Position = mvpMatrix * vec4(aPos, 1.0);
                    texCoord = aTexCoord;
                    
                    #ifdef USE_SHADOWS
                    shadowCoord = calculateShadowCoord(vec4(aPos, 1.0));
                    #endif
                }
                """;
            
            String fragmentSource = """
                #version 330 core
                
                #define MAX_LIGHTS """ + "MAX_LIGHTS" + """
                
                #ifdef USE_SHADOWS
                    #import "common/shadow_sampling"
                #endif
                
                #import <common/lighting>
                
                in vec2 texCoord;
                #ifdef USE_SHADOWS
                in vec4 shadowCoord;
                #endif
                
                uniform sampler2D mainTexture;
                uniform vec3 lightPositions[MAX_LIGHTS];
                uniform vec3 lightColors[MAX_LIGHTS];
                uniform int numLights;
                
                out vec4 fragColor;
                
                void main() {
                    vec4 baseColor = texture(mainTexture, texCoord);
                    
                    vec3 lighting = vec3(0.0);
                    for (int i = 0; i < min(numLights, MAX_LIGHTS); i++) {
                        lighting += calculateLighting(lightPositions[i], lightColors[i]);
                    }
                    
                    #ifdef USE_SHADOWS
                    float shadow = sampleShadow(shadowCoord);
                    lighting *= shadow;
                    #endif
                    
                    fragColor = vec4(baseColor.rgb * lighting, baseColor.a);
                }
                """;
            
            RecompilableGraphicsShader shader = factory.createGraphicsShader(
                shaderId, vertexSource, fragmentSource
            );
            
            System.out.println("Created shader with dependencies: " + shader.getDependencies());
            
            // Example 2: Runtime configuration changes
            ShaderConfigurationManager configManager = ShaderConfigurationManager.getInstance();
            
            // Change configuration - shader will automatically recompile
            configManager.updateConfiguration(shaderId, config -> {
                config.define("MAX_LIGHTS", 16); // Increase light count
                config.enableFeature("volumetric_fog");
                config.define("FOG_QUALITY", "high");
            });
            
            // Example 3: Global configuration affecting all shaders
            configManager.updateGlobalConfiguration(config -> {
                config.define("GLOBAL_DEBUG");
                config.enableFeature("performance_monitoring");
            });
            
            // Example 4: Using presets
            ShaderFactory debugFactory = ShaderFactory.withPreset(resourceProvider, "debug");
            RecompilableComputeShader computeShader = debugFactory.createComputeShader(
                Identifier.of("example:compute_test"),
                """
                #version 430
                
                #ifdef DEBUG
                    #define LOG_ENABLED
                #endif
                
                #import <compute/common>
                
                layout (local_size_x = 64) in;
                
                layout (std430, binding = 0) buffer DataBuffer {
                    float data[];
                };
                
                void main() {
                    uint index = gl_GlobalInvocationID.x;
                    if (index >= data.length()) return;
                    
                    #ifdef LOG_ENABLED
                    // Debug logging would be enabled
                    #endif
                    
                    data[index] = processData(data[index]);
                }
                """
            );
            
            // Example 5: Manual recompilation
            if (shader.needsRecompilation()) {
                shader.recompile();
            }
            
            // Force recompilation regardless of configuration
            shader.forceRecompile();
            
            // Example 6: Configuration listener
            configManager.addConfigurationListener(shaderId, newConfig -> {
                System.out.println("Shader " + shaderId + " configuration changed: " + newConfig);
            });
            
            // Clean up
            shader.dispose();
            computeShader.dispose();
            
        } catch (IOException e) {
            System.err.println("Shader preprocessing example failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Example of creating common shader configurations
     */
    public static void setupCommonConfigurations() {
        ShaderConfigurationManager manager = ShaderConfigurationManager.getInstance();
        
        // High quality configuration
        ShaderConfiguration highQuality = ShaderConfiguration.builder()
            .define("HIGH_QUALITY_SHADING")
            .define("MAX_SHADOW_CASCADES", 4)
            .define("SHADOW_MAP_SIZE", 2048)
            .enableFeature("volumetric_lighting")
            .enableFeature("screen_space_reflections")
            .setProperty("quality_preset", "ultra")
            .build();
        
        // Performance configuration
        ShaderConfiguration performance = ShaderConfiguration.builder()
            .define("OPTIMIZE_PERFORMANCE")
            .define("MAX_SHADOW_CASCADES", 2)
            .define("SHADOW_MAP_SIZE", 1024)
            .define("DISABLE_EXPENSIVE_EFFECTS")
            .enableFeature("fast_approximations")
            .setProperty("quality_preset", "fast")
            .build();
        
        // Apply configurations to specific shaders
        manager.setConfiguration(Identifier.of("lighting:main"), highQuality);
        manager.setConfiguration(Identifier.of("post_process:bloom"), performance);
    }
}
