package rogo.sketchrender.compat.sodium;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.DrawCommandList;
import me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.tessellation.TessellationBinding;
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
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkShaderInterface;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.ShaderChunkRendererExt;
import net.minecraft.core.BlockPos;
import org.apache.commons.compress.utils.Lists;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.*;
import rogo.sketchrender.culling.ChunkCullingUniform;
import rogo.sketchrender.culling.CullingStateManager;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.ShaderManager;

import java.util.Iterator;
import java.util.List;

import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class ComputeShaderChunkRenderer extends ShaderChunkRenderer implements ExtraChunkRenderer {
    private final SharedQuadIndexBuffer sharedIndexBuffer;
    private final GlVertexAttributeBinding[] vertexAttributeBindings;
    protected int maxElementCount = 0;
    protected Class<?> extraShaderInterface;
    protected boolean checkedShaderInterface = false;
    private int lastUpdateFrame;
    private List<RenderRegion> cachedRegions;

    public ComputeShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);
        this.sharedIndexBuffer = new SharedQuadIndexBuffer(device.createCommandList(), SharedQuadIndexBuffer.IndexType.INTEGER);
        this.vertexAttributeBindings = this.getBindingsForType();
    }

    @Override
    public void render(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera) {
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
            if (maxElementCount < ChunkCullingUniform.sectionMaxElement[0]) {
                sharedIndexBuffer.ensureCapacity(commandList, ChunkCullingUniform.sectionMaxElement[0]);
                maxElementCount = ChunkCullingUniform.sectionMaxElement[0];
            }

            boolean update = false;
            if (lastUpdateFrame != ChunkCullingUniform.currentFrame) {
                lastUpdateFrame = ChunkCullingUniform.currentFrame;
                update = true;
                Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isReverseOrder());
                cachedRegions = Lists.newArrayList(iterator).stream().map(ChunkRenderList::getRegion).filter((r) -> {
                    SectionRenderDataStorage storage = r.getStorage(renderPass);
                    return storage != null && r.getResources() != null;
                }).toList();
            }

            if (cachedRegions != null) {
                if (update) {
                    preRender();
                }

                shaderInterface.setProjectionMatrix(matrices.projection());
                shaderInterface.setModelViewMatrix(matrices.modelView());
                onRender(this, shaderInterface, commandList, renderPass, camera);
            }
        }
        super.end(renderPass);
    }

    public void preRender() {
        ChunkCullingUniform.batchElement.bindShaderSlot(7);

        if (!Config.getAutoDisableAsync()) {
            ShaderManager.COLLECT_CHUNK_PASS_CS.bindUniforms();
            for (RenderRegion region : cachedRegions) {
                ChunkCullingUniform.switchRegion(region.getChunkX(), region.getChunkY(), region.getChunkZ());
                ((RegionData) region).bindMeshData(0);

                for (int j = 0; j < DefaultTerrainRenderPasses.ALL.length; ++j) {
                    TerrainRenderPass pass = DefaultTerrainRenderPasses.ALL[j];
                    SectionRenderDataStorage storage = region.createStorage(pass);
                    SectionData dataStorage = (SectionData) storage;
                    dataStorage.clearCounter();
                    dataStorage.bindIndirectCommand(1 + j * 2);
                    dataStorage.bindCounter(2 + j * 2);
                }

                executeShaderCulling();
            }
            ShaderManager.COLLECT_CHUNK_PASS_CS.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        } else {
            ShaderManager.COLLECT_CHUNK_BATCH_CS.bindUniforms();
            long ptr = ChunkCullingUniform.batchRegionIndex.getMemoryAddress();
            for (int i = 0; i < cachedRegions.size(); ++i) {
                RenderRegion region = cachedRegions.get(i);
                long offset = i * 16L;
                MemoryUtil.memPutInt(ptr + offset, ChunkCullingUniform.addIndexedRegion(region));
                MemoryUtil.memPutInt(ptr + offset + 4, region.getChunkX());
                MemoryUtil.memPutInt(ptr + offset + 8, region.getChunkY());
                MemoryUtil.memPutInt(ptr + offset + 12, region.getChunkZ());
            }
            ChunkCullingUniform.batchRegionIndex.position = ChunkCullingUniform.batchRegionIndex.getSize();
            ChunkCullingUniform.batchRegionIndex.upload();
            ChunkCullingUniform.batchMesh.bindShaderSlot(0);
            ChunkCullingUniform.batchCommand.bindShaderSlot(1);
            ChunkCullingUniform.batchCounter.bindShaderSlot(2);
            ChunkCullingUniform.batchRegionIndex.bindShaderSlot(3);

            ShaderManager.COLLECT_CHUNK_BATCH_CS.execute(256, cachedRegions.size(), 1);
            ShaderManager.COLLECT_CHUNK_BATCH_CS.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }

        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
        GL20.glUseProgram(ChunkShaderTracker.lastProgram);
    }

    public void executeShaderCulling() {
        BlockPos regionPos = new BlockPos(ChunkCullingUniform.getRegionPos());
        ((ExtraUniform) ShaderManager.COLLECT_CHUNK_PASS_CS).getUniforms().setUniform("sketch_region_pos", regionPos);
        ShaderManager.COLLECT_CHUNK_PASS_CS.execute(256, 1, 1);
    }

    public void onRender(ExtraChunkRenderer renderer, ChunkShaderInterface shader, CommandList commandList, TerrainRenderPass pass, CameraTransform camera) {
        boolean async = Config.getAutoDisableAsync();
        int passIndex = 0;
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
            passIndex = 2;
        } else if (pass == DefaultTerrainRenderPasses.CUTOUT) {
            passIndex = 1;
        }
        long passOffset = 256L * 64L * passIndex;
        long regionStride = 256L * 64L * 3;

        for (int i = 0; i < cachedRegions.size(); ++i) {
            RenderRegion region = cachedRegions.get(i);
            if (!async) {
                SectionRenderDataStorage storage = region.getStorage(pass);
                SectionData dataStorage = (SectionData) storage;
                dataStorage.bindCounterBuffer();
                dataStorage.bindCommandBuffer();
            } else {
                IndirectCommandBuffer.INSTANCE.bind();
                ChunkCullingUniform.cullingCounter.bind();
            }
            GlTessellation tessellation = renderer.sodiumTessellation(commandList, region);
            renderer.sodiumModelMatrixUniforms(shader, region, camera);
            DrawCommandList drawCommandList = commandList.beginTessellating(tessellation);

            try {
                GlPrimitiveType primitiveType = ((TessellationDevice) GLRenderDevice.INSTANCE).getTessellation().getPrimitiveType();
                if (!async) {
                    GL46C.nglMultiDrawElementsIndirectCount(primitiveType.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), 0L, 0L, 1792, 20);
                } else {
                    GL46C.nglMultiDrawElementsIndirectCount(primitiveType.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), regionStride * i + passOffset, i * 4L, 1792, 20);
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
        //ChunkRenderMixinHook.asyncReadInt(ChunkCullingUniform.batchElement.getId(), 0, ChunkCullingUniform.sectionMaxElement);
    }

    private GlVertexAttributeBinding[] getBindingsForType() {
        if (this.vertexType == ChunkMeshFormats.COMPACT) {
            return new GlVertexAttributeBinding[]{new GlVertexAttributeBinding(1, this.vertexFormat.getAttribute(ChunkMeshAttribute.POSITION_MATERIAL_MESH)), new GlVertexAttributeBinding(2, this.vertexFormat.getAttribute(ChunkMeshAttribute.COLOR_SHADE)), new GlVertexAttributeBinding(3, this.vertexFormat.getAttribute(ChunkMeshAttribute.BLOCK_TEXTURE)), new GlVertexAttributeBinding(4, this.vertexFormat.getAttribute(ChunkMeshAttribute.LIGHT_TEXTURE))};
        } else if (this.vertexType == ChunkMeshFormats.VANILLA_LIKE) {
            GlVertexFormat<ChunkMeshAttribute> vanillaFormat = this.vertexFormat;
            return new GlVertexAttributeBinding[]{new GlVertexAttributeBinding(1, vanillaFormat.getAttribute(ChunkMeshAttribute.POSITION_MATERIAL_MESH)), new GlVertexAttributeBinding(2, vanillaFormat.getAttribute(ChunkMeshAttribute.COLOR_SHADE)), new GlVertexAttributeBinding(3, vanillaFormat.getAttribute(ChunkMeshAttribute.BLOCK_TEXTURE)), new GlVertexAttributeBinding(4, vanillaFormat.getAttribute(ChunkMeshAttribute.LIGHT_TEXTURE))};
        } else {
            return null;
        }
    }

    private GlTessellation createRegionTessellation(CommandList commandList, RenderRegion.DeviceResources resources) {
        return commandList.createTessellation(GlPrimitiveType.TRIANGLES, new TessellationBinding[]{TessellationBinding.forVertexBuffer(resources.getVertexBuffer(), this.vertexAttributeBindings), TessellationBinding.forElementBuffer(this.sharedIndexBuffer.getBufferObject())});
    }

    @Override
    public GlTessellation sodiumTessellation(CommandList commandList, RenderRegion region) {
        RenderRegion.DeviceResources resources = region.getResources();
        GlTessellation tessellation = resources.getTessellation();
        if (tessellation == null) {
            resources.updateTessellation(commandList, tessellation = this.createRegionTessellation(commandList, resources));
        }

        return tessellation;
    }

    @Override
    public void sodiumModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);
        shader.setRegionOffset(x, y, z);
    }

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (float) (chunkBlockPos - cameraBlockPos) - cameraPos;
    }
}
