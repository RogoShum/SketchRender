package rogo.example;

import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.resource.Texture;
import rogo.sketch.render.resource.loader.RenderTargetLoader;
import rogo.sketch.render.resource.loader.TextureHelper;
import rogo.sketch.render.resource.loader.TextureLoader;
import rogo.sketch.util.Identifier;

/**
 * Examples demonstrating the smart texture loading system
 */
public class SmartTextureExample {

    public static void main(String[] args) {
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();

        // Register loaders
        resourceManager.registerLoader(ResourceTypes.TEXTURE, new TextureLoader());
        resourceManager.registerLoader(ResourceTypes.RENDER_TARGET, new RenderTargetLoader());

        // Example 1: Traditional separate texture registration
        traditionalTextureExample(resourceManager);

        // Example 2: Inline texture definitions in RenderTarget
        inlineTextureExample(resourceManager);

        // Example 3: Mixed texture types (regular + MC)
        mixedTextureExample(resourceManager);

        // Example 4: Texture reuse and smart detection
        textureReuseExample(resourceManager);

        // Cleanup
        resourceManager.dispose();
    }

    /**
     * Example 1: Traditional separate texture registration
     */
    private static void traditionalTextureExample(GraphicsResourceManager resourceManager) {
        System.out.println("=== Traditional Texture Example ===");

        // Register textures first
        Identifier colorTextureId = TextureHelper.registerBasicTexture(
                resourceManager, "scene_color", "RGBA", "LINEAR", "CLAMP_TO_EDGE"
        );

        Identifier depthTextureId = TextureHelper.registerBasicTexture(
                resourceManager, "scene_depth", "DEPTH", "NEAREST", "CLAMP_TO_EDGE"
        );

        // Use pre-registered textures in RenderTarget
        String renderTargetJson = """
                {
                    "identifier": "scene_buffer",
                    "resolutionMode": "FIXED",
                    "width": 1920,
                    "height": 1080,
                    "clearColor": "#FF0066CC",
                    "colorAttachments": [
                        "scene_color"
                    ],
                    "depthAttachment": "scene_depth"
                }
                """;

        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("scene_buffer"), renderTargetJson);

        System.out.println("Created scene buffer with pre-registered textures");
    }

    /**
     * Example 2: Inline texture definitions
     */
    private static void inlineTextureExample(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== Inline Texture Example ===");

        // Define RenderTarget with inline texture definitions
        String renderTargetWithInlineTexturesJson = """
                {
                    "identifier": "post_process_buffer",
                    "resolutionMode": "SCREEN_RELATIVE",
                    "scale": 0.5,
                    "clearColor": "#FF000000",
                    "colorAttachments": [
                        {
                            "identifier": "post_color",
                            "format": "RGBA",
                            "filter": "LINEAR",
                            "wrap": "CLAMP_TO_EDGE"
                        },
                        {
                            "identifier": "post_bright",
                            "format": "RGB",
                            "filter": "LINEAR",
                            "wrap": "CLAMP_TO_EDGE"
                        }
                    ],
                    "depthAttachment": {
                        "identifier": "post_depth",
                        "format": "DEPTH",
                        "filter": "NEAREST",
                        "wrap": "CLAMP_TO_EDGE"
                    }
                }
                """;

        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("post_process_buffer"), renderTargetWithInlineTexturesJson);

        System.out.println("Created post-process buffer with inline texture definitions");

        // Check if inline textures were automatically registered
        ResourceReference<Texture> postColorRef = resourceManager.getReference(ResourceTypes.TEXTURE, Identifier.of("post_color"));
        if (postColorRef.isAvailable()) {
            System.out.println("  Inline texture 'post_color' was automatically registered");
        }
    }

    /**
     * Example 3: Mixed texture types (regular + MC)
     */
    private static void mixedTextureExample(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== Mixed Texture Example ===");

        // Register a MC texture manually (if MC integration was available)
        // For demonstration, we'll show the JSON structure
        System.out.println("Note: MC textures would be registered with VanillaTextureLoader");

        // RenderTarget with mixed texture types
        String mixedRenderTargetJson = """
                {
                    "identifier": "mixed_buffer",
                    "resolutionMode": "FIXED",
                    "width": 512,
                    "height": 512,
                    "colorAttachments": [
                        {
                            "identifier": "custom_color",
                            "format": "RGBA",
                            "filter": "LINEAR"
                        },
                        "scene_color"
                    ],
                    "depthAttachment": {
                        "format": "DEPTH",
                        "filter": "NEAREST"
                    }
                }
                """;

        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("mixed_buffer"), mixedRenderTargetJson);

        System.out.println("Created mixed buffer with both inline and referenced textures");
    }

    /**
     * Example 4: Texture reuse and smart detection
     */
    private static void textureReuseExample(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== Texture Reuse Example ===");

        // First RenderTarget creates an inline texture
        String firstRTJson = """
                {
                    "identifier": "first_rt",
                    "width": 1024,
                    "height": 768,
                    "colorAttachments": [
                        {
                            "identifier": "shared_color_buffer",
                            "format": "RGBA",
                            "filter": "LINEAR",
                            "wrap": "REPEAT"
                        }
                    ]
                }
                """;

        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("first_rt"), firstRTJson);

        // Second RenderTarget tries to use the same texture (by identifier)
        String secondRTJson = """
                {
                    "identifier": "second_rt",
                    "width": 1024,
                    "height": 768,
                    "colorAttachments": [
                        "shared_color_buffer"
                    ]
                }
                """;

        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("second_rt"), secondRTJson);

        System.out.println("Created two render targets sharing the same texture");

        // Third RenderTarget tries to redefine the same texture (should reuse existing)
        String thirdRTJson = """
                {
                    "identifier": "third_rt",
                    "width": 1024,
                    "height": 768,
                    "colorAttachments": [
                        {
                            "identifier": "shared_color_buffer",
                            "format": "RGBA",
                            "filter": "LINEAR",
                            "wrap": "REPEAT"
                        }
                    ]
                }
                """;

        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("third_rt"), thirdRTJson);

        System.out.println("Third render target reused existing texture (smart detection)");
    }

    /**
     * Example 5: Anonymous inline textures
     */
    public static void anonymousTextureExample(GraphicsResourceManager resourceManager) {
        System.out.println("\n=== Anonymous Texture Example ===");

        // RenderTarget with anonymous inline textures (no identifier specified)
        String anonymousTextureRTJson = """
                {
                    "identifier": "anonymous_rt",
                    "width": 800,
                    "height": 600,
                    "colorAttachments": [
                        {
                            "format": "RGBA",
                            "filter": "LINEAR"
                        }
                    ],
                    "depthAttachment": {
                        "format": "DEPTH",
                        "filter": "NEAREST"
                    }
                }
                """;

        resourceManager.registerJson(ResourceTypes.RENDER_TARGET, Identifier.of("anonymous_rt"), anonymousTextureRTJson);

        System.out.println("Created render target with auto-generated texture identifiers");
    }
} 