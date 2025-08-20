package rogo.sketch.render.shader.config;

import rogo.sketch.util.Identifier;

/**
 * 详细展示ShaderConfiguration各项属性的含义和工作原理
 */
public class ShaderConfigurationExample {
    
    public static void demonstrateShaderConfiguration() {
        System.out.println("=== Shader Configuration 属性详解 ===\n");
        
        // 1. 基本宏定义示例
        demonstrateMacros();
        
        // 2. 功能特性示例
        demonstrateFeatures();
        
        // 3. 属性配置示例
        demonstrateProperties();
        
        // 4. 综合配置示例
        demonstrateComprehensiveConfiguration();
        
        // 5. 预设配置示例
        demonstratePresetConfigurations();
    }
    
    private static void demonstrateMacros() {
        System.out.println("1. 宏定义 (Macros/Defines)");
        System.out.println("用途：编译时代码替换和条件编译\n");
        
        ShaderConfiguration config = ShaderConfiguration.builder()
            // 数值宏 - 定义常量
            .define("MAX_LIGHTS", 16)
            .define("SHADOW_MAP_SIZE", 2048)
            .define("MAX_BONES", 64)
            
            // 浮点数宏 - 定义精度参数
            .define("PI", 3.14159f)
            .define("EPSILON", 0.001f)
            
            // 布尔宏 - 功能开关
            .define("USE_SHADOWS", true)
            .define("ENABLE_INSTANCING", true)
            .define("DEBUG_MODE", false)
            
            // 字符串宏 - 算法选择
            .define("LIGHTING_MODEL", "pbr")
            .define("SHADOW_TECHNIQUE", "pcf")
            .build();
        
        System.out.println("配置的宏定义：");
        config.getMacros().forEach((name, value) -> 
            System.out.println("  " + name + " = " + value));
        
        System.out.println("\n在shader中的效果：");
        System.out.println("  uniform vec3 lights[MAX_LIGHTS];     // 变成: uniform vec3 lights[16];");
        System.out.println("  #if SHADOW_MAP_SIZE > 1024           // 变成: #if 2048 > 1024");
        System.out.println("  #ifdef USE_SHADOWS                   // 条件为真，包含阴影代码");
        System.out.println("  float pi = PI;                       // 变成: float pi = 3.14159;");
        System.out.println();
    }
    
    private static void demonstrateFeatures() {
        System.out.println("2. 功能特性 (Features)");
        System.out.println("用途：高级渲染特性的组合开关\n");
        
        ShaderConfiguration config = ShaderConfiguration.builder()
            // 光照特性
            .enableFeature("pbr_lighting")          // PBR光照模型
            .enableFeature("ambient_occlusion")     // 环境光遮蔽
            .enableFeature("screen_space_reflections") // 屏幕空间反射
            
            // 阴影特性  
            .enableFeature("shadow_mapping")        // 阴影映射
            .enableFeature("cascade_shadows")       // 级联阴影
            .enableFeature("soft_shadows")          // 软阴影
            
            // 后处理特性
            .enableFeature("tone_mapping")          // 色调映射
            .enableFeature("bloom")                 // 泛光效果
            .enableFeature("volumetric_fog")        // 体积雾
            .build();
        
        System.out.println("启用的功能特性：");
        config.getFeatures().forEach(feature -> 
            System.out.println("  " + feature));
        
        System.out.println("\n特性自动生成的宏：");
        System.out.println("  pbr_lighting → PBR_LIGHTING = 1");
        System.out.println("  ambient_occlusion → AMBIENT_OCCLUSION = 1");
        System.out.println("  screen_space_reflections → SCREEN_SPACE_REFLECTIONS = 1");
        
        System.out.println("\n在shader中的使用：");
        System.out.println("  #ifdef PBR_LIGHTING");
        System.out.println("      #import <lighting/pbr_lighting>");
        System.out.println("      vec3 color = calculatePBRLighting(...);");
        System.out.println("  #endif");
        System.out.println();
    }
    
    private static void demonstrateProperties() {
        System.out.println("3. 属性配置 (Properties)");
        System.out.println("用途：应用程序级别的配置参数\n");
        
        ShaderConfiguration config = ShaderConfiguration.builder()
            // 质量等级
            .setProperty("quality_level", "ultra")
            .setProperty("shadow_quality", "high")
            .setProperty("texture_quality", "high")
            
            // 性能参数
            .setProperty("target_fps", 60)
            .setProperty("optimization_level", 2)
            .setProperty("memory_budget_mb", 512)
            
            // 调试参数
            .setProperty("debug_wireframe", false)
            .setProperty("show_normals", false)
            .setProperty("log_performance", true)
            .build();
        
        System.out.println("配置的属性：");
        config.getProperties().forEach((key, value) -> 
            System.out.println("  " + key + " = " + value));
        
        System.out.println("\n属性的使用方式：");
        System.out.println("Java代码中：");
        System.out.println("  String quality = config.getProperty(\"quality_level\", \"medium\");");
        System.out.println("  if (\"ultra\".equals(quality)) {");
        System.out.println("      config.define(\"ULTRA_QUALITY\");");
        System.out.println("      config.enableFeature(\"advanced_effects\");");
        System.out.println("  }");
        System.out.println();
    }
    
    private static void demonstrateComprehensiveConfiguration() {
        System.out.println("4. 综合配置示例");
        System.out.println("展示如何组合使用所有配置类型\n");
        
        // 创建一个完整的lighting shader配置
        ShaderConfiguration lightingConfig = ShaderConfiguration.builder()
            // === 基础宏定义 ===
            .define("MAX_POINT_LIGHTS", 32)
            .define("MAX_SPOT_LIGHTS", 16) 
            .define("MAX_DIRECTIONAL_LIGHTS", 4)
            .define("SHADOW_MAP_SIZE", 2048)
            
            // === 功能特性 ===
            .enableFeature("pbr_lighting")
            .enableFeature("shadow_mapping")
            .enableFeature("ambient_occlusion")
            .enableFeature("normal_mapping")
            
            // === 属性配置 ===
            .setProperty("lighting_model", "pbr")
            .setProperty("shadow_technique", "pcf")
            .setProperty("ao_technique", "ssao")
            .setProperty("quality_preset", "high")
            .build();
        
        System.out.println("完整的lighting shader配置：");
        System.out.println("宏定义数量: " + lightingConfig.getMacros().size());
        System.out.println("功能特性数量: " + lightingConfig.getFeatures().size());
        System.out.println("属性配置数量: " + lightingConfig.getProperties().size());
        System.out.println("配置哈希: " + lightingConfig.getConfigurationHash());
        
        // 展示生成的shader代码效果
        System.out.println("\n生成的shader代码特点：");
        System.out.println("- 支持最多32个点光源");
        System.out.println("- 启用PBR光照计算");
        System.out.println("- 包含阴影映射代码");
        System.out.println("- 支持法线贴图");
        System.out.println("- 启用环境光遮蔽");
        System.out.println();
    }
    
    private static void demonstratePresetConfigurations() {
        System.out.println("5. 预设配置");
        System.out.println("系统提供的常用配置组合\n");
        
        // Debug预设
        ShaderConfiguration debugConfig = ShaderConfigurationManager.createPreset("debug");
        System.out.println("Debug预设：");
        System.out.println("  用途：开发调试");
        System.out.println("  特点：启用调试信息，添加验证代码");
        debugConfig.getMacros().forEach((name, value) -> 
            System.out.println("    " + name + " = " + value));
        
        // Performance预设  
        ShaderConfiguration perfConfig = ShaderConfigurationManager.createPreset("performance");
        System.out.println("\nPerformance预设：");
        System.out.println("  用途：性能优化");
        System.out.println("  特点：禁用昂贵效果，启用快速近似");
        perfConfig.getMacros().forEach((name, value) -> 
            System.out.println("    " + name + " = " + value));
        
        // Quality预设
        ShaderConfiguration qualityConfig = ShaderConfigurationManager.createPreset("quality");
        System.out.println("\nQuality预设：");
        System.out.println("  用途：高质量渲染");
        System.out.println("  特点：启用所有视觉特效");
        qualityConfig.getMacros().forEach((name, value) -> 
            System.out.println("    " + name + " = " + value));
    }
    
    /**
     * 演示配置如何影响shader编译
     */
    public static void demonstrateConfigurationImpact() {
        System.out.println("\n=== 配置对Shader编译的影响 ===");
        
        // 原始shader代码
        String originalShader = """
            #version 430 core
            
            #ifdef PBR_LIGHTING
                #import <lighting/pbr>
                #define LIGHTING_FUNCTION calculatePBRLighting
            #else
                #define LIGHTING_FUNCTION calculateBasicLighting  
            #endif
            
            #ifdef SHADOW_MAPPING
                uniform sampler2D shadowMap;
            #endif
            
            uniform vec3 lightPositions[MAX_LIGHTS];
            
            void main() {
                vec3 color = LIGHTING_FUNCTION();
                
                #ifdef SHADOW_MAPPING
                float shadow = texture(shadowMap, shadowCoord).r;
                color *= shadow;
                #endif
                
                gl_FragColor = vec4(color, 1.0);
            }
            """;
        
        System.out.println("原始shader代码包含条件编译指令");
        
        // 不同配置下的编译结果
        ShaderConfiguration basicConfig = ShaderConfiguration.builder()
            .define("MAX_LIGHTS", 8)
            .build();
            
        ShaderConfiguration advancedConfig = ShaderConfiguration.builder()
            .define("MAX_LIGHTS", 32)
            .enableFeature("pbr_lighting")
            .enableFeature("shadow_mapping")
            .build();
        
        System.out.println("\n基础配置编译结果：");
        System.out.println("- MAX_LIGHTS = 8");
        System.out.println("- 使用基础光照");
        System.out.println("- 不包含阴影代码");
        
        System.out.println("\n高级配置编译结果：");
        System.out.println("- MAX_LIGHTS = 32"); 
        System.out.println("- 使用PBR光照");
        System.out.println("- 包含阴影映射");
        System.out.println("- 导入PBR光照库");
    }
}
