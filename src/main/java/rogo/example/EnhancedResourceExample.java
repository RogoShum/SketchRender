package rogo.example;

import rogo.sketch.render.resource.*;
import rogo.sketch.render.resource.loader.RenderTargetLoader;
import rogo.sketch.render.resource.loader.TextureLoader;
import rogo.sketch.util.Identifier;

/**
 * Examples demonstrating the enhanced Texture and RenderTarget system
 */
public class EnhancedResourceExample {

    public static void main(String[] args) {
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();

        // Register loaders
        resourceManager.registerLoader(ResourceTypes.TEXTURE, new TextureLoader());
        resourceManager.registerLoader(ResourceTypes.RENDER_TARGET, new RenderTargetLoader());

        // Example 1: Custom textures for render targets
        customTextureExample(resourceManager);

        // Example 2: MC texture integration
        mcTextureExample(resourceManager);

        // Example 3: Resolution modes
        resolutionModeExample(resourceManager);

        // Example 4: Texture sharing and conflicts
        textureConflictExample(resourceManager);

        // Cleanup
        resourceManager.dispose();
    }

    /**
     * Example 1: Custom textures for render targets
     */
    private static void customTextureExample(GraphicsResourceManager resourceManager) {
        System.out.println("=== Custom Texture Example ===");

        // Define custom textures (no dimensions - managed by RT)
        String colorTextureJson = """
                {
                    "identifier": "color_buffer",
                    "format": "RGBA",
                    "filter": "LINEAR",
                    "wrap": "CLAMP_TO_EDGE"
                }
                """;

        String depthTextureJson = """
                {
                    "identifier": "depth_buffer",
                    "format": "DEPTH",
                    "filter": "NEAREST",
                    "wrap": "CLAMP_TO_EDGE"
                }
                """;

        resourceManager.registerJson(ResourceTypes.TEXTURE, Identifier.of("color_buffer"), colorTextureJson);
        resourceManager.registerJson(ResourceTypes.TEXTURE, Identifier.of("depth_buffer"), depthTextureJson);

        // Define render target that manages texture sizes
        String renderTargetJson = """
                {
                    "identifier": "main_framebuffer",
                    "resolutionMode": "FIXED",
                    "width": 1920,
                    "height": 1080,
                    "clearColor": "#FF336699",
                    "colorAttachments": [
                        "color_buffer"
                    ],
                    "depthAttachment": "depth_buffer",
                    "clearSettings": {
                        "color": true,
                        "depth": true,
                        "stencil": false
                    }
                }
                """;

        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("main_framebuffer"), renderTargetJson);

        // Use the render target
        ResourceReference<RenderTarget> rtRef = resourceManager.getReference(ResourceTypes.RENDER_TARGET, Identifier.of("main_framebuffer"));
        rtRef.ifPresent(rt -> {
            System.out.println("Created render target: " + rt.getIdentifier());
            System.out.println("  Resolution: " + rt.getCurrentWidth() + "x" + rt.getCurrentHeight());
            System.out.println("  Mode: " + rt.getResolutionMode());
        });
    }

    /**
     * Example 2: MC texture integration
     */
    private static void mcTextureExample(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== MC Texture Example ===");

        // Define MC-compatible texture
        String mcTextureJson = """
                {
                    "identifier": "mc_block_texture",
                    "mcResourceLocation": "minecraft:block/stone"
                }
                """;

        resourceManager.registerJson(ResourceTypes.TEXTURE, Identifier.of("mc_block_texture"), mcTextureJson);

        // Use in render target
        String rtWithMcTextureJson = """
                {
                    "identifier": "mc_preview",
                    "resolutionMode": "FIXED",
                    "width": 512,
                    "height": 512,
                    "colorAttachments": [
                        "mc_block_texture"
                    ]
                }
                """;

        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("mc_preview"), rtWithMcTextureJson);

        ResourceReference<Texture> mcTextureRef = resourceManager.getReference(ResourceTypes.TEXTURE, Identifier.of("mc_block_texture"));
        mcTextureRef.ifPresent(texture -> {
            System.out.println("MC Texture: " + texture.getIdentifier());
        });
    }

    /**
     * Example 3: Different resolution modes
     */
    private static void resolutionModeExample(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== Resolution Mode Example ===");

        // Half-resolution render target
        String halfResJson = """
                {
                    "identifier": "half_res_buffer",
                    "resolutionMode": "SCREEN_RELATIVE",
                    "scale": 0.5,
                    "clearColor": "#FF000000",
                    "colorAttachments": [
                        "color_buffer"
                    ]
                }
                """;

        // Full screen render target
        String fullScreenJson = """
                {
                    "identifier": "fullscreen_buffer",
                    "resolutionMode": "SCREEN_SIZE",
                    "clearColor": "#FFFFFFFF",
                    "colorAttachments": [
                        "color_buffer"
                    ]
                }
                """;

        // Super-resolution render target
        String superResJson = """
                {
                    "identifier": "super_res_buffer",
                    "resolutionMode": "SCREEN_RELATIVE",
                    "scaleX": 2.0,
                    "scaleY": 2.0,
                    "clearColor": "#FF00FF00",
                    "colorAttachments": [
                        "color_buffer"
                    ]
                }
                """;

        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("half_res_buffer"), halfResJson);
        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("fullscreen_buffer"), fullScreenJson);
        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("super_res_buffer"), superResJson);

        // Show resolution info
        ResourceReference<RenderTarget> halfResRef = resourceManager.getReference(ResourceTypes.RENDER_TARGET, Identifier.of("half_res_buffer"));
        halfResRef.ifPresent(rt -> {
            System.out.println("Half-res RT: " + rt.getCurrentWidth() + "x" + rt.getCurrentHeight() +
                    " (scale: " + rt.getScaleX() + ")");
        });

        ResourceReference<RenderTarget> superResRef = resourceManager.getReference(ResourceTypes.RENDER_TARGET, Identifier.of("super_res_buffer"));
        superResRef.ifPresent(rt -> {
            System.out.println("Super-res RT: " + rt.getCurrentWidth() + "x" + rt.getCurrentHeight() +
                    " (scale: " + rt.getScaleX() + "x" + rt.getScaleY() + ")");
        });
    }

    /**
     * Example 4: Texture sharing and conflict detection
     */
    private static void textureConflictExample(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== Texture Conflict Example ===");

        // Create a shared texture
        String sharedTextureJson = """
                {
                    "identifier": "shared_texture",
                    "format": "RGBA",
                    "filter": "LINEAR"
                }
                """;

        resourceManager.registerJson(ResourceTypes.TEXTURE, Identifier.of("shared_texture"), sharedTextureJson);

        // Two render targets with different sizes using the same texture
        String rt1Json = """
                {
                    "identifier": "rt_512",
                    "resolutionMode": "FIXED",
                    "width": 512,
                    "height": 512,
                    "colorAttachments": [
                        "shared_texture"
                    ]
                }
                """;

        String rt2Json = """
                {
                    "identifier": "rt_1024",
                    "resolutionMode": "FIXED",
                    "width": 1024,
                    "height": 1024,
                    "colorAttachments": [
                        "shared_texture"
                    ]
                }
                """;

        // This should generate a warning about texture size conflict
        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("rt_512"), rt1Json);
        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("rt_1024"), rt2Json);

        System.out.println("Created render targets with conflicting texture sizes - check console for warnings");
    }

    /**
     * Example 5: Runtime resize operations
     */
    public static void runtimeResizeExample(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== Runtime Resize Example ===");

        ResourceReference<RenderTarget> rtRef = resourceManager.getReference(ResourceTypes.RENDER_TARGET, Identifier.of("main_framebuffer"));
        rtRef.ifPresent(rt -> {
            System.out.println("Original size: " + rt.getCurrentWidth() + "x" + rt.getCurrentHeight());

            // Simulate window resize
            rt.resize(2560, 1440);
            System.out.println("After resize: " + rt.getCurrentWidth() + "x" + rt.getCurrentHeight());

            // Update dimensions based on resolution mode
            rt.updateDimensions();
            System.out.println("After update: " + rt.getCurrentWidth() + "x" + rt.getCurrentHeight());
        });
    }
} 