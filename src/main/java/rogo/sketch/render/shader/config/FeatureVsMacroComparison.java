package rogo.sketch.render.shader.config;

/**
 * 详细对比Feature和Macro的区别，解释为什么需要两套系统
 */
public class FeatureVsMacroComparison {
    
    public static void demonstrateFeatureVsMacro() {
        System.out.println("=== Feature vs Macro 对比分析 ===\n");
        
        // 1. 展示重复的问题
        demonstrateOverlap();
        
        // 2. 展示它们的不同用途
        demonstrateDifferentPurposes();
        
        // 3. 展示Feature的复合特性
        demonstrateCompositeFeatures();
        
        // 4. 展示优化后的设计
        demonstrateOptimizedDesign();
    }
    
    private static void demonstrateOverlap() {
        System.out.println("1. 看似重复的功能");
        
        // 看起来重复的配置方式
        ShaderConfiguration config1 = ShaderConfiguration.builder()
            .enableFeature("shadows")          // Feature方式
            .define("USE_SHADOWS", true)       // Macro方式 - 看起来重复！
            .build();
        
        System.out.println("表面上看起来重复：");
        System.out.println("  enableFeature(\"shadows\") → 生成 SHADOWS = 1");
        System.out.println("  define(\"USE_SHADOWS\", true) → 生成 USE_SHADOWS = 1");
        System.out.println("  结果：shader中有两个相似的宏定义");
        System.out.println();
        
        // 实际上它们的作用不同
        System.out.println("但实际作用不同：");
        System.out.println("  SHADOWS：启用整个阴影功能模块");
        System.out.println("  USE_SHADOWS：在当前上下文中是否使用阴影");
        System.out.println();
    }
    
    private static void demonstrateDifferentPurposes() {
        System.out.println("2. 不同的设计用途");
        
        // Feature: 功能模块级别的控制
        System.out.println("Feature的用途 - 功能模块控制：");
        ShaderConfiguration featureConfig = ShaderConfiguration.builder()
            .enableFeature("pbr_lighting")     // 启用整个PBR光照系统
            .enableFeature("shadow_mapping")   // 启用整个阴影映射系统
            .enableFeature("post_processing")  // 启用后处理管道
            .build();
        
        System.out.println("  pbr_lighting → 启用PBR相关的所有代码模块");
        System.out.println("  shadow_mapping → 启用阴影相关的所有算法");
        System.out.println("  post_processing → 启用后处理相关的所有效果");
        
        // Macro: 具体实现细节的控制
        System.out.println("\nMacro的用途 - 实现细节控制：");
        ShaderConfiguration macroConfig = ShaderConfiguration.builder()
            .define("PBR_METALLIC_WORKFLOW", true)    // PBR的具体工作流
            .define("SHADOW_PCF_SAMPLES", 9)          // 阴影的具体采样数
            .define("BLOOM_PASSES", 3)                // 后处理的具体pass数
            .define("MAX_LIGHTS", 32)                 // 具体的数值限制
            .build();
        
        System.out.println("  PBR_METALLIC_WORKFLOW → PBR内部的工作流选择");
        System.out.println("  SHADOW_PCF_SAMPLES → 阴影算法的具体参数");
        System.out.println("  BLOOM_PASSES → 后处理算法的具体参数");
        System.out.println("  MAX_LIGHTS → 具体的数值配置");
        System.out.println();
    }
    
    private static void demonstrateCompositeFeatures() {
        System.out.println("3. Feature的复合特性 - 这是关键区别！");
        
        // 一个Feature可能包含多个相关的宏定义
        System.out.println("启用 pbr_lighting Feature 实际上会：");
        
        ShaderConfiguration config = new ShaderConfiguration();
        // 模拟 enableFeature("pbr_lighting") 的内部实现
        simulateFeatureExpansion(config, "pbr_lighting");
        
        System.out.println("自动设置的宏：");
        config.getMacros().forEach((name, value) -> 
            System.out.println("  " + name + " = " + value));
        
        System.out.println("\n这就是为什么需要Feature的原因：");
        System.out.println("  - 一次操作设置多个相关宏");
        System.out.println("  - 确保相关配置的一致性");
        System.out.println("  - 提供高层次的抽象接口");
        System.out.println("  - 避免用户遗漏相关配置");
        System.out.println();
    }
    
    private static void simulateFeatureExpansion(ShaderConfiguration config, String feature) {
        // 模拟Feature展开为多个宏的过程
        switch (feature) {
            case "pbr_lighting" -> {
                config.define("PBR_LIGHTING", "1");                    // 主开关
                config.define("ENABLE_METALLIC_ROUGHNESS", "1");       // 启用金属度-粗糙度工作流
                config.define("ENABLE_FRESNEL_CALCULATION", "1");      // 启用菲涅尔计算
                config.define("ENABLE_IBL_LIGHTING", "1");             // 启用基于图像的光照
                config.define("ENABLE_NORMAL_DISTRIBUTION", "1");      // 启用法线分布函数
                config.define("ENABLE_GEOMETRY_FUNCTION", "1");        // 启用几何函数
                config.define("PBR_WORKFLOW", "metallic_roughness");   // 设置工作流类型
            }
            case "shadow_mapping" -> {
                config.define("SHADOW_MAPPING", "1");                  // 主开关
                config.define("ENABLE_SHADOW_SAMPLING", "1");          // 启用阴影采样
                config.define("ENABLE_PCF_FILTERING", "1");            // 启用PCF过滤
                config.define("ENABLE_SHADOW_BIAS", "1");              // 启用阴影偏移
                config.define("DEFAULT_SHADOW_SAMPLES", "4");          // 默认采样数
                config.define("SHADOW_TECHNIQUE", "pcf");              // 默认技术
            }
        }
    }
    
    private static void demonstrateOptimizedDesign() {
        System.out.println("4. 优化后的设计理念");
        
        System.out.println("最佳实践的配置方式：");
        
        ShaderConfiguration optimizedConfig = ShaderConfiguration.builder()
            // === 使用Feature控制大功能模块 ===
            .enableFeature("pbr_lighting")      // 启用PBR（自动设置相关宏）
            .enableFeature("shadow_mapping")    // 启用阴影（自动设置相关宏）
            .enableFeature("normal_mapping")    // 启用法线贴图
            
            // === 使用Macro微调具体参数 ===
            .define("MAX_POINT_LIGHTS", 16)     // 具体数值配置
            .define("SHADOW_MAP_SIZE", 2048)    // 具体分辨率配置
            .define("PBR_WORKFLOW", "specular_glossiness")  // 覆盖默认工作流
            .define("SHADOW_PCF_SAMPLES", 9)    // 覆盖默认采样数
            
            // === 使用Properties存储元数据 ===
            .setProperty("quality_preset", "high")
            .setProperty("target_platform", "desktop")
            .build();
        
        System.out.println("\n这种设计的优势：");
        System.out.println("  1. 层次清晰：Feature负责模块，Macro负责细节");
        System.out.println("  2. 易于使用：Feature提供一键开关，Macro允许精细调节");
        System.out.println("  3. 避免错误：Feature确保相关配置的一致性");
        System.out.println("  4. 可扩展性：可以轻松添加新的Feature组合");
        System.out.println();
    }
    
    /**
     * 展示在实际shader中的应用
     */
    public static void demonstrateShaderUsage() {
        System.out.println("=== 在Shader中的实际应用 ===");
        
        String shaderExample = """
            #version 430 core
            
            // Feature级别的条件编译 - 控制大模块
            #ifdef PBR_LIGHTING
                #import <lighting/pbr_lighting>
                
                // Macro级别的细节控制 - 控制具体实现
                #if PBR_WORKFLOW == metallic_roughness
                    #define SAMPLE_METALLIC(uv) texture(metallicMap, uv).r
                    #define SAMPLE_ROUGHNESS(uv) texture(roughnessMap, uv).r
                #elif PBR_WORKFLOW == specular_glossiness
                    #define SAMPLE_SPECULAR(uv) texture(specularMap, uv).rgb
                    #define SAMPLE_GLOSSINESS(uv) texture(glossinessMap, uv).r
                #endif
                
            #else
                #import <lighting/basic_lighting>
            #endif
            
            #ifdef SHADOW_MAPPING
                #import <shadows/shadow_sampling>
                
                // 使用Macro控制具体的采样策略
                #if SHADOW_PCF_SAMPLES == 4
                    #define SAMPLE_SHADOW(coord) sampleShadowPCF4(coord)
                #elif SHADOW_PCF_SAMPLES == 9
                    #define SAMPLE_SHADOW(coord) sampleShadowPCF9(coord)
                #else
                    #define SAMPLE_SHADOW(coord) sampleShadowBasic(coord)
                #endif
            #endif
            
            uniform vec3 lightPositions[MAX_POINT_LIGHTS];  // Macro控制数组大小
            
            void main() {
                // Feature决定使用哪套光照算法
                #ifdef PBR_LIGHTING
                    vec3 color = calculatePBRLighting();
                #else
                    vec3 color = calculateBasicLighting();
                #endif
                
                // Feature决定是否计算阴影
                #ifdef SHADOW_MAPPING
                    float shadow = SAMPLE_SHADOW(shadowCoord);  // Macro决定采样方法
                    color *= shadow;
                #endif
                
                gl_FragColor = vec4(color, 1.0);
            }
            """;
        
        System.out.println("Shader代码示例展示了两者的配合：");
        System.out.println("  - Feature控制是否包含整个代码模块");
        System.out.println("  - Macro控制模块内部的具体实现细节");
        System.out.println("  - 两者结合提供了灵活且强大的控制能力");
    }
    
    /**
     * 总结两者的关系
     */
    public static void summarizeRelationship() {
        System.out.println("\n=== Feature与Macro的关系总结 ===");
        
        System.out.println("它们不是重复，而是互补的：");
        System.out.println();
        
        System.out.println("Feature (功能特性)：");
        System.out.println("  - 作用域：模块级别");
        System.out.println("  - 粒度：粗粒度");
        System.out.println("  - 用途：功能开关、模块组合");
        System.out.println("  - 特点：一对多（一个Feature → 多个Macro）");
        System.out.println("  - 示例：pbr_lighting, shadow_mapping, post_processing");
        System.out.println();
        
        System.out.println("Macro (宏定义)：");
        System.out.println("  - 作用域：代码级别");
        System.out.println("  - 粒度：细粒度");
        System.out.println("  - 用途：数值配置、算法选择、条件编译");
        System.out.println("  - 特点：一对一（一个Macro → 一个值）");
        System.out.println("  - 示例：MAX_LIGHTS=16, SHADOW_SAMPLES=9, PI=3.14159");
        System.out.println();
        
        System.out.println("最佳实践：");
        System.out.println("  1. 用Feature控制要不要某个功能");
        System.out.println("  2. 用Macro控制功能的具体参数");
        System.out.println("  3. Feature提供合理的默认Macro值");
        System.out.println("  4. Macro可以覆盖Feature的默认值");
    }
}
