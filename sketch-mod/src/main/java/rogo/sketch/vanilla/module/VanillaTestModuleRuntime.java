package rogo.sketch.vanilla.module;

import org.joml.Vector3f;
import rogo.sketch.SketchRender;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.Usage;
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
                context.pipeline().renderTraceConfig().setEnabled(true);
                context.pipeline().renderTraceConfig().traceGraphicsClass(CubeTestGraphics.class);

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
        VertexLayoutSpec layout = VertexLayoutSpec.builder().build();
        RenderParameter renderParameter = RasterizationParameter.create(layout, PrimitiveType.QUADS, Usage.STATIC_DRAW, false);
        context.registerGraphics(
                MinecraftRenderStages.ENTITIES.getIdentifier(),
                playerGraphics,
                renderParameter,
                ModuleGraphicsLifetime.SESSION);
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
        RenderParameter renderParameter = RasterizationParameter.create(layout, PrimitiveType.QUADS, Usage.DYNAMIC_DRAW, false);
        context.registerGraphics(
                translucent ? MinecraftRenderStages.TRANSLUCENT.getIdentifier() : stage,
                cubeTestGraphics,
                renderParameter,
                translucent ? PipelineType.TRANSLUCENT : PipelineType.RASTERIZATION,
                ModuleGraphicsLifetime.SESSION);
    }

    private float randomOffset(Random random) {
        if (random.nextBoolean()) {
            return 0.5f + random.nextFloat() * 4.5f;
        }
        return -5.0f + random.nextFloat() * 4.5f;
    }
}
