package rogo.sketch.compat.sodium;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.DrawCommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkShaderInterface;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.ShaderChunkRendererExt;
import rogo.sketch.compat.sodium.api.ExtraChunkRenderer;
import rogo.sketch.core.backend.IndirectDrawService;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.module.culling.TerrainMeshResourceSet;

import java.util.List;

public class IndirectDrawChunkRenderer extends ShaderChunkRenderer implements ExtraChunkRenderer {
    protected int maxElementCount = 0;
    private boolean isIndexedPass;
    protected Class<?> extraShaderInterface;
    protected boolean checkedShaderInterface = false;
    private final ExtraChunkRenderer defaultChunkRenderer;

    public IndirectDrawChunkRenderer(RenderDevice device, ChunkVertexType vertexType, ExtraChunkRenderer renderer) {
        super(device, vertexType);
        defaultChunkRenderer = renderer;
    }

    @Override
    public void render(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera) {
        MeshResource.ensureInitialized();
        TerrainMeshResourceSet terrainResources = MeshResource.resourceSet();
        List<RenderRegion> orderedRegions = SodiumTerrainCullCoordinator.getInstance().orderedRegions();

        super.begin(renderPass);
        ChunkShaderInterface shaderInterface = null;
        if (this.activeProgram != null) {
            shaderInterface = this.activeProgram.getInterface();
        } else {
            if (!checkedShaderInterface) {
                try {
                    extraShaderInterface = Class.forName("net.irisshaders.iris.compat.sodium.impl.shader_overrides.ShaderChunkRendererExt");
                } catch (ClassNotFoundException ignored) {
                } finally {
                    checkedShaderInterface = true;
                }
            } else if (extraShaderInterface != null && extraShaderInterface.isAssignableFrom(ShaderChunkRenderer.class)) {
                GlProgram<IrisChunkShaderInterface> program = ((ShaderChunkRendererExt) this).iris$getOverride();
                if (program != null) {
                    shaderInterface = program.getInterface();
                }
            }
        }

        if (shaderInterface != null) {
            this.isIndexedPass = renderPass.isSorted();
            int maxElement = terrainResources.maxElementReadbackBuffer() != null
                    ? terrainResources.maxElementReadbackBuffer().getInt(0)
                    : 0;
            if (maxElementCount < maxElement && !this.isIndexedPass) {
                getSharedIndexBuffer().ensureCapacity(commandList, maxElement);
                maxElementCount = maxElement;
            }

            if (orderedRegions != null && !orderedRegions.isEmpty()) {
                shaderInterface.setProjectionMatrix(matrices.projection());
                shaderInterface.setModelViewMatrix(matrices.modelView());
                onRender(defaultChunkRenderer, shaderInterface, commandList, renderPass, camera, orderedRegions);
            }
        }
        super.end(renderPass);
    }

    public static final long FACING_COUNT = 7L;
    public static final long REGION_MESH_STRIDE = 256L * 3L * FACING_COUNT * 20L;
    public static final long LAYER_MESH_STRIDE = 256L * FACING_COUNT * 20L;

    public void onRender(
            ExtraChunkRenderer renderer,
            ChunkShaderInterface shader,
            CommandList commandList,
            TerrainRenderPass pass,
            CameraTransform camera,
            List<RenderRegion> orderedRegions) {
        TerrainMeshResourceSet terrainResources = MeshResource.resourceSet();
        renderer.setIndexedPass(this.isIndexedPass);

        int passIndex = 0;
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
            passIndex = 2;
        } else if (pass == DefaultTerrainRenderPasses.CUTOUT) {
            passIndex = 1;
        }

        long passOffset = LAYER_MESH_STRIDE * passIndex;

        if (!orderedRegions.isEmpty()) {
            if (terrainResources.indirectCommands() != null) {
                terrainResources.indirectCommands().bind();
            }
            if (terrainResources.cullingCounter() != null) {
                terrainResources.cullingCounter().bind();
            }
        }

        for (int i = 0; i < orderedRegions.size(); ++i) {
            int index = pass.isReverseOrder() ? (orderedRegions.size() - 1 - i) : i;
            RenderRegion region = orderedRegions.get(index);
            SectionRenderDataStorage storage = region.getStorage(pass);
            if (!(storage instanceof RegionMeshDataStorage regionMeshDataStorage)) {
                continue;
            }

            int meshCount = regionMeshDataStorage.getTotalFacingCount();
            GlTessellation tessellation = renderer.sodiumTessellation(commandList, region);
            renderer.sodiumModelMatrixUniforms(shader, region, camera);
            DrawCommandList drawCommandList = commandList.beginTessellating(tessellation);

            try {
                IndirectDrawService indirectDrawService = GraphicsDriver.renderDevice().indirectDrawService();
                if (indirectDrawService.isSupported()) {
                    indirectDrawService.multiDrawElementsIndirectCount(
                            REGION_MESH_STRIDE * index + passOffset,
                            (index * 12L) + (passIndex * 4L),
                            meshCount,
                            20);
                }
            } catch (Throwable var7) {
                if (drawCommandList != null) {
                    try {
                        drawCommandList.close();
                    } catch (Throwable var6) {
                        var7.addSuppressed(var6);
                    }
                }

                throw var7;
            }

            if (drawCommandList != null) {
                drawCommandList.close();
            }
        }
    }

    @Override
    public GlVertexAttributeBinding[] getAttributeBindings() {
        return this.defaultChunkRenderer.getAttributeBindings();
    }

    @Override
    public GlTessellation sodiumTessellation(CommandList commandList, RenderRegion region) {
        return this.defaultChunkRenderer.sodiumTessellation(commandList, region);
    }

    @Override
    public void sodiumModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);
        shader.setRegionOffset(x, y, z);
    }

    @Override
    public void setIndexedPass(boolean indexedPass) {
        this.isIndexedPass = indexedPass;
    }

    @Override
    public SharedQuadIndexBuffer getSharedIndexBuffer() {
        return this.defaultChunkRenderer.getSharedIndexBuffer();
    }

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (float) (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);
        MeshResource.clearRegions();
    }
}

