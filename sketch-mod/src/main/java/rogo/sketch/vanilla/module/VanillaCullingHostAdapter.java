package rogo.sketch.vanilla.module;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.compat.sodium.SodiumTerrainCullCoordinator;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.feature.culling.CullingStages;
import rogo.sketch.feature.culling.MinecraftCullingDebugState;
import rogo.sketch.feature.culling.MinecraftHiZState;
import rogo.sketch.feature.culling.MinecraftShaderCapabilityService;
import rogo.sketch.module.culling.CullingModuleDescriptor;
import rogo.sketch.module.culling.CullingModuleRuntime;
import rogo.sketch.module.culling.CullingHostAdapter;
import rogo.sketch.module.culling.TerrainRegionSource;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.PipelineUtil;

import java.util.List;

/**
 * Minecraft-side culling adapter used by the core culling module.
 */
public class VanillaCullingHostAdapter implements CullingHostAdapter {
    private final MinecraftHiZState hiZState = MinecraftHiZState.getInstance();
    private final MinecraftShaderCapabilityService shaderCapabilities = MinecraftShaderCapabilityService.getInstance();
    private final MinecraftCullingDebugState debugState = MinecraftCullingDebugState.getInstance();

    @Override
    public KeyId terrainStageId() {
        return CullingStages.TERRAIN_CULLING;
    }

    @Override
    public KeyId hizStageId() {
        return CullingStages.HIZ;
    }

    @Override
    public KeyId frameSyncStageId() {
        return MinecraftRenderStages.SKY.getIdentifier();
    }

    @Override
    public void refreshHiZInputs(RenderContext renderContext) {
        hiZState.updateDepthMap(
                isAnyCullingEnabled(),
                debugState.checkingCull(),
                shaderCapabilities);
    }

    @Override
    public List<TerrainRegionSource> refreshTerrainInputs(RenderContext renderContext) {
        MeshResource.ensureInitialized();
        if (Minecraft.getInstance().level != null) {
            MeshResource.updateDistance(Minecraft.getInstance().options.getEffectiveRenderDistance());
        }
        return SodiumTerrainCullCoordinator.getInstance().prepareTerrainSources();
    }

    @Override
    public @Nullable Texture hizTexture() {
        return hiZState.depthBufferTarget();
    }

    @Override
    public @Nullable Texture asyncHiZWriteTexture() {
        return hiZState.writeDepthBufferTarget();
    }

    @Override
    public @Nullable Texture hizSourceDepthSnapshotTexture() {
        return hiZState.sourceDepthSnapshotTexture();
    }

    @Override
    public Vector3i[] hizDepthInfo() {
        return hiZState.depthBufferInformation();
    }

    @Override
    public void onAsyncHiZSubmitted(long epoch) {
        hiZState.onAsyncHiZSubmitted(epoch);
    }

    @Override
    public void onAsyncHiZCompleted(long epoch) {
        hiZState.onAsyncHiZCompleted(epoch);
    }

    @Override
    public void clearTransientState() {
        hiZState.clearTransientState();
        SodiumTerrainCullCoordinator.getInstance().clear();
    }

    @Override
    public boolean entityCullingHostActive() {
        return shaderCapabilities.entityCullingHostActive();
    }

    @Override
    public boolean terrainCullingHostActive() {
        return shaderCapabilities.terrainCullingHostActive();
    }

    @Override
    public boolean freezeHiZUpdates() {
        return debugState.checkingCull();
    }

    @Override
    public void commitVisibleTerrainRegions(List<TerrainRegionSource> visibleTerrainRegions) {
        SodiumTerrainCullCoordinator.getInstance()
                .commitVisibleRegions(visibleTerrainRegions, MeshResource.resourceSet().currentFrame());
    }

    private boolean isAnyCullingEnabled() {
        if (PipelineUtil.pipeline() == null || PipelineUtil.pipeline().runtimeHost() == null) {
            return rogo.sketch.Config.getCullChunk() || rogo.sketch.Config.doEntityCulling();
        }
        if (PipelineUtil.pipeline().runtimeHost().runtimeById(CullingModuleDescriptor.MODULE_ID) instanceof CullingModuleRuntime runtime) {
            return runtime.anyCullingRuntimeEnabled();
        }
        return rogo.sketch.Config.getCullChunk() || rogo.sketch.Config.doEntityCulling();
    }
}
