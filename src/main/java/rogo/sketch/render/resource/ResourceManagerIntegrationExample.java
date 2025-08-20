package rogo.sketch.render.resource;

import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.util.Identifier;

/**
 * Example of integrating the enhanced shader system with existing GraphicsResourceManager
 */
public class ResourceManagerIntegrationExample {
    
    public static void demonstrateIntegration(ResourceProvider resourceProvider) {
        // 获取现有的资源管理器实例
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
        
        // 启用增强的shader加载功能
        resourceManager.enableEnhancedShaderLoading(resourceProvider);
        
        // 现在JSON文件会自动支持预处理功能
        // 例如：assets/sketch/render/shader_program/advanced_lighting.json
        /*
        {
          "vertex": "lighting/advanced_vertex",
          "fragment": "lighting/advanced_fragment", 
          "config": {
            "defines": {
              "MAX_LIGHTS": 16,
              "USE_SHADOWS": true
            },
            "features": ["pbr_lighting"],
            "preset": "quality"
          }
        }
        */
        
        // 通过资源系统获取shader（会自动应用预处理）
        Identifier shaderId = Identifier.of("sketch:advanced_lighting");
        var shaderRef = resourceManager.getReference(ResourceTypes.SHADER_PROGRAM, shaderId);
        
        if (shaderRef.isAvailable()) {
            System.out.println("Enhanced shader loaded successfully with preprocessing");
            
            // 如果需要运行时配置控制，可以这样做：
            if (resourceManager instanceof GraphicsResourceManagerEnhanced enhanced) {
                // 动态修改配置
                enhanced.updateShaderConfiguration(shaderId, config -> {
                    config.define("DYNAMIC_QUALITY", "ultra");
                    config.enableFeature("volumetric_fog");
                });
                
                // 获取统计信息
                var stats = enhanced.getShaderStats();
                System.out.println("Shader stats: " + stats);
                
                // 重编译需要更新的shader
                enhanced.recompileShadersIfNeeded();
            }
        }
    }
    
    /**
     * 展示如何在现有的RenderResourceManager中集成
     */
    public static void integrateWithRenderResourceManager(ResourceProvider resourceProvider) {
        // 在RenderResourceManager.onResourceManagerReload中调用
        GraphicsResourceManager.getInstance().enableEnhancedShaderLoading(resourceProvider);
        
        // 现在所有通过JSON加载的shader都会自动支持：
        // 1. Import/Include功能
        // 2. 宏定义和条件编译  
        // 3. 配置文件支持
        // 4. 自动重编译（如果使用RecompilableShader）
        
        System.out.println("Enhanced shader system integrated with existing resource management");
    }
}
