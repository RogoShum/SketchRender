package rogo.example;

import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.resource.loader.*;
import rogo.sketch.util.Identifier;

/**
 * Complete example demonstrating all supported JSON resource formats
 */
public class CompleteLoaderExample {
    
    public static void main(String[] args) {
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
        
        // Register all loaders
        registerAllLoaders(resourceManager);
        
        // Demonstrate all resource types
        demonstrateTextureLoading(resourceManager);
        demonstrateRenderTargetLoading(resourceManager);
        demonstrateShaderProgramLoading(resourceManager);
        demonstrateRenderSettingLoading(resourceManager);
        
        // Cleanup
        resourceManager.dispose();
    }
    
    private static void registerAllLoaders(GraphicsResourceManager resourceManager) {
        resourceManager.registerLoader(ResourceTypes.TEXTURE, new TextureLoader());
        resourceManager.registerLoader(ResourceTypes.RENDER_TARGET, new RenderTargetLoader());
        // Note: ShaderProgram and RenderSetting would need proper ResourceTypes constants
        // resourceManager.registerLoader(ResourceTypes.SHADER_PROGRAM, new ShaderProgramLoader());
        // resourceManager.registerLoader(ResourceTypes.RENDER_SETTING, new RenderSettingLoader());
    }
    
    /**
     * Demonstrate all texture loading formats
     */
    private static void demonstrateTextureLoading(GraphicsResourceManager resourceManager) {
        System.out.println("=== Texture Loading Examples ===");
        
        // Basic texture
        String basicTextureJson = """
            {
                "identifier": "basic_texture",
                "format": "RGBA",
                "filter": "LINEAR",
                "wrap": "REPEAT"
            }
            """;
        
        // Advanced texture with mipmaps
        String advancedTextureJson = """
            {
                "identifier": "advanced_texture",
                "format": "RGB",
                "filter": "LINEAR_MIPMAP_LINEAR",
                "wrap": "CLAMP_TO_EDGE",
                "mipmaps": true
            }
            """;
        
        // Depth texture
        String depthTextureJson = """
            {
                "identifier": "depth_texture",
                "format": "DEPTH",
                "filter": "NEAREST",
                "wrap": "CLAMP_TO_BORDER"
            }
            """;
        
        resourceManager.registerJson(ResourceTypes.TEXTURE, Identifier.of("basic_texture"), basicTextureJson);
        resourceManager.registerJson(ResourceTypes.TEXTURE, Identifier.of("advanced_texture"), advancedTextureJson);
        resourceManager.registerJson(ResourceTypes.TEXTURE, Identifier.of("depth_texture"), depthTextureJson);
        
        System.out.println("Registered texture examples");
    }
    
    /**
     * Demonstrate render target loading formats
     */
    private static void demonstrateRenderTargetLoading(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== RenderTarget Loading Examples ===");
        
        // Simple fixed resolution render target
        String simpleRTJson = """
            {
                "identifier": "simple_rt",
                "resolutionMode": "FIXED",
                "width": 1024,
                "height": 768,
                "clearColor": "#FF336699",
                "colorAttachments": ["basic_texture"],
                "depthAttachment": "depth_texture"
            }
            """;
        
        // Screen-relative render target with inline textures
        String screenRelativeRTJson = """
            {
                "identifier": "screen_relative_rt",
                "resolutionMode": "SCREEN_RELATIVE",
                "scale": 0.5,
                "clearColor": "#FF000000",
                "colorAttachments": [
                    {
                        "identifier": "half_res_color",
                        "format": "RGBA",
                        "filter": "LINEAR",
                        "wrap": "CLAMP_TO_EDGE"
                    }
                ],
                "depthAttachment": {
                    "identifier": "half_res_depth",
                    "format": "DEPTH",
                    "filter": "NEAREST",
                    "wrap": "CLAMP_TO_EDGE"
                },
                "clearSettings": {
                    "color": true,
                    "depth": true,
                    "stencil": false
                }
            }
            """;
        
        // Multi-target render target
        String multiTargetRTJson = """
            {
                "identifier": "multi_target_rt",
                "resolutionMode": "FIXED",
                "width": 1920,
                "height": 1080,
                "colorAttachments": [
                    {
                        "identifier": "albedo_buffer",
                        "format": "RGBA",
                        "filter": "NEAREST"
                    },
                    {
                        "identifier": "normal_buffer", 
                        "format": "RGB",
                        "filter": "NEAREST"
                    },
                    {
                        "identifier": "material_buffer",
                        "format": "RGBA",
                        "filter": "NEAREST"
                    }
                ],
                "depthAttachment": "depth_texture"
            }
            """;
        
        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("simple_rt"), simpleRTJson);
        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("screen_relative_rt"), screenRelativeRTJson);
        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("multi_target_rt"), multiTargetRTJson);
        
        System.out.println("Registered render target examples");
    }
    
    /**
     * Demonstrate shader program loading formats
     */
    private static void demonstrateShaderProgramLoading(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== ShaderProgram Loading Examples ===");
        
        // Basic vertex + fragment shader from files
        String basicShaderJson = """
            {
                "identifier": "basic_shader",
                "vertex": "file:shaders/basic.vert",
                "fragment": "file:shaders/basic.frag"
            }
            """;
        
        // Shader with inline source
        String inlineShaderJson = """
            {
                "identifier": "inline_shader",
                "vertex": {
                    "source": "#version 330 core\\nlayout(location = 0) in vec3 position;\\nvoid main() { gl_Position = vec4(position, 1.0); }"
                },
                "fragment": {
                    "source": "#version 330 core\\nout vec4 fragColor;\\nvoid main() { fragColor = vec4(1.0, 0.0, 0.0, 1.0); }"
                }
            }
            """;
        
        // Advanced shader with geometry and tessellation
        String advancedShaderJson = """
            {
                "identifier": "advanced_shader",
                "vertex": "shaders/advanced.vert",
                "fragment": "shaders/advanced.frag",
                "geometry": "shaders/advanced.geom",
                "tessControl": "shaders/advanced.tesc",
                "tessEvaluation": "shaders/advanced.tese"
            }
            """;
        
        // Note: These would be registered if ShaderProgram ResourceType was defined
        System.out.println("Shader program JSON examples prepared (registration commented out)");
        // resourceManager.registerJson(ResourceTypes.SHADER_PROGRAM, Identifier.of("basic_shader"), basicShaderJson);
    }
    
    /**
     * Demonstrate render setting loading formats
     */
    private static void demonstrateRenderSettingLoading(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== RenderSetting Loading Examples ===");
        
        // Complete render setting with all components
        String completeRenderSettingJson = """
            {
                "identifier": "complete_render_setting",
                "renderState": {
                    "blend_state": {
                        "enabled": true,
                        "srcFactor": "SRC_ALPHA",
                        "dstFactor": "ONE_MINUS_SRC_ALPHA",
                        "equation": "ADD"
                    },
                    "depth_test": {
                        "enabled": true,
                        "function": "LESS",
                        "write": true
                    },
                    "cull_face": {
                        "enabled": true,
                        "face": "BACK",
                        "frontFace": "CCW"
                    }
                },
                "resourceBinding": {
                    "texture": {
                        "u_albedo": "basic_texture",
                        "u_normal": "normal_map",
                        "u_material": "material_map"
                    },
                    "shader_storage_buffer": {
                        "InstanceData": "instance_buffer",
                        "LightData": "light_buffer"
                    },
                    "uniform_buffer": {
                        "CameraUniforms": "camera_ubo",
                        "MaterialUniforms": "material_ubo"
                    },
                    "counter_buffer": {
                        "draw_counter": "indirect_draw_counter"
                    }
                },
                "renderTarget": "multi_target_rt"
            }
            """;
        
        // Minimal render setting
        String minimalRenderSettingJson = """
            {
                "identifier": "minimal_render_setting",
                "resourceBinding": {
                    "texture": {
                        "u_texture": "basic_texture"
                    }
                },
                "renderTarget": "simple_rt"
            }
            """;
        
        System.out.println("Render setting JSON examples prepared (registration commented out)");
        // resourceManager.registerJson(ResourceTypes.RENDER_SETTING, Identifier.of("complete_render_setting"), completeRenderSettingJson);
    }
    
    /**
     * Print comprehensive JSON format documentation
     */
    public static void printJsonDocumentation() {
        System.out.println("""
            
            ================================
            COMPLETE JSON FORMAT DOCUMENTATION
            ================================
            
            ## 1. TEXTURE LOADER
            
            ### Basic Format:
            {
                "identifier": "texture_name",        // Required: String
                "format": "RGBA",                     // Optional: RGB, RGBA, DEPTH, DEPTH_STENCIL, R, RG (default: RGBA)
                "filter": "LINEAR",                   // Optional: NEAREST, LINEAR, *_MIPMAP_* variants (default: LINEAR)
                "wrap": "REPEAT",                     // Optional: REPEAT, CLAMP, CLAMP_TO_EDGE, CLAMP_TO_BORDER, MIRRORED_REPEAT (default: REPEAT)
                "mipmaps": false                      // Optional: Boolean (default: false)
            }
            
            ### MC Texture Format (VanillaTextureLoader):
            {
                "identifier": "mc_texture_name",     // Required: String
                "mcResourceLocation": "minecraft:block/stone"  // Required: MC Resource Location
            }
            
            ## 2. RENDER TARGET LOADER
            
            ### Complete Format:
            {
                "identifier": "rt_name",             // Required: String
                "resolutionMode": "FIXED",           // Optional: FIXED, SCREEN_SIZE, SCREEN_RELATIVE (default: FIXED)
                "width": 1920,                       // Optional: Int (default: 1920)
                "height": 1080,                      // Optional: Int (default: 1080)
                "scale": 1.0,                        // Optional: Float (uniform scale)
                "scaleX": 1.0,                       // Optional: Float (X scale)
                "scaleY": 1.0,                       // Optional: Float (Y scale)
                "clearColor": "#RRGGBB" or "#AARRGGBB", // Optional: Hex color (default: #00000000)
                "colorAttachments": [                // Optional: Array of textures
                    "existing_texture_id",           // String reference
                    {                                // Inline texture definition
                        "identifier": "inline_texture",
                        "format": "RGBA",
                        "filter": "LINEAR"
                    }
                ],
                "depthAttachment": "depth_texture",  // Optional: String or inline texture
                "stencilAttachment": "stencil_texture", // Optional: String or inline texture
                "clearSettings": {                   // Optional: Clear configuration
                    "color": true,                   // Optional: Boolean (default: true)
                    "depth": true,                   // Optional: Boolean (default: true)
                    "stencil": false                 // Optional: Boolean (default: false)
                }
            }
            
            ## 3. SHADER PROGRAM LOADER
            
            ### File-based Format:
            {
                "identifier": "shader_name",        // Required: String
                "vertex": "path/to/vertex.vert",    // Required: File path or file:path
                "fragment": "path/to/fragment.frag", // Required: File path or file:path
                "geometry": "path/to/geometry.geom", // Optional: File path
                "tessControl": "path/to/tess.tesc", // Optional: File path
                "tessEvaluation": "path/to/tess.tese" // Optional: File path
            }
            
            ### Inline Source Format:
            {
                "identifier": "shader_name",
                "vertex": {
                    "source": "#version 330 core\\n..."  // Inline GLSL source
                },
                "fragment": {
                    "file": "path/to/fragment.frag"     // Or file reference
                }
            }
            
            ## 4. RENDER SETTING LOADER
            
            ### Complete Format:
            {
                "identifier": "setting_name",       // Required: String
                "renderState": {                     // Optional: Render state components
                    "component_type": {              // Component type identifier
                        // Component-specific properties
                    }
                },
                "resourceBinding": {                 // Optional: Resource bindings
                    "resource_type": {               // Resource type (texture, shader_storage_buffer, etc.)
                        "binding_name": "resource_id" // Binding name -> Resource identifier
                    }
                },
                "renderTarget": "rt_id"             // Optional: String reference or inline RT
            }
            
            ### Resource Binding Types:
            - "texture": Texture bindings for samplers
            - "shader_storage_buffer": SSBO bindings
            - "uniform_buffer": UBO bindings  
            - "counter_buffer": Atomic counter buffer bindings
            - "image": Image load/store bindings
            
            ================================
            """);
    }
} 