package rogo.sketch.vanilla.module;

import org.joml.Vector3f;
import rogo.sketch.SketchRender;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphicsLifetime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.session.ModuleSession;
import rogo.sketch.core.pipeline.module.session.ModuleSessionContext;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.DefaultDataFormats;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.graphics.CubeTestGraphics;
import rogo.sketch.vanilla.graphics.PlayerGraphics;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class VanillaTestModuleRuntime implements ModuleRuntime {
    private static final int TEST_CUBE_COUNT = 5;
    private static final boolean ENABLE_DEFAULT_TEST_TRACE =
            Boolean.getBoolean("sketch.renderTrace.testModule");

    @Override
    public String id() {
        return VanillaTestModuleDescriptor.MODULE_ID;
    }

    @Override
    public ModuleSession createSession() {
        return new ModuleSession() {
            @Override
            public String id() {
                return "vanilla_test_session";
            }

            @Override
            public void onWorldEnter(ModuleSessionContext context) {
                if (ENABLE_DEFAULT_TEST_TRACE) {
                    context.pipeline().renderTraceConfig().setEnabled(true);
                    context.pipeline().renderTraceConfig().traceGraphicsClass(CubeTestGraphics.class);
                }

                Random random = new Random();
                KeyId renderSettingKey = KeyId.of(SketchRender.MOD_ID, "cube_test_transform");
                KeyId cubeGeometry = KeyId.of("cube_geometry");
                KeyId sphereGeometry = KeyId.of("sphere_geometry");

                List<KeyId> stages = List.of(
                        MinecraftRenderStages.ENTITIES.getIdentifier(),
                        MinecraftRenderStages.BLOCK_ENTITIES.getIdentifier(),
                        MinecraftRenderStages.DESTROY_PROGRESS.getIdentifier(),
                        MinecraftRenderStages.PARTICLE.getIdentifier(),
                        MinecraftRenderStages.WEATHER.getIdentifier());

                PlayerGraphics playerGraphics = registerPlayerGraphics(context);

                for (int i = 0; i < TEST_CUBE_COUNT; i++) {
                    KeyId stage = stages.get(random.nextInt(stages.size()));
                    boolean translucent = random.nextBoolean();
                    Vector3f offset = new Vector3f(randomOffset(random), randomOffset(random), randomOffset(random));
                    Vector3f scale = new Vector3f(
                            0.5f + random.nextFloat() * 0.5f,
                            0.5f + random.nextFloat() * 0.5f,
                            0.5f + random.nextFloat() * 0.5f);

                    registerTestCube(
                            context,
                            renderSettingKey,
                            random.nextBoolean() ? cubeGeometry : sphereGeometry,
                            offset,
                            scale,
                            new Vector3f(0),
                            stage,
                            translucent,
                            playerGraphics);
                }
            }
        };
    }

    private PlayerGraphics registerPlayerGraphics(ModuleSessionContext context) {
        PlayerGraphics playerGraphics = new PlayerGraphics(KeyId.of(SketchRender.MOD_ID, "player_transform"));
        context.registerAuxiliaryGraphics(playerGraphics, ModuleGraphicsLifetime.SESSION);
        return playerGraphics;
    }

    private void registerTestCube(
            ModuleSessionContext context,
            KeyId renderSettingKey,
            KeyId meshName,
            Vector3f offset,
            Vector3f scale,
            Vector3f rotation,
            KeyId stage,
            boolean translucent,
            PlayerGraphics playerGraphics) {
        ResourceReference<PartialRenderSetting> renderSetting = GraphicsResourceManager.getInstance()
                .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, renderSettingKey);

        CubeTestGraphics cubeTestGraphics = new CubeTestGraphics(
                KeyId.of(SketchRender.MOD_ID, "cube_test_" + UUID.randomUUID()),
                renderSetting,
                meshName,
                offset,
                scale,
                rotation);
        cubeTestGraphics.setParentGraphics(playerGraphics);

        VertexLayoutSpec layout = VertexLayoutSpec.builder()
                .addStatic(BakedTypeMesh.BAKED_MESH, DefaultDataFormats.POSITION_UV_NORMAL)
                .addDynamicInstanced(CubeTestGraphics.TRANSFORM_ID, DefaultDataFormats.INT)
                .build();
        MeshGroup meshGroup = GraphicsResourceManager.getInstance()
                .getResource(ResourceTypes.MESH, KeyId.of(SketchRender.MOD_ID, "cube"));
        PreparedMesh preparedMesh = meshGroup != null ? meshGroup.getMesh(meshName) : null;
        PrimitiveType primitiveType = preparedMesh != null ? preparedMesh.getPrimitiveType() : PrimitiveType.TRIANGLES;
        MeshIndexMode indexMode = resolveIndexMode(meshGroup, preparedMesh);
        RenderParameter renderParameter = RasterizationParameter.create(
                layout,
                primitiveType,
                indexMode,
                BufferUpdatePolicy.DYNAMIC,
                false);
        context.registerGraphics(
                translucent ? MinecraftRenderStages.TRANSLUCENT.getIdentifier() : stage,
                cubeTestGraphics,
                renderParameter,
                translucent ? PipelineType.TRANSLUCENT : PipelineType.RASTERIZATION,
                ModuleGraphicsLifetime.SESSION);
    }

    private MeshIndexMode resolveIndexMode(MeshGroup meshGroup, PreparedMesh preparedMesh) {
        if (meshGroup != null) {
            Object raw = meshGroup.getMetadata("indexMode");
            if (raw instanceof String value) {
                return switch (value.toLowerCase()) {
                    case "generated" -> MeshIndexMode.GENERATED;
                    case "none" -> MeshIndexMode.NONE;
                    default -> MeshIndexMode.EXPLICIT_LOCAL;
                };
            }
        }
        if (preparedMesh != null && preparedMesh.isIndexed()) {
            return MeshIndexMode.EXPLICIT_LOCAL;
        }
        return MeshIndexMode.NONE;
    }

    private float randomOffset(Random random) {
        if (random.nextBoolean()) {
            return 0.5f + random.nextFloat() * 4.5f;
        }
        return -5.0f + random.nextFloat() * 4.5f;
    }
}

