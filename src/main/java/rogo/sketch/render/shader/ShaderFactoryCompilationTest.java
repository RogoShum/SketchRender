package rogo.sketch.render.shader;

import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 简单的编译测试，验证ShaderFactory的方法调用是否正确
 */
public class ShaderFactoryCompilationTest {
    
    public static void testShaderFactoryCompilation(ResourceProvider resourceProvider) {
        System.out.println("=== ShaderFactory编译测试 ===");
        
        try {
            // 测试基础工厂创建
            ShaderFactory basicFactory = new ShaderFactory(resourceProvider, false);
            System.out.println("✓ 基础工厂创建成功");
            
            // 测试可重编译工厂创建
            ShaderFactory recompilableFactory = new ShaderFactory(resourceProvider, true);
            System.out.println("✓ 可重编译工厂创建成功");
            
            // 测试基础shader创建
            String simpleComputeSource = "#version 430 core\nlayout (local_size_x = 32) in;\nvoid main() { }";
            ShaderProvider basicShader = basicFactory.createComputeShader(
                Identifier.of("test:basic_compute"), 
                simpleComputeSource
            );
            System.out.println("✓ 基础ComputeShader创建成功");
            basicShader.dispose();
            
            // 测试可重编译shader创建
            ShaderProvider recompilableShader = recompilableFactory.createComputeShader(
                Identifier.of("test:recompilable_compute"), 
                simpleComputeSource
            );
            System.out.println("✓ 可重编译ComputeShader创建成功");
            
            // 验证返回的是适配器类型
            if (recompilableShader instanceof ShaderAdapter) {
                System.out.println("✓ 返回了正确的ShaderAdapter类型");
                ShaderAdapter adapter = (ShaderAdapter) recompilableShader;
                System.out.println("  - 依赖数量: " + adapter.getRecompilableWrapper().getDependencies().size());
            }
            
            recompilableShader.dispose();
            
            // 测试图形shader创建
            Map<ShaderType, String> graphicsSources = new HashMap<>();
            graphicsSources.put(ShaderType.VERTEX, "#version 430 core\nlayout (location = 0) in vec3 pos;\nvoid main() { gl_Position = vec4(pos, 1.0); }");
            graphicsSources.put(ShaderType.FRAGMENT, "#version 430 core\nout vec4 fragColor;\nvoid main() { fragColor = vec4(1.0); }");
            
            ShaderProvider graphicsShader = recompilableFactory.createGraphicsShader(
                Identifier.of("test:graphics"), 
                graphicsSources
            );
            System.out.println("✓ 可重编译GraphicsShader创建成功");
            graphicsShader.dispose();
            
            System.out.println("🎉 所有编译测试通过！");
            
        } catch (IOException e) {
            System.err.println("❌ 编译测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试工厂配置功能
     */
    public static void testFactoryConfiguration(ResourceProvider resourceProvider) {
        System.out.println("\n=== 工厂配置测试 ===");
        
        try {
            ShaderFactory factory = new ShaderFactory(resourceProvider);
            
            // 测试配置设置
            Identifier shaderId = Identifier.of("test:configured_shader");
            factory.withConfiguration(shaderId, config -> {
                config.define("TEST_MACRO", "1");
                config.enableFeature("test_feature");
            });
            
            System.out.println("✓ 工厂配置设置成功");
            
            // 测试预设配置
            ShaderFactory presetFactory = ShaderFactory.withPreset(resourceProvider, "debug");
            System.out.println("✓ 预设工厂创建成功");
            
        } catch (Exception e) {
            System.err.println("❌ 配置测试失败: " + e.getMessage());
        }
    }
}
