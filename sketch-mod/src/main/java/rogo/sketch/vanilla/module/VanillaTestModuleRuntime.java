package rogo.sketch.vanilla.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import rogo.sketch.SketchRender;
import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.MeshIndexMode;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.VertexLayoutSpec;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsEntityPresets;
import rogo.sketch.core.graphics.ecs.GraphicsUpdateDomain;
import rogo.sketch.core.model.MeshGroup;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphicsLifetime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.session.ModuleSession;
import rogo.sketch.core.pipeline.module.session.ModuleSessionContext;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.DefaultDataFormats;
import rogo.sketch.core.graphics.ecs.TransformWriter;
import rogo.sketch.vanilla.MinecraftRenderStages;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class VanillaTestModuleRuntime implements ModuleRuntime {
    private static final int TEST_CUBE_COUNT = 5;
    private static final boolean ENABLE_DEFAULT_TEST_TRACE =
            Boolean.getBoolean("sketch.renderTrace.testModule");
    private static final KeyId CUBE_TRANSFORM_ID = KeyId.of("transform_id");

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

                PlayerRegistration playerGraphics = registerPlayerGraphics(context);

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

    private PlayerRegistration registerPlayerGraphics(ModuleSessionContext context) {
        GraphicsEntityBlueprint blueprint = GraphicsEntityPresets.auxiliary(
                        KeyId.of(SketchRender.MOD_ID, "player_transform"),
                        () -> false,
                        () -> false)
                .put(
                                GraphicsBuiltinComponents.TRANSFORM_BINDING,
                        new GraphicsBuiltinComponents.TransformBindingComponent(
                                GraphicsUpdateDomain.SYNC_FRAME,
                                VanillaTestModuleRuntime::writePlayerFrameTransform,
                                -1))
                .build();
        GraphicsEntityId entityId = context.registerGraphicsEntity(blueprint, ModuleGraphicsLifetime.SESSION);
        return new PlayerRegistration(entityId);
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
            PlayerRegistration playerGraphics) {
        ResourceReference<PartialRenderSetting> renderSetting = context.resourceManager()
                .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, renderSettingKey);
        KeyId cubeId = KeyId.of(SketchRender.MOD_ID, "cube_test_" + UUID.randomUUID());

        VertexLayoutSpec layout = VertexLayoutSpec.builder()
                .addStatic(BakedTypeMesh.BAKED_MESH, DefaultDataFormats.POSITION_UV_NORMAL)
                .addDynamicInstanced(CUBE_TRANSFORM_ID, DefaultDataFormats.INT)
                .build();
        MeshGroup meshGroup = context.resourceManager()
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
        GraphicsEntityId[] entityHolder = new GraphicsEntityId[1];
        GraphicsEntityBlueprint blueprint = GraphicsEntityPresets.raster(
                        cubeId,
                        translucent ? MinecraftRenderStages.TRANSLUCENT.getIdentifier() : stage,
                        translucent ? PipelineType.TRANSLUCENT : PipelineType.RASTERIZATION,
                        renderParameter,
                        rogo.sketch.core.pipeline.flow.ecs.GraphicsContainerHints.DEFAULT,
                        null,
                        0L,
                        0,
                        () -> Minecraft.getInstance().player != null,
                        () -> false,
                        SubmissionCapability.DIRECT_BATCHABLE,
                        DescriptorStability.DYNAMIC,
                        () -> GraphicsEntityPresets.partialDescriptorVersion(resolvePartialRenderSetting(renderSetting)),
                        parameter -> GraphicsEntityPresets.compilePartialDescriptor(
                                context.resourceManager(),
                                parameter,
                                resolvePartialRenderSetting(renderSetting)))
                .put(GraphicsBuiltinComponents.PREPARED_MESH, new GraphicsBuiltinComponents.PreparedMeshComponent(() ->
                        resolvePreparedMesh(context.resourceManager(), meshName)))
                .put(GraphicsBuiltinComponents.GEOMETRY_VERSION, new GraphicsBuiltinComponents.GeometryVersionComponent(() ->
                        Objects.hash(meshName, primitiveType, indexMode)))
                .put(
                        GraphicsBuiltinComponents.INSTANCE_VERTEX_AUTHORING,
                        new GraphicsBuiltinComponents.InstanceVertexAuthoringComponent((componentKey, writer) -> {
                            if (!CUBE_TRANSFORM_ID.equals(componentKey)) {
                                return;
                            }
                            GraphicsEntityId entityId = entityHolder[0];
                            GraphicsBuiltinComponents.TransformBindingComponent bindingComponent = entityId != null
                                    ? context.graphicsWorld().component(entityId, GraphicsBuiltinComponents.TRANSFORM_BINDING)
                                    : null;
                            writer.put(bindingComponent != null ? bindingComponent.transformId() : -1);
                        }))
                .put(
                        GraphicsBuiltinComponents.TRANSFORM_BINDING,
                        new GraphicsBuiltinComponents.TransformBindingComponent(
                                GraphicsUpdateDomain.ASYNC_TICK,
                                writer -> writeLocalTransform(writer, offset, scale, rotation),
                                -1))
                .put(
                        GraphicsBuiltinComponents.TRANSFORM_HIERARCHY,
                        new GraphicsBuiltinComponents.TransformHierarchyComponent(playerGraphics::entityId))
                .build();
        entityHolder[0] = context.registerGraphicsEntity(blueprint, ModuleGraphicsLifetime.SESSION);
    }

    private static PreparedMesh resolvePreparedMesh(
            rogo.sketch.core.resource.GraphicsResourceManager resourceManager,
            KeyId meshName) {
        MeshGroup meshGroup = resourceManager
                .getResource(ResourceTypes.MESH, KeyId.of(SketchRender.MOD_ID, "cube"));
        return meshGroup != null ? meshGroup.getMesh(meshName) : null;
    }

    private static PartialRenderSetting resolvePartialRenderSetting(ResourceReference<PartialRenderSetting> renderSetting) {
        return renderSetting != null && renderSetting.isAvailable() ? renderSetting.get() : PartialRenderSetting.EMPTY;
    }

    private static void writePlayerFrameTransform(TransformWriter writer) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            Vec3 pos = player.getPosition(Minecraft.getInstance().getPartialTick());
            writer.setPosition((float) pos.x, (float) pos.y, (float) pos.z);
            float pitch = (float) Math.toRadians(player.getXRot());
            float yaw = (float) Math.toRadians(-player.getYRot());
            writer.setRotation(pitch, yaw, 0);
            return;
        }
        writer.reset();
    }

    private static void writeLocalTransform(
            TransformWriter writer,
            Vector3f offset,
            Vector3f scale,
            Vector3f rotation) {
        writer.setPosition(offset);
        writer.setScale(scale);
        writer.setRotation(rotation);
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

    private record PlayerRegistration(GraphicsEntityId entityId) {
    }
}

