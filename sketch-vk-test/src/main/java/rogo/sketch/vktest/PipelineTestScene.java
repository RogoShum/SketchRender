package rogo.sketch.vktest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import rogo.sketch.backend.opengl.OpenGLFramebufferHandleResource;
import rogo.sketch.backend.vulkan.VulkanBackendRuntime;
import rogo.sketch.core.api.graphics.InstanceVertexEncoder;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.builder.VertexRecordWriter;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.state.DefaultRenderStates;
import rogo.sketch.core.event.GraphicsPipelineInitEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.core.instance.ComputeGraphics;
import rogo.sketch.core.instance.DrawCallGraphics;
import rogo.sketch.core.instance.MeshGraphics;
import rogo.sketch.core.model.BakedMesh;
import rogo.sketch.core.model.DynamicMesh;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.DispatchPacket;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.packet.GenerateMipmapPacket;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketKind;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.GraphicsStage;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.PipelineConfig;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.TargetBinding;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.IndirectPlanData;
import rogo.sketch.core.pipeline.indirect.IndirectRewriteResult;
import rogo.sketch.core.pipeline.kernel.StageExecutionPlan;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphicsLifetime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.pipeline.parmeter.ComputeParameter;
import rogo.sketch.core.pipeline.parmeter.FunctionParameter;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.resource.ResourceScope;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.ImageFormat;
import rogo.sketch.core.resource.descriptor.ImageUsage;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.RenderTargetResolutionMode;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.SamplerFilter;
import rogo.sketch.core.resource.descriptor.SamplerWrap;
import rogo.sketch.core.resource.loader.MeshLoader;
import rogo.sketch.core.resource.loader.ResourceLoadContext;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.preprocessor.SketchShaderPreprocessor;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.shader.variant.ShaderVariantSpec;
import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.OrderRequirement;
import rogo.sketch.core.util.RenderTargetUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class PipelineTestScene implements AutoCloseable {
    private static final Gson TEST_GSON = new GsonBuilder()
            .registerTypeAdapter(KeyId.class, new KeyId.GsonAdapter())
            .create();
    private static final KeyId MAIN_STAGE_ID = KeyId.of("sketch_vktest:main_stage");
    private static final KeyId COMPUTE_STAGE_ID = KeyId.of("sketch_vktest:compute_stage");
    private static final KeyId FUNCTION_STAGE_ID = KeyId.of("sketch_vktest:function_stage");
    private static final KeyId OVERLAY_STAGE_ID = KeyId.of("sketch_vktest:overlay_stage");
    private static final KeyId COLOR_SHADER_ID = KeyId.of("sketch_vktest:color_shader");
    private static final KeyId INSTANCED_SHADER_ID = KeyId.of("sketch_vktest:instanced_color_shader");
    private static final KeyId COMPUTE_SHADER_ID = KeyId.of("sketch_vktest:compute_shader");
    private static final KeyId DEPTH_SHADER_ID = KeyId.of("sketch_vktest:depth_validation_shader");
    private static final KeyId GEOMETRY_COMPONENT = KeyId.of("sketch_vktest:geometry");
    private static final KeyId INSTANCE_OFFSET_COMPONENT = KeyId.of("sketch_vktest:instance_offset");
    private static final KeyId TEXTURE_BINDING = KeyId.of("test_texture");
    private static final KeyId UNIFORM_BINDING = KeyId.of("test_uniforms");
    private static final KeyId TEXTURE_A = KeyId.of("sketch_vktest:texture_a");
    private static final KeyId TEXTURE_B = KeyId.of("sketch_vktest:texture_b");
    private static final KeyId TEXTURE_C = KeyId.of("sketch_vktest:texture_c");
    private static final KeyId VULKAN_CLEAR_TARGET = KeyId.of("sketch_vktest:vulkan_clear_target");
    private static final KeyId VULKAN_CLEAR_COLOR = KeyId.of("sketch_vktest:vulkan_clear_color");
    private static final KeyId VULKAN_CLEAR_DEPTH = KeyId.of("sketch_vktest:vulkan_clear_depth");
    private static final KeyId MAIN_TARGET_COLOR = KeyId.of("sketch_vktest:main_target_color");
    private static final KeyId MAIN_TARGET_DEPTH = KeyId.of("sketch_vktest:main_target_depth");
    private static final KeyId UBO_A = KeyId.of("sketch_vktest:ubo_a");
    private static final KeyId UBO_B = KeyId.of("sketch_vktest:ubo_b");
    private static final KeyId UBO_C = KeyId.of("sketch_vktest:ubo_c");
    private static final KeyId MAIN_TARGET = TargetBinding.DEFAULT_RENDER_TARGET;
    private static final KeyId SHARED_SOURCE_ID = KeyId.of("sketch_vktest:shared_source_asset");
    private static final KeyId INDIRECT_GRAPHICS_ID = KeyId.of("sketch_vktest:indirect_triangle");
    private static final String BUNNY_MODEL_PATH = "models/bunny/bunny.obj";
    private static final String TEAPOT_MODEL_PATH = "models/teapot/teapot.obj";
    private static final KeyId SAMPLED_TEXTURE_SHADER_ID = KeyId.of("sketch_vktest:sampled_texture_shader");
    private static final KeyId COLOR_SCENE_SETTING_ID = KeyId.of("sketch_vktest:color_scene");
    private static final KeyId INSTANCED_COLOR_SCENE_SETTING_ID = KeyId.of("sketch_vktest:instanced_color_scene");
    private static final KeyId DRAW_CALL_SCENE_SETTING_ID = KeyId.of("sketch_vktest:draw_call_scene");
    private static final KeyId SUBSET_SCENE_SETTING_ID = KeyId.of("sketch_vktest:subset_scene");
    private static final KeyId DEPTH_VALIDATION_SCENE_SETTING_ID = KeyId.of("sketch_vktest:depth_validation_scene");
    private static final KeyId SAMPLED_TEXTURE_SCENE_SETTING_ID = KeyId.of("sketch_vktest:sampled_texture_scene");
    private static final KeyId COMPUTE_SCENE_SETTING_ID = KeyId.of("sketch_vktest:compute_scene");
    private static final String INDIRECT_MODULE_ID = "sketch_vktest_indirect_harness";
    private static final long SHARED_SOURCE_REF = Integer.toUnsignedLong(SHARED_SOURCE_ID.hashCode());
    private static final GraphicsStage MAIN_STAGE = new GraphicsStage(
            MAIN_STAGE_ID,
            OrderRequirement.Builder.<GraphicsStage>create().build());
    private static final GraphicsStage COMPUTE_STAGE = new GraphicsStage(
            COMPUTE_STAGE_ID,
            OrderRequirement.Builder.<GraphicsStage>create().mustFollow(MAIN_STAGE).build());
    private static final GraphicsStage FUNCTION_STAGE = new GraphicsStage(
            FUNCTION_STAGE_ID,
            OrderRequirement.Builder.<GraphicsStage>create().mustFollow(COMPUTE_STAGE).build());
    private static final GraphicsStage OVERLAY_STAGE = new GraphicsStage(
            OVERLAY_STAGE_ID,
            OrderRequirement.Builder.<GraphicsStage>create().mustFollow(FUNCTION_STAGE).build());
    private static final KeyId SUBSET_SHADER_ID = KeyId.of("sketch_vktest:subset_shader");
    private static final StructLayout SCENE_FORMAT = StructLayout.builder("sketch_vktest_pos_color")
            .add(0, "position", ValueType.VEC2F)
            .add(1, "color", ValueType.VEC3F)
            .build();
    private static final StructLayout DEPTH_SCENE_FORMAT = StructLayout.builder("sketch_vktest_pos3_color")
            .add(0, "position", ValueType.VEC3F)
            .add(1, "color", ValueType.VEC3F)
            .build();
    private static final StructLayout INSTANCE_OFFSET_FORMAT = StructLayout.builder("sketch_vktest_instance_offset")
            .add(0, "instanceOffset", ValueType.VEC2F)
            .build();
    private static final VertexLayoutSpec SCENE_LAYOUT = VertexLayoutSpec.builder()
            .addDynamic(GEOMETRY_COMPONENT, SCENE_FORMAT)
            .build();
    private static final VertexLayoutSpec INSTANCED_SOURCE_LAYOUT = VertexLayoutSpec.builder()
            .addStatic(BakedTypeMesh.BAKED_MESH, SCENE_FORMAT)
            .addDynamicInstanced(INSTANCE_OFFSET_COMPONENT, INSTANCE_OFFSET_FORMAT)
            .build();
    private static final VertexLayoutSpec DRAW_CALL_LAYOUT = VertexLayoutSpec.builder()
            .addStatic(BakedTypeMesh.BAKED_MESH, SCENE_FORMAT)
            .build();
    private static final VertexLayoutSpec DEPTH_SCENE_LAYOUT = VertexLayoutSpec.builder()
            .addDynamic(GEOMETRY_COMPONENT, DEPTH_SCENE_FORMAT)
            .build();
    private static final RasterizationParameter SCENE_PARAMETER = new RasterizationParameter(
            SCENE_LAYOUT,
            PrimitiveType.TRIANGLES,
            MeshIndexMode.NONE,
            BufferUpdatePolicy.DYNAMIC,
            false);
    private static final RasterizationParameter INSTANCED_SOURCE_PARAMETER = new RasterizationParameter(
            INSTANCED_SOURCE_LAYOUT,
            PrimitiveType.TRIANGLES,
            MeshIndexMode.NONE,
            BufferUpdatePolicy.DYNAMIC,
            false);
    private static final RasterizationParameter DRAW_CALL_PARAMETER = new RasterizationParameter(
            DRAW_CALL_LAYOUT,
            PrimitiveType.TRIANGLES,
            MeshIndexMode.NONE,
            BufferUpdatePolicy.IMMUTABLE,
            false);
    private static final RasterizationParameter DEPTH_SCENE_PARAMETER = new RasterizationParameter(
            DEPTH_SCENE_LAYOUT,
            PrimitiveType.TRIANGLES,
            MeshIndexMode.NONE,
            BufferUpdatePolicy.DYNAMIC,
            false);
    private static final DynamicMesh SHARED_TRIANGLE_MESH = new DynamicMesh(
            KeyId.of("sketch_vktest:shared_triangle_mesh"),
            SCENE_FORMAT,
            PrimitiveType.TRIANGLES,
            3,
            0,
            builder -> {
            });
    private static final DynamicMesh DEPTH_TRIANGLE_MESH = new DynamicMesh(
            KeyId.of("sketch_vktest:depth_triangle_mesh"),
            DEPTH_SCENE_FORMAT,
            PrimitiveType.TRIANGLES,
            3,
            0,
            builder -> {
            });
    private static final String COLOR_VERTEX_SHADER = """
            #version 450 core
            in vec3 color;
            in vec2 position;
            #ifdef ENABLE_INSTANCE_OFFSET
            in vec2 instanceOffset;
            #endif
            out vec3 vColor;

            void main() {
                vec2 offset = vec2(0.0);
            #ifdef ENABLE_INSTANCE_OFFSET
                offset = instanceOffset;
            #endif
                gl_Position = vec4(position + offset, 0.0, 1.0);
                vColor = color;
            }
            """;
    private static final String FRAGMENT_SHADER = """
            #version 450 core
            in vec3 vColor;
            out vec4 outColor;

            void main() {
                outColor = vec4(vColor, 1.0);
            }
            """;
    private static final String SUBSET_VERTEX_SHADER = """
            #version 450 core
            in vec2 position;

            void main() {
                gl_Position = vec4(position, 0.0, 1.0);
            }
            """;
    private static final String SUBSET_FRAGMENT_SHADER = """
            #version 450 core
            out vec4 outColor;

            void main() {
                outColor = vec4(0.95, 0.90, 0.20, 1.0);
            }
            """;
    private static final String DEPTH_VERTEX_SHADER = """
            #version 450 core
            in vec3 position;
            in vec3 color;
            out vec3 vColor;

            void main() {
                gl_Position = vec4(position, 1.0);
                vColor = color;
            }
            """;
    private static final String SAMPLED_VERTEX_SHADER = """
            #version 450 core
            in vec2 position;
            out vec2 vUv;

            void main() {
                gl_Position = vec4(position, 0.0, 1.0);
                vUv = position * 0.5 + 0.5;
            }
            """;
    private static final String SAMPLED_FRAGMENT_SHADER = """
            #version 450 core
            in vec2 vUv;
            out vec4 outColor;
            uniform sampler2D test_texture;

            void main() {
                outColor = textureLod(test_texture, vUv, 2.0);
            }
            """;
    private static final String COMPUTE_SHADER_SOURCE = """
            #version 430 core
            layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;

            void main() {
            }
            """;
    private static final int VALIDATION_WARMUP_FRAMES = 2;
    private static final int VALIDATION_HARD_DEADLINE_FRAMES = 4;

    private final BackendKind backendKind;
    private final HarnessRenderContext context;
    private final GraphicsPipeline<HarnessRenderContext> pipeline;
    private final List<AutoCloseable> ownedCloseables = new ArrayList<>();
    private CompiledRenderSetting resourceSetA;
    private CompiledRenderSetting resourceSetB;
    private CompiledRenderSetting resourceSetC;
    private CompiledRenderSetting instancedResourceSetA;
    private CompiledRenderSetting subsetResourceSet;
    private CompiledRenderSetting depthValidationResourceSet;
    private CompiledRenderSetting computeSetting;
    private int renderedFrames;
    private boolean statsPrinted;
    private boolean validated;

    private PipelineTestScene(BackendKind backendKind, int framebufferWidth, int framebufferHeight) {
        this.backendKind = backendKind;
        this.context = new HarnessRenderContext(framebufferWidth, framebufferHeight);
        PipelineConfig config = new PipelineConfig();
        this.pipeline = new GraphicsPipeline<>(config, context);
        this.context.setRenderStateManager(pipeline.renderStateManager());
        initializeResources();
        initializePipeline();
    }

    private void initializeResources() {
        DefaultRenderStates.init();
    }

    private void installDesktopGlMainTarget() {
        var installer = GraphicsDriver.runtime().resourceInstaller();
        Texture colorTexture = installer.createTexture(
                MAIN_TARGET_COLOR,
                new ResolvedImageResource(
                        MAIN_TARGET_COLOR,
                        Math.max(1, context.windowWidth()),
                        Math.max(1, context.windowHeight()),
                        1,
                        ImageFormat.RGBA8_UNORM,
                        EnumSet.of(ImageUsage.COLOR_ATTACHMENT, ImageUsage.SAMPLED),
                        SamplerFilter.LINEAR,
                        SamplerFilter.LINEAR,
                        null,
                        SamplerWrap.CLAMP_TO_EDGE,
                        SamplerWrap.CLAMP_TO_EDGE,
                        null),
                null,
                null);
        Texture depthTexture = installer.createTexture(
                MAIN_TARGET_DEPTH,
                new ResolvedImageResource(
                        MAIN_TARGET_DEPTH,
                        Math.max(1, context.windowWidth()),
                        Math.max(1, context.windowHeight()),
                        1,
                        ImageFormat.D24_UNORM,
                        EnumSet.of(ImageUsage.DEPTH_ATTACHMENT),
                        SamplerFilter.NEAREST,
                        SamplerFilter.NEAREST,
                        null,
                        SamplerWrap.CLAMP_TO_EDGE,
                        SamplerWrap.CLAMP_TO_EDGE,
                        null),
                null,
                null);
        GraphicsResourceManager.getInstance().registerDirect(
                "sketch-vk-test",
                ResourceScope.EPHEMERAL_TEST,
                ResourceTypes.TEXTURE,
                MAIN_TARGET_COLOR,
                colorTexture);
        GraphicsResourceManager.getInstance().registerDirect(
                "sketch-vk-test",
                ResourceScope.EPHEMERAL_TEST,
                ResourceTypes.TEXTURE,
                MAIN_TARGET_DEPTH,
                depthTexture);
        RenderTarget mainTarget = installer.createRenderTarget(
                MAIN_TARGET,
                new ResolvedRenderTargetSpec(
                        MAIN_TARGET,
                        RenderTargetResolutionMode.SCREEN_SIZE,
                        1,
                        1,
                        1.0f,
                        1.0f,
                        List.of(MAIN_TARGET_COLOR),
                        MAIN_TARGET_DEPTH,
                        null));
        GraphicsResourceManager.getInstance().registerDirect(
                "sketch-vk-test",
                ResourceScope.EPHEMERAL_TEST,
                ResourceTypes.RENDER_TARGET,
                MAIN_TARGET,
                mainTarget);
        RenderTargetUtil.resizeRT(context.windowWidth(), context.windowHeight());
    }

    private void initializePipeline() {
        pipeline.registerStage(MAIN_STAGE);
        pipeline.registerStage(COMPUTE_STAGE);
        pipeline.registerStage(FUNCTION_STAGE);
        pipeline.registerStage(OVERLAY_STAGE);

        EventBusBridge.subscribe(GraphicsPipelineInitEvent.class, event -> {
            if (event == null || event.getPipeline() != pipeline || event.getPhase() != GraphicsPipelineInitEvent.InitPhase.EARLY) {
                return;
            }
            if (event.getPipeline().kernel() != null) {
                event.getPipeline().kernel().moduleRegistry().registerDescriptor(new IndirectHarnessModuleDescriptor());
            }
        });

        pipeline.initPipeline();
        configureResourceManager();
        installBackendSpecificResources();
        compileSceneSettings();
        pipeline.initKernel();
        pipeline.enterWorld();
        registerBasicStateResourceSplitScenario();
        registerSharedSourceMultiSubmeshScenario();
        registerShaderLayoutScenario();
        registerComputeScenario();
        registerCrossBackendValidationScenario();
        runStaticSpecValidations();
        pipeline.resetRenderContext(context);
    }

    private void configureResourceManager() {
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
        resourceManager.setScanProvider(null);
        resourceManager.setScanProvider(new VkTestClasspathResourceScanProvider());
        resourceManager.reload();
        pipeline.installPipelineResources();
    }

    private void installBackendSpecificResources() {
        if (backendKind == BackendKind.DESKTOP_GL) {
            installDesktopGlMainTarget();
        }

        if (backendKind == BackendKind.VULKAN && GraphicsDriver.runtime() instanceof VulkanBackendRuntime runtime) {
            runtime.registerTextureResource(TEXTURE_A, runtime.createPlaceholderTextureResource(1, 1));
            runtime.registerTextureResource(TEXTURE_B, runtime.createPlaceholderTextureResource(1, 1));
            runtime.registerTextureResource(TEXTURE_C, runtime.createPlaceholderTextureResource(1, 1));
            GraphicsResourceManager.getInstance().registerDirect(
                    "sketch-vk-test",
                    ResourceScope.EPHEMERAL_TEST,
                    ResourceTypes.TEXTURE,
                    VULKAN_CLEAR_COLOR,
                    runtime.createRenderTargetColorTextureResource(4, 4, 4));
            GraphicsResourceManager.getInstance().registerDirect(
                    "sketch-vk-test",
                    ResourceScope.EPHEMERAL_TEST,
                    ResourceTypes.TEXTURE,
                    VULKAN_CLEAR_DEPTH,
                    runtime.createDepthTextureResource(4, 4));
            runtime.registerUniformBufferResource(UBO_A, runtime.createUniformBufferResource(new byte[]{1, 0, 0, 0}));
            runtime.registerUniformBufferResource(UBO_B, runtime.createUniformBufferResource(new byte[]{0, 1, 0, 0}));
            runtime.registerUniformBufferResource(UBO_C, runtime.createUniformBufferResource(new byte[]{0, 0, 1, 0}));
        }
    }

    private void compileSceneSettings() {
        resourceSetA = compileRenderSetting(COLOR_SCENE_SETTING_ID, SCENE_PARAMETER, buildResourceBinding(TEXTURE_A, UBO_A));
        resourceSetB = compileRenderSetting(COLOR_SCENE_SETTING_ID, SCENE_PARAMETER, buildResourceBinding(TEXTURE_B, UBO_B));
        resourceSetC = compileRenderSetting(COLOR_SCENE_SETTING_ID, SCENE_PARAMETER, buildResourceBinding(TEXTURE_C, UBO_C));
        instancedResourceSetA = compileRenderSetting(
                INSTANCED_COLOR_SCENE_SETTING_ID,
                INSTANCED_SOURCE_PARAMETER,
                buildResourceBinding(TEXTURE_A, UBO_A));
        subsetResourceSet = compileRenderSetting(
                SUBSET_SCENE_SETTING_ID,
                SCENE_PARAMETER,
                buildResourceBinding(TEXTURE_A, UBO_A));
        depthValidationResourceSet = compileRenderSetting(
                DEPTH_VALIDATION_SCENE_SETTING_ID,
                DEPTH_SCENE_PARAMETER,
                buildResourceBinding(TEXTURE_A, UBO_A));
        computeSetting = compileComputeSetting(COMPUTE_SCENE_SETTING_ID);
    }

    private void registerBasicStateResourceSplitScenario() {
        pipeline.addGraphInstance(
                MAIN_STAGE_ID,
                new TestTriangleGraphics(
                        KeyId.of("sketch_vktest:triangle_a"),
                        resourceSetA,
                        new float[]{
                                -0.90f, -0.70f, 1.0f, 0.1f, 0.1f,
                                -0.45f, -0.70f, 0.1f, 1.0f, 0.1f,
                                -0.68f, -0.20f, 0.1f, 0.1f, 1.0f
                        }),
                SCENE_PARAMETER,
                PipelineType.RASTERIZATION);
        pipeline.addGraphInstance(
                MAIN_STAGE_ID,
                new TestTriangleGraphics(
                        KeyId.of("sketch_vktest:triangle_b"),
                        resourceSetB,
                        new float[]{
                                0.35f, -0.70f, 1.0f, 0.8f, 0.2f,
                                0.85f, -0.70f, 0.2f, 0.8f, 1.0f,
                                0.60f, -0.20f, 0.9f, 0.9f, 0.2f
                        }),
                SCENE_PARAMETER,
                PipelineType.RASTERIZATION);
        pipeline.addGraphInstance(
                OVERLAY_STAGE_ID,
                new TestTriangleGraphics(
                        KeyId.of("sketch_vktest:triangle_overlay"),
                        resourceSetA,
                        new float[]{
                                -0.20f, 0.15f, 0.9f, 0.3f, 0.2f,
                                0.20f, 0.15f, 0.2f, 0.9f, 0.3f,
                                0.00f, 0.70f, 0.2f, 0.3f, 0.9f
                        }),
                SCENE_PARAMETER,
                PipelineType.RASTERIZATION);
    }

    private void registerSharedSourceMultiSubmeshScenario() {
        SharedSourceMeshes sharedSourceMeshes = createSharedSourceMeshes();
        pipeline.addGraphInstance(
                MAIN_STAGE_ID,
                new InstancedBakedGraphics(
                        KeyId.of("sketch_vktest:shared_left_a"),
                        instancedResourceSetA,
                        sharedSourceMeshes.leftMesh(),
                        -0.62f,
                        0.28f),
                INSTANCED_SOURCE_PARAMETER,
                PipelineType.RASTERIZATION);
        pipeline.addGraphInstance(
                MAIN_STAGE_ID,
                new InstancedBakedGraphics(
                        KeyId.of("sketch_vktest:shared_left_b"),
                        instancedResourceSetA,
                        sharedSourceMeshes.leftMesh(),
                        -0.18f,
                        0.28f),
                INSTANCED_SOURCE_PARAMETER,
                PipelineType.RASTERIZATION);
        pipeline.addGraphInstance(
                MAIN_STAGE_ID,
                new InstancedBakedGraphics(
                        KeyId.of("sketch_vktest:shared_right"),
                        instancedResourceSetA,
                        sharedSourceMeshes.rightMesh(),
                        0.28f,
                        0.28f),
                INSTANCED_SOURCE_PARAMETER,
                PipelineType.RASTERIZATION);
    }

    private void registerShaderLayoutScenario() {
        pipeline.addGraphInstance(
                OVERLAY_STAGE_ID,
                new TestTriangleGraphics(
                        KeyId.of("sketch_vktest:triangle_subset"),
                        subsetResourceSet,
                        new float[]{
                                -0.85f, 0.20f, 1.0f, 0.0f, 0.0f,
                                -0.55f, 0.20f, 0.0f, 1.0f, 0.0f,
                                -0.70f, 0.55f, 0.0f, 0.0f, 1.0f
                        }),
                SCENE_PARAMETER,
                PipelineType.RASTERIZATION);
    }

    private void registerComputeScenario() {
        pipeline.addCompute(
                COMPUTE_STAGE_ID,
                new TestComputeGraphics(
                        KeyId.of("sketch_vktest:compute_dispatch"),
                        computeSetting));
    }

    private void registerCrossBackendValidationScenario() {
        registerMainTargetDepthValidationScenario();
    }

    private void registerMainTargetDepthValidationScenario() {
        pipeline.addGraphInstance(
                MAIN_STAGE_ID,
                new TestDepthTriangleGraphics(
                        KeyId.of("sketch_vktest:main_depth_base"),
                        depthValidationResourceSet,
                        new float[]{
                                -0.20f, -0.20f, 0.20f, 1.0f, 0.05f, 0.05f,
                                0.20f, -0.20f, 0.20f, 1.0f, 0.05f, 0.05f,
                                0.00f, 0.20f, 0.20f, 1.0f, 0.05f, 0.05f
                        }),
                DEPTH_SCENE_PARAMETER,
                PipelineType.RASTERIZATION);
        pipeline.addGraphInstance(
                OVERLAY_STAGE_ID,
                new TestDepthTriangleGraphics(
                        KeyId.of("sketch_vktest:main_depth_overlay"),
                        depthValidationResourceSet,
                        new float[]{
                                -0.20f, -0.20f, 0.60f, 0.05f, 0.95f, 0.10f,
                                0.20f, -0.20f, 0.60f, 0.05f, 0.95f, 0.10f,
                                0.00f, 0.20f, 0.60f, 0.05f, 0.95f, 0.10f
                        }),
                DEPTH_SCENE_PARAMETER,
                PipelineType.RASTERIZATION);
    }

    private CompiledRenderSetting compileRenderSetting(
            KeyId partialSettingId,
            RasterizationParameter renderParameter,
            ResourceBinding binding) {
        PartialRenderSetting partialRenderSetting = requireResource(ResourceTypes.PARTIAL_RENDER_SETTING, partialSettingId, PartialRenderSetting.class);
        return RenderSettingCompiler.compile(RenderSetting.fromPartial(renderParameter, mergePartialSetting(partialRenderSetting, binding)));
    }

    private CompiledRenderSetting compileComputeSetting(KeyId partialSettingId) {
        PartialRenderSetting partialRenderSetting = requireResource(ResourceTypes.PARTIAL_RENDER_SETTING, partialSettingId, PartialRenderSetting.class);
        return RenderSettingCompiler.compile(RenderSetting.fromPartial(ComputeParameter.COMPUTE_PARAMETER, partialRenderSetting));
    }

    private ResourceBinding buildResourceBinding(KeyId textureId, KeyId uniformBufferId) {
        ResourceBinding binding = new ResourceBinding();
        binding.addBinding(ResourceTypes.TEXTURE, TEXTURE_BINDING, textureId);
        binding.addBinding(ResourceTypes.UNIFORM_BUFFER, UNIFORM_BINDING, uniformBufferId);
        return binding;
    }

    private void runStaticSpecValidations() {
        validateMeshSchemaHardErrors();
        validateBundledObjModelResources();
        validateShaderAuthoringHardErrors();
        validateShaderResourceBindingRules();
        validateDrawIndexAlignmentContracts();
        validatePassOverwriteOrdering();
    }

    private void validateDrawIndexAlignmentContracts() {
        SharedSourceMeshes sharedSourceMeshes = createSharedSourceMeshes();
        assertSharedIndexedSlice("shared_left", sharedSourceMeshes.leftMesh(), 0, 0, 3, new int[]{0, 1, 2});
        assertSharedIndexedSlice("shared_right", sharedSourceMeshes.rightMesh(), 3, 3, 3, new int[]{0, 1, 2});
    }

    private void assertSharedIndexedSlice(
            String name,
            BakedMesh mesh,
            int expectedVertexOffset,
            int expectedIndexOffset,
            int expectedIndexCount,
            int[] expectedLocalIndices) {
        if (mesh == null || mesh.sharedGeometrySourceSnapshot() == null) {
            throw new IllegalStateException("draw_index_alignment_validation: missing shared snapshot for " + name);
        }
        if (mesh.getVertexOffset() != expectedVertexOffset) {
            throw new IllegalStateException(
                    "draw_index_alignment_validation: unexpected base vertex for " + name
                            + ", expected " + expectedVertexOffset
                            + " but was " + mesh.getVertexOffset());
        }
        if (mesh.getIndexOffset() != expectedIndexOffset) {
            throw new IllegalStateException(
                    "draw_index_alignment_validation: unexpected first index for " + name
                            + ", expected " + expectedIndexOffset
                            + " but was " + mesh.getIndexOffset());
        }
        if (mesh.getIndicesCount() != expectedIndexCount) {
            throw new IllegalStateException(
                    "draw_index_alignment_validation: unexpected index count for " + name
                            + ", expected " + expectedIndexCount
                            + " but was " + mesh.getIndicesCount());
        }

        ByteBuffer indexBytes = ByteBuffer.wrap(mesh.sharedGeometrySourceSnapshot().indexData()).order(ByteOrder.LITTLE_ENDIAN);
        int[] actual = new int[expectedIndexCount];
        indexBytes.position(mesh.getIndexOffset() * Integer.BYTES);
        for (int i = 0; i < expectedIndexCount; i++) {
            actual[i] = indexBytes.getInt();
        }
        if (!java.util.Arrays.equals(actual, expectedLocalIndices)) {
            throw new IllegalStateException(
                    "draw_index_alignment_validation: " + name
                            + " expected local indices " + java.util.Arrays.toString(expectedLocalIndices)
                            + " but found " + java.util.Arrays.toString(actual));
        }
    }

    private void validatePassOverwriteOrdering() {
        if (!OVERLAY_STAGE.getOrderRequirement().getMustFollow().contains(FUNCTION_STAGE)) {
            throw new IllegalStateException(
                    "pass_overwrite_validation: overlay stage must follow function stage so the clear/fullscreen pass ordering stays explicit");
        }
    }

    private void validateBundledObjModelResources() {
        validateBundledObjModel(KeyId.of("sketch_vktest:bunny_model"), BUNNY_MODEL_PATH);
        validateBundledObjModel(KeyId.of("sketch_vktest:teapot_model"), TEAPOT_MODEL_PATH);
    }

    private void validateBundledObjModel(KeyId resourceId, String classpathPath) {
        MeshLoader meshLoader = new MeshLoader();
        try (InputStream stream = PipelineTestScene.class.getClassLoader().getResourceAsStream(classpathPath)) {
            if (stream == null) {
                throw new IllegalStateException("Bundled OBJ model resource is missing from classpath: " + classpathPath);
            }
            ResourceLoadContext context = new ResourceLoadContext(resourceId, stream, TEST_GSON, id -> Optional.empty());
            if (meshLoader.load(context) == null) {
                throw new IllegalStateException("Bundled OBJ model failed to load: " + classpathPath);
            }
        } catch (Exception e) {
            throw new IllegalStateException("OBJ model validation failed for " + classpathPath, e);
        }
    }

    private void validateMeshSchemaHardErrors() {
        MeshLoader meshLoader = new MeshLoader();
        expectFailure(
                "mesh_schema_validation: deprecated primitiveType",
                () -> meshLoader.load(jsonContext(
                        KeyId.of("sketch_vktest:invalid_mesh_primitive"),
                        """
                        {
                          "primitiveType": "triangles",
                          "primitiveTopology": "triangles",
                          "indexMode": "none",
                          "vertexFormat": { "name": "f", "elements": [ { "name": "position", "dataType": "vec2f" } ] },
                          "subMeshes": [ { "name": "bad", "vertices": [0,0, 1,0, 0,1] } ]
                        }
                        """)));
        expectFailure(
                "mesh_schema_validation: deprecated recalculateIndices",
                () -> meshLoader.load(jsonContext(
                        KeyId.of("sketch_vktest:invalid_mesh_recalculate"),
                        """
                        {
                          "primitiveTopology": "triangles",
                          "indexMode": "explicit_local",
                          "vertexFormat": { "name": "f", "elements": [ { "name": "position", "dataType": "vec2f" } ] },
                          "subMeshes": [ { "name": "bad", "recalculateIndices": true, "vertices": [0,0, 1,0, 0,1], "indices": [0,1,2] } ]
                        }
                        """)));
        expectFailure(
                "mesh_schema_validation: out-of-range local index",
                () -> meshLoader.load(jsonContext(
                        KeyId.of("sketch_vktest:invalid_mesh_index"),
                        """
                        {
                          "primitiveTopology": "triangles",
                          "indexMode": "explicit_local",
                          "vertexFormat": { "name": "f", "elements": [ { "name": "position", "dataType": "vec2f" } ] },
                          "subMeshes": [ { "name": "bad", "vertices": [0,0, 1,0, 0,1], "indices": [0,1,3] } ]
                        }
                        """)));
        expectFailure(
                "mesh_schema_validation: illegal generated topology",
                () -> meshLoader.load(jsonContext(
                        KeyId.of("sketch_vktest:invalid_mesh_generated"),
                        """
                        {
                          "primitiveTopology": "triangles",
                          "indexMode": "generated",
                          "vertexFormat": { "name": "f", "elements": [ { "name": "position", "dataType": "vec2f" } ] },
                          "subMeshes": [ { "name": "bad", "vertices": [0,0, 1,0, 0,1] } ]
                        }
                        """)));
    }

    private void validateShaderAuthoringHardErrors() {
        expectFailure(
                "shader_authoring_validation: layout(location)",
                () -> resolveInlineTemplate(
                        KeyId.of("sketch_vktest:invalid_shader_location"),
                        Map.of(
                                ShaderType.VERTEX, """
                                        #version 330 core
                                        layout(location = 0) in vec3 position;
                                        void main() { gl_Position = vec4(position, 1.0); }
                                        """,
                                ShaderType.FRAGMENT, FRAGMENT_SHADER),
                        ShaderVertexLayout.empty(),
                        Map.of()));
        expectFailure(
                "shader_authoring_validation: layout(binding)",
                () -> resolveInlineTemplate(
                        KeyId.of("sketch_vktest:invalid_shader_binding"),
                        Map.of(
                                ShaderType.COMPUTE, """
                                        #version 430 core
                                        layout(local_size_x = 1) in;
                                        layout(binding = 0) uniform sampler2D test_texture;
                                        void main() {}
                                        """),
                        ShaderVertexLayout.empty(),
                        Map.of()));
        expectFailure(
                "shader_authoring_validation: gl_FragData",
                () -> resolveInlineTemplate(
                        KeyId.of("sketch_vktest:invalid_shader_fragdata"),
                        Map.of(
                                ShaderType.VERTEX, SUBSET_VERTEX_SHADER,
                                ShaderType.FRAGMENT, """
                                        #version 330 core
                                        void main() { gl_FragData[0] = vec4(1.0); }
                                        """),
                        ShaderVertexLayout.empty(),
                        Map.of()));
        expectFailure(
                "shader_authoring_validation: texture2D",
                () -> resolveInlineTemplate(
                        KeyId.of("sketch_vktest:invalid_shader_texture2d"),
                        Map.of(
                                ShaderType.VERTEX, SAMPLED_VERTEX_SHADER,
                                ShaderType.FRAGMENT, """
                                        #version 330 core
                                        uniform sampler2D test_texture;
                                        in vec2 vUv;
                                        out vec4 outColor;
                                        void main() { outColor = texture2D(test_texture, vUv); }
                                        """),
                        ShaderVertexLayout.empty(),
                        Map.of()));
    }

    private void validateShaderResourceBindingRules() {
        expectFailure(
                "shader_resource_binding_validation: missing resourceBindings",
                () -> resolveInlineTemplate(
                        KeyId.of("sketch_vktest:missing_resource_bindings"),
                        Map.of(
                                ShaderType.FRAGMENT, """
                                        #version 330 core
                                        uniform sampler2D texture_a;
                                        uniform sampler2D texture_b;
                                        out vec4 outColor;
                                        void main() { outColor = texture(texture_a, vec2(0.5)) + texture(texture_b, vec2(0.5)); }
                                        """),
                        ShaderVertexLayout.empty(),
                        Map.of()));

        ShaderVariantSpec spec = resolveInlineTemplate(
                KeyId.of("sketch_vktest:explicit_resource_bindings"),
                Map.of(
                        ShaderType.FRAGMENT, """
                                #version 330 core
                                uniform sampler2D texture_a;
                                uniform sampler2D texture_b;
                                out vec4 outColor;
                                void main() { outColor = texture(texture_a, vec2(0.5)) + texture(texture_b, vec2(0.5)); }
                                """),
                ShaderVertexLayout.empty(),
                Map.of(
                        ResourceTypes.TEXTURE,
                        Map.of(
                                KeyId.of("texture_a"), 0,
                                KeyId.of("texture_b"), 1)));
        Map<KeyId, Integer> textureBindings = spec.interfaceSpec().resourceBindings().get(ResourceTypes.TEXTURE);
        if (textureBindings == null
                || textureBindings.get(KeyId.of("texture_a")) != 0
                || textureBindings.get(KeyId.of("texture_b")) != 1) {
            throw new IllegalStateException("shader_resource_binding_validation failed: explicit resourceBindings were not preserved");
        }
    }

    private ShaderVariantSpec resolveInlineTemplate(
            KeyId shaderId,
            Map<ShaderType, String> sources,
            ShaderVertexLayout vertexLayout,
            Map<KeyId, Map<KeyId, Integer>> resourceBindings) {
        try {
            ShaderTemplate template = new ShaderTemplate(
                    shaderId,
                    sources,
                    vertexLayout,
                    new SketchShaderPreprocessor(),
                    GraphicsResourceManager.getInstance().getSubResourceProvider(),
                    resourceBindings);
            return template.resolveVariantSpec(ShaderVariantKey.EMPTY);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ResourceLoadContext jsonContext(KeyId resourceId, String json) {
        return new ResourceLoadContext(
                resourceId,
                new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                TEST_GSON,
                GraphicsResourceManager.getInstance().getSubResourceProvider());
    }

    private void expectFailure(String label, Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            return;
        }
        throw new IllegalStateException(label + " did not fail");
    }

    private SharedSourceMeshes createSharedSourceMeshes() {
        MeshGroup meshGroup = requireResource(ResourceTypes.MESH, SHARED_SOURCE_ID, MeshGroup.class);
        BakedMesh leftMesh = requireMesh(meshGroup, KeyId.of("sketch_vktest:shared_left_mesh"));
        BakedMesh rightMesh = requireMesh(meshGroup, KeyId.of("sketch_vktest:shared_right_mesh"));
        return new SharedSourceMeshes(leftMesh, rightMesh);
    }

    private PartialRenderSetting mergePartialSetting(PartialRenderSetting base, ResourceBinding overrideBinding) {
        if (base == null) {
            throw new IllegalArgumentException("Base partial render setting must not be null");
        }
        ResourceBinding mergedBinding = null;
        if (base.resourceBinding() != null || overrideBinding != null) {
            mergedBinding = new ResourceBinding();
            if (base.resourceBinding() != null) {
                mergedBinding.merge(base.resourceBinding());
            }
            if (overrideBinding != null) {
                mergedBinding.merge(overrideBinding);
            }
        }
        return PartialRenderSetting.create(
                base.renderState(),
                base.targetBinding(),
                mergedBinding,
                base.shouldSwitchRenderState());
    }

    private <T> T requireResource(KeyId resourceType, KeyId resourceId, Class<T> type) {
        Object resource = GraphicsResourceManager.getInstance().getResource(resourceType, resourceId);
        if (!type.isInstance(resource)) {
            throw new IllegalStateException("Missing required resource " + resourceType + " -> " + resourceId);
        }
        return type.cast(resource);
    }

    private BakedMesh requireMesh(MeshGroup meshGroup, KeyId meshId) {
        if (meshGroup == null) {
            throw new IllegalStateException("Missing mesh group for " + meshId);
        }
        if (!(meshGroup.getMesh(meshId) instanceof BakedMesh bakedMesh)) {
            throw new IllegalStateException("Missing baked mesh " + meshId + " in group " + meshGroup.getName());
        }
        return bakedMesh;
    }

    static PipelineTestScene create(BackendKind backendKind, int framebufferWidth, int framebufferHeight) {
        return new PipelineTestScene(backendKind, framebufferWidth, framebufferHeight);
    }

    void resize(int framebufferWidth, int framebufferHeight) {
        context.setWindowSize(framebufferWidth, framebufferHeight);
        context.projectionMatrix().identity();
        context.viewMatrix().identity();
        context.modelMatrix().identity();
        if (backendKind == BackendKind.DESKTOP_GL) {
            RenderTargetUtil.resizeRT(context.windowWidth(), context.windowHeight());
        }
        pipeline.resetRenderContext(context);
    }

    void renderFrame() {
        pipeline.tickFrame();
        context.setRenderTick(context.renderTick() + 1);
        context.setPartialTicks(0.0f);
        context.setNextTick(true);
        pipeline.kernel().executeFrame(context);

        if (backendKind == BackendKind.DESKTOP_GL) {
            pipeline.renderStagesAfter(MAIN_STAGE_ID);
            presentDesktopGlMainTarget();
        }

        renderedFrames++;
        validateAndPrintStatsIfReady();
    }

    GraphicsPipeline<HarnessRenderContext> pipeline() {
        return pipeline;
    }

    private void presentDesktopGlMainTarget() {
        RenderTarget logicalTarget = GraphicsResourceManager.getInstance().getResource(ResourceTypes.RENDER_TARGET, MAIN_TARGET);
        if (!(logicalTarget instanceof OpenGLFramebufferHandleResource framebufferHandleResource)) {
            return;
        }
        int width = Math.max(1, context.windowWidth());
        int height = Math.max(1, context.windowHeight());
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebufferHandleResource.framebufferHandle());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        GL30.glBlitFramebuffer(
                0,
                0,
                width,
                height,
                0,
                0,
                width,
                height,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
    }

    private void validateAndPrintStatsIfReady() {
        validateShaderLayouts();
        validateVulkanFormalBoundaries();
        SceneStats stats = collectStats();
        if (!validated && renderedFrames >= VALIDATION_WARMUP_FRAMES && stats.isSatisfied()) {
            validated = true;
            printSceneStats(stats);
            return;
        }
        if (!validated && renderedFrames >= VALIDATION_HARD_DEADLINE_FRAMES) {
            throw new IllegalStateException("Pipeline test scene validation failed: " + stats);
        }
    }

    private void validateShaderLayouts() {
        ShaderTemplate flexTemplate = GraphicsResourceManager.getInstance().getResource(ResourceTypes.SHADER_TEMPLATE, COLOR_SHADER_ID);
        ShaderTemplate subsetTemplate = GraphicsResourceManager.getInstance().getResource(ResourceTypes.SHADER_TEMPLATE, SUBSET_SHADER_ID);
        if (flexTemplate == null || subsetTemplate == null) {
            throw new IllegalStateException("Shader layout validation failed: missing test shader templates");
        }

        ShaderVariantSpec baseSpec;
        ShaderVariantSpec offsetSpec;
        ShaderVariantSpec subsetSpec;
        try {
            baseSpec = flexTemplate.resolveVariantSpec(ShaderVariantKey.EMPTY);
            offsetSpec = flexTemplate.resolveVariantSpec(ShaderVariantKey.of("ENABLE_INSTANCE_OFFSET"));
            subsetSpec = subsetTemplate.resolveVariantSpec(ShaderVariantKey.EMPTY);
        } catch (Exception e) {
            throw new IllegalStateException("Shader layout validation failed", e);
        }

        assertActiveAttributes(baseSpec, "color", "position");
        assertActiveAttributes(offsetSpec, "color", "position", "instanceOffset");
        assertActiveAttributes(subsetSpec, "position");
    }

    private void assertActiveAttributes(ShaderVariantSpec spec, String... expectedSemantics) {
        List<String> actual = spec.activeVertexLayout()
                .getAttributes()
                .stream()
                .map(it -> it.name())
                .toList();
        List<String> expected = List.of(expectedSemantics);
        if (!actual.equals(expected)) {
            throw new IllegalStateException(
                    "Active shader layout mismatch for " + spec.templateId() + " / " + spec.variantKey()
                            + ": expected " + expected + " but was " + actual);
        }
    }

    private void validateVulkanFormalBoundaries() {
        if (backendKind != BackendKind.VULKAN) {
            return;
        }
        var diagnostics = rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics.get().snapshot();
        for (var entry : diagnostics) {
            String message = entry.message();
            if (message == null) {
                continue;
            }
            if (message.contains("current/default target depth clear is not installed")
                    || message.contains("missing_vulkan_texture")
                    || message.contains("missing_vulkan_uniform_buffer")
                    || message.contains("No Vulkan backend-native resource resolved")
                    || message.contains("Unsupported Vulkan descriptor resource type")) {
                throw new IllegalStateException("Vulkan formal boundary validation failed: " + message);
            }
        }
    }

    private SceneStats collectStats() {
        RenderPacketQueue<HarnessRenderContext> queue = pipeline.getRenderPacketQueue();
        int stageCount = 0;
        int stateBucketCount = 0;
        int drawPacketCount = 0;
        int dispatchPacketCount = 0;
        int functionPacketCount = 0;
        int clearPacketCount = 0;
        int directBatchCount = 0;
        int indirectPacketCount = 0;
        int sameSourceMultiSubmeshGroupCount = 0;
        int drawCallPacketCount = 0;

        for (KeyId stageId : queue.stagedPacketIds()) {
            StageExecutionPlan stagePlan = queue.stagePlan(stageId);
            if (stagePlan == null || stagePlan.isEmpty()) {
                continue;
            }
            stageCount++;
            for (Map<PipelineStateKey, List<RenderPacket>> packetMap : stagePlan.packets().values()) {
                stateBucketCount += packetMap.size();
                for (List<RenderPacket> packets : packetMap.values()) {
                    for (RenderPacket packet : packets) {
                        if (packet.packetKind() == RenderPacketKind.DISPATCH) {
                            dispatchPacketCount++;
                            continue;
                        }
                        if (packet.packetKind() == RenderPacketKind.CLEAR) {
                            functionPacketCount++;
                            clearPacketCount++;
                            continue;
                        }
                        if (packet.packetKind() == RenderPacketKind.GENERATE_MIPMAP) {
                            functionPacketCount++;
                            continue;
                        }
                        if (packet.packetKind() != RenderPacketKind.DRAW
                                || !(packet instanceof DrawPacket drawPacket)
                                || drawPacket.drawPlan() == null) {
                            continue;
                        }
                        drawPacketCount++;
                        DrawPlan drawPlan = drawPacket.drawPlan();
                        if (drawPlan.submission() == DrawPlan.DrawSubmission.DIRECT_BATCH) {
                            directBatchCount++;
                            if (drawPacket.geometryHandle() != null
                                    && drawPacket.geometryHandle().vertexBufferKey() != null
                                    && drawPacket.geometryHandle().vertexBufferKey().sourceResourceID() == SHARED_SOURCE_REF
                                    && drawPlan.drawCount() > 1) {
                                sameSourceMultiSubmeshGroupCount++;
                            }
                        } else if (drawPlan.submission() == DrawPlan.DrawSubmission.MULTI_DRAW_INDIRECT) {
                            indirectPacketCount++;
                        }
                        if (drawPacket.completionGraphics() != null && drawPacket.completionGraphics().stream().anyMatch(DrawCallGraphics.class::isInstance)) {
                            drawCallPacketCount++;
                        }
                    }
                }
            }
        }

        int honoredIndirectRewriteCount = 0;
        IndirectPlanData indirectPlanData = pipeline
                .getPipelineDataStore(PipelineType.RASTERIZATION, FrameDataDomain.SYNC_READ)
                .get(IndirectPlanData.KEY);
        if (indirectPlanData != null) {
            for (IndirectRewriteResult result : indirectPlanData.allResults()) {
                if (result != null && result.honored()) {
                    honoredIndirectRewriteCount++;
                }
            }
        }

        return new SceneStats(
                stageCount,
                stateBucketCount,
                drawPacketCount,
                dispatchPacketCount,
                functionPacketCount,
                clearPacketCount,
                directBatchCount,
                indirectPacketCount,
                sameSourceMultiSubmeshGroupCount,
                honoredIndirectRewriteCount,
                drawCallPacketCount);
    }

    private void printSceneStats(SceneStats stats) {
        if (statsPrinted) {
            return;
        }
        statsPrinted = true;
        String scenarios = "basic_state_resource_split,shared_source_multi_submesh,draw_index_alignment_validation,draw_call_validation,shader_layout_validation,explicit_indirect_request,compute_side_effect_validation,function_clear_restore_validation,pass_overwrite_validation,mixed_order_validation";
        scenarios += ",named_clear_validation,mipmap_validation,main_target_depth_validation";
        System.out.println("Pipeline scene installed");
        System.out.println("  scenarios=" + scenarios);
        System.out.println("  stageCount=" + stats.stageCount());
        System.out.println("  stateBucketCount=" + stats.stateBucketCount());
        System.out.println("  drawPacketCount=" + stats.drawPacketCount());
        System.out.println("  dispatchPacketCount=" + stats.dispatchPacketCount());
        System.out.println("  functionPacketCount=" + stats.functionPacketCount());
        System.out.println("  clearPacketCount=" + stats.clearPacketCount());
        System.out.println("  directBatchCount=" + stats.directBatchCount());
        System.out.println("  indirectPacketCount=" + stats.indirectPacketCount());
        System.out.println("  sameSourceMultiSubmeshGroupCount=" + stats.sameSourceMultiSubmeshGroupCount());
        System.out.println("  honoredIndirectRewriteCount=" + stats.honoredIndirectRewriteCount());
        System.out.println("  drawCallPacketCount=" + stats.drawCallPacketCount());
    }

    @Override
    public void close() {
        for (AutoCloseable ownedCloseable : ownedCloseables) {
            if (ownedCloseable == null) {
                continue;
            }
            try {
                ownedCloseable.close();
            } catch (Exception ignored) {
            }
        }
        pipeline.shutdown();
    }

    private final class IndirectHarnessModuleDescriptor implements ModuleDescriptor {
        @Override
        public String id() {
            return INDIRECT_MODULE_ID;
        }

        @Override
        public ModuleRuntime createRuntime() {
            return new IndirectHarnessRuntime();
        }
    }

    private final class IndirectHarnessRuntime implements ModuleRuntime {
        @Override
        public String id() {
            return INDIRECT_MODULE_ID;
        }

        @Override
        public void onKernelInit(ModuleRuntimeContext context) {
            context.registerGraphics(
                    MAIN_STAGE_ID,
                    new TestTriangleGraphics(
                            INDIRECT_GRAPHICS_ID,
                            resourceSetC,
                            new float[]{
                                    -0.15f, -0.05f, 0.9f, 0.9f, 0.9f,
                                    0.15f, -0.05f, 0.9f, 0.5f, 0.2f,
                                    0.00f, 0.35f, 0.4f, 0.9f, 0.9f
                            },
                            SubmissionCapability.INDIRECT_READY),
                    SCENE_PARAMETER,
                    PipelineType.RASTERIZATION,
                    ModuleGraphicsLifetime.PERMANENT);
            context.requestIndirectPlan(MAIN_STAGE_ID, INDIRECT_GRAPHICS_ID);
        }
    }

    private static final class HarnessRenderContext extends RenderContext {
        private HarnessRenderContext(int framebufferWidth, int framebufferHeight) {
            setWindowSize(framebufferWidth, framebufferHeight);
        }

        private void setWindowSize(int framebufferWidth, int framebufferHeight) {
            this.windowWidth = Math.max(1, framebufferWidth);
            this.windowHeight = Math.max(1, framebufferHeight);
            projectionMatrix().identity();
            viewMatrix().identity();
            modelMatrix().identity();
        }
    }

    private abstract static class BaseMeshGraphics extends MeshGraphics {
        private final CompiledRenderSetting compiledRenderSetting;

        private BaseMeshGraphics(KeyId keyId, CompiledRenderSetting compiledRenderSetting) {
            super(keyId);
            this.compiledRenderSetting = compiledRenderSetting;
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
        public long descriptorVersion() {
            return 1L;
        }

        @Override
        public CompiledRenderSetting buildRenderDescriptor(rogo.sketch.core.pipeline.parmeter.RenderParameter renderParameter) {
            return compiledRenderSetting;
        }
    }

    private static final class TestTriangleGraphics extends BaseMeshGraphics implements InstanceVertexEncoder {
        private final DynamicMesh mesh = SHARED_TRIANGLE_MESH;
        private final float[] vertices;
        private final SubmissionCapability submissionCapability;

        private TestTriangleGraphics(KeyId keyId, CompiledRenderSetting compiledRenderSetting, float[] vertices) {
            this(keyId, compiledRenderSetting, vertices, SubmissionCapability.DIRECT_BATCHABLE);
        }

        private TestTriangleGraphics(
                KeyId keyId,
                CompiledRenderSetting compiledRenderSetting,
                float[] vertices,
                SubmissionCapability submissionCapability) {
            super(keyId, compiledRenderSetting);
            this.vertices = vertices != null ? vertices.clone() : new float[0];
            this.submissionCapability = submissionCapability != null
                    ? submissionCapability
                    : SubmissionCapability.DIRECT_BATCHABLE;
        }

        @Override
        public DynamicMesh getPreparedMesh() {
            return mesh;
        }

        @Override
        public void writeInstanceVertex(KeyId componentKey, VertexRecordWriter writer) {
            if (!GEOMETRY_COMPONENT.equals(componentKey) || writer == null) {
                return;
            }
            for (int i = 0; i < vertices.length; i += 5) {
                writer.put(vertices[i], vertices[i + 1]);
                writer.put(vertices[i + 2], vertices[i + 3], vertices[i + 4]);
            }
        }

        @Override
        public SubmissionCapability submissionCapability() {
            return submissionCapability;
        }
    }

    private static final class TestDepthTriangleGraphics extends BaseMeshGraphics implements InstanceVertexEncoder {
        private final DynamicMesh mesh = DEPTH_TRIANGLE_MESH;
        private final float[] vertices;

        private TestDepthTriangleGraphics(KeyId keyId, CompiledRenderSetting compiledRenderSetting, float[] vertices) {
            super(keyId, compiledRenderSetting);
            this.vertices = vertices != null ? vertices.clone() : new float[0];
        }

        @Override
        public DynamicMesh getPreparedMesh() {
            return mesh;
        }

        @Override
        public void writeInstanceVertex(KeyId componentKey, VertexRecordWriter writer) {
            if (!GEOMETRY_COMPONENT.equals(componentKey) || writer == null) {
                return;
            }
            for (int i = 0; i < vertices.length; i += 6) {
                writer.put(vertices[i], vertices[i + 1], vertices[i + 2]);
                writer.put(vertices[i + 3], vertices[i + 4], vertices[i + 5]);
            }
        }
    }

    private static final class InstancedBakedGraphics extends BaseMeshGraphics implements InstanceVertexEncoder {
        private final BakedMesh mesh;
        private final float offsetX;
        private final float offsetY;

        private InstancedBakedGraphics(
                KeyId keyId,
                CompiledRenderSetting compiledRenderSetting,
                BakedMesh mesh,
                float offsetX,
                float offsetY) {
            super(keyId, compiledRenderSetting);
            this.mesh = mesh;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        @Override
        public BakedMesh getPreparedMesh() {
            return mesh;
        }

        @Override
        public void writeInstanceVertex(KeyId componentKey, VertexRecordWriter writer) {
            if (!INSTANCE_OFFSET_COMPONENT.equals(componentKey) || writer == null) {
                return;
            }
            writer.put(offsetX, offsetY);
        }
    }

    private static final class TestComputeGraphics extends ComputeGraphics {
        private final PartialRenderSetting partialRenderSetting;

        private TestComputeGraphics(KeyId keyId, CompiledRenderSetting compiledRenderSetting) {
            super(keyId, null, dispatchContext -> dispatchContext.dispatch(1, 1, 1));
            this.partialRenderSetting = compiledRenderSetting != null
                    ? new PartialRenderSetting(
                    compiledRenderSetting.renderSetting().renderState(),
                    compiledRenderSetting.renderSetting().targetBinding(),
                    compiledRenderSetting.renderSetting().resourceBinding(),
                    compiledRenderSetting.renderSetting().shouldSwitchRenderState())
                    : PartialRenderSetting.EMPTY;
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
        public long descriptorVersion() {
            return partialDescriptorVersion(partialRenderSetting);
        }

        @Override
        public CompiledRenderSetting buildRenderDescriptor(rogo.sketch.core.pipeline.parmeter.RenderParameter renderParameter) {
            return compilePartialDescriptor(renderParameter, partialRenderSetting);
        }
    }

    private record SharedSourceMeshes(BakedMesh leftMesh, BakedMesh rightMesh) {
    }

    private record SceneStats(
            int stageCount,
            int stateBucketCount,
            int drawPacketCount,
            int dispatchPacketCount,
            int functionPacketCount,
            int clearPacketCount,
            int directBatchCount,
            int indirectPacketCount,
            int sameSourceMultiSubmeshGroupCount,
            int honoredIndirectRewriteCount,
            int drawCallPacketCount
    ) {
        private boolean isSatisfied() {
            return stageCount >= 4
                    && stateBucketCount >= 6
                    && drawPacketCount >= 8
                    && dispatchPacketCount >= 1
                    && functionPacketCount >= 4
                    && clearPacketCount >= 2
                    && directBatchCount >= 1
                    && indirectPacketCount >= 1
                    && sameSourceMultiSubmeshGroupCount >= 1
                    && honoredIndirectRewriteCount >= 1
                    && drawCallPacketCount >= 1;
        }
    }
}

