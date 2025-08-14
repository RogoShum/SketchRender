package rogo.sketch.vanilla.event;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import rogo.sketch.Config;
import rogo.sketch.SketchRender;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.event.GraphicsPipelineInitEvent;
import rogo.sketch.event.UniformHookRegisterEvent;
import rogo.sketch.event.bridge.ProxyModEvent;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.mixin.AccessorFrustum;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.shader.uniform.ValueGetter;
import rogo.sketch.util.Identifier;
import rogo.sketch.vanilla.McGraphicsPipeline;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.resource.TempTexture;

import java.util.ArrayList;
import java.util.List;

public class VanillaPipelineEventHandler {
    public static final TempTexture mainColor = new TempTexture(() -> Minecraft.getInstance().getMainRenderTarget(), false);
    public static final TempTexture mainDepth = new TempTexture(() -> Minecraft.getInstance().getMainRenderTarget(), true);

    public static void registerStaticResource() {
        GraphicsResourceManager.getInstance().registerManual(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_0"),
                () -> CullingStateManager.DEPTH_BUFFER_TARGET[0].texture());
        GraphicsResourceManager.getInstance().registerManual(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_1"),
                () -> CullingStateManager.DEPTH_BUFFER_TARGET[1].texture());
        GraphicsResourceManager.getInstance().registerManual(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_2"),
                () -> CullingStateManager.DEPTH_BUFFER_TARGET[2].texture());
        GraphicsResourceManager.getInstance().registerManual(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_3"),
                () -> CullingStateManager.DEPTH_BUFFER_TARGET[3].texture());
        GraphicsResourceManager.getInstance().registerManual(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_4"),
                () -> CullingStateManager.DEPTH_BUFFER_TARGET[4].texture());
        GraphicsResourceManager.getInstance().registerManual(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_5"),
                () -> CullingStateManager.DEPTH_BUFFER_TARGET[5].texture());
        GraphicsResourceManager.getInstance().registerManual(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_6"),
                () -> CullingStateManager.DEPTH_BUFFER_TARGET[6].texture());
        GraphicsResourceManager.getInstance().registerManual(ResourceTypes.TEXTURE, Identifier.of(SketchRender.MOD_ID, "hiz_texture_7"),
                () -> CullingStateManager.DEPTH_BUFFER_TARGET[7].texture());

        GraphicsResourceManager.getInstance().registerManual(ResourceTypes.TEXTURE, Identifier.of("minecraft", "main_color"),
                () -> mainColor.getTexture());
        GraphicsResourceManager.getInstance().registerManual(ResourceTypes.TEXTURE, Identifier.of("minecraft", "main_depth"),
                () -> mainDepth.getTexture());
    }

    public static void onUniformInit(ProxyModEvent event) {
        if (event.getWrapped() instanceof UniformHookRegisterEvent uniformEvent) {
            uniformEvent.register(Identifier.of("sketch_cullFacing"), ValueGetter.create(() -> {
                return SodiumClientMod.options().performance.useBlockFaceCulling ? 1 : 0;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_translucentSort"), ValueGetter.create(() -> {
                return SodiumClientMod.canApplyTranslucencySorting() ? 1 : 0;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_entityCount"), ValueGetter.create(() -> {
                return CullingStateManager.ENTITY_CULLING_MASK.getEntityMap().size();
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_cullingTerrain"), ValueGetter.create(() -> {
                return !Config.getCullChunk() || CullingStateManager.SHADER_LOADER.renderingShadowPass() ? 0 : 1;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_checkCulling"), ValueGetter.create(() -> {
                return CullingStateManager.checkCulling ? 1 : 0;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_levelMinPos"), ValueGetter.create(() -> {
                return CullingStateManager.LEVEL_MIN_POS;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_levelPosRange"), ValueGetter.create(() -> {
                return CullingStateManager.LEVEL_POS_RANGE;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_levelSectionRange"), ValueGetter.create(() -> {
                return CullingStateManager.LEVEL_SECTION_RANGE;
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_cameraOffset"), ValueGetter.create(() -> {
                Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                BlockPos blockPos = new BlockPos((int) pos.x >> 4, CullingStateManager.LEVEL_MIN_POS >> 4, (int) pos.z >> 4);
                return new Vector3i(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            }, Vector3i.class));

            uniformEvent.register(Identifier.of("sketch_cameraPos"), ValueGetter.create(() -> {
                Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                return new Vector3i((int) pos.x, (int) pos.y, (int) pos.z);
            }, Vector3i.class));

            uniformEvent.register(Identifier.of("sketch_renderDistance"), ValueGetter.create(() -> {
                return MeshResource.getRenderDistance();
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_spacePartitionSize"), ValueGetter.create(() -> {
                return MeshResource.getSpacePartitionSize();
            }, Integer.class));

            uniformEvent.register(Identifier.of("sketch_cullingViewMat"), ValueGetter.create(() -> {
                return CullingStateManager.VIEW_MATRIX;
            }, Matrix4f.class));

            uniformEvent.register(Identifier.of("sketch_cullingProjMat"), ValueGetter.create(() -> {
                return CullingStateManager.PROJECTION_MATRIX;
            }, Matrix4f.class));

            uniformEvent.register(Identifier.of("sketch_cullingCameraPos"), ValueGetter.create(() -> {
                return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().toVector3f();
            }, Vector3f.class));

            uniformEvent.register(Identifier.of("sketch_cullingCameraDir"), ValueGetter.create(() -> {
                return Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
            }, Vector3f.class));

            uniformEvent.register(Identifier.of("sketch_frustumPos"), ValueGetter.create(() -> {
                return new Vec3(
                        ((AccessorFrustum) CullingStateManager.FRUSTUM).camX(),
                        ((AccessorFrustum) CullingStateManager.FRUSTUM).camY(),
                        ((AccessorFrustum) CullingStateManager.FRUSTUM).camZ()).toVector3f();
            }, Vector3f.class));

            uniformEvent.register(Identifier.of("sketch_cullingFrustum"), ValueGetter.create(() -> {
                Vector4f[] frustumData = SketchRender.getFrustumPlanes(((AccessorFrustum) CullingStateManager.FRUSTUM).frustumIntersection());
                List<Float> data = new ArrayList<>();
                for (Vector4f frustumDatum : frustumData) {
                    data.add(frustumDatum.x());
                    data.add(frustumDatum.y());
                    data.add(frustumDatum.z());
                    data.add(frustumDatum.w());
                }
                float[] array = new float[data.size()];
                for (int i = 0; i < data.size(); i++) {
                    array[i] = data.get(i);
                }

                return array;
            }, float[].class));

            uniformEvent.register(Identifier.of("sketch_depthSize"), ValueGetter.create(() -> {
                float[] array = new float[CullingStateManager.DEPTH_SIZE * 2];
                for (int i = 0; i < CullingStateManager.DEPTH_SIZE; ++i) {
                    int arrayIdx = i * 2;
                    array[arrayIdx] = (float) CullingStateManager.DEPTH_BUFFER_TARGET[i].width;
                    array[arrayIdx + 1] = (float) CullingStateManager.DEPTH_BUFFER_TARGET[i].height;
                }

                return array;
            }, float[].class));
        }
    }

    public static void onPipelineInit(ProxyModEvent event) {
        if (event.getWrapped() instanceof GraphicsPipelineInitEvent pipeLineInitEvent) {
            if (!(pipeLineInitEvent.getPipeline() instanceof McGraphicsPipeline mcPipeline)) {
                return;
            }

            switch (pipeLineInitEvent.getPhase()) {
                case EARLY -> {
                    // Register vanilla stages first
                    MinecraftRenderStages.registerVanillaStages(mcPipeline);
                }
                case NORMAL -> {
                    // Register extra stages that might be added by mods
                    MinecraftRenderStages.registerExtraStages(mcPipeline);
                }
                case LATE -> {
                    if (!mcPipeline.getPendingStages().isEmpty()) {
                        SketchRender.LOGGER.warn("Warning: {} stages are still pending", mcPipeline.getPendingStages().size());
                    }
                }
            }
        }
    }
}