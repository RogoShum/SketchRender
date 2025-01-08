package rogo.sketchrender.compat.sodium;

import com.mojang.blaze3d.systems.RenderSystem;
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
import org.apache.commons.compress.utils.Lists;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.api.ExtraChunkRenderer;
import rogo.sketchrender.api.TessellationDevice;
import rogo.sketchrender.culling.CullingStateManager;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.ShaderManager;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
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
        boolean update = false;
        if (lastUpdateFrame != MeshUniform.currentFrame) {
            lastUpdateFrame = MeshUniform.currentFrame;
            update = true;
            Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isReverseOrder());
            cachedRegions = Lists.newArrayList(iterator).stream().map(ChunkRenderList::getRegion).filter((r) -> {
                SectionRenderDataStorage storage = r.getStorage(renderPass);
                return storage != null && r.getResources() != null;
            }).sorted(Comparator.comparingInt(MeshUniform.meshManager::indexOf)).toList();
        }

        if (cachedRegions != null) {
            if (update) {
                preRender();
            }
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
            if (maxElementCount < MeshUniform.sectionMaxElement[0]) {
                sharedIndexBuffer.ensureCapacity(commandList, MeshUniform.sectionMaxElement[0]);
                maxElementCount = MeshUniform.sectionMaxElement[0];
            }


            if (cachedRegions != null) {
                shaderInterface.setProjectionMatrix(matrices.projection());
                shaderInterface.setModelViewMatrix(matrices.modelView());
                onRender(this, shaderInterface, commandList, renderPass, camera);
            }
        }
        super.end(renderPass);
    }

    public void preRender() {
        MeshUniform.batchElement.bindShaderSlot(7);
        long ptr = MeshUniform.batchRegionIndex.getMemoryAddress();
        for (int i = 0; i < cachedRegions.size(); ++i) {
            RenderRegion region = cachedRegions.get(i);
            long offset = i * 16L;
            MemoryUtil.memPutInt(ptr + offset, region.getChunkX());
            MemoryUtil.memPutInt(ptr + offset + 4, region.getChunkY());
            MemoryUtil.memPutInt(ptr + offset + 8, region.getChunkZ());
            MemoryUtil.memPutInt(ptr + offset + 12, MeshUniform.meshManager.indexOf(region));
        }
        MeshUniform.batchRegionIndex.position = (int) MeshUniform.batchRegionIndex.getSize();
        MeshUniform.batchRegionIndex.upload();
        MeshUniform.meshManager.bindMeshData(0);
        MeshUniform.batchCommand.bindShaderSlot(1);
        MeshUniform.batchCounter.bindShaderSlot(2);
        MeshUniform.batchRegionIndex.bindShaderSlot(3);
        MeshUniform.cullingCounter.updateCount(0);

        if (!Config.getAutoDisableAsync()) {
            CullingStateManager.runOnDepthFrame((depthContext) -> {
                RenderSystem.activeTexture(GL_TEXTURE0 + depthContext.index());
                RenderSystem.bindTexture(CullingStateManager.DEPTH_TEXTURE[depthContext.index()]);
            });
            RenderSystem.activeTexture(GL_TEXTURE0);
            ShaderManager.CULL_COLLECT_CHUNK_BATCH_CS.bindUniforms();
            ShaderManager.CULL_COLLECT_CHUNK_BATCH_CS.execute(12, cachedRegions.size(), 1);
            ShaderManager.CULL_COLLECT_CHUNK_BATCH_CS.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        } else {
            ShaderManager.COLLECT_CHUNK_BATCH_CS.bindUniforms();
            ShaderManager.COLLECT_CHUNK_BATCH_CS.execute(12, cachedRegions.size(), 1);
            ShaderManager.COLLECT_CHUNK_BATCH_CS.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }

        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
        GL20.glUseProgram(ChunkShaderTracker.lastProgram);
    }

    public static final long FACING_COUNT = 7L;
    public static final long REGION_MESH_STRIDE = 256L * 3L * FACING_COUNT * 20L;
    public static final long LAYER_MESH_STRIDE = 256L * FACING_COUNT * 20L;

    public void onRender(ExtraChunkRenderer renderer, ChunkShaderInterface shader, CommandList commandList, TerrainRenderPass pass, CameraTransform camera) {
        int passIndex = 0;
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
            passIndex = 2;
        } else if (pass == DefaultTerrainRenderPasses.CUTOUT) {
            passIndex = 1;
        }

        long passOffset = LAYER_MESH_STRIDE * passIndex;

        for (int i = 0; i < cachedRegions.size(); ++i) {
            RenderRegion region = cachedRegions.get(i);
            SectionRenderDataStorage storage = region.getStorage(pass);
            if (storage == null) {
                continue;
            }

            IndirectCommandBuffer.INSTANCE.bind();
            MeshUniform.cullingCounter.bind();

            GlTessellation tessellation = renderer.sodiumTessellation(commandList, region);
            renderer.sodiumModelMatrixUniforms(shader, region, camera);
            DrawCommandList drawCommandList = commandList.beginTessellating(tessellation);

            try {
                GlPrimitiveType primitiveType = ((TessellationDevice) GLRenderDevice.INSTANCE).getTessellation().getPrimitiveType();
                GL46C.nglMultiDrawElementsIndirectCount(primitiveType.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), REGION_MESH_STRIDE * i + passOffset, (i * 12L) + (passIndex * 4L), 1792, 20);
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

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);
        MeshUniform.clearRegions();
    }
}
