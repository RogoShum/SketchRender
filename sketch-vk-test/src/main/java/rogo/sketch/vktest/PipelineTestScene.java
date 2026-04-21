package rogo.sketch.vktest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.joml.Vector2i;
import org.joml.Vector3i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import rogo.sketch.backend.opengl.OpenGLFramebufferHandleResource;
import rogo.sketch.backend.vulkan.VulkanBackendRuntime;
import rogo.sketch.backend.vulkan.VulkanTextureResource;
import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.backend.AsyncComputeRequest;
import rogo.sketch.core.backend.AsyncGraphicsRequest;
import rogo.sketch.core.backend.BufferedResourceDescriptor;
import rogo.sketch.core.backend.BufferedResourceSet;
import rogo.sketch.core.backend.BufferingMode;
import rogo.sketch.core.backend.AsyncGpuJobHandle;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.ResourceEpoch;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.builder.VertexRecordWriter;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.state.RenderStatePatch;
import rogo.sketch.core.driver.state.DefaultRenderStates;
import rogo.sketch.core.driver.state.component.ShaderState;
import rogo.sketch.core.event.GraphicsPipelineInitEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityPresets;
import rogo.sketch.core.graphics.ecs.GraphicsQuery;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.model.BakedMesh;
import rogo.sketch.core.model.DynamicMesh;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.CopyTexturePacket;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.packet.GenerateMipmapPacket;
import rogo.sketch.core.packet.TransferPlanKey;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketKind;
import rogo.sketch.core.packet.RenderPacketQueue;
import rogo.sketch.core.packet.ResourceBindingPlan;
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
import rogo.sketch.core.pipeline.graph.scheduler.SimpleProfiler;
import rogo.sketch.core.pipeline.parmeter.ComputeParameter;
import rogo.sketch.core.pipeline.parmeter.FunctionParameter;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
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
import rogo.sketch.core.shader.uniform.UniformUpdateDomain;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.util.OrderRequirement;
import rogo.sketch.core.util.RenderTargetUtil;
import rogo.sketch.core.vertex.DefaultDataFormats;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

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
    private static final KeyId ASYNC_HIZ_SOURCE_TARGET = KeyId.of("sketch_vktest:async_hiz_source_target");
    private static final KeyId ASYNC_HIZ_SOURCE_COLOR = KeyId.of("sketch_vktest:async_hiz_source_color");
    private static final KeyId ASYNC_HIZ_SOURCE_DEPTH = KeyId.of("sketch_vktest:async_hiz_source_depth");
    private static final KeyId ASYNC_HIZ_SOURCE_SNAPSHOT = KeyId.of("sketch_vktest:async_hiz_source_snapshot");
    private static final KeyId ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT = KeyId.of("sketch_vktest:async_hiz_source_snapshot_inspect");
    private static final KeyId ASYNC_HIZ_TEXTURE = KeyId.of("sketch_vktest:async_hiz_texture");
    private static final KeyId ASYNC_HIZ_ASYNC_TEXTURE = KeyId.of("sketch_vktest:async_hiz_async_texture");
    private static final KeyId ASYNC_OFFSCREEN_TARGET = KeyId.of("sketch_vktest:async_offscreen_target");
    private static final KeyId ASYNC_OFFSCREEN_PRESENT_TEXTURE = KeyId.of("sketch_vktest:async_offscreen_present_texture");
    private static final KeyId UBO_A = KeyId.of("sketch_vktest:ubo_a");
    private static final KeyId UBO_B = KeyId.of("sketch_vktest:ubo_b");
    private static final KeyId UBO_C = KeyId.of("sketch_vktest:ubo_c");
    private static final KeyId MAIN_TARGET = TargetBinding.DEFAULT_RENDER_TARGET;
    private static final KeyId SHARED_SOURCE_ID = KeyId.of("sketch_vktest:shared_source_asset");
    private static final KeyId INDIRECT_GRAPHICS_ID = KeyId.of("sketch_vktest:indirect_triangle");
    private static final String BUNNY_MODEL_PATH = "models/bunny/bunny.obj";
    private static final String TEAPOT_MODEL_PATH = "models/teapot/teapot.obj";
    private static final KeyId SAMPLED_TEXTURE_SHADER_ID = KeyId.of("sketch_vktest:sampled_texture_shader");
    private static final KeyId INSPECTION_TEXTURE_SHADER_ID = KeyId.of("sketch_vktest:inspection_texture_shader");
    private static final KeyId INSPECTION_LINEARIZED_TEXTURE_SHADER_ID = KeyId.of("sketch_vktest:inspection_linearized_texture_shader");
    private static final KeyId HIZ_SNAPSHOT_COPY_SHADER_ID = KeyId.of("sketch_vktest:hiz_snapshot_copy_shader");
    private static final KeyId ASYNC_HIZ_FIRST_SHADER_ID = KeyId.of("sketch_vktest:async_hiz_first_shader");
    private static final KeyId ASYNC_HIZ_SECOND_SHADER_ID = KeyId.of("sketch_vktest:async_hiz_second_shader");
    private static final KeyId TEAPOT_OFFSCREEN_SHADER_ID = KeyId.of("sketch_vktest:teapot_offscreen_shader");
    private static final KeyId COLOR_SCENE_SETTING_ID = KeyId.of("sketch_vktest:color_scene");
    private static final KeyId INSTANCED_COLOR_SCENE_SETTING_ID = KeyId.of("sketch_vktest:instanced_color_scene");
    private static final KeyId DRAW_CALL_SCENE_SETTING_ID = KeyId.of("sketch_vktest:draw_call_scene");
    private static final KeyId SUBSET_SCENE_SETTING_ID = KeyId.of("sketch_vktest:subset_scene");
    private static final KeyId DEPTH_VALIDATION_SCENE_SETTING_ID = KeyId.of("sketch_vktest:depth_validation_scene");
    private static final KeyId SAMPLED_TEXTURE_SCENE_SETTING_ID = KeyId.of("sketch_vktest:sampled_texture_scene");
    private static final KeyId INSPECTION_TEXTURE_SCENE_SETTING_ID = KeyId.of("sketch_vktest:inspection_texture_scene");
    private static final KeyId COMPUTE_SCENE_SETTING_ID = KeyId.of("sketch_vktest:compute_scene");
    private static final KeyId ASYNC_HIZ_SCENE_SETTING_ID = KeyId.of("sketch_vktest:async_hiz_scene");
    private static final KeyId ASYNC_HIZ_FIRST_SCENE_SETTING_ID = KeyId.of("sketch_vktest:async_hiz_first_scene");
    private static final KeyId ASYNC_HIZ_SECOND_SCENE_SETTING_ID = KeyId.of("sketch_vktest:async_hiz_second_scene");
    private static final KeyId ASYNC_HIZ_SNAPSHOT_COPY_SCENE_SETTING_ID = KeyId.of("sketch_vktest:async_hiz_snapshot_copy_scene");
    private static final KeyId ASYNC_OFFSCREEN_SCENE_SETTING_ID = KeyId.of("sketch_vktest:async_offscreen_scene");
    private static final String INDIRECT_MODULE_ID = "sketch_vktest_indirect_harness";
    private static final KeyId HIZ_FIRST_TAG = KeyId.of("sketch_vktest:hiz_first");
    private static final KeyId HIZ_SECOND_TAG = KeyId.of("sketch_vktest:hiz_second");
    private static final int HIZ_DEPTH_LEVELS = 8;
    private static final int VK_TEST_RENDER_DISTANCE = 16;
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
    private static final VertexLayoutSpec OBJ_SCENE_LAYOUT = VertexLayoutSpec.builder()
            .addStatic(BakedTypeMesh.BAKED_MESH, DefaultDataFormats.OBJ)
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
    private static final RasterizationParameter OBJ_SCENE_PARAMETER = new RasterizationParameter(
            OBJ_SCENE_LAYOUT,
            PrimitiveType.TRIANGLES,
            MeshIndexMode.EXPLICIT_LOCAL,
            BufferUpdatePolicy.IMMUTABLE,
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
    private static final DynamicMesh INSPECTION_QUAD_MESH = new DynamicMesh(
            KeyId.of("sketch_vktest:inspection_quad_mesh"),
            SCENE_FORMAT,
            PrimitiveType.TRIANGLES,
            6,
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
    private static final String INSPECTION_VERTEX_SHADER = """
            #version 450 core
            in vec2 position;
            in vec3 color;
            out vec2 vUv;

            void main() {
                gl_Position = vec4(position, 0.0, 1.0);
                vUv = color.xy;
            }
            """;
    private static final String INSPECTION_FRAGMENT_SHADER = """
            #version 450 core
            in vec2 vUv;
            out vec4 outColor;
            uniform sampler2D test_texture;

            void main() {
                float value = texture(test_texture, vUv).r;
                outColor = vec4(value, value, value, 1.0);
            }
            """;
    private static final String INSPECTION_LINEARIZED_FRAGMENT_SHADER = """
            #version 450 core
            in vec2 vUv;
            out vec4 outColor;
            uniform sampler2D test_texture;

            float near = 0.05;
            float far = 16.0;

            float LinearizeDepth(float depth) {
                float z = depth * 2.0 - 1.0;
                return (near * far) / (far + near - z * (far - near));
            }

            void main() {
                float depth = texture(test_texture, vUv).r;
                float linearDepth = clamp(LinearizeDepth(depth) / far, 0.0, 1.0);
                outColor = vec4(linearDepth, linearDepth, linearDepth, 1.0);
            }
            """;
    private static final String COMPUTE_SHADER_SOURCE = """
            #version 450 core
            layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;

            void main() {
            }
            """;
    private static final String HIZ_SNAPSHOT_COPY_SHADER_SOURCE = """
            #version 450 core
            layout(r32f) uniform writeonly image2D output_texture_0;

            uniform sampler2D sketch_SamplerTexture;

            layout(local_size_x = 16, local_size_y = 16) in;

            void main() {
                ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
                ivec2 outputSize = imageSize(output_texture_0);
                if (pixel.x >= outputSize.x || pixel.y >= outputSize.y) {
                    return;
                }

                float depth = texelFetch(sketch_SamplerTexture, pixel, 0).r;
                imageStore(output_texture_0, pixel, vec4(depth, 0.0, 0.0, 1.0));
            }
            """;
    private static final String ASYNC_HIZ_FIRST_SHADER_SOURCE = """
            #version 430 core
            layout(local_size_x = 16, local_size_y = 16) in;

            layout(r32f) uniform writeonly image2D output_texture_0;
            uniform sampler2D hiz_source_snapshot;

            const int TILE_SIZE = 16;
            const int TILE_EDGE = TILE_SIZE * 2;
            const float HIZ_NEAR = 0.05;
            const float HIZ_FAR = 1024.0;

            shared float depthCache[TILE_EDGE * TILE_EDGE];
            shared float depthCacheSmall[TILE_SIZE * TILE_SIZE];

            int cacheIndex32(int x, int y) {
                return y * TILE_EDGE + x;
            }

            int cacheIndex16(int x, int y) {
                return y * TILE_SIZE + x;
            }

            float linearizeDepth(float depth) {
                float z = depth * 2.0 - 1.0;
                return (HIZ_NEAR * HIZ_FAR) / (HIZ_FAR + HIZ_NEAR - z * (HIZ_FAR - HIZ_NEAR));
            }

            ivec2 packedLevelSize(ivec2 screenSize, int packedLevel) {
                int width = max(1, screenSize.x >> (packedLevel + 1));
                int height = max(1, screenSize.y >> (packedLevel + 1));
                if ((width & 1) == 1) {
                    width += 1;
                }
                if ((height & 1) == 1) {
                    height += 1;
                }
                return ivec2(width, height);
            }

            int packedLevelYOffset(ivec2 screenSize, int packedLevel) {
                int offset = 0;
                for (int level = 0; level < packedLevel; ++level) {
                    offset += packedLevelSize(screenSize, level).y;
                }
                return offset;
            }

            void reduce32To16(ivec2 localID, int nextWidth, int nextHeight) {
                if (localID.x >= nextWidth || localID.y >= nextHeight) {
                    return;
                }
                int x0 = localID.x * 2;
                int y0 = localID.y * 2;
                float d0 = depthCache[cacheIndex32(x0, y0)];
                float d1 = depthCache[cacheIndex32(x0 + 1, y0)];
                float d2 = depthCache[cacheIndex32(x0, y0 + 1)];
                float d3 = depthCache[cacheIndex32(x0 + 1, y0 + 1)];
                depthCacheSmall[cacheIndex16(localID.x, localID.y)] = max(max(d0, d1), max(d2, d3));
            }

            void reduce16To32(ivec2 localID, int nextWidth, int nextHeight) {
                if (localID.x >= nextWidth || localID.y >= nextHeight) {
                    return;
                }
                int x0 = localID.x * 2;
                int y0 = localID.y * 2;
                float d0 = depthCacheSmall[cacheIndex16(x0, y0)];
                float d1 = depthCacheSmall[cacheIndex16(x0 + 1, y0)];
                float d2 = depthCacheSmall[cacheIndex16(x0, y0 + 1)];
                float d3 = depthCacheSmall[cacheIndex16(x0 + 1, y0 + 1)];
                depthCache[cacheIndex32(localID.x, localID.y)] = max(max(d0, d1), max(d2, d3));
            }

            void store16(ivec2 screenSize, ivec2 groupStart, ivec2 localID, int nextWidth, int nextHeight, int packedLevel) {
                if (localID.x >= nextWidth || localID.y >= nextHeight) {
                    return;
                }
                ivec2 outPos = groupStart + localID;
                ivec2 outSize = packedLevelSize(screenSize, packedLevel);
                if (outPos.x >= outSize.x || outPos.y >= outSize.y) {
                    return;
                }
                outPos.y += packedLevelYOffset(screenSize, packedLevel);
                imageStore(output_texture_0, outPos, vec4(depthCacheSmall[cacheIndex16(localID.x, localID.y)], 0.0, 0.0, 1.0));
            }

            void store32(ivec2 screenSize, ivec2 groupStart, ivec2 localID, int nextWidth, int nextHeight, int packedLevel) {
                if (localID.x >= nextWidth || localID.y >= nextHeight) {
                    return;
                }
                ivec2 outPos = groupStart + localID;
                ivec2 outSize = packedLevelSize(screenSize, packedLevel);
                if (outPos.x >= outSize.x || outPos.y >= outSize.y) {
                    return;
                }
                outPos.y += packedLevelYOffset(screenSize, packedLevel);
                imageStore(output_texture_0, outPos, vec4(depthCache[cacheIndex32(localID.x, localID.y)], 0.0, 0.0, 1.0));
            }

            void main() {
                ivec2 screenSize = textureSize(hiz_source_snapshot, 0);
                ivec2 groupStart = ivec2(gl_WorkGroupID.xy) * TILE_SIZE;
                ivec2 localID = ivec2(gl_LocalInvocationID.xy);
                ivec2 minCoords = screenSize - ivec2(1);

                for (int dy = 0; dy < 2; ++dy) {
                    for (int dx = 0; dx < 2; ++dx) {
                        ivec2 cachePos = localID * 2 + ivec2(dx, dy);
                        ivec2 texPos = min(groupStart * 2 + cachePos, minCoords);
                        depthCache[cacheIndex32(cachePos.x, cachePos.y)] =
                                linearizeDepth(texelFetch(hiz_source_snapshot, texPos, 0).r);
                    }
                }
                memoryBarrierShared();
                barrier();

                int nextWidth = TILE_SIZE;
                int nextHeight = TILE_SIZE;
                reduce32To16(localID, nextWidth, nextHeight);
                memoryBarrierShared();
                barrier();
                store16(screenSize, groupStart, localID, nextWidth, nextHeight, 0);

                nextWidth /= 2;
                nextHeight /= 2;
                reduce16To32(localID, nextWidth, nextHeight);
                memoryBarrierShared();
                barrier();
                store32(screenSize, groupStart / 2, localID, nextWidth, nextHeight, 1);

                nextWidth /= 2;
                nextHeight /= 2;
                reduce32To16(localID, nextWidth, nextHeight);
                memoryBarrierShared();
                barrier();
                store16(screenSize, groupStart / 4, localID, nextWidth, nextHeight, 2);

                nextWidth /= 2;
                nextHeight /= 2;
                reduce16To32(localID, nextWidth, nextHeight);
                memoryBarrierShared();
                barrier();
                store32(screenSize, groupStart / 8, localID, nextWidth, nextHeight, 3);
            }
            """;
    private static final String ASYNC_HIZ_SECOND_SHADER_SOURCE = """
            #version 430 core
            layout(local_size_x = 16, local_size_y = 16) in;

            layout(r32f) uniform writeonly image2D output_texture_0;
            uniform sampler2D hiz_source_atlas;
            uniform sampler2D hiz_source_snapshot;

            const int TILE_SIZE = 16;
            const int TILE_EDGE = TILE_SIZE * 2;

            shared float depthCache[TILE_EDGE * TILE_EDGE];
            shared float depthCacheSmall[TILE_SIZE * TILE_SIZE];

            int cacheIndex32(int x, int y) {
                return y * TILE_EDGE + x;
            }

            int cacheIndex16(int x, int y) {
                return y * TILE_SIZE + x;
            }

            ivec2 packedLevelSize(ivec2 screenSize, int packedLevel) {
                int width = max(1, screenSize.x >> (packedLevel + 1));
                int height = max(1, screenSize.y >> (packedLevel + 1));
                if ((width & 1) == 1) {
                    width += 1;
                }
                if ((height & 1) == 1) {
                    height += 1;
                }
                return ivec2(width, height);
            }

            int packedLevelYOffset(ivec2 screenSize, int packedLevel) {
                int offset = 0;
                for (int level = 0; level < packedLevel; ++level) {
                    offset += packedLevelSize(screenSize, level).y;
                }
                return offset;
            }

            void reduce32To16(ivec2 localID, int nextWidth, int nextHeight) {
                if (localID.x >= nextWidth || localID.y >= nextHeight) {
                    return;
                }
                int x0 = localID.x * 2;
                int y0 = localID.y * 2;
                float d0 = depthCache[cacheIndex32(x0, y0)];
                float d1 = depthCache[cacheIndex32(x0 + 1, y0)];
                float d2 = depthCache[cacheIndex32(x0, y0 + 1)];
                float d3 = depthCache[cacheIndex32(x0 + 1, y0 + 1)];
                depthCacheSmall[cacheIndex16(localID.x, localID.y)] = max(max(d0, d1), max(d2, d3));
            }

            void reduce16To32(ivec2 localID, int nextWidth, int nextHeight) {
                if (localID.x >= nextWidth || localID.y >= nextHeight) {
                    return;
                }
                int x0 = localID.x * 2;
                int y0 = localID.y * 2;
                float d0 = depthCacheSmall[cacheIndex16(x0, y0)];
                float d1 = depthCacheSmall[cacheIndex16(x0 + 1, y0)];
                float d2 = depthCacheSmall[cacheIndex16(x0, y0 + 1)];
                float d3 = depthCacheSmall[cacheIndex16(x0 + 1, y0 + 1)];
                depthCache[cacheIndex32(localID.x, localID.y)] = max(max(d0, d1), max(d2, d3));
            }

            void store16(ivec2 sourceScreen, ivec2 originalScreen, ivec2 groupStart, ivec2 localID, int nextWidth, int nextHeight, int packedLevel) {
                if (localID.x >= nextWidth || localID.y >= nextHeight) {
                    return;
                }
                ivec2 outPos = groupStart + localID;
                ivec2 outSize = packedLevelSize(sourceScreen, packedLevel - 4);
                if (outPos.x >= outSize.x || outPos.y >= outSize.y) {
                    return;
                }
                outPos.y += packedLevelYOffset(originalScreen, packedLevel);
                imageStore(output_texture_0, outPos, vec4(depthCacheSmall[cacheIndex16(localID.x, localID.y)], 0.0, 0.0, 1.0));
            }

            void store32(ivec2 sourceScreen, ivec2 originalScreen, ivec2 groupStart, ivec2 localID, int nextWidth, int nextHeight, int packedLevel) {
                if (localID.x >= nextWidth || localID.y >= nextHeight) {
                    return;
                }
                ivec2 outPos = groupStart + localID;
                ivec2 outSize = packedLevelSize(sourceScreen, packedLevel - 4);
                if (outPos.x >= outSize.x || outPos.y >= outSize.y) {
                    return;
                }
                outPos.y += packedLevelYOffset(originalScreen, packedLevel);
                imageStore(output_texture_0, outPos, vec4(depthCache[cacheIndex32(localID.x, localID.y)], 0.0, 0.0, 1.0));
            }

            void main() {
                ivec2 originalScreen = textureSize(hiz_source_snapshot, 0);
                ivec2 sourceScreen = packedLevelSize(originalScreen, 3);
                ivec2 groupStart = ivec2(gl_WorkGroupID.xy) * TILE_SIZE;
                ivec2 localID = ivec2(gl_LocalInvocationID.xy);
                ivec2 minCoords = sourceScreen - ivec2(1);
                int sourceYOffset = packedLevelYOffset(originalScreen, 3);

                for (int dy = 0; dy < 2; ++dy) {
                    for (int dx = 0; dx < 2; ++dx) {
                        ivec2 cachePos = localID * 2 + ivec2(dx, dy);
                        ivec2 texPos = min(groupStart * 2 + cachePos, minCoords);
                        texPos.y += sourceYOffset;
                        depthCache[cacheIndex32(cachePos.x, cachePos.y)] = texelFetch(hiz_source_atlas, texPos, 0).r;
                    }
                }
                memoryBarrierShared();
                barrier();

                int nextWidth = TILE_SIZE;
                int nextHeight = TILE_SIZE;
                reduce32To16(localID, nextWidth, nextHeight);
                memoryBarrierShared();
                barrier();
                store16(sourceScreen, originalScreen, groupStart, localID, nextWidth, nextHeight, 4);

                nextWidth /= 2;
                nextHeight /= 2;
                reduce16To32(localID, nextWidth, nextHeight);
                memoryBarrierShared();
                barrier();
                store32(sourceScreen, originalScreen, groupStart / 2, localID, nextWidth, nextHeight, 5);

                nextWidth /= 2;
                nextHeight /= 2;
                reduce32To16(localID, nextWidth, nextHeight);
                memoryBarrierShared();
                barrier();
                store16(sourceScreen, originalScreen, groupStart / 4, localID, nextWidth, nextHeight, 6);

                nextWidth /= 2;
                nextHeight /= 2;
                reduce16To32(localID, nextWidth, nextHeight);
                memoryBarrierShared();
                barrier();
                store32(sourceScreen, originalScreen, groupStart / 8, localID, nextWidth, nextHeight, 7);
            }
            """;
    private static final String TEAPOT_OFFSCREEN_VERTEX_SHADER = """
            #version 450 core
            in vec3 position;
            out vec3 vWorld;

            mat3 rotateX(float angle) {
                float s = sin(angle);
                float c = cos(angle);
                return mat3(
                        1.0, 0.0, 0.0,
                        0.0, c, -s,
                        0.0, s, c);
            }

            mat3 rotateY(float angle) {
                float s = sin(angle);
                float c = cos(angle);
                return mat3(
                        c, 0.0, s,
                        0.0, 1.0, 0.0,
                        -s, 0.0, c);
            }

            void main() {
                vec3 centered = position - vec3(0.0, 27.5, 0.0);
                centered /= 60.0;
                mat3 rotation = rotateY(0.65) * rotateX(-0.55);
                vec3 world = rotation * centered;
                vWorld = world;
                gl_Position = vec4(world.xy * 0.54 + vec2(0.01, -0.08), 0.0, 1.0);
            }
            """;
    private static final String TEAPOT_OFFSCREEN_FRAGMENT_SHADER = """
            #version 450 core
            in vec3 vWorld;
            out vec4 outColor;

            void main() {
                float diffuse = clamp(0.55 + vWorld.z * 0.65, 0.0, 1.0);
                vec3 base = vec3(0.82, 0.56, 0.24);
                vec3 color = base * (0.25 + 0.75 * diffuse);
                outColor = vec4(color, 1.0);
            }
            """;
    private static final int VALIDATION_WARMUP_FRAMES = 2;
    private static final int VALIDATION_HARD_DEADLINE_FRAMES = 4;
    private static final int ASYNC_VALIDATION_HARD_DEADLINE_FRAMES = 24;

    private static KeyId asyncHiZLevelShaderId(int level) {
        return KeyId.of("sketch_vktest:async_hiz_level_" + level + "_shader");
    }

    private static KeyId asyncHiZLevelSceneSettingId(int level) {
        return KeyId.of("sketch_vktest:async_hiz_level_" + level + "_scene");
    }

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
    private CompiledRenderSetting asyncHiZSourceInspectSetting;
    private CompiledRenderSetting asyncHiZSnapshotInspectSetting;
    private CompiledRenderSetting asyncHiZLinearizedInspectSetting;
    private CompiledRenderSetting asyncHiZPublishedInspectSetting;
    private CompiledRenderSetting asyncHiZFirstSetting;
    private CompiledRenderSetting asyncHiZSecondSetting;
    private CompiledRenderSetting asyncHiZSnapshotFreezeSetting;
    private CompiledRenderSetting asyncOffscreenInspectSetting;
    private CompiledRenderSetting asyncOffscreenGraphicsSetting;
    private final List<CompiledRenderSetting> asyncHiZLevelSettings = new ArrayList<>();
    private GraphicsEntityBlueprint asyncHiZFirstBlueprint;
    private GraphicsEntityBlueprint asyncHiZSecondBlueprint;
    private GraphicsEntityBlueprint asyncHiZSnapshotFreezeBlueprint;
    private final List<GraphicsEntityBlueprint> asyncHiZLevelBlueprints = new ArrayList<>();
    private List<GraphicsEntityBlueprint> asyncOffscreenGraphicsBlueprints = List.of();
    private int renderedFrames;
    private int asyncSubmissionCooldownFrames;
    private long asyncSubmissionResumeSwapchainGeneration;
    private long asyncSubmissionResumeRenderedFrameCount;
    private long asyncOffscreenResumeSwapchainGeneration;
    private long asyncOffscreenResumeRenderedFrameCount;
    private boolean vulkanOffscreenForceSync;
    private boolean statsPrinted;
    private boolean asyncOffscreenDirty = true;
    private boolean validated;
    private boolean relaxAsyncValidation;
    private long blueprintOrderHint;
    private final AsyncHiZState asyncHiZState = new AsyncHiZState();
    private final AsyncOffscreenState asyncOffscreenState = new AsyncOffscreenState();
    private VulkanTextureResource asyncHiZSourceSnapshotNative;
    private VulkanTextureResource asyncHiZSourceSnapshotInspectNative;
    private List<BakedMesh> teapotMeshes = List.of();

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
                        EnumSet.of(ImageUsage.DEPTH_ATTACHMENT, ImageUsage.SAMPLED),
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
        teapotMeshes = loadBundledBakedMeshes(KeyId.of("sketch_vktest:teapot_model"), TEAPOT_MODEL_PATH);
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
        installDirectSceneResources(resourceManager);
        pipeline.installPipelineResources();
    }

    private void installDirectSceneResources(GraphicsResourceManager resourceManager) {
        if (resourceManager == null) {
            return;
        }
        replaceDirectResource(
                resourceManager,
                ResourceTypes.SHADER_TEMPLATE,
                INSPECTION_TEXTURE_SHADER_ID,
                new ShaderTemplate(
                        INSPECTION_TEXTURE_SHADER_ID,
                        Map.of(
                                ShaderType.VERTEX, INSPECTION_VERTEX_SHADER,
                                ShaderType.FRAGMENT, INSPECTION_FRAGMENT_SHADER),
                        ShaderVertexLayout.empty(),
                        new SketchShaderPreprocessor(),
                        resourceManager.getSubResourceProvider(),
                        Map.of(
                                ResourceTypes.TEXTURE,
                                Map.of(TEXTURE_BINDING, 0))));
        replaceDirectResource(
                resourceManager,
                ResourceTypes.SHADER_TEMPLATE,
                INSPECTION_LINEARIZED_TEXTURE_SHADER_ID,
                new ShaderTemplate(
                        INSPECTION_LINEARIZED_TEXTURE_SHADER_ID,
                        Map.of(
                                ShaderType.VERTEX, INSPECTION_VERTEX_SHADER,
                                ShaderType.FRAGMENT, INSPECTION_LINEARIZED_FRAGMENT_SHADER),
                        ShaderVertexLayout.empty(),
                        new SketchShaderPreprocessor(),
                        resourceManager.getSubResourceProvider(),
                        Map.of(
                                ResourceTypes.TEXTURE,
                                Map.of(TEXTURE_BINDING, 0))));
        replaceDirectResource(
                resourceManager,
                ResourceTypes.SHADER_TEMPLATE,
                HIZ_SNAPSHOT_COPY_SHADER_ID,
                new ShaderTemplate(
                        HIZ_SNAPSHOT_COPY_SHADER_ID,
                        Map.of(ShaderType.COMPUTE, HIZ_SNAPSHOT_COPY_SHADER_SOURCE),
                        ShaderVertexLayout.empty(),
                        new SketchShaderPreprocessor(),
                        resourceManager.getSubResourceProvider(),
                        Map.of(
                                ResourceTypes.IMAGE, Map.of(KeyId.of("output_texture_0"), 0),
                                ResourceTypes.TEXTURE, Map.of(KeyId.of("sketch_SamplerTexture"), 1))));
        for (int level = 0; level < HIZ_DEPTH_LEVELS; level++) {
            KeyId shaderId = asyncHiZLevelShaderId(level);
            Map<KeyId, Map<KeyId, Integer>> bindings = level == 0
                    ? Map.of(
                    ResourceTypes.IMAGE, Map.of(KeyId.of("output_texture_0"), 0),
                    ResourceTypes.TEXTURE, Map.of(KeyId.of("hiz_source_snapshot"), 1))
                    : Map.of(
                    ResourceTypes.IMAGE, Map.of(KeyId.of("output_texture_0"), 0),
                    ResourceTypes.TEXTURE, Map.of(
                            KeyId.of("hiz_source_snapshot"), 1,
                            KeyId.of("hiz_source_atlas"), 2));
            replaceDirectResource(
                    resourceManager,
                    ResourceTypes.SHADER_TEMPLATE,
                    shaderId,
                    new ShaderTemplate(
                            shaderId,
                            Map.of(ShaderType.COMPUTE, buildAsyncHiZLevelShaderSource(level)),
                            ShaderVertexLayout.empty(),
                            new SketchShaderPreprocessor(),
                            resourceManager.getSubResourceProvider(),
                            bindings));
        }
        replaceDirectResource(
                resourceManager,
                ResourceTypes.SHADER_TEMPLATE,
                TEAPOT_OFFSCREEN_SHADER_ID,
                new ShaderTemplate(
                        TEAPOT_OFFSCREEN_SHADER_ID,
                        Map.of(
                                ShaderType.VERTEX, TEAPOT_OFFSCREEN_VERTEX_SHADER,
                                ShaderType.FRAGMENT, TEAPOT_OFFSCREEN_FRAGMENT_SHADER),
                        ShaderVertexLayout.empty(),
                        new SketchShaderPreprocessor(),
                        resourceManager.getSubResourceProvider(),
                        Map.of()));
        replaceJsonResource(
                resourceManager,
                ResourceTypes.PARTIAL_RENDER_SETTING,
                ASYNC_HIZ_SNAPSHOT_COPY_SCENE_SETTING_ID,
                buildAsyncHiZSnapshotCopyRenderSettingJson(resolveHiZSourceDepthTextureId()));
        for (int level = 0; level < HIZ_DEPTH_LEVELS; level++) {
            replaceJsonResource(
                    resourceManager,
                    ResourceTypes.PARTIAL_RENDER_SETTING,
                    asyncHiZLevelSceneSettingId(level),
                    buildAsyncHiZLevelRenderSettingJson(level));
        }
    }

    private void replaceJsonResource(
            GraphicsResourceManager resourceManager,
            KeyId resourceType,
            KeyId resourceId,
            String json) {
        if (resourceManager.hasResource(resourceType, resourceId)) {
            resourceManager.removeResource(resourceType, resourceId);
        }
        resourceManager.registerJson(resourceType, resourceId, json, resourceManager.getSubResourceProvider());
    }

    private String buildAsyncHiZSnapshotCopyRenderSettingJson(KeyId sourceDepthTextureId) {
        return """
                {
                  "executionDomain": "COMPUTE",
                  "renderState": {
                    "shader_template": {
                      "identifier": "%s"
                    }
                  },
                  "resourceBinding": {
                    "image": {
                      "output_texture_0": {
                        "resource": "%s",
                        "view": "STORAGE_IMAGE",
                        "access": "WRITE"
                      }
                    },
                    "texture": {
                      "sketch_SamplerTexture": {
                        "resource": "%s",
                        "view": "SAMPLED_TEXTURE",
                        "access": "READ"
                      }
                    }
                  },
                  "shouldSwitchRenderState": true
                }
                """.formatted(HIZ_SNAPSHOT_COPY_SHADER_ID, ASYNC_HIZ_SOURCE_SNAPSHOT, sourceDepthTextureId);
    }

    private String buildAsyncHiZLevelRenderSettingJson(int level) {
        String atlasBinding = level > 0
                ? """
                    ,
                          "hiz_source_atlas": {
                            "resource": "%s",
                            "view": "SAMPLED_TEXTURE",
                            "access": "READ"
                          }
                  """.formatted(ASYNC_HIZ_ASYNC_TEXTURE)
                : "";
        String aliasPolicy = level > 0
                ? """
                  ,
                  "aliasPolicy": "HIZ_ATLAS_READ_WRITE"
                  """
                : "";
        return """
                {
                  "executionDomain": "COMPUTE",
                  "renderState": {
                    "shader_template": {
                      "identifier": "%s"
                    }
                  },
                  "resourceBinding": {
                    "image": {
                      "output_texture_0": {
                        "resource": "%s",
                        "view": "STORAGE_IMAGE",
                        "access": "WRITE"
                      }
                    },
                    "texture": {
                      "hiz_source_snapshot": {
                        "resource": "%s",
                        "view": "SAMPLED_TEXTURE",
                        "access": "READ"
                      }%s
                    }
                  },
                  "shouldSwitchRenderState": true%s
                }
                """.formatted(
                asyncHiZLevelShaderId(level),
                ASYNC_HIZ_ASYNC_TEXTURE,
                ASYNC_HIZ_SOURCE_SNAPSHOT,
                atlasBinding,
                aliasPolicy);
    }

    private void installBackendSpecificResources() {
        if (backendKind == BackendKind.DESKTOP_GL) {
            installDesktopGlMainTarget();
        }

        if (backendKind == BackendKind.VULKAN && GraphicsDriver.runtime() instanceof VulkanBackendRuntime runtime) {
            installVulkanMainTarget(runtime);
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
        installAsyncHiZResources();
    }

    private void installVulkanMainTarget(VulkanBackendRuntime runtime) {
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
        replaceBuiltInResource(
                resourceManager,
                ResourceTypes.TEXTURE,
                MAIN_TARGET_COLOR,
                runtime::currentPresentedColorAttachment);
        replaceBuiltInResource(
                resourceManager,
                ResourceTypes.TEXTURE,
                MAIN_TARGET_DEPTH,
                runtime::currentPresentedDepthAttachment);

        var installer = GraphicsDriver.runtime().resourceInstaller();
        installLogicalMainRenderTarget(resourceManager, installer, MAIN_TARGET);
        installLogicalMainRenderTarget(resourceManager, installer, ExecutionKey.DEFAULT_RENDER_TARGET);
    }

    private void installLogicalMainRenderTarget(
            GraphicsResourceManager resourceManager,
            rogo.sketch.core.backend.BackendResourceInstaller installer,
            KeyId renderTargetId) {
        if (renderTargetId == null) {
            return;
        }
        replaceDirectResource(
                resourceManager,
                ResourceTypes.RENDER_TARGET,
                renderTargetId,
                installer.createRenderTarget(
                        renderTargetId,
                        new ResolvedRenderTargetSpec(
                                renderTargetId,
                                RenderTargetResolutionMode.SCREEN_SIZE,
                                1,
                                1,
                                1.0f,
                                1.0f,
                                List.of(MAIN_TARGET_COLOR),
                                MAIN_TARGET_DEPTH,
                                null)));
    }

    private void installAsyncHiZResources() {
        asyncHiZState.awaitPending();
        asyncOffscreenState.awaitPending();
        disposeAsyncHiZSourceNativeResources();

        int framebufferWidth = Math.max(1, context.windowWidth());
        int framebufferHeight = Math.max(1, context.windowHeight());
        int hizDispatchWidth = computeFirstLevelWidth(framebufferWidth);
        int hizDispatchHeight = computeFirstLevelHeight(framebufferHeight);
        int hizPackedHeight = computePackedHiZHeight(framebufferWidth, framebufferHeight);

        var installer = GraphicsDriver.runtime().resourceInstaller();
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();

        if (backendKind == BackendKind.VULKAN && resourceManager.hasResource(ResourceTypes.TEXTURE, ASYNC_HIZ_SOURCE_DEPTH)) {
            resourceManager.removeResource(ResourceTypes.TEXTURE, ASYNC_HIZ_SOURCE_DEPTH);
        }
        replaceDirectResource(
                resourceManager,
                ResourceTypes.TEXTURE,
                ASYNC_HIZ_SOURCE_SNAPSHOT,
                installer.createTexture(
                        ASYNC_HIZ_SOURCE_SNAPSHOT,
                        new ResolvedImageResource(
                                ASYNC_HIZ_SOURCE_SNAPSHOT,
                                framebufferWidth,
                                framebufferHeight,
                                1,
                                ImageFormat.R32_FLOAT,
                                EnumSet.of(ImageUsage.STORAGE, ImageUsage.SAMPLED),
                                SamplerFilter.NEAREST,
                                SamplerFilter.NEAREST,
                                null,
                                SamplerWrap.CLAMP_TO_EDGE,
                                SamplerWrap.CLAMP_TO_EDGE,
                                null),
                        null,
                        null));
        replaceDirectResource(
                resourceManager,
                ResourceTypes.TEXTURE,
                ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT,
                installer.createTexture(
                        ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT,
                        new ResolvedImageResource(
                                ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT,
                                framebufferWidth,
                                framebufferHeight,
                                1,
                                ImageFormat.R32_FLOAT,
                                EnumSet.of(ImageUsage.SAMPLED, ImageUsage.TRANSFER_DST, ImageUsage.TRANSFER_SRC),
                                SamplerFilter.NEAREST,
                                SamplerFilter.NEAREST,
                                null,
                                SamplerWrap.CLAMP_TO_EDGE,
                                SamplerWrap.CLAMP_TO_EDGE,
                                null),
                        null,
                        null));
        VulkanBackendRuntime runtime = vulkanRuntime();
        if (runtime != null) {
            asyncHiZSourceSnapshotNative = runtime.createStorageTextureResource(framebufferWidth, framebufferHeight);
            runtime.registerTextureResource(ASYNC_HIZ_SOURCE_SNAPSHOT, asyncHiZSourceSnapshotNative);
            asyncHiZSourceSnapshotInspectNative = runtime.createSampledFloatTextureResource(framebufferWidth, framebufferHeight);
            runtime.registerTextureResource(ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT, asyncHiZSourceSnapshotInspectNative);
            asyncHiZState.recreate(installer, runtime, hizDispatchWidth, hizDispatchHeight, hizDispatchWidth, hizPackedHeight);
            resourceManager.registerBuiltIn(
                    "sketch-vk-test@scene",
                    ResourceScope.EPHEMERAL_TEST,
                    ResourceTypes.TEXTURE,
                    ASYNC_HIZ_SOURCE_DEPTH,
                    runtime::currentPresentedDepthAttachment);
            refreshVulkanAsyncHiZTextureOverrides(runtime);
        } else {
            asyncHiZState.recreate(installer, hizDispatchWidth, hizDispatchHeight, hizDispatchWidth, hizPackedHeight);
        }
        resourceManager.registerBuiltIn(
                "sketch-vk-test@scene",
                ResourceScope.EPHEMERAL_TEST,
                ResourceTypes.TEXTURE,
                ASYNC_HIZ_TEXTURE,
                asyncHiZState::readTexture);
        resourceManager.registerBuiltIn(
                "sketch-vk-test@scene",
                ResourceScope.EPHEMERAL_TEST,
                ResourceTypes.TEXTURE,
                ASYNC_HIZ_ASYNC_TEXTURE,
                asyncHiZState::writeTexture);
        asyncOffscreenState.recreate(installer, framebufferWidth, framebufferHeight);
        asyncOffscreenDirty = true;
        resourceManager.registerBuiltIn(
                "sketch-vk-test@scene",
                ResourceScope.EPHEMERAL_TEST,
                ResourceTypes.RENDER_TARGET,
                ASYNC_OFFSCREEN_TARGET,
                asyncOffscreenState::writeTarget);
        resourceManager.registerBuiltIn(
                "sketch-vk-test@scene",
                ResourceScope.EPHEMERAL_TEST,
                ResourceTypes.TEXTURE,
                ASYNC_OFFSCREEN_PRESENT_TEXTURE,
                asyncOffscreenState::readTexture);
        if (runtime != null) {
            refreshVulkanAsyncOffscreenTextureOverrides(runtime);
        }
    }

    private <T extends ResourceObject> void replaceDirectResource(
            GraphicsResourceManager resourceManager,
            KeyId resourceType,
            KeyId resourceId,
            T resource) {
        if (resourceManager.hasResource(resourceType, resourceId)) {
            resourceManager.removeResource(resourceType, resourceId);
        }
        resourceManager.registerDirect(
                "sketch-vk-test@scene",
                ResourceScope.EPHEMERAL_TEST,
                resourceType,
                resourceId,
                resource);
    }

    private void replaceBuiltInResource(
            GraphicsResourceManager resourceManager,
            KeyId resourceType,
            KeyId resourceId,
            java.util.function.Supplier<? extends ResourceObject> supplier) {
        if (resourceManager.hasResource(resourceType, resourceId)) {
            resourceManager.removeResource(resourceType, resourceId);
        }
        resourceManager.registerBuiltIn(
                "sketch-vk-test@scene",
                ResourceScope.EPHEMERAL_TEST,
                resourceType,
                resourceId,
                supplier::get);
    }

    private Texture createAsyncHiZTexture(
            rogo.sketch.core.backend.BackendResourceInstaller installer,
            KeyId resourceId,
            int width,
            int height) {
        return installer.createTexture(
                resourceId,
                new ResolvedImageResource(
                    resourceId,
                    Math.max(1, width),
                    Math.max(1, height),
                    1,
                    ImageFormat.R32_FLOAT,
                    EnumSet.of(ImageUsage.STORAGE, ImageUsage.SAMPLED),
                    SamplerFilter.LINEAR,
                    SamplerFilter.LINEAR,
                        null,
                        SamplerWrap.CLAMP_TO_EDGE,
                        SamplerWrap.CLAMP_TO_EDGE,
                        null),
                null,
                null);
    }

    private static int computeFirstLevelWidth(int width) {
        int value = Math.max(1, width >> 1);
        return (value & 1) == 1 ? value + 1 : value;
    }

    private static int computeFirstLevelHeight(int height) {
        int value = Math.max(1, height >> 1);
        return (value & 1) == 1 ? value + 1 : value;
    }

    private static int computePackedHiZHeight(int width, int height) {
        int totalHeight = 0;
        for (int index = 0; index < HIZ_DEPTH_LEVELS; ++index) {
            int scaleWidth = Math.max(1, width >> (index + 1));
            int scaleHeight = Math.max(1, height >> (index + 1));
            if ((scaleWidth & 1) == 1) {
                scaleWidth += 1;
            }
            if ((scaleHeight & 1) == 1) {
                scaleHeight += 1;
            }
            totalHeight += scaleHeight;
        }
        return Math.max(1, totalHeight);
    }

    private static String buildAsyncHiZLevelShaderSource(int level) {
        boolean firstLevel = level == 0;
        String extraSampler = firstLevel ? "" : "uniform sampler2D hiz_source_atlas;\n";
        String sourceSizeExpr = firstLevel
                ? "originalScreen"
                : "ivec2(levelExtent(originalScreen.x, %d), levelExtent(originalScreen.y, %d))".formatted(level - 1, level - 1);
        String sourceYOffsetExpr = firstLevel ? "0" : buildAsyncHiZYOffsetExpr(level - 1);
        String destSizeExpr = "ivec2(levelExtent(originalScreen.x, %d), levelExtent(originalScreen.y, %d))".formatted(level, level);
        String destYOffsetExpr = buildAsyncHiZYOffsetExpr(level);
        String readSource = firstLevel
                ? "return linearizeDepth(texelFetch(hiz_source_snapshot, pos, 0).r);"
                : "return texelFetch(hiz_source_atlas, pos, 0).r;";
        return """
                #version 430 core
                layout(local_size_x = 16, local_size_y = 16) in;

                layout(r32f) uniform writeonly image2D output_texture_0;
                uniform sampler2D hiz_source_snapshot;
                %s
                const float HIZ_NEAR = 0.05;
                const float HIZ_FAR = 1024.0;
                const int DEST_LEVEL = %d;

                int levelExtent(int value, int packedLevel) {
                    int extent = max(1, value >> (packedLevel + 1));
                    return (extent & 1) == 1 ? extent + 1 : extent;
                }

                float linearizeDepth(float depth) {
                    float z = depth * 2.0 - 1.0;
                    return (HIZ_NEAR * HIZ_FAR) / (HIZ_FAR + HIZ_NEAR - z * (HIZ_FAR - HIZ_NEAR));
                }

                float readSourceDepth(ivec2 pos) {
                    %s
                }

                void main() {
                    ivec2 originalScreen = textureSize(hiz_source_snapshot, 0);
                    ivec2 sourceSize = %s;
                    int sourceYOffset = %s;
                    ivec2 destSize = %s;
                    int destYOffset = %s;
                    ivec2 outPos = ivec2(gl_GlobalInvocationID.xy);
                    if (outPos.x >= destSize.x || outPos.y >= destSize.y) {
                        return;
                    }

                    ivec2 sourceMax = sourceSize - ivec2(1);
                    ivec2 base = outPos * 2;
                    ivec2 p0 = min(base + ivec2(0, 0), sourceMax);
                    ivec2 p1 = min(base + ivec2(1, 0), sourceMax);
                    ivec2 p2 = min(base + ivec2(0, 1), sourceMax);
                    ivec2 p3 = min(base + ivec2(1, 1), sourceMax);
                    p0.y += sourceYOffset;
                    p1.y += sourceYOffset;
                    p2.y += sourceYOffset;
                    p3.y += sourceYOffset;

                    float d0 = readSourceDepth(p0);
                    float d1 = readSourceDepth(p1);
                    float d2 = readSourceDepth(p2);
                    float d3 = readSourceDepth(p3);
                    float hizDepth = max(max(d0, d1), max(d2, d3));
                    imageStore(output_texture_0, ivec2(outPos.x, outPos.y + destYOffset), vec4(hizDepth, 0.0, 0.0, 1.0));
                }
                """.formatted(extraSampler, level, readSource, sourceSizeExpr, sourceYOffsetExpr, destSizeExpr, destYOffsetExpr);
    }

    private static String buildAsyncHiZYOffsetExpr(int packedLevel) {
        if (packedLevel <= 0) {
            return "0";
        }
        StringBuilder builder = new StringBuilder("0");
        for (int index = 0; index < packedLevel; index++) {
            builder.append(" + levelExtent(originalScreen.y, ").append(index).append(')');
        }
        return builder.toString();
    }

    private Vector3i[] asyncHiZDepthInfo() {
        int width = Math.max(1, context.windowWidth());
        int height = Math.max(1, context.windowHeight());
        Vector3i[] depthInfo = new Vector3i[HIZ_DEPTH_LEVELS];
        int totalHeight = 0;
        for (int index = 0; index < HIZ_DEPTH_LEVELS; ++index) {
            int scaleWidth = Math.max(1, width >> (index + 1));
            int scaleHeight = Math.max(1, height >> (index + 1));
            if ((scaleWidth & 1) == 1) {
                scaleWidth += 1;
            }
            if ((scaleHeight & 1) == 1) {
                scaleHeight += 1;
            }
            depthInfo[index] = new Vector3i(scaleWidth, scaleHeight, totalHeight);
            totalHeight += scaleHeight;
        }
        return depthInfo;
    }

    private int[] asyncHiZDispatchGroups(boolean firstPass) {
        Vector3i[] info = asyncHiZDepthInfo();
        if (info.length == 0) {
            return new int[]{0, 0};
        }
        Vector3i screenSize = firstPass ? info[0] : info[Math.min(3, info.length - 1)];
        return new int[]{
                Math.max(1, (screenSize.x + 15) / 16),
                Math.max(1, (screenSize.y + 15) / 16)
        };
    }

    private int[] asyncHiZDispatchGroups(int level) {
        Vector3i[] info = asyncHiZDepthInfo();
        if (level < 0 || level >= info.length) {
            return new int[]{1, 1};
        }
        Vector3i levelSize = info[level];
        return new int[]{
                Math.max(1, (levelSize.x + 15) / 16),
                Math.max(1, (levelSize.y + 15) / 16)
        };
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
        asyncHiZSourceInspectSetting = compileInspectionSetting(ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT);
        asyncHiZSnapshotInspectSetting = compileInspectionSetting(ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT);
        asyncHiZLinearizedInspectSetting = compileLinearizedInspectionSetting(ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT);
        asyncHiZPublishedInspectSetting = compileInspectionSetting(ASYNC_HIZ_TEXTURE);
        asyncOffscreenInspectSetting = compileInspectionSetting(ASYNC_OFFSCREEN_PRESENT_TEXTURE);
        asyncHiZSnapshotFreezeSetting = compileComputeSetting(ASYNC_HIZ_SNAPSHOT_COPY_SCENE_SETTING_ID);
        asyncHiZLevelSettings.clear();
        asyncHiZLevelBlueprints.clear();
        for (int level = 0; level < HIZ_DEPTH_LEVELS; level++) {
            CompiledRenderSetting levelSetting = compileComputeSetting(
                    asyncHiZLevelSceneSettingId(level));
            asyncHiZLevelSettings.add(levelSetting);
            asyncHiZLevelBlueprints.add(createAsyncHiZLevelComputeBlueprint(level, levelSetting));
        }
        asyncOffscreenGraphicsSetting = compileTeapotOffscreenRenderSetting();
        asyncHiZSnapshotFreezeBlueprint = createAsyncHiZSnapshotFreezeBlueprint();
        asyncOffscreenGraphicsBlueprints = createAsyncOffscreenGraphicsBlueprints();
    }

    private void registerBasicStateResourceSplitScenario() {
        pipeline.spawnGraphicsEntity(createTriangleBlueprint(
                KeyId.of("sketch_vktest:triangle_a"),
                MAIN_STAGE_ID,
                resourceSetA,
                new float[]{
                        -0.90f, -0.70f, 1.0f, 0.1f, 0.1f,
                        -0.45f, -0.70f, 0.1f, 1.0f, 0.1f,
                        -0.68f, -0.20f, 0.1f, 0.1f, 1.0f
                },
                SubmissionCapability.DIRECT_BATCHABLE));
        pipeline.spawnGraphicsEntity(createTriangleBlueprint(
                KeyId.of("sketch_vktest:triangle_b"),
                MAIN_STAGE_ID,
                resourceSetB,
                new float[]{
                        0.35f, -0.70f, 1.0f, 0.8f, 0.2f,
                        0.85f, -0.70f, 0.2f, 0.8f, 1.0f,
                        0.60f, -0.20f, 0.9f, 0.9f, 0.2f
                },
                SubmissionCapability.DIRECT_BATCHABLE));
        pipeline.spawnGraphicsEntity(createTriangleBlueprint(
                KeyId.of("sketch_vktest:triangle_overlay"),
                OVERLAY_STAGE_ID,
                resourceSetA,
                new float[]{
                        -0.20f, 0.15f, 0.9f, 0.3f, 0.2f,
                        0.20f, 0.15f, 0.2f, 0.9f, 0.3f,
                        0.00f, 0.70f, 0.2f, 0.3f, 0.9f
                },
                SubmissionCapability.DIRECT_BATCHABLE));
    }

    private void registerSharedSourceMultiSubmeshScenario() {
        SharedSourceMeshes sharedSourceMeshes = createSharedSourceMeshes();
        pipeline.spawnGraphicsEntity(createInstancedBakedBlueprint(
                KeyId.of("sketch_vktest:shared_left_a"),
                MAIN_STAGE_ID,
                instancedResourceSetA,
                sharedSourceMeshes.leftMesh(),
                -0.62f,
                0.28f));
        pipeline.spawnGraphicsEntity(createInstancedBakedBlueprint(
                KeyId.of("sketch_vktest:shared_left_b"),
                MAIN_STAGE_ID,
                instancedResourceSetA,
                sharedSourceMeshes.leftMesh(),
                -0.18f,
                0.28f));
        pipeline.spawnGraphicsEntity(createInstancedBakedBlueprint(
                KeyId.of("sketch_vktest:shared_right"),
                MAIN_STAGE_ID,
                instancedResourceSetA,
                sharedSourceMeshes.rightMesh(),
                0.28f,
                0.28f));
    }

    private void registerShaderLayoutScenario() {
        pipeline.spawnGraphicsEntity(createTriangleBlueprint(
                KeyId.of("sketch_vktest:triangle_subset"),
                OVERLAY_STAGE_ID,
                subsetResourceSet,
                new float[]{
                        -0.85f, 0.20f, 1.0f, 0.0f, 0.0f,
                        -0.55f, 0.20f, 0.0f, 1.0f, 0.0f,
                        -0.70f, 0.55f, 0.0f, 0.0f, 1.0f
                },
                SubmissionCapability.DIRECT_BATCHABLE));
    }

    private void registerComputeScenario() {
        pipeline.spawnGraphicsEntity(createComputeBlueprint(
                KeyId.of("sketch_vktest:compute_dispatch"),
                COMPUTE_STAGE_ID,
                computeSetting));
    }

    private void registerCrossBackendValidationScenario() {
        registerMainTargetDepthValidationScenario();
        registerAsyncHiZScenario();
    }

    private void registerMainTargetDepthValidationScenario() {
        pipeline.spawnGraphicsEntity(createDepthTriangleBlueprint(
                KeyId.of("sketch_vktest:main_depth_base"),
                MAIN_STAGE_ID,
                depthValidationResourceSet,
                new float[]{
                        -0.20f, -0.20f, 0.20f, 1.0f, 0.05f, 0.05f,
                        0.20f, -0.20f, 0.20f, 1.0f, 0.05f, 0.05f,
                        0.00f, 0.20f, 0.20f, 1.0f, 0.05f, 0.05f
                }));
        pipeline.spawnGraphicsEntity(createDepthTriangleBlueprint(
                KeyId.of("sketch_vktest:main_depth_overlay"),
                OVERLAY_STAGE_ID,
                depthValidationResourceSet,
                new float[]{
                        -0.20f, -0.20f, 0.60f, 0.05f, 0.95f, 0.10f,
                        0.20f, -0.20f, 0.60f, 0.05f, 0.95f, 0.10f,
                        0.00f, 0.20f, 0.60f, 0.05f, 0.95f, 0.10f
                }));
    }

    private void registerAsyncHiZScenario() {
        pipeline.spawnGraphicsEntity(createInspectionQuadBlueprint(
                KeyId.of("sketch_vktest:inspect_source_depth"),
                asyncHiZSourceInspectSetting,
                -0.95f,
                -0.61f,
                -0.95f,
                -0.53f));
        pipeline.spawnGraphicsEntity(createInspectionQuadBlueprint(
                KeyId.of("sketch_vktest:inspect_source_snapshot"),
                asyncHiZSnapshotInspectSetting,
                -0.57f,
                -0.23f,
                -0.95f,
                -0.53f));
        pipeline.spawnGraphicsEntity(createInspectionQuadBlueprint(
                KeyId.of("sketch_vktest:inspect_source_snapshot_linearized"),
                asyncHiZLinearizedInspectSetting,
                -0.19f,
                0.15f,
                -0.95f,
                -0.53f));
        pipeline.spawnGraphicsEntity(createInspectionQuadBlueprint(
                KeyId.of("sketch_vktest:inspect_published_hiz"),
                asyncHiZPublishedInspectSetting,
                0.19f,
                0.53f,
                -0.95f,
                -0.53f));
        pipeline.spawnGraphicsEntity(createInspectionQuadBlueprint(
                KeyId.of("sketch_vktest:inspect_async_offscreen"),
                asyncOffscreenInspectSetting,
                0.57f,
                0.91f,
                -0.95f,
                -0.53f));
    }

    private CompiledRenderSetting compileRenderSetting(
            KeyId partialSettingId,
            RasterizationParameter renderParameter,
            ResourceBinding binding) {
        return compileRenderSetting(partialSettingId, renderParameter, binding, null);
    }

    private CompiledRenderSetting compileRenderSetting(
            KeyId partialSettingId,
            RasterizationParameter renderParameter,
            ResourceBinding binding,
            TargetBinding targetOverride) {
        PartialRenderSetting partialRenderSetting = requireResource(ResourceTypes.PARTIAL_RENDER_SETTING, partialSettingId, PartialRenderSetting.class);
        return RenderSettingCompiler.compile(RenderSetting.fromPartial(
                renderParameter,
                mergePartialSetting(partialRenderSetting, binding, targetOverride)));
    }

    private CompiledRenderSetting compileDerivedRenderSetting(
            KeyId partialSettingId,
            ExecutionDomain executionDomain,
            RasterizationParameter renderParameter,
            ResourceBinding binding,
            TargetBinding targetOverride,
            KeyId shaderOverride) {
        PartialRenderSetting partialRenderSetting = requireResource(ResourceTypes.PARTIAL_RENDER_SETTING, partialSettingId, PartialRenderSetting.class);
        RenderStatePatch renderState = partialRenderSetting.renderState();
        if (shaderOverride != null) {
            renderState = (renderState != null ? renderState : RenderStatePatch.empty())
                    .with(new ShaderState(shaderOverride));
        }
        PartialRenderSetting derived = PartialRenderSetting.create(
                executionDomain,
                renderState,
                targetOverride != null ? targetOverride : partialRenderSetting.targetBinding(),
                mergeResourceBinding(partialRenderSetting.resourceBinding(), binding),
                partialRenderSetting.shouldSwitchRenderState(),
                partialRenderSetting.aliasPolicy());
        return RenderSettingCompiler.compile(RenderSetting.fromPartial(renderParameter, derived));
    }

    private CompiledRenderSetting compileComputeSetting(KeyId partialSettingId) {
        return compileComputeSetting(partialSettingId, null);
    }

    private CompiledRenderSetting compileComputeSetting(KeyId partialSettingId, ResourceBinding binding) {
        PartialRenderSetting partialRenderSetting = requireResource(ResourceTypes.PARTIAL_RENDER_SETTING, partialSettingId, PartialRenderSetting.class);
        return RenderSettingCompiler.compile(RenderSetting.fromPartial(
                ComputeParameter.COMPUTE_PARAMETER,
                mergePartialSetting(partialRenderSetting, binding, null)));
    }

    private CompiledRenderSetting compileInspectionSetting(KeyId textureId) {
        return compileDerivedRenderSetting(
                SAMPLED_TEXTURE_SCENE_SETTING_ID,
                ExecutionDomain.RASTER,
                SCENE_PARAMETER,
                buildTextureBinding(textureId),
                null,
                INSPECTION_TEXTURE_SHADER_ID);
    }

    private CompiledRenderSetting compileLinearizedInspectionSetting(KeyId textureId) {
        return compileDerivedRenderSetting(
                SAMPLED_TEXTURE_SCENE_SETTING_ID,
                ExecutionDomain.RASTER,
                SCENE_PARAMETER,
                buildTextureBinding(textureId),
                null,
                INSPECTION_LINEARIZED_TEXTURE_SHADER_ID);
    }

    private CompiledRenderSetting compileOffscreenRenderSetting(
            KeyId partialSettingId,
            RasterizationParameter renderParameter,
            ResourceBinding binding,
            KeyId renderTargetId) {
        return compileDerivedRenderSetting(
                partialSettingId,
                ExecutionDomain.OFFSCREEN_GRAPHICS,
                renderParameter,
                binding,
                new TargetBinding(renderTargetId, List.of(), Boolean.TRUE, Boolean.TRUE),
                null);
    }

    private CompiledRenderSetting compileTeapotOffscreenRenderSetting() {
        return compileDerivedRenderSetting(
                COLOR_SCENE_SETTING_ID,
                ExecutionDomain.OFFSCREEN_GRAPHICS,
                OBJ_SCENE_PARAMETER,
                null,
                new TargetBinding(ASYNC_OFFSCREEN_TARGET, List.of(), Boolean.TRUE, Boolean.TRUE),
                TEAPOT_OFFSCREEN_SHADER_ID);
    }

    private ResourceBinding buildResourceBinding(KeyId textureId, KeyId uniformBufferId) {
        ResourceBinding binding = new ResourceBinding();
        binding.addBinding(ResourceTypes.TEXTURE, TEXTURE_BINDING, textureId);
        binding.addBinding(ResourceTypes.UNIFORM_BUFFER, UNIFORM_BINDING, uniformBufferId);
        return binding;
    }

    private ResourceBinding buildTextureBinding(KeyId textureId) {
        ResourceBinding binding = new ResourceBinding();
        binding.addBinding(ResourceTypes.TEXTURE, TEXTURE_BINDING, textureId);
        return binding;
    }

    private ResourceBinding buildAsyncHiZFirstBinding() {
        ResourceBinding binding = new ResourceBinding();
        binding.addBinding(ResourceTypes.IMAGE, KeyId.of("output_texture_0"), ASYNC_HIZ_ASYNC_TEXTURE);
        binding.addBinding(ResourceTypes.TEXTURE, KeyId.of("hiz_source_snapshot"), ASYNC_HIZ_SOURCE_SNAPSHOT);
        return binding;
    }

    private ResourceBinding buildAsyncHiZSecondBinding() {
        ResourceBinding binding = new ResourceBinding();
        binding.addBinding(ResourceTypes.IMAGE, KeyId.of("output_texture_0"), ASYNC_HIZ_ASYNC_TEXTURE);
        binding.addBinding(ResourceTypes.TEXTURE, KeyId.of("hiz_source_atlas"), ASYNC_HIZ_ASYNC_TEXTURE);
        binding.addBinding(ResourceTypes.TEXTURE, KeyId.of("hiz_source_snapshot"), ASYNC_HIZ_SOURCE_SNAPSHOT);
        return binding;
    }

    private ResourceBinding buildAsyncHiZLevelBinding(int level) {
        ResourceBinding binding = new ResourceBinding();
        binding.addBinding(ResourceTypes.IMAGE, KeyId.of("output_texture_0"), ASYNC_HIZ_ASYNC_TEXTURE);
        binding.addBinding(ResourceTypes.TEXTURE, KeyId.of("hiz_source_snapshot"), ASYNC_HIZ_SOURCE_SNAPSHOT);
        if (level > 0) {
            binding.addBinding(ResourceTypes.TEXTURE, KeyId.of("hiz_source_atlas"), ASYNC_HIZ_ASYNC_TEXTURE);
        }
        return binding;
    }

    private ResourceBinding buildAsyncHiZSnapshotBinding(KeyId sourceDepthTextureId) {
        ResourceBinding binding = new ResourceBinding();
        binding.addBinding(ResourceTypes.IMAGE, KeyId.of("output_texture_0"), ASYNC_HIZ_SOURCE_SNAPSHOT);
        binding.addBinding(ResourceTypes.TEXTURE, KeyId.of("sketch_SamplerTexture"), sourceDepthTextureId);
        return binding;
    }

    private KeyId resolveHiZSourceDepthTextureId() {
        return backendKind == BackendKind.DESKTOP_GL ? MAIN_TARGET_DEPTH : ASYNC_HIZ_SOURCE_DEPTH;
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

    private List<BakedMesh> loadBundledBakedMeshes(KeyId resourceId, String classpathPath) {
        MeshLoader meshLoader = new MeshLoader();
        try (InputStream stream = PipelineTestScene.class.getClassLoader().getResourceAsStream(classpathPath)) {
            if (stream == null) {
                throw new IllegalStateException("Bundled OBJ model resource is missing from classpath: " + classpathPath);
            }
            ResourceLoadContext context = new ResourceLoadContext(resourceId, stream, TEST_GSON, id -> Optional.empty());
            MeshGroup meshGroup = meshLoader.load(context);
            if (meshGroup == null) {
                throw new IllegalStateException("Bundled OBJ model failed to load: " + classpathPath);
            }
            List<BakedMesh> meshes = new ArrayList<>();
            for (PreparedMesh preparedMesh : meshGroup.getSubMeshes()) {
                if (preparedMesh instanceof BakedMesh bakedMesh) {
                    meshes.add(bakedMesh);
                }
            }
            if (meshes.isEmpty()) {
                throw new IllegalStateException("Bundled OBJ model produced no baked submeshes: " + classpathPath);
            }
            return List.copyOf(meshes);
        } catch (Exception e) {
            throw new IllegalStateException("OBJ model load failed for " + classpathPath, e);
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

    private PartialRenderSetting mergePartialSetting(
            PartialRenderSetting base,
            ResourceBinding overrideBinding,
            TargetBinding targetOverride) {
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
                base.executionDomain(),
                base.renderState(),
                targetOverride != null ? targetOverride : base.targetBinding(),
                mergedBinding,
                base.shouldSwitchRenderState(),
                base.aliasPolicy());
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
        deferAsyncSubmissions(3);
        String threadName = Thread.currentThread().getName();
        SimpleProfiler.get().begin("ResizeAwaitAsyncHiZ", threadName);
        try {
            asyncHiZState.awaitPending();
        } finally {
            SimpleProfiler.get().end("ResizeAwaitAsyncHiZ", threadName);
        }
        SimpleProfiler.get().begin("ResizeAwaitAsyncGraphics", threadName);
        try {
            asyncOffscreenState.awaitPending();
        } finally {
            SimpleProfiler.get().end("ResizeAwaitAsyncGraphics", threadName);
        }
        if (pipeline != null && pipeline.kernel() != null) {
            SimpleProfiler.get().begin("ResizeDrainAsyncGpu", threadName);
            try {
                pipeline.kernel().asyncGpuScheduler().drain();
            } finally {
                SimpleProfiler.get().end("ResizeDrainAsyncGpu", threadName);
            }
        }
        VulkanBackendRuntime runtime = vulkanRuntime();
        if (runtime != null) {
            SimpleProfiler.get().begin("ResizeWaitDeviceIdle", threadName);
            try {
                runtime.waitForDeviceIdle();
            } finally {
                SimpleProfiler.get().end("ResizeWaitDeviceIdle", threadName);
            }
        }
        context.setWindowSize(framebufferWidth, framebufferHeight);
        context.projectionMatrix().identity();
        context.viewMatrix().identity();
        context.modelMatrix().identity();
        if (backendKind == BackendKind.DESKTOP_GL) {
            RenderTargetUtil.resizeRT(context.windowWidth(), context.windowHeight());
        }
        installAsyncHiZResources();
        pipeline.resetRenderContext(context);
    }

    void prepareForWindowMutation() {
        deferAsyncSubmissions(3);
        String threadName = Thread.currentThread().getName();
        SimpleProfiler.get().begin("WindowMutationAwaitAsyncHiZ", threadName);
        try {
            asyncHiZState.awaitPending();
        } finally {
            SimpleProfiler.get().end("WindowMutationAwaitAsyncHiZ", threadName);
        }
        SimpleProfiler.get().begin("WindowMutationAwaitAsyncGraphics", threadName);
        try {
            asyncOffscreenState.awaitPending();
        } finally {
            SimpleProfiler.get().end("WindowMutationAwaitAsyncGraphics", threadName);
        }
        if (pipeline != null && pipeline.kernel() != null) {
            SimpleProfiler.get().begin("WindowMutationDrainAsyncGpu", threadName);
            try {
                pipeline.kernel().asyncGpuScheduler().drain();
            } finally {
                SimpleProfiler.get().end("WindowMutationDrainAsyncGpu", threadName);
            }
        }
        VulkanBackendRuntime runtime = vulkanRuntime();
        if (runtime != null) {
            SimpleProfiler.get().begin("WindowMutationWaitDeviceIdle", threadName);
            try {
                runtime.waitForDeviceIdle();
            } finally {
                SimpleProfiler.get().end("WindowMutationWaitDeviceIdle", threadName);
            }
        }
    }

    void deferAsyncSubmissions(int frames) {
        int clampedFrames = Math.max(asyncSubmissionCooldownFrames, Math.max(0, frames));
        asyncSubmissionCooldownFrames = clampedFrames;
        VulkanBackendRuntime runtime = vulkanRuntime();
        if (runtime != null) {
            vulkanOffscreenForceSync = true;
            asyncSubmissionResumeSwapchainGeneration = Math.max(
                    asyncSubmissionResumeSwapchainGeneration,
                    runtime.swapchainGeneration() + 1L);
            asyncSubmissionResumeRenderedFrameCount = Math.max(
                    asyncSubmissionResumeRenderedFrameCount,
                    runtime.renderedFrameCount() + Math.max(2L, clampedFrames));
            asyncOffscreenResumeSwapchainGeneration = Math.max(
                    asyncOffscreenResumeSwapchainGeneration,
                    runtime.swapchainGeneration() + 1L);
            asyncOffscreenResumeRenderedFrameCount = Math.max(
                    asyncOffscreenResumeRenderedFrameCount,
                    runtime.renderedFrameCount() + Math.max(12L, clampedFrames + 12L));
        }
    }

    void renderFrame() {
        promoteCompletedAsyncHiZ();
        promoteCompletedAsyncOffscreen();
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

    void submitAsyncHiZAfterFrame() {
        promoteCompletedAsyncHiZ();
        promoteCompletedAsyncOffscreen();
        if (shouldDeferAsyncSubmission()) {
            if (asyncSubmissionCooldownFrames > 0) {
                asyncSubmissionCooldownFrames--;
            }
            return;
        }
        refreshVulkanMainTargetDepthOverride();
        freezeCurrentDepthSnapshot();
        copyDepthSnapshotForInspection();
        triggerAsyncHiZCompute();
        triggerAsyncOffscreenGraphics();
    }

    private boolean shouldDeferAsyncSubmission() {
        if (asyncSubmissionCooldownFrames > 0) {
            return true;
        }
        VulkanBackendRuntime runtime = vulkanRuntime();
        if (runtime == null) {
            return false;
        }
        if (runtime.swapchainGeneration() < asyncSubmissionResumeSwapchainGeneration) {
            return true;
        }
        return runtime.renderedFrameCount() < asyncSubmissionResumeRenderedFrameCount;
    }

    GraphicsPipeline<HarnessRenderContext> pipeline() {
        return pipeline;
    }

    private void triggerAsyncHiZCompute() {
        if (pipeline.kernel() == null || asyncHiZLevelBlueprints.isEmpty()) {
            return;
        }
        if (asyncHiZState.hasPendingJob()) {
            return;
        }
        if (backendKind == BackendKind.DESKTOP_GL) {
            GL11.glFlush();
        }
        List<RenderPacket> packets = new ArrayList<>();
        for (GraphicsEntityBlueprint blueprint : asyncHiZLevelBlueprints) {
            packets.addAll(pipeline.buildImmediatePackets(blueprint, context));
        }
        if (packets.isEmpty()) {
            return;
        }
        long epoch = asyncHiZState.nextEpoch();
        AsyncGpuJobHandle handle = pipeline.kernel().asyncGpuScheduler().submitCompute(
                new AsyncComputeRequest<>(
                        pipeline,
                        context,
                        packets,
                        ASYNC_HIZ_TEXTURE,
                        epoch,
                        null));
        asyncHiZState.onSubmitted(handle);
    }

    private void freezeCurrentDepthSnapshot() {
        if (asyncHiZSnapshotFreezeBlueprint == null) {
            return;
        }
        pipeline.renderImmediate(asyncHiZSnapshotFreezeBlueprint, context);
    }

    private void copyDepthSnapshotForInspection() {
        CopyTexturePacket packet = new CopyTexturePacket(
                COMPUTE_STAGE_ID,
                PipelineType.FUNCTION,
                TransferPlanKey.forTexture(ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT),
                ResourceBindingPlan.empty(),
                UniformValueSnapshot.empty(),
                List.of(),
                ASYNC_HIZ_SOURCE_SNAPSHOT,
                ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT,
                Math.max(1, context.windowWidth()),
                Math.max(1, context.windowHeight()),
                false);
        GraphicsDriver.runtime().frameExecutor().executeImmediate(
                pipeline,
                packet,
                pipeline.renderStateManager(),
                context);
    }

    private void triggerAsyncOffscreenGraphics() {
        if (asyncOffscreenGraphicsBlueprints.isEmpty() || pipeline.kernel() == null) {
            return;
        }
        if (!asyncOffscreenDirty) {
            return;
        }
        if (shouldDeferAsyncOffscreenSubmission()) {
            return;
        }
        if (asyncOffscreenState.hasPendingJob()) {
            return;
        }
        List<RenderPacket> packets = new ArrayList<>();
        packets.add(new ClearPacket(
                OVERLAY_STAGE_ID,
                PipelineType.RASTERIZATION,
                TransferPlanKey.forRenderTarget(ASYNC_OFFSCREEN_TARGET),
                ResourceBindingPlan.empty(),
                UniformValueSnapshot.empty(),
                List.of(),
                ASYNC_OFFSCREEN_TARGET,
                List.of(0),
                true,
                true,
                new float[]{0.08f, 0.12f, 0.24f, 1.0f},
                1.0f,
                new boolean[]{true, true, true, true},
                false));
        for (GraphicsEntityBlueprint blueprint : asyncOffscreenGraphicsBlueprints) {
            packets.addAll(pipeline.buildImmediatePackets(blueprint, context));
        }
        if (packets.isEmpty()) {
            return;
        }
        long epoch = asyncOffscreenState.nextEpoch();
        if (backendKind == BackendKind.DESKTOP_GL || (backendKind == BackendKind.VULKAN && vulkanOffscreenForceSync)) {
            for (RenderPacket packet : packets) {
                GraphicsDriver.runtime().frameExecutor().executeImmediate(
                        pipeline,
                        packet,
                        pipeline.renderStateManager(),
                        context);
            }
            asyncOffscreenState.onCompletedSync(epoch);
            asyncOffscreenDirty = false;
            return;
        }
        AsyncGpuJobHandle handle = pipeline.kernel().asyncGpuScheduler().submitGraphics(
                new AsyncGraphicsRequest<>(
                        pipeline,
                        context,
                        List.copyOf(packets),
                        ASYNC_OFFSCREEN_PRESENT_TEXTURE,
                        epoch,
                        null));
        asyncOffscreenState.onSubmitted(handle);
        asyncOffscreenDirty = false;
    }

    private boolean shouldDeferAsyncOffscreenSubmission() {
        VulkanBackendRuntime runtime = vulkanRuntime();
        if (runtime == null) {
            return false;
        }
        if (runtime.swapchainGeneration() < asyncOffscreenResumeSwapchainGeneration) {
            return true;
        }
        return runtime.renderedFrameCount() < asyncOffscreenResumeRenderedFrameCount;
    }

    private void promoteCompletedAsyncHiZ() {
        asyncHiZState.promoteCompleted();
        refreshVulkanAsyncHiZTextureOverrides();
    }

    private void promoteCompletedAsyncOffscreen() {
        asyncOffscreenState.promoteCompleted();
        refreshVulkanAsyncOffscreenTextureOverrides();
    }

    private VulkanBackendRuntime vulkanRuntime() {
        if (backendKind != BackendKind.VULKAN) {
            return null;
        }
        return GraphicsDriver.runtime() instanceof VulkanBackendRuntime runtime ? runtime : null;
    }

    private void refreshVulkanAsyncHiZTextureOverrides() {
        refreshVulkanMainTargetDepthOverride();
        refreshVulkanAsyncHiZTextureOverrides(vulkanRuntime());
    }

    private void refreshVulkanMainTargetDepthOverride() {
        refreshVulkanMainTargetDepthOverride(vulkanRuntime());
    }

    private void refreshVulkanMainTargetDepthOverride(VulkanBackendRuntime runtime) {
        if (runtime == null) {
            return;
        }
        VulkanTextureResource mainDepthTexture = runtime.currentPresentedDepthAttachment();
        if (mainDepthTexture != null) {
            runtime.registerTextureResource(ASYNC_HIZ_SOURCE_DEPTH, mainDepthTexture);
        }
    }

    private void refreshVulkanAsyncHiZTextureOverrides(VulkanBackendRuntime runtime) {
        if (runtime == null) {
            return;
        }
        VulkanTextureResource readTexture = asyncHiZState.readNativeTexture();
        VulkanTextureResource writeTexture = asyncHiZState.writeNativeTexture();
        if (readTexture != null) {
            runtime.registerTextureResource(ASYNC_HIZ_TEXTURE, readTexture);
        }
        if (writeTexture != null) {
            runtime.registerTextureResource(ASYNC_HIZ_ASYNC_TEXTURE, writeTexture);
        }
    }

    private void refreshVulkanAsyncOffscreenTextureOverrides() {
        refreshVulkanAsyncOffscreenTextureOverrides(vulkanRuntime());
    }

    private void refreshVulkanAsyncOffscreenTextureOverrides(VulkanBackendRuntime runtime) {
        if (runtime == null) {
            return;
        }
        VulkanTextureResource readTexture = asyncOffscreenState.readNativeTexture();
        if (readTexture != null) {
            runtime.registerTextureResource(ASYNC_OFFSCREEN_PRESENT_TEXTURE, readTexture);
        }
    }

    private void disposeAsyncHiZSourceNativeResources() {
        disposeQuietly(asyncHiZSourceSnapshotNative);
        asyncHiZSourceSnapshotNative = null;
        disposeQuietly(asyncHiZSourceSnapshotInspectNative);
        asyncHiZSourceSnapshotInspectNative = null;
    }

    private void disposeQuietly(ResourceObject resourceObject) {
        if (resourceObject == null || resourceObject.isDisposed()) {
            return;
        }
        try {
            resourceObject.dispose();
        } catch (Exception ignored) {
        }
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
        boolean satisfied = relaxAsyncValidation ? stats.isBaseSatisfied() : stats.isSatisfied();
        if (!validated && renderedFrames >= VALIDATION_WARMUP_FRAMES && satisfied) {
            validated = true;
            printSceneStats(stats);
            return;
        }
        if (!validated
                && renderedFrames >= VALIDATION_HARD_DEADLINE_FRAMES
                && !stats.isBaseSatisfied()) {
            throw new IllegalStateException("Pipeline test scene validation failed: " + stats);
        }
        if (!validated
                && !relaxAsyncValidation
                && renderedFrames >= ASYNC_VALIDATION_HARD_DEADLINE_FRAMES
                && !stats.isSatisfied()) {
            throw new IllegalStateException("Pipeline test scene validation failed: " + stats);
        }
    }

    void setRelaxAsyncValidation(boolean relaxAsyncValidation) {
        this.relaxAsyncValidation = relaxAsyncValidation;
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
            for (Map<ExecutionKey, List<RenderPacket>> packetMap : stagePlan.packets().values()) {
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

        for (var entityId : pipeline.graphicsWorld().query(
                GraphicsQuery.builder()
                        .require(GraphicsBuiltinComponents.RESOURCE_ORIGIN)
                        .require(GraphicsBuiltinComponents.RASTER_RENDERABLE)
                        .build())) {
            GraphicsBuiltinComponents.ResourceOriginComponent resourceOrigin =
                    pipeline.graphicsWorld().component(entityId, GraphicsBuiltinComponents.RESOURCE_ORIGIN);
            if (resourceOrigin != null && ResourceTypes.DRAW_CALL.equals(resourceOrigin.resourceType())) {
                drawCallPacketCount++;
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
                drawCallPacketCount,
                asyncHiZState.submissionCount(),
                asyncHiZState.completionCount(),
                asyncHiZState.publishedEpoch(),
                asyncOffscreenState.submissionCount(),
                asyncOffscreenState.completionCount(),
                asyncOffscreenState.publishedEpoch());
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
        System.out.println("  asyncHiZSubmissionCount=" + stats.asyncHiZSubmissionCount());
        System.out.println("  asyncHiZCompletionCount=" + stats.asyncHiZCompletionCount());
        System.out.println("  publishedAsyncHiZEpoch=" + stats.publishedAsyncHiZEpoch());
        System.out.println("  asyncGraphicsSubmissionCount=" + stats.asyncGraphicsSubmissionCount());
        System.out.println("  asyncGraphicsCompletionCount=" + stats.asyncGraphicsCompletionCount());
        System.out.println("  publishedAsyncGraphicsEpoch=" + stats.publishedAsyncGraphicsEpoch());
    }

    @Override
    public void close() {
        asyncHiZState.close();
        asyncOffscreenState.close();
        disposeAsyncHiZSourceNativeResources();
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
        resourceManager.removeResource(ResourceTypes.RENDER_TARGET, ASYNC_HIZ_SOURCE_TARGET);
        resourceManager.removeResource(ResourceTypes.TEXTURE, ASYNC_HIZ_SOURCE_COLOR);
        resourceManager.removeResource(ResourceTypes.TEXTURE, ASYNC_HIZ_SOURCE_DEPTH);
        resourceManager.removeResource(ResourceTypes.TEXTURE, ASYNC_HIZ_SOURCE_SNAPSHOT);
        resourceManager.removeResource(ResourceTypes.TEXTURE, ASYNC_HIZ_SOURCE_SNAPSHOT_INSPECT);
        resourceManager.removeResource(ResourceTypes.TEXTURE, ASYNC_HIZ_TEXTURE);
        resourceManager.removeResource(ResourceTypes.TEXTURE, ASYNC_HIZ_ASYNC_TEXTURE);
        resourceManager.removeResource(ResourceTypes.RENDER_TARGET, ASYNC_OFFSCREEN_TARGET);
        resourceManager.removeResource(ResourceTypes.TEXTURE, ASYNC_OFFSCREEN_PRESENT_TEXTURE);
        resourceManager.removeResource(ResourceTypes.SHADER_TEMPLATE, INSPECTION_TEXTURE_SHADER_ID);
        resourceManager.removeResource(ResourceTypes.SHADER_TEMPLATE, INSPECTION_LINEARIZED_TEXTURE_SHADER_ID);
        resourceManager.removeResource(ResourceTypes.SHADER_TEMPLATE, HIZ_SNAPSHOT_COPY_SHADER_ID);
        resourceManager.removeResource(ResourceTypes.PARTIAL_RENDER_SETTING, ASYNC_HIZ_SNAPSHOT_COPY_SCENE_SETTING_ID);
        for (int level = 0; level < HIZ_DEPTH_LEVELS; level++) {
            resourceManager.removeResource(ResourceTypes.SHADER_TEMPLATE, asyncHiZLevelShaderId(level));
            resourceManager.removeResource(ResourceTypes.PARTIAL_RENDER_SETTING, asyncHiZLevelSceneSettingId(level));
        }
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
            context.registerUniform(KeyId.of("sketch_depthSize"), ValueGetter.create(
                    ignored -> asyncHiZDepthInfo(),
                    Vector3i[].class,
                    UniformUpdateDomain.BUILD_SNAPSHOT));
            context.registerUniform(KeyId.of("sketch_linerDepth"), ValueGetter.create(
                    instance -> {
                        GraphicsUniformSubject subject = (GraphicsUniformSubject) instance;
                        return subject.hasTag(HIZ_FIRST_TAG) ? 1 : 0;
                    },
                    Integer.class,
                    UniformUpdateDomain.BUILD_SNAPSHOT,
                    GraphicsUniformSubject.class));
            context.registerUniform(KeyId.of("sketch_screenSize"), ValueGetter.create(
                    instance -> {
                        GraphicsUniformSubject subject = (GraphicsUniformSubject) instance;
                        Vector3i[] info = asyncHiZDepthInfo();
                        if (subject.hasTag(HIZ_SECOND_TAG) && info.length > 0) {
                            Vector3i screenSize = info[Math.min(3, info.length - 1)];
                            return new Vector2i(screenSize.x, screenSize.y);
                        }
                        return new Vector2i(
                                Math.max(1, PipelineTestScene.this.context.windowWidth()),
                                Math.max(1, PipelineTestScene.this.context.windowHeight()));
                    },
                    Vector2i.class,
                    UniformUpdateDomain.BUILD_SNAPSHOT,
                    GraphicsUniformSubject.class));
            context.registerUniform(KeyId.of("sketch_renderDistance"), ValueGetter.create(
                    () -> VK_TEST_RENDER_DISTANCE,
                    Integer.class,
                    UniformUpdateDomain.BUILD_SNAPSHOT));
            context.registerGraphicsEntity(
                    createTriangleBlueprint(
                            INDIRECT_GRAPHICS_ID,
                            MAIN_STAGE_ID,
                            resourceSetC,
                            new float[]{
                                    -0.15f, -0.05f, 0.9f, 0.9f, 0.9f,
                                    0.15f, -0.05f, 0.9f, 0.5f, 0.2f,
                                    0.00f, 0.35f, 0.4f, 0.9f, 0.9f
                            },
                            SubmissionCapability.INDIRECT_READY),
                    ModuleGraphicsLifetime.PERMANENT);
            context.requestIndirectPlan(MAIN_STAGE_ID, INDIRECT_GRAPHICS_ID);
        }
    }

    private final class AsyncHiZState implements AutoCloseable {
        private final BufferedResourceSet<Texture> textures = BufferedResourceSet.create(
                new BufferedResourceDescriptor(KeyId.of("sketch_vktest:async_hiz_texture_family"),
                        BufferingMode.ASYNC_PRODUCE_SYNC_CONSUME, 2, "vk-test-async-hiz"),
                ignored -> null,
                PipelineTestScene.this::disposeQuietly);
        private final BufferedResourceSet<VulkanTextureResource> nativeTextures = BufferedResourceSet.create(
                new BufferedResourceDescriptor(KeyId.of("sketch_vktest:async_hiz_texture_native_family"),
                        BufferingMode.ASYNC_PRODUCE_SYNC_CONSUME, 2, "vk-test-async-hiz-native"),
                ignored -> null,
                PipelineTestScene.this::disposeQuietly);
        private int dispatchWidth = 1;
        private int dispatchHeight = 1;
        private int textureWidth = 1;
        private int textureHeight = 1;
        private AsyncGpuJobHandle pendingJob;
        private long nextEpoch = 1L;
        private long submittedEpoch;
        private int submissionCount;
        private int completionCount;

        private Texture readTexture() {
            return textures.readResource();
        }

        private Texture writeTexture() {
            return textures.writeResource();
        }

        private VulkanTextureResource readNativeTexture() {
            return nativeTextures.readResource();
        }

        private VulkanTextureResource writeNativeTexture() {
            return nativeTextures.writeResource();
        }

        private long nextEpoch() {
            return nextEpoch++;
        }

        private boolean hasPendingJob() {
            return pendingJob != null && !pendingJob.isDone();
        }

        private void onSubmitted(AsyncGpuJobHandle handle) {
            pendingJob = handle;
            submittedEpoch = handle != null ? handle.epoch() : submittedEpoch;
            submissionCount++;
        }

        private void promoteCompleted() {
            if (pendingJob == null || !pendingJob.isDone()) {
                return;
            }
            pendingJob.await();
            if (textures.promote(new ResourceEpoch(pendingJob.epoch()))) {
                nativeTextures.promote(new ResourceEpoch(pendingJob.epoch()));
                completionCount++;
            }
            pendingJob = null;
        }

        private void awaitPending() {
            if (pendingJob != null) {
                pendingJob.await();
                pendingJob = null;
            }
        }

        private void recreate(
                rogo.sketch.core.backend.BackendResourceInstaller installer,
                int dispatchWidth,
                int dispatchHeight,
                int textureWidth,
                int textureHeight) {
            recreate(installer, null, dispatchWidth, dispatchHeight, textureWidth, textureHeight);
        }

        private void recreate(
                rogo.sketch.core.backend.BackendResourceInstaller installer,
                VulkanBackendRuntime runtime,
                int dispatchWidth,
                int dispatchHeight,
                int textureWidth,
                int textureHeight) {
            closeTextures();
            this.dispatchWidth = Math.max(1, dispatchWidth);
            this.dispatchHeight = Math.max(1, dispatchHeight);
            this.textureWidth = Math.max(1, textureWidth);
            this.textureHeight = Math.max(1, textureHeight);
            textures.recreate(slot -> createAsyncHiZTexture(
                    installer,
                    slot == 0
                            ? KeyId.of("sketch_vktest:async_hiz_texture_read")
                            : KeyId.of("sketch_vktest:async_hiz_texture_write"),
                    this.textureWidth,
                    this.textureHeight));
            if (runtime != null) {
                nativeTextures.recreate(ignored -> runtime.createHiZStorageTextureResource(this.textureWidth, this.textureHeight));
            } else {
                nativeTextures.recreate(ignored -> null);
            }
            nextEpoch = 1L;
            submittedEpoch = 0L;
            submissionCount = 0;
            completionCount = 0;
        }

        private long publishedEpoch() {
            return textures.publishedEpoch().value();
        }

        private long submittedEpoch() {
            return submittedEpoch;
        }

        private int submissionCount() {
            return submissionCount;
        }

        private int completionCount() {
            return completionCount;
        }

        @Override
        public void close() {
            awaitPending();
            closeTextures();
        }

        private void closeTextures() {
            textures.close();
            nativeTextures.close();
        }
    }

    private final class AsyncOffscreenState implements AutoCloseable {
        private final BufferedResourceSet<AsyncOffscreenBundle> bundles = BufferedResourceSet.create(
                new BufferedResourceDescriptor(
                        KeyId.of("sketch_vktest:async_offscreen_family"),
                        BufferingMode.ASYNC_PRODUCE_SYNC_CONSUME,
                        2,
                        "vk-test-async-offscreen"),
                ignored -> null,
                PipelineTestScene.this::disposeQuietly);
        private AsyncGpuJobHandle pendingJob;
        private long nextEpoch = 1L;
        private int submissionCount;
        private int completionCount;

        private RenderTarget writeTarget() {
            AsyncOffscreenBundle bundle = bundles.writeResource();
            return bundle != null ? bundle.renderTarget() : null;
        }

        private Texture readTexture() {
            AsyncOffscreenBundle bundle = bundles.readResource();
            return bundle != null ? bundle.colorTexture() : null;
        }

        private VulkanTextureResource readNativeTexture() {
            AsyncOffscreenBundle bundle = bundles.readResource();
            return bundle != null ? bundle.nativeColorTexture() : null;
        }

        private long nextEpoch() {
            return nextEpoch++;
        }

        private boolean hasPendingJob() {
            return pendingJob != null && !pendingJob.isDone();
        }

        private void onSubmitted(AsyncGpuJobHandle handle) {
            pendingJob = handle;
            submissionCount++;
        }

        private void onCompletedSync(long epoch) {
            submissionCount++;
            if (bundles.promote(new ResourceEpoch(epoch))) {
                completionCount++;
            }
        }

        private void promoteCompleted() {
            if (pendingJob == null || !pendingJob.isDone()) {
                return;
            }
            pendingJob.await();
            if (bundles.promote(new ResourceEpoch(pendingJob.epoch()))) {
                completionCount++;
            }
            pendingJob = null;
        }

        private void awaitPending() {
            if (pendingJob != null) {
                pendingJob.await();
                pendingJob = null;
            }
        }

        private void recreate(
                rogo.sketch.core.backend.BackendResourceInstaller installer,
                int width,
                int height) {
            recreate(installer, vulkanRuntime(), width, height);
        }

        private void recreate(
                rogo.sketch.core.backend.BackendResourceInstaller installer,
                VulkanBackendRuntime runtime,
                int width,
                int height) {
            awaitPending();
            bundles.recreate(slot -> createAsyncOffscreenBundle(installer, runtime, slot, width, height));
            nextEpoch = 1L;
            submissionCount = 0;
            completionCount = 0;
        }

        private int submissionCount() {
            return submissionCount;
        }

        private int completionCount() {
            return completionCount;
        }

        private long publishedEpoch() {
            return bundles.publishedEpoch().value();
        }

        @Override
        public void close() {
            awaitPending();
            bundles.close();
        }
    }

    ControlSnapshot captureControlSnapshot() {
        AsyncOffscreenBundle readBundle = asyncOffscreenState.bundles.readResource();
        return new ControlSnapshot(
                context.windowWidth(),
                context.windowHeight(),
                asyncHiZSourceWidth(),
                asyncHiZSourceHeight(),
                asyncHiZSnapshotWidth(),
                asyncHiZSnapshotHeight(),
                asyncHiZPublishedWidth(),
                asyncHiZPublishedHeight(),
                asyncHiZState.submittedEpoch(),
                asyncHiZState.publishedEpoch(),
                asyncHiZState.hasPendingJob(),
                asyncHiZState.submissionCount(),
                asyncHiZState.completionCount(),
                asyncOffscreenState.submissionCount(),
                asyncOffscreenState.completionCount(),
                asyncOffscreenState.hasPendingJob(),
                readBundle != null && readBundle.colorTexture() != null ? readBundle.colorTexture().getCurrentWidth() : 0,
                readBundle != null && readBundle.colorTexture() != null ? readBundle.colorTexture().getCurrentHeight() : 0,
                asyncOffscreenState.publishedEpoch());
    }

    private int asyncHiZSourceWidth() {
        return textureWidth(ASYNC_HIZ_SOURCE_DEPTH);
    }

    private int asyncHiZSourceHeight() {
        return textureHeight(ASYNC_HIZ_SOURCE_DEPTH);
    }

    private int asyncHiZSnapshotWidth() {
        return textureWidth(ASYNC_HIZ_SOURCE_SNAPSHOT);
    }

    private int asyncHiZSnapshotHeight() {
        return textureHeight(ASYNC_HIZ_SOURCE_SNAPSHOT);
    }

    private int asyncHiZPublishedWidth() {
        Texture texture = asyncHiZState.readTexture();
        return texture != null ? texture.getCurrentWidth() : 0;
    }

    private int asyncHiZPublishedHeight() {
        Texture texture = asyncHiZState.readTexture();
        return texture != null ? texture.getCurrentHeight() : 0;
    }

    private int textureWidth(KeyId textureId) {
        Object texture = GraphicsResourceManager.getInstance().getResource(ResourceTypes.TEXTURE, textureId);
        if (texture instanceof Texture logicalTexture) {
            return logicalTexture.getCurrentWidth();
        }
        if (texture instanceof VulkanTextureResource vulkanTexture) {
            return vulkanTexture.width();
        }
        return 0;
    }

    private int textureHeight(KeyId textureId) {
        Object texture = GraphicsResourceManager.getInstance().getResource(ResourceTypes.TEXTURE, textureId);
        if (texture instanceof Texture logicalTexture) {
            return logicalTexture.getCurrentHeight();
        }
        if (texture instanceof VulkanTextureResource vulkanTexture) {
            return vulkanTexture.height();
        }
        return 0;
    }

    private AsyncOffscreenBundle createAsyncOffscreenBundle(
            rogo.sketch.core.backend.BackendResourceInstaller installer,
            VulkanBackendRuntime runtime,
            int slot,
            int width,
            int height) {
        KeyId colorId = KeyId.of("sketch_vktest:async_offscreen_color_" + slot);
        KeyId depthId = KeyId.of("sketch_vktest:async_offscreen_depth_" + slot);
        KeyId targetId = KeyId.of("sketch_vktest:async_offscreen_target_" + slot);

        Texture colorTexture = installer.createTexture(
                colorId,
                new ResolvedImageResource(
                        colorId,
                        Math.max(1, width),
                        Math.max(1, height),
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
                depthId,
                new ResolvedImageResource(
                        depthId,
                        Math.max(1, width),
                        Math.max(1, height),
                        1,
                        ImageFormat.D32_FLOAT,
                        EnumSet.of(ImageUsage.DEPTH_ATTACHMENT, ImageUsage.SAMPLED),
                        SamplerFilter.NEAREST,
                        SamplerFilter.NEAREST,
                        null,
                        SamplerWrap.CLAMP_TO_EDGE,
                        SamplerWrap.CLAMP_TO_EDGE,
                        null),
                null,
                null);
        replaceDirectResource(GraphicsResourceManager.getInstance(), ResourceTypes.TEXTURE, colorId, colorTexture);
        replaceDirectResource(GraphicsResourceManager.getInstance(), ResourceTypes.TEXTURE, depthId, depthTexture);

        VulkanTextureResource nativeColor = null;
        VulkanTextureResource nativeDepth = null;
        if (runtime != null) {
            nativeColor = runtime.createRenderTargetColorTextureResource(width, height, 1);
            nativeDepth = runtime.createSampledDepthTextureResource(width, height);
            runtime.registerTextureResource(colorId, nativeColor);
            runtime.registerTextureResource(depthId, nativeDepth);
        }

        RenderTarget renderTarget = installer.createRenderTarget(
                targetId,
                new ResolvedRenderTargetSpec(
                        targetId,
                        RenderTargetResolutionMode.SCREEN_SIZE,
                        1,
                        1,
                        1.0f,
                        1.0f,
                        List.of(colorId),
                        depthId,
                        null));
        return new AsyncOffscreenBundle(colorId, depthId, targetId, colorTexture, depthTexture, renderTarget, nativeColor, nativeDepth);
    }

    private ResourceBinding mergeResourceBinding(ResourceBinding base, ResourceBinding override) {
        if (base == null && override == null) {
            return null;
        }
        ResourceBinding merged = new ResourceBinding();
        if (base != null) {
            merged.merge(base);
        }
        if (override != null) {
            merged.merge(override);
        }
        return merged;
    }

    private GraphicsEntityBlueprint createInspectionQuadBlueprint(
            KeyId identifier,
            CompiledRenderSetting compiledRenderSetting,
            float left,
            float right,
            float bottom,
            float top) {
        DynamicMesh inspectionMesh = new DynamicMesh(
                KeyId.of(identifier + "_mesh"),
                SCENE_FORMAT,
                PrimitiveType.TRIANGLES,
                6,
                0,
                builder -> {
                });
        return createRasterBlueprint(
                identifier,
                OVERLAY_STAGE_ID,
                SCENE_PARAMETER,
                compiledRenderSetting,
                inspectionMesh,
                GEOMETRY_COMPONENT,
                writer -> writeInspectionQuadVertices(left, right, bottom, top, writer),
                SubmissionCapability.DIRECT_BATCHABLE);
    }

    private GraphicsEntityBlueprint createAsyncHiZSnapshotFreezeBlueprint() {
        return createComputeBlueprint(
                KeyId.of("sketch_vktest:async_hiz_snapshot_freeze"),
                COMPUTE_STAGE_ID,
                asyncHiZSnapshotFreezeSetting,
                () -> Math.max(1, (context.windowWidth() + 15) / 16),
                () -> Math.max(1, (context.windowHeight() + 15) / 16),
                100);
    }

    private List<GraphicsEntityBlueprint> createAsyncOffscreenGraphicsBlueprints() {
        if (teapotMeshes.isEmpty()) {
            return List.of();
        }
        List<GraphicsEntityBlueprint> blueprints = new ArrayList<>(teapotMeshes.size());
        for (int index = 0; index < teapotMeshes.size(); index++) {
            BakedMesh mesh = teapotMeshes.get(index);
            blueprints.add(createBakedBlueprint(
                    KeyId.of("sketch_vktest:async_offscreen_teapot_" + index),
                    OVERLAY_STAGE_ID,
                    OBJ_SCENE_PARAMETER,
                    asyncOffscreenGraphicsSetting,
                    mesh,
                    SubmissionCapability.DIRECT_BATCHABLE));
        }
        return List.copyOf(blueprints);
    }

    private static void writeInspectionQuadVertices(
            float left,
            float right,
            float bottom,
            float top,
            VertexRecordWriter writer) {
        if (writer == null) {
            return;
        }
        putInspectionVertex(writer, left, bottom, 0.0f, 0.0f);
        putInspectionVertex(writer, right, bottom, 1.0f, 0.0f);
        putInspectionVertex(writer, right, top, 1.0f, 1.0f);
        putInspectionVertex(writer, left, bottom, 0.0f, 0.0f);
        putInspectionVertex(writer, right, top, 1.0f, 1.0f);
        putInspectionVertex(writer, left, top, 0.0f, 1.0f);
    }

    private static void putInspectionVertex(VertexRecordWriter writer, float x, float y, float u, float v) {
        writer.put(x, y);
        writer.put(u, v, 0.0f);
    }

    private static final class HarnessRenderContext extends RenderContext {
        private HarnessRenderContext(int framebufferWidth, int framebufferHeight) {
            setWindowSize(framebufferWidth, framebufferHeight);
        }

        @Override
        public HarnessRenderContext snapshot() {
            HarnessRenderContext snapshot = new HarnessRenderContext(windowWidth, windowHeight);
            copyInto(snapshot);
            return snapshot;
        }

        private void setWindowSize(int framebufferWidth, int framebufferHeight) {
            this.windowWidth = Math.max(1, framebufferWidth);
            this.windowHeight = Math.max(1, framebufferHeight);
            projectionMatrix().identity();
            viewMatrix().identity();
            modelMatrix().identity();
        }
    }

    private GraphicsEntityBlueprint createTriangleBlueprint(
            KeyId identifier,
            KeyId stageId,
            CompiledRenderSetting compiledRenderSetting,
            float[] vertices,
            SubmissionCapability submissionCapability) {
        return createRasterBlueprint(
                identifier,
                stageId,
                SCENE_PARAMETER,
                compiledRenderSetting,
                SHARED_TRIANGLE_MESH,
                GEOMETRY_COMPONENT,
                writer -> writeSceneVertices(vertices, writer),
                submissionCapability);
    }

    private GraphicsEntityBlueprint createDepthTriangleBlueprint(
            KeyId identifier,
            KeyId stageId,
            CompiledRenderSetting compiledRenderSetting,
            float[] vertices) {
        return createRasterBlueprint(
                identifier,
                stageId,
                DEPTH_SCENE_PARAMETER,
                compiledRenderSetting,
                DEPTH_TRIANGLE_MESH,
                GEOMETRY_COMPONENT,
                writer -> writeDepthVertices(vertices, writer),
                SubmissionCapability.DIRECT_BATCHABLE);
    }

    private GraphicsEntityBlueprint createInstancedBakedBlueprint(
            KeyId identifier,
            KeyId stageId,
            CompiledRenderSetting compiledRenderSetting,
            BakedMesh mesh,
            float offsetX,
            float offsetY) {
        return createRasterBlueprint(
                identifier,
                stageId,
                INSTANCED_SOURCE_PARAMETER,
                compiledRenderSetting,
                mesh,
                INSTANCE_OFFSET_COMPONENT,
                writer -> writer.put(offsetX, offsetY),
                SubmissionCapability.DIRECT_BATCHABLE);
    }

    private GraphicsEntityBlueprint createBakedBlueprint(
            KeyId identifier,
            KeyId stageId,
            RasterizationParameter renderParameter,
            CompiledRenderSetting compiledRenderSetting,
            PreparedMesh preparedMesh,
            SubmissionCapability submissionCapability) {
        return createRasterBlueprint(
                identifier,
                stageId,
                renderParameter,
                compiledRenderSetting,
                preparedMesh,
                GEOMETRY_COMPONENT,
                null,
                submissionCapability);
    }

    private GraphicsEntityBlueprint createRasterBlueprint(
            KeyId identifier,
            KeyId stageId,
            RasterizationParameter renderParameter,
            CompiledRenderSetting compiledRenderSetting,
            PreparedMesh preparedMesh,
            KeyId componentKey,
            Consumer<VertexRecordWriter> authoring,
            SubmissionCapability submissionCapability) {
        GraphicsEntityBlueprint.Builder builder = GraphicsEntityPresets.raster(
                identifier,
                stageId,
                PipelineType.RASTERIZATION,
                renderParameter,
                rogo.sketch.core.pipeline.flow.ecs.GraphicsContainerHints.DEFAULT,
                null,
                blueprintOrderHint++,
                0,
                () -> true,
                () -> false,
                submissionCapability,
                DescriptorStability.STABLE,
                () -> 1L,
                ignored -> compiledRenderSetting);
        builder.put(GraphicsBuiltinComponents.PREPARED_MESH, new GraphicsBuiltinComponents.PreparedMeshComponent(() -> preparedMesh));
        builder.put(GraphicsBuiltinComponents.GEOMETRY_VERSION, new GraphicsBuiltinComponents.GeometryVersionComponent(() ->
                Objects.hash(
                        preparedMesh != null ? preparedMesh.getKetId() : null,
                        renderParameter.getLayout(),
                        renderParameter.indexMode(),
                        submissionCapability)));
        builder.put(
                GraphicsBuiltinComponents.INSTANCE_VERTEX_AUTHORING,
                new GraphicsBuiltinComponents.InstanceVertexAuthoringComponent((requestedComponentKey, writer) -> {
                    if (writer == null || !componentKey.equals(requestedComponentKey) || authoring == null) {
                        return;
                    }
                    authoring.accept(writer);
                }));
        return builder.build();
    }

    private GraphicsEntityBlueprint createComputeBlueprint(
            KeyId identifier,
            KeyId stageId,
            CompiledRenderSetting compiledRenderSetting) {
        return createComputeBlueprint(identifier, stageId, compiledRenderSetting, () -> 1, () -> 1, 100);
    }

    private GraphicsEntityBlueprint createComputeBlueprint(
            KeyId identifier,
            KeyId stageId,
            CompiledRenderSetting compiledRenderSetting,
            java.util.function.IntSupplier workGroupsX,
            java.util.function.IntSupplier workGroupsY,
            int order,
            KeyId... tags) {
        PartialRenderSetting partialRenderSetting = compiledRenderSetting != null
                ? PartialRenderSetting.create(
                compiledRenderSetting.renderSetting().executionDomain(),
                compiledRenderSetting.renderSetting().renderState(),
                compiledRenderSetting.renderSetting().targetBinding(),
                compiledRenderSetting.renderSetting().resourceBinding(),
                compiledRenderSetting.renderSetting().shouldSwitchRenderState(),
                compiledRenderSetting.renderSetting().aliasPolicy())
                : PartialRenderSetting.EMPTY;
        GraphicsEntityBlueprint.Builder builder = GraphicsEntityPresets.compute(
                        identifier,
                        stageId,
                        ComputeParameter.COMPUTE_PARAMETER,
                        order,
                        () -> true,
                        () -> false,
                        DescriptorStability.DYNAMIC,
                        () -> GraphicsEntityPresets.partialDescriptorVersion(partialRenderSetting),
                        renderParameter -> GraphicsEntityPresets.compilePartialDescriptor(renderParameter, partialRenderSetting),
                        dispatchContext -> dispatchContext.dispatch(
                                Math.max(1, workGroupsX.getAsInt()),
                                Math.max(1, workGroupsY.getAsInt()),
                                1))
                ;
        return GraphicsEntityPresets.withTags(builder, tags).build();
    }

    private GraphicsEntityBlueprint createAsyncHiZFirstComputeBlueprint() {
        return createComputeBlueprint(
                KeyId.of("sketch_vktest:async_hiz_dispatch_first"),
                COMPUTE_STAGE_ID,
                asyncHiZFirstSetting,
                () -> asyncHiZDispatchGroups(true)[0],
                () -> asyncHiZDispatchGroups(true)[1],
                101,
                HIZ_FIRST_TAG);
    }

    private GraphicsEntityBlueprint createAsyncHiZSecondComputeBlueprint() {
        return createComputeBlueprint(
                KeyId.of("sketch_vktest:async_hiz_dispatch_second"),
                COMPUTE_STAGE_ID,
                asyncHiZSecondSetting,
                () -> asyncHiZDispatchGroups(false)[0],
                () -> asyncHiZDispatchGroups(false)[1],
                102,
                HIZ_SECOND_TAG);
    }

    private GraphicsEntityBlueprint createAsyncHiZLevelComputeBlueprint(int level, CompiledRenderSetting compiledRenderSetting) {
        return createComputeBlueprint(
                KeyId.of("sketch_vktest:async_hiz_dispatch_level_" + level),
                COMPUTE_STAGE_ID,
                compiledRenderSetting,
                () -> asyncHiZDispatchGroups(level)[0],
                () -> asyncHiZDispatchGroups(level)[1],
                101 + level);
    }

    private static void writeSceneVertices(float[] vertices, VertexRecordWriter writer) {
        if (vertices == null || writer == null) {
            return;
        }
        for (int i = 0; i < vertices.length; i += 5) {
            writer.put(vertices[i], vertices[i + 1]);
            writer.put(vertices[i + 2], vertices[i + 3], vertices[i + 4]);
        }
    }

    private static void writeDepthVertices(float[] vertices, VertexRecordWriter writer) {
        if (vertices == null || writer == null) {
            return;
        }
        for (int i = 0; i < vertices.length; i += 6) {
            writer.put(vertices[i], vertices[i + 1], vertices[i + 2]);
            writer.put(vertices[i + 3], vertices[i + 4], vertices[i + 5]);
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
            int drawCallPacketCount,
            int asyncHiZSubmissionCount,
            int asyncHiZCompletionCount,
            long publishedAsyncHiZEpoch,
            int asyncGraphicsSubmissionCount,
            int asyncGraphicsCompletionCount,
            long publishedAsyncGraphicsEpoch
    ) {
        private boolean isBaseSatisfied() {
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

        private boolean isAsyncHiZSatisfied() {
            return asyncHiZSubmissionCount >= 1
                    && asyncHiZSubmissionCount >= 1
                    && asyncHiZCompletionCount >= 1
                    && publishedAsyncHiZEpoch >= 1L;
        }

        private boolean isAsyncGraphicsSatisfied() {
            return asyncGraphicsSubmissionCount >= 1
                    && asyncGraphicsCompletionCount >= 1
                    && publishedAsyncGraphicsEpoch >= 1L;
        }

        private boolean isSatisfied() {
            return isBaseSatisfied() && isAsyncHiZSatisfied() && isAsyncGraphicsSatisfied();
        }
    }

    record ControlSnapshot(
            int windowWidth,
            int windowHeight,
            int hizSourceWidth,
            int hizSourceHeight,
            int hizSnapshotWidth,
            int hizSnapshotHeight,
            int hizPublishedWidth,
            int hizPublishedHeight,
            long hizSubmittedEpoch,
            long hizPublishedEpoch,
            boolean hizPendingJob,
            int asyncHiZSubmissionCount,
            int asyncHiZCompletionCount,
            int asyncGraphicsSubmissionCount,
            int asyncGraphicsCompletionCount,
            boolean asyncGraphicsPendingJob,
            int asyncGraphicsWidth,
            int asyncGraphicsHeight,
            long asyncGraphicsPublishedEpoch
    ) {
    }

    private record AsyncOffscreenBundle(
            KeyId colorId,
            KeyId depthId,
            KeyId targetId,
            Texture colorTexture,
            Texture depthTexture,
            RenderTarget renderTarget,
            VulkanTextureResource nativeColorTexture,
            VulkanTextureResource nativeDepthTexture
    ) implements ResourceObject {
        @Override
        public void dispose() {
            if (renderTarget != null && !renderTarget.isDisposed()) {
                renderTarget.dispose();
            }
            if (colorTexture != null && !colorTexture.isDisposed()) {
                colorTexture.dispose();
            }
            if (depthTexture != null && !depthTexture.isDisposed()) {
                depthTexture.dispose();
            }
            if (nativeColorTexture != null && !nativeColorTexture.isDisposed()) {
                nativeColorTexture.dispose();
            }
            if (nativeDepthTexture != null && !nativeDepthTexture.isDisposed()) {
                nativeDepthTexture.dispose();
            }
            GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
            resourceManager.removeResource(ResourceTypes.TEXTURE, colorId);
            resourceManager.removeResource(ResourceTypes.TEXTURE, depthId);
        }

        @Override
        public boolean isDisposed() {
            return colorTexture == null || colorTexture.isDisposed();
        }
    }
}

