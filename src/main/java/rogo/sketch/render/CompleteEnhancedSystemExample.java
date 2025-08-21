package rogo.sketch.render;

import net.minecraft.server.packs.resources.ResourceManager;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.config.ShaderConfigurationManager;
import rogo.sketch.util.Identifier;
import rogo.sketch.vanilla.McGraphicsPipeline;

/**
 * 完整的增强系统使用示例
 * 展示如何在现有架构基础上使用所有新功能
 */
public class CompleteEnhancedSystemExample {
    
    public static void demonstrateCompleteSystem(ResourceManager resourceManager, McGraphicsPipeline pipeline) {
        System.out.println("=== 完整增强系统演示 ===\n");
        
        // 第一步：启用增强功能
        initializeEnhancedSystem(resourceManager);
        
        // 第二步：配置Shader预处理
        setupShaderPreprocessing();
        
        // 第三步：创建可重载的RenderSetting
        setupReloadableRenderSettings(pipeline);
        
        // 第四步：演示热重载功能
        demonstrateHotReloading(pipeline);
        
        // 第五步：性能监控和统计
        monitorSystemPerformance(pipeline);
    }
    
    private static void initializeEnhancedSystem(ResourceManager resourceManager) {
        System.out.println("1. 初始化增强系统");
        
        // 启用GraphicsResourceManager的增强功能
        GraphicsResourceManager manager = GraphicsResourceManager.getInstance();
        manager.enableEnhancedFeatures(resourceManager);
        
        System.out.println("✓ 增强功能已启用");
        System.out.println("  - Shader预处理支持");
        System.out.println("  - 自动重载监听器");
        System.out.println("  - 多资源类型管理");
        System.out.println();
    }
    
    private static void setupShaderPreprocessing() {
        System.out.println("2. 配置Shader预处理");
        
        ShaderConfigurationManager configManager = ShaderConfigurationManager.getInstance();
        
        // 设置全局配置
        configManager.updateGlobalConfiguration(config -> {
            config.define("GLOBAL_DEBUG", "1");
            config.define("API_VERSION", "430");
            config.enableFeature("error_checking");
        });
        
        // 配置具体的shader
        Identifier lightingShader = Identifier.of("sketch:advanced_lighting");
        configManager.setConfiguration(lightingShader, ShaderConfiguration.builder()
            .define("MAX_LIGHTS", 32)
            .define("SHADOW_MAP_SIZE", 2048)
            .enableFeature("pbr_lighting")
            .enableFeature("shadow_mapping")
            .enableFeature("normal_mapping")
            .setProperty("quality_preset", "ultra")
            .build());
        
        System.out.println("✓ Shader配置完成");
        System.out.println("  - 全局配置：调试和API版本");
        System.out.println("  - 光照Shader：PBR + 阴影 + 法线贴图");
        System.out.println();
    }
    
    private static void setupReloadableRenderSettings(McGraphicsPipeline pipeline) {
        System.out.println("3. 设置可重载的RenderSetting");
        
        GraphicsResourceManager manager = GraphicsResourceManager.getInstance();
        
        // 获取各种PartialRenderSetting并创建可重载的RenderSetting
        String[] settingIds = {
            "sketchrender:hierarchy_depth_buffer_first",
            "sketchrender:hierarchy_depth_buffer_second", 
            "sketchrender:cull_entity_batch"
        };
        
        int reloadableCount = 0;
        for (String settingIdStr : settingIds) {
            Identifier settingId = Identifier.of(settingIdStr);
            var partialRef = manager.getReference(ResourceTypes.PARTIAL_RENDER_SETTING, settingId);
            
            if (partialRef.isAvailable()) {
                PartialRenderSetting partial = (PartialRenderSetting) partialRef.get();
                RenderSetting setting = RenderSetting.computeShader(partial);
                
                if (setting.isReloadable()) {
                    reloadableCount++;
                    
                    // 添加更新监听器
                    setting.addUpdateListener(newSetting -> {
                        System.out.println("RenderSetting " + settingId + " 已更新");
                    });
                }
                
                System.out.println("  ✓ " + settingId + " (可重载: " + setting.isReloadable() + ")");
            }
        }
        
        System.out.println("总计创建 " + reloadableCount + " 个可重载的RenderSetting");
        System.out.println();
    }
    
    private static void demonstrateHotReloading(McGraphicsPipeline pipeline) {
        System.out.println("4. 演示热重载功能");
        
        // 获取pipeline统计信息
        var initialStats = pipeline.getStats();
        System.out.println("初始状态: " + initialStats);
        
        var reloadableStats = pipeline.getReloadableSettingsStats();
        System.out.println("可重载设置统计:");
        reloadableStats.forEach(stats -> System.out.println("  " + stats));
        
        // 模拟资源重载
        System.out.println("\n模拟资源包重载...");
        
        // 强制重载所有可重载的RenderSetting
        pipeline.forceReloadAllRenderSettings();
        
        // 检查重载后的状态
        var finalStats = pipeline.getStats();
        System.out.println("重载后状态: " + finalStats);
        System.out.println();
    }
    
    private static void monitorSystemPerformance(McGraphicsPipeline pipeline) {
        System.out.println("5. 系统性能监控");
        
        // Pipeline统计
        var pipelineStats = pipeline.getStats();
        System.out.println("Pipeline统计: " + pipelineStats);
        
        // Shader配置统计
        ShaderConfigurationManager configManager = ShaderConfigurationManager.getInstance();
        var configuredShaders = configManager.getConfiguredShaders();
        System.out.println("已配置的Shader数量: " + configuredShaders.size());
        
        // 资源管理器统计
        GraphicsResourceManager manager = GraphicsResourceManager.getInstance();
        
        // 检查各类资源
        var shaderPrograms = manager.getResourcesOfType(ResourceTypes.SHADER_PROGRAM);
        var partialSettings = manager.getResourcesOfType(ResourceTypes.PARTIAL_RENDER_SETTING);
        var textures = manager.getResourcesOfType(ResourceTypes.TEXTURE);
        
        System.out.println("资源统计:");
        System.out.println("  - Shader程序: " + shaderPrograms.size());
        System.out.println("  - 部分渲染设置: " + partialSettings.size());
        System.out.println("  - 纹理: " + textures.size());
        
        // 内存使用估算
        long estimatedMemory = (shaderPrograms.size() * 50 + // 每个shader约50KB
                               partialSettings.size() * 1 +   // 每个setting约1KB
                               textures.size() * 100) * 1024; // 每个纹理约100KB
        
        System.out.println("估算内存使用: " + (estimatedMemory / 1024 / 1024) + "MB");
        System.out.println();
    }
    
    /**
     * 演示完整的开发工作流程
     */
    public static void demonstrateDevelopmentWorkflow() {
        System.out.println("=== 开发工作流程演示 ===");
        
        System.out.println("1. 开发阶段 - 使用调试配置");
        ShaderConfiguration debugConfig = ShaderConfigurationManager.createPreset("debug");
        System.out.println("   调试配置: " + debugConfig.getMacros().keySet());
        
        System.out.println("\n2. 性能测试 - 切换到性能配置");
        ShaderConfiguration perfConfig = ShaderConfigurationManager.createPreset("performance");
        System.out.println("   性能配置: " + perfConfig.getMacros().keySet());
        
        System.out.println("\n3. 发布阶段 - 使用质量配置");
        ShaderConfiguration qualityConfig = ShaderConfigurationManager.createPreset("quality");
        System.out.println("   质量配置: " + qualityConfig.getMacros().keySet());
        
        System.out.println("\n4. 运行时 - 根据硬件动态调整");
        System.out.println("   检测GPU能力 → 动态修改配置 → 自动重编译shader");
        
        System.out.println("\n整个过程中：");
        System.out.println("✓ 无需重启应用");
        System.out.println("✓ 配置即时生效");
        System.out.println("✓ 保持渲染状态");
        System.out.println("✓ 自动错误恢复");
    }
    
    /**
     * 展示系统的主要优势
     */
    public static void summarizeAdvantages() {
        System.out.println("\n=== 系统优势总结 ===");
        
        System.out.println("🎯 架构优势:");
        System.out.println("  ✓ 基于现有系统扩展，无破坏性变更");
        System.out.println("  ✓ 保持GraphicsResourceManager的多资源管理特性");
        System.out.println("  ✓ 可选择性启用功能，按需使用");
        
        System.out.println("\n⚡ 性能优势:");
        System.out.println("  ✓ 智能重编译，仅在需要时执行");
        System.out.println("  ✓ 保持RenderSetting批处理特性");
        System.out.println("  ✓ 异步处理支持");
        
        System.out.println("\n🔧 开发优势:");
        System.out.println("  ✓ Shader import/include系统");
        System.out.println("  ✓ 宏定义和条件编译");
        System.out.println("  ✓ 热重载支持");
        System.out.println("  ✓ 配置文件驱动");
        
        System.out.println("\n🛡️ 稳定性优势:");
        System.out.println("  ✓ 错误恢复机制");
        System.out.println("  ✓ 依赖关系跟踪");
        System.out.println("  ✓ 版本兼容性检查");
        System.out.println("  ✓ 详细的日志和监控");
    }
}
