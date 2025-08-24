package rogo.sketch.render;

import net.minecraft.server.packs.resources.ResourceManager;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.util.Identifier;
import rogo.sketch.vanilla.McGraphicsPipeline;

/**
 * 展示可重载RenderSetting的完整使用流程
 */
public class ReloadableRenderSettingExample {
    
    public static void demonstrateReloadableRenderSettings(ResourceManager resourceManager, McGraphicsPipeline pipeline) {
        System.out.println("=== 可重载RenderSetting演示 ===\n");
        
        // 1. 启用增强功能
//        GraphicsResourceManager.getInstance().enableEnhancedFeatures(resourceManager);
//
//        // 2. 演示自动重载功能
//        demonstrateAutomaticReloading(pipeline);
//
//        // 3. 演示在GraphicsPipeline中的使用
//        demonstratePipelineIntegration(pipeline);
//
//        // 4. 演示批处理保持功能
//        demonstrateBatchingPreservation(pipeline);
    }
    
    private static void demonstrateAutomaticReloading(McGraphicsPipeline pipeline) {
        System.out.println("1. 自动重载功能演示");
        
        // 从资源管理器获取PartialRenderSetting
        Identifier settingId = Identifier.of("sketchrender:hierarchy_depth_buffer_first");
        var partialSettingRef = GraphicsResourceManager.getInstance()
                .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, settingId);
        
        if (partialSettingRef.isAvailable()) {
            PartialRenderSetting partialSetting = (PartialRenderSetting) partialSettingRef.get();
            
            // 创建可重载的RenderSetting
            RenderSetting reloadableSetting = RenderSetting.computeShader(partialSetting);
            
            // 添加更新监听器
            reloadableSetting.addUpdateListener(newSetting -> {
                System.out.println("RenderSetting updated! New setting: " + newSetting.hashCode());
                // 这里可以处理setting更新后的逻辑
            });
            
            System.out.println("Created reloadable RenderSetting from: " + settingId);
            System.out.println("Is reloadable: " + reloadableSetting.isReloadable());
            
            // 模拟资源重载（在实际环境中，这会由ResourceManager触发）
            partialSetting.forceReload();
            
        } else {
            System.out.println("PartialRenderSetting not found: " + settingId);
        }
        
        System.out.println();
    }
    
    private static void demonstratePipelineIntegration(McGraphicsPipeline pipeline) {
        System.out.println("2. GraphicsPipeline集成演示");
        
        // 获取可重载的PartialRenderSetting
        Identifier settingId = Identifier.of("sketchrender:hierarchy_depth_buffer_second");
        var partialSettingRef = GraphicsResourceManager.getInstance()
                .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, settingId);
        
        if (partialSettingRef.isAvailable()) {
            PartialRenderSetting partialSetting = (PartialRenderSetting) partialSettingRef.get();
            RenderSetting reloadableSetting = RenderSetting.computeShader(partialSetting);
            
            // 添加到pipeline（这会自动设置重载监听器）
            pipeline.addGraphInstance(
                Identifier.of("hiz_stage"), 
                new TestGraphicsInstance(), 
                reloadableSetting
            );
            
            System.out.println("Added reloadable RenderSetting to pipeline");
            
            // 获取重载统计信息
            var reloadableStats = pipeline.getReloadableSettingsStats();
            System.out.println("Reloadable settings stats:");
            reloadableStats.forEach(stats -> System.out.println("  " + stats));
            
        }
        
        System.out.println();
    }
    
    private static void demonstrateBatchingPreservation(McGraphicsPipeline pipeline) {
        System.out.println("3. 批处理保持功能演示");
        
        // 创建多个使用相同RenderSetting的实例
        Identifier settingId = Identifier.of("sketchrender:cull_entity_batch");
        var partialSettingRef = GraphicsResourceManager.getInstance()
                .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, settingId);
        
        if (partialSettingRef.isAvailable()) {
            PartialRenderSetting partialSetting = (PartialRenderSetting) partialSettingRef.get();
            RenderSetting sharedSetting = RenderSetting.computeShader(partialSetting);
            
            // 添加多个使用相同setting的实例
            for (int i = 0; i < 5; i++) {
                pipeline.addGraphInstance(
                    Identifier.of("culling_stage"),
                    new TestGraphicsInstance(),
                    sharedSetting  // 相同的setting，会被合批
                );
            }
            
            System.out.println("Added 5 instances with shared RenderSetting");
            System.out.println("These will be batched together for rendering");
            
            // 模拟setting更新
            System.out.println("Simulating setting update...");
            sharedSetting.forceReload();
            System.out.println("After reload, batching is preserved with new setting");
            
        }
        
        System.out.println();
    }
    
    /**
     * 简单的测试GraphicsInstance
     */
    private static class TestGraphicsInstance implements rogo.sketch.api.GraphicsInstance {
        private final Identifier id = Identifier.of("test_instance_" + System.nanoTime());
        
        @Override
        public Identifier getIdentifier() {
            return id;
        }

        @Override
        public boolean shouldTick() {
            return false;
        }

        @Override
        public <C extends RenderContext> void tick(C context) {

        }

        @Override
        public boolean shouldDiscard() {
            return false;
        }

        @Override
        public boolean shouldRender() {
            return true;
        }

        @Override
        public <C extends RenderContext> void afterDraw(C context) {

        }


    }
}