package rogo.sketch.core.shader.config;

import rogo.sketch.core.util.KeyId;

/**
 * 展示配置属性的实际工作流程
 */
public class ConfigurationWorkflowExample {
    
    /**
     * 演示配置属性的完整工作流程
     */
    public static void demonstrateWorkflow() {
        System.out.println("=== 配置属性工作流程演示 ===\n");
        
        // 第一步：创建配置
        ShaderConfiguration config = createLightingConfiguration();
        
        // 第二步：应用配置到shader
        applyConfigurationToShader(config);
        
        // 第三步：运行时动态调整
        demonstrateDynamicConfiguration();
        
        // 第四步：配置优先级和继承
        demonstrateConfigurationPriority();
    }
    
    private static ShaderConfiguration createLightingConfiguration() {
        System.out.println("1. 创建Lighting Shader配置");
        
        ShaderConfiguration config = ShaderConfiguration.builder()
            // === 光源配置 ===
            .define("MAX_POINT_LIGHTS", 16)      // 最大点光源数量
            .define("MAX_SPOT_LIGHTS", 8)        // 最大聚光灯数量  
            .define("MAX_DIRECTIONAL_LIGHTS", 2) // 最大方向光数量
            
            // === 阴影配置 ===
            .define("SHADOW_MAP_SIZE", 1024)     // 阴影贴图尺寸
            .define("CASCADE_COUNT", 3)          // 级联阴影层数
            .define("PCF_SAMPLES", 9)            // PCF采样数
            
            // === 质量配置 ===
            .define("QUALITY_LEVEL", 2)          // 质量等级 0-3
            .define("LOD_BIAS", 0.5f)           // LOD偏移
            
            // === 功能开关 ===
            .enableFeature("pbr_lighting")       // 启用PBR光照
            .enableFeature("shadow_mapping")     // 启用阴影映射
            .enableFeature("normal_mapping")     // 启用法线贴图
            .enableFeature("ambient_occlusion")  // 启用环境光遮蔽
            
            // === 应用程序属性 ===
            .setProperty("lighting_model", "pbr")
            .setProperty("shadow_technique", "cascade_pcf")
            .setProperty("performance_target", "60fps")
            .setProperty("memory_budget", "256MB")
            .build();
        
        System.out.println("配置创建完成:");
        System.out.println("  宏定义: " + config.getMacros().size() + " 项");
        System.out.println("  功能特性: " + config.getFeatures().size() + " 项");
        System.out.println("  属性配置: " + config.getProperties().size() + " 项");
        System.out.println("  配置哈希: " + Integer.toHexString(config.getConfigurationHash()));
        System.out.println();
        
        return config;
    }
    
    private static void applyConfigurationToShader(ShaderConfiguration config) {
        System.out.println("2. 配置应用到Shader的过程");
        
        // 模拟预处理器处理过程
        System.out.println("预处理器处理步骤：");
        
        // 步骤1：宏替换
        System.out.println("  a) 宏替换：");
        config.getMacros().forEach((name, value) -> {
            System.out.println("     " + name + " → " + value);
        });
        
        // 步骤2：条件编译
        System.out.println("  b) 条件编译：");
        config.getFeatures().forEach(feature -> {
            String macroName = feature.toUpperCase();
            System.out.println("     #ifdef " + macroName + " → 包含对应代码块");
        });
        
        // 步骤3：生成最终shader代码
        System.out.println("  c) 生成的shader特征：");
        System.out.println("     - 支持 " + config.getMacro("MAX_POINT_LIGHTS") + " 个点光源");
        System.out.println("     - 阴影贴图分辨率: " + config.getMacro("SHADOW_MAP_SIZE"));
        System.out.println("     - 启用PBR光照模型");
        System.out.println("     - 包含法线贴图支持");
        System.out.println("     - 启用环境光遮蔽计算");
        System.out.println();
    }
    
    private static void demonstrateDynamicConfiguration() {
        System.out.println("3. 运行时动态配置调整");
        
        KeyId shaderId = KeyId.of("lighting:main");
        ShaderConfigurationManager manager = ShaderConfigurationManager.getInstance();
        
        // 初始配置
        ShaderConfiguration initialConfig = ShaderConfiguration.builder()
            .define("QUALITY_LEVEL", 1)
            .enableFeature("basic_lighting")
            .setProperty("target_fps", 30)
            .build();
        
        manager.setConfiguration(shaderId, initialConfig);
        System.out.println("初始配置: 低质量模式");
        
        // 模拟性能检测后的调整
        System.out.println("检测到性能充足，提升质量...");
        manager.updateConfiguration(shaderId, config -> {
            config.define("QUALITY_LEVEL", 3);           // 提升质量等级
            config.disableFeature("basic_lighting");     // 禁用基础光照
            config.enableFeature("pbr_lighting");        // 启用PBR光照
            config.enableFeature("shadow_mapping");      // 启用阴影
            config.setProperty("target_fps", 60);        // 提升目标帧率
        });
        System.out.println("配置已更新: 高质量模式");
        System.out.println("→ Shader将自动重新编译");
        
        // 模拟低性能设备的降级
        System.out.println("检测到性能不足，降级配置...");
        manager.updateConfiguration(shaderId, config -> {
            config.define("QUALITY_LEVEL", 0);
            config.define("MAX_POINT_LIGHTS", 4);        // 减少光源数量
            config.disableFeature("shadow_mapping");     // 禁用阴影
            config.disableFeature("ambient_occlusion");  // 禁用AO
            config.setProperty("target_fps", 30);
        });
        System.out.println("配置已降级: 性能优先模式");
        System.out.println("→ Shader将自动重新编译以匹配新配置");
        System.out.println();
    }
    
    private static void demonstrateConfigurationPriority() {
        System.out.println("4. 配置优先级和继承");
        
        ShaderConfigurationManager manager = ShaderConfigurationManager.getInstance();
        
        // 全局配置（最低优先级）
        System.out.println("设置全局配置（所有shader继承）：");
        manager.updateGlobalConfiguration(globalConfig -> {
            globalConfig.define("GLOBAL_DEBUG", "1");
            globalConfig.define("API_VERSION", "430");
            globalConfig.enableFeature("error_checking");
            globalConfig.setProperty("renderer", "opengl");
        });
        
        // 预设配置（中等优先级）
        System.out.println("应用质量预设：");
        KeyId shaderId = KeyId.of("test:shader");
        ShaderConfiguration qualityPreset = ShaderConfigurationManager.createPreset("quality");
        manager.setConfiguration(shaderId, qualityPreset);
        
        // 特定配置（最高优先级）
        System.out.println("设置特定shader配置（覆盖预设）：");
        manager.updateConfiguration(shaderId, config -> {
            config.define("CUSTOM_FEATURE", "enabled");   // 新增配置
            config.define("HIGH_QUALITY", "ultra");       // 覆盖预设配置
        });
        
        // 最终配置结果
        ShaderConfiguration finalConfig = manager.getConfiguration(shaderId);
        System.out.println("最终有效配置：");
        System.out.println("  来源1 - 全局: GLOBAL_DEBUG, API_VERSION, error_checking");
        System.out.println("  来源2 - 预设: 质量预设的所有配置");  
        System.out.println("  来源3 - 特定: CUSTOM_FEATURE, HIGH_QUALITY (覆盖)");
        System.out.println("  总计宏数量: " + finalConfig.getMacros().size());
        System.out.println("  配置哈希: " + Integer.toHexString(finalConfig.getConfigurationHash()));
        System.out.println();
    }
    
    /**
     * 演示不同配置对性能的影响
     */
    public static void demonstratePerformanceImpact() {
        System.out.println("=== 配置对性能的影响 ===");
        
        // 性能优先配置
        ShaderConfiguration performanceConfig = ShaderConfiguration.builder()
            .define("MAX_LIGHTS", 4)              // 减少光源
            .define("SHADOW_SAMPLES", 1)          // 减少阴影采样
            .define("QUALITY_LEVEL", 0)           // 最低质量
            .enableFeature("fast_approximations") // 快速近似
            .setProperty("target_fps", 60)
            .build();
        
        // 质量优先配置  
        ShaderConfiguration qualityConfig = ShaderConfiguration.builder()
            .define("MAX_LIGHTS", 64)             // 大量光源
            .define("SHADOW_SAMPLES", 25)         // 高质量阴影
            .define("QUALITY_LEVEL", 3)           // 最高质量
            .enableFeature("pbr_lighting")
            .enableFeature("volumetric_effects")
            .setProperty("target_fps", 30)
            .build();
        
        System.out.println("性能配置特点：");
        System.out.println("  - 光源数量: " + performanceConfig.getMacro("MAX_LIGHTS"));
        System.out.println("  - 阴影采样: " + performanceConfig.getMacro("SHADOW_SAMPLES"));
        System.out.println("  - 预期性能: 高帧率，低GPU占用");
        
        System.out.println("质量配置特点：");
        System.out.println("  - 光源数量: " + qualityConfig.getMacro("MAX_LIGHTS"));
        System.out.println("  - 阴影采样: " + qualityConfig.getMacro("SHADOW_SAMPLES"));
        System.out.println("  - 预期性能: 低帧率，高GPU占用，高视觉质量");
    }
}
