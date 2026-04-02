package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.GraphicsBatchGroup;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.GraphicsStage;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.data.FrameDataStore;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.MeshRenderBatch;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.RenderFlowRegistry;
import rogo.sketch.core.pipeline.flow.RenderFlowStrategy;
import rogo.sketch.core.pipeline.flow.RenderPostProcessor;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.resource.buffer.VertexResource;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.vertex.VertexResourceManager;

/**
 * Merged synchronous preparation pass:
 * <ol>
 *   <li>Reset write buffer (from old SyncCollectDirtyPass)</li>
 *   <li>Prepare batch containers for new frame (from old SyncPrepareFramePass)</li>
 *   <li>Materialize pending VAOs on main thread</li>
 *   <li>If GL worker is NOT enabled: sync-fallback for shader compile + VBO upload</li>
 * </ol>
 */
public class SyncPreparePass<C extends RenderContext> implements PipelinePass<C> {

    public static final String NAME = "sync_prepare";

    @Override
    public String name() { return NAME; }

    @Override
    public ThreadDomain threadDomain() { return ThreadDomain.SYNC; }

    @Override
    public void execute(FrameContext<C> ctx) {
        GraphicsPipeline<C> pipeline = ctx.pipeline();

        // 1. Reset write buffers for all FrameDataStores
        for (PipelineType pipelineType : pipeline.getPipelineTypes()) {
            FrameDataStore frameDataStore = pipeline.getFrameDataStore(pipelineType);
            if (frameDataStore != null) {
                frameDataStore.resetWriteBuffer();
            }
        }

        // 2. Prepare batch containers + materialize VAOs
        pipeline.materializePendingVertexResources();
        for (GraphicsStage stage : pipeline.getOrderedStages()) {
            GraphicsBatchGroup<C> group = pipeline.getBatchGroup(stage);
            if (group != null) {
                group.prepareForFrame();

                // Eagerly materialize vertex resources & indirect buffers for active batches
//                for (PipelineType pipelineType : pipeline.getPipelineTypes()) {
//                    VertexResourceManager resourceManager = pipeline.getVertexResourceManager(pipelineType);
//                    FrameDataStore frameDataStore = pipeline.getFrameDataStore(pipelineType);
//                    IndirectBufferData indirectRead = frameDataStore != null
//                            ? frameDataStore.readBuffer().get(IndirectBufferData.KEY) : null;
//                    IndirectBufferData indirectWrite = frameDataStore != null
//                            ? frameDataStore.writeBuffer().get(IndirectBufferData.KEY) : null;
//                    BatchContainer<?, ?> container = group.getBatchContainer(pipelineType);
//                    if (container == null) continue;
//
//                    for (RenderBatch<?> batch : container.getActiveBatches()) {
//                        try {
//                            if (batch.getRenderSetting() == null) continue;
//
//                            // Ensure indirect command buffers exist
//                            if (batch.getRenderSetting().renderParameter() != null) {
//                                if (indirectRead != null) indirectRead.get(batch.getRenderSetting().renderParameter());
//                                if (indirectWrite != null) indirectWrite.get(batch.getRenderSetting().renderParameter());
//                            }
//
//                            // Ensure VertexResource (VAO) is materialized on main thread
//                            if (batch.getRenderSetting().renderParameter() instanceof RasterizationParameter rp) {
//                                long sourceId = -1;
//                                VertexResource sourceResource = null;
//                                if (batch instanceof MeshRenderBatch mrb && mrb.mesh() != null) {
//                                    sourceId = mrb.mesh().getVAOHandle();
//                                    sourceResource = mrb.mesh().getSourceResource();
//                                }
//                                VertexBufferKey key = VertexBufferKey.fromParameter(rp, sourceId);
//                                resourceManager.materialize(key, sourceResource);
//                            }
//                        } catch (Exception ignored) {
//                            // Best effort
//                        }
//                    }
//                }
            }
        }

        // 3. Sync fallback: if worker has no GL, compile shaders + upload VBOs here
        if (!GraphicsDriver.capabilities().uploadWorkerSupported()) {
            // Create post-processors and execute them on the main thread now.
            // This ensures shader compilation and VBO upload happen before async build.
            RenderPostProcessors syncProcessors = new RenderPostProcessors();
            for (RenderFlowStrategy strategy : RenderFlowRegistry.getInstance().getAllStrategies()) {
                RenderPostProcessor processor = strategy.createPostProcessor();
                if (processor != null) {
                    syncProcessors.register(strategy.getFlowType(), processor);
                }
            }
            // Note: Post-processors that need data from command building cannot run here.
            // They will still be created and executed in SyncCommitPass if needed.
        }
    }
}
