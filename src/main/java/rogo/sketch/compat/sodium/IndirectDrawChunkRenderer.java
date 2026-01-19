package rogo.sketch.compat.sodium;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.DrawCommandList;
import me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
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
import org.lwjgl.opengl.ARBIndirectParameters;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.SketchRender;
import rogo.sketch.compat.sodium.api.ExtraChunkRenderer;
import rogo.sketch.compat.sodium.api.TessellationDevice;
import rogo.sketch.feature.culling.graphics.ComputeChunkCullingGraphics;
import rogo.sketch.render.pipeline.PartialRenderSetting;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.util.GLFeatureChecker;
import rogo.sketch.util.KeyId;
import rogo.sketch.vanilla.PipelineUtil;
import rogo.sketch.feature.culling.graphics.CopyCounterGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IndirectDrawChunkRenderer extends ShaderChunkRenderer implements ExtraChunkRenderer {
    protected int maxElementCount = 0;
    private boolean isIndexedPass;
    protected Class<?> extraShaderInterface;
    protected boolean checkedShaderInterface = false;
    private int lastUpdateFrame = 0;
    private List<RenderRegion> orderedRegions;
    private final ExtraChunkRenderer defaultChunkRenderer;
    private final ComputeChunkCullingGraphics chunkCullingGraphics = new ComputeChunkCullingGraphics(KeyId.of(SketchRender.MOD_ID, "culling_chunk"));
    private final ResourceReference<PartialRenderSetting> cullingChunkSetting = GraphicsResourceManager.getInstance().getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of(SketchRender.MOD_ID, "cull_chunk"));
    private final CopyCounterGraphics copyCounterGraphics = new CopyCounterGraphics(KeyId.of(SketchRender.MOD_ID, "copy_counter"));
    private final ResourceReference<PartialRenderSetting> copyCounterSetting = GraphicsResourceManager.getInstance().getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of(SketchRender.MOD_ID, "copy_counter"));

    public IndirectDrawChunkRenderer(RenderDevice device, ChunkVertexType vertexType, ExtraChunkRenderer renderer) {
        super(device, vertexType);
        defaultChunkRenderer = renderer;
    }

    @Override
    public void render(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera) {
        if (lastUpdateFrame != MeshResource.CURRENT_FRAME) {
            lastUpdateFrame = MeshResource.CURRENT_FRAME;

            List<RenderRegion> allRegions = new ArrayList<>();

            Iterator<ChunkRenderList> iterator = renderLists.iterator(false);
            while (iterator.hasNext()) {
                RenderRegion region = iterator.next().getRegion();
                boolean exists = false;

                for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
                    SectionRenderDataStorage storage = region.getStorage(pass);
                    if (storage != null && region.getResources() != null) {
                        exists = true;
                    }
                }

                if (exists) {
                    allRegions.add(region);
                }
            }

            orderedRegions = allRegions;

            long ptr = MeshResource.REGION_INDEX_BUFFER.getMemoryAddress();

            List<RenderRegion> regions = orderedRegions;
            for (int i = 0; i < regions.size(); ++i) {
                RenderRegion region = regions.get(i);
                long offset = i * 16L;
                MemoryUtil.memPutInt(ptr + offset, region.getChunkX());
                MemoryUtil.memPutInt(ptr + offset + 4, region.getChunkY());
                MemoryUtil.memPutInt(ptr + offset + 8, region.getChunkZ());
                MemoryUtil.memPutInt(ptr + offset + 12, MeshResource.MESH_MANAGER.indexOf(region));
            }
            MeshResource.REGION_INDEX_BUFFER.position = MeshResource.REGION_INDEX_BUFFER.getCapacity();
            MeshResource.REGION_INDEX_BUFFER.upload();
        }

        if (renderPass == DefaultTerrainRenderPasses.SOLID && orderedRegions != null && !orderedRegions.isEmpty()) {
            preRender();
        }

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
            int maxElement = MeshResource.PERSISTENT_MAX_ELEMENT_BUFFER.getInt(0);
            if (maxElementCount < maxElement && !this.isIndexedPass) {
                getSharedIndexBuffer().ensureCapacity(commandList, maxElement);
                maxElementCount = maxElement;
            }

            if (orderedRegions != null && !orderedRegions.isEmpty()) {
                shaderInterface.setProjectionMatrix(matrices.projection());
                shaderInterface.setModelViewMatrix(matrices.modelView());
                onRender(defaultChunkRenderer, shaderInterface, commandList, renderPass, camera);
            }
        }
        super.end(renderPass);
    }

    public void preRender() {
        MeshResource.ELEMENT_COUNTER.updateCount(0);
        MeshResource.CULLING_COUNTER.updateCount(0);
        MeshResource.ORDERED_REGION_SIZE = orderedRegions.size();

        if (cullingChunkSetting.isAvailable()) {
            RenderSetting setting = RenderSetting.computeShader(cullingChunkSetting.get());
            PipelineUtil.renderHelper().renderInstanceImmediately(chunkCullingGraphics, setting);
        }

        if (copyCounterSetting.isAvailable()) {
            RenderSetting setting = RenderSetting.computeShader(copyCounterSetting.get());
            PipelineUtil.renderHelper().renderInstanceImmediately(copyCounterGraphics, setting);
        }

        GL20.glUseProgram(ChunkShaderTracker.lastProgram);
    }

    public static final long FACING_COUNT = 7L;
    public static final long REGION_MESH_STRIDE = 256L * 3L * FACING_COUNT * 20L;
    public static final long LAYER_MESH_STRIDE = 256L * FACING_COUNT * 20L;

    public void onRender(ExtraChunkRenderer renderer, ChunkShaderInterface shader, CommandList commandList, TerrainRenderPass pass, CameraTransform camera) {
        renderer.setIndexedPass(this.isIndexedPass);

        int passIndex = 0;
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
            passIndex = 2;
        } else if (pass == DefaultTerrainRenderPasses.CUTOUT) {
            passIndex = 1;
        }

        long passOffset = LAYER_MESH_STRIDE * passIndex;

        List<RenderRegion> regions = orderedRegions;
        if (!regions.isEmpty()) {
            MeshResource.CHUNK_COMMAND.bind();
            MeshResource.CULLING_COUNTER.bind();
        }

        for (int i = 0; i < regions.size(); ++i) {
            int index = pass.isReverseOrder() ? (regions.size() - 1 - i) : i;
            RenderRegion region = regions.get(index);
            SectionRenderDataStorage storage = region.getStorage(pass);
            if (!(storage instanceof RegionMeshDataStorage regionMeshDataStorage)) {
                continue;
            }

            int meshCount = regionMeshDataStorage.getTotalFacingCount();
            GlTessellation tessellation = renderer.sodiumTessellation(commandList, region);
            renderer.sodiumModelMatrixUniforms(shader, region, camera);
            DrawCommandList drawCommandList = commandList.beginTessellating(tessellation);

            try {
                GlPrimitiveType primitiveType = ((TessellationDevice) GLRenderDevice.INSTANCE).getTessellation().getPrimitiveType();
                if (GLFeatureChecker.supportsIndirectDrawCount46()) {
                    GL46C.nglMultiDrawElementsIndirectCount(primitiveType.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), REGION_MESH_STRIDE * index + passOffset, (index * 12L) + (passIndex * 4L), meshCount, 20);
                } else if (GLFeatureChecker.supportsIndirectDrawCountARB()) {
                    ARBIndirectParameters.nglMultiDrawElementsIndirectCountARB(primitiveType.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), REGION_MESH_STRIDE * index + passOffset, (index * 12L) + (passIndex * 4L), meshCount, 20);
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