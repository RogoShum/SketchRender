package rogo.sketch.render.pipeline;

import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.graphics.GraphicsInstance;
import rogo.sketch.event.GraphicsPipelineStageEvent;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.render.pipeline.async.AsyncRenderManager;
import rogo.sketch.render.command.RenderCommand;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.data.filler.VertexFillerManager;
import rogo.sketch.render.pipeline.information.GraphicsInstanceInformation;
import rogo.sketch.render.pipeline.information.InfoCollector;
import rogo.sketch.render.pipeline.information.RenderList;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.resource.buffer.VertexResource;
import rogo.sketch.render.shader.uniform.UniformValueSnapshot;
import rogo.sketch.render.state.gl.ShaderState;
import rogo.sketch.render.vertex.AsyncVertexFiller;
import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.render.vertex.VertexResourcePair;
import rogo.sketch.util.Identifier;

import java.util.*;

public class GraphicsBatchGroup<C extends RenderContext> {
    private final GraphicsPipeline<C> graphicsPipeline;
    private final Identifier stageIdentifier;
    private final Map<RenderSetting, GraphicsBatch<C>> groups = new LinkedHashMap<>();
    private final VertexResourceManager vertexResourceManager = VertexResourceManager.getInstance();
    private final VertexFillerManager vertexFillerManager = VertexFillerManager.getInstance();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();
    private final AsyncVertexFiller asyncVertexFiller = AsyncVertexFiller.getInstance();

    public GraphicsBatchGroup(GraphicsPipeline<C> graphicsPipeline, Identifier stageIdentifier) {
        this.graphicsPipeline = graphicsPipeline;
        this.stageIdentifier = stageIdentifier;
    }

    public void addGraphInstance(GraphicsInstance instance, RenderSetting setting) {
        GraphicsBatch<C> group = groups.computeIfAbsent(setting, s -> new GraphicsBatch<>());
        group.addGraphInstance(instance);
    }

    public void tick(C context) {
        Collection<GraphicsInstance> allInstances = groups.values().stream()
                .flatMap(graphicsBatch -> graphicsBatch.getAllInstances().stream())
                .toList();

        if (asyncManager.shouldUseAsync(allInstances.size())) {
            asyncManager.tickInstancesAsync(allInstances, context).join();
        } else {
            tickSync(context);
        }
    }

    private void tickSync(C context) {
        groups.values().forEach((group) -> {
            group.tick(context);
        });
    }

    public void render(RenderStateManager manager, C context) {
        EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageIdentifier, context, GraphicsPipelineStageEvent.Phase.PRE));
        context.preStage(stageIdentifier);

        renderSharedResources(manager, context);
        renderInstanceResources(manager, context);
        renderCustomResources(manager, context);

        context.postStage(stageIdentifier);
        EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageIdentifier, context, GraphicsPipelineStageEvent.Phase.POST));
    }

    private void renderSharedResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsBatch<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsBatch<C> graphicsBatch = entry.getValue();

            if (setting.renderParameter().isInvalid() || !graphicsBatch.containsShared()) {
                continue;
            }

            ShaderProvider shaderProvider = null;
            ResourceReference<ShaderProvider> reference = ((ShaderState) setting.renderState().get(ResourceTypes.SHADER_PROGRAM)).shader();
            if (reference.isAvailable()) {
                shaderProvider = reference.get();
            }

            if (shaderProvider == null) {
                continue;
            }

            List<UniformBatchGroup> uniformBatches = collectUniformBatches(graphicsBatch, shaderProvider);
            if (setting.shouldSwitchRenderState() && !uniformBatches.isEmpty()) {
                applyRenderSetting(manager, context, setting);
            }
            VertexResource resource = vertexResourceManager.getOrCreateVertexResource(setting.renderParameter());

            for (UniformBatchGroup batch : uniformBatches) {
                renderUniformBatch(batch, resource, setting, graphicsBatch, context);
            }
        }
    }

    private List<UniformBatchGroup> collectUniformBatches(GraphicsBatch<C> graphicsBatch, ShaderProvider shaderProvider) {
        Collection<GraphicsInstance> instances = graphicsBatch.getSharedInstances();

        try {
            return asyncManager.collectUniformsAsync(
                    () -> collectUniformBatchesSync(instances, shaderProvider),
                    instances.size()
            ).join();
        } catch (Exception e) {
            // Fallback to sync if async fails
            return collectUniformBatchesSync(instances, shaderProvider);
        }
    }

    private List<UniformBatchGroup> collectUniformBatchesSync(Collection<GraphicsInstance> instances, ShaderProvider shaderProvider) {
        Map<UniformValueSnapshot, UniformBatchGroup> batches = new HashMap<>();

        for (GraphicsInstance instance : instances) {
            if (instance.shouldRender()) {
                UniformValueSnapshot snapshot = UniformValueSnapshot.captureFrom(
                        shaderProvider.getUniformHookGroup(), instance);

                batches.computeIfAbsent(snapshot, UniformBatchGroup::new).addInstance(instance);
            }
        }

        return new ArrayList<>(batches.values());
    }

    // Note: collectUniformBatchesAsync method removed, using AsyncRenderManager instead

    private List<UniformBatchGroup> collectIndependentUniformBatches(GraphicsBatch<C> graphicsBatch, ShaderProvider shaderProvider) {
        Collection<GraphicsInstance> instances = graphicsBatch.getIndependentInstances();

        try {
            return asyncManager.collectUniformsAsync(
                    () -> collectUniformBatchesSync(instances, shaderProvider),
                    instances.size()
            ).join();
        } catch (Exception e) {
            return collectUniformBatchesSync(instances, shaderProvider);
        }
    }

    private List<UniformBatchGroup> collectCustomUniformBatches(GraphicsBatch<C> graphicsBatch, ShaderProvider shaderProvider) {
        Collection<GraphicsInstance> instances = graphicsBatch.getCustomInstances();

        try {
            return asyncManager.collectUniformsAsync(
                    () -> collectUniformBatchesSync(instances, shaderProvider),
                    instances.size()
            ).join();
        } catch (Exception e) {
            return collectUniformBatchesSync(instances, shaderProvider);
        }
    }

    private void renderUniformBatch(UniformBatchGroup batch, VertexResource resource,
                                    RenderSetting setting, GraphicsBatch<C> graphicsBatch, C context) {
        if (batch.isEmpty()) {
            return;
        }

        ShaderProvider shader = context.shaderProvider();
        batch.getUniformSnapshot().applyTo(shader.getUniformHookGroup());

        VertexFiller filler = vertexFillerManager.getOrCreateVertexFiller(setting.renderParameter());

        boolean hasData = graphicsBatch.fillSharedVertexForBatch(filler, batch.getInstances());

        if (hasData) {
            resource.uploadFromVertexFiller(filler);

            context.set(Identifier.of("rendered"), true);
            VertexRenderer.render(resource);

            for (GraphicsInstance instance : batch.getInstances()) {
                instance.afterDraw(context);
            }

            vertexFillerManager.resetFiller(setting.renderParameter());
        }
    }

    private void renderInstanceResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsBatch<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsBatch<C> graphicsBatch = entry.getValue();

            if (setting.renderParameter().isInvalid() || !graphicsBatch.containsIndependent()) {
                continue;
            }

            ShaderProvider shaderProvider = null;
            ResourceReference<ShaderProvider> reference = ((ShaderState) setting.renderState().get(ResourceTypes.SHADER_PROGRAM)).shader();
            if (reference.isAvailable()) {
                shaderProvider = reference.get();
            }

            if (shaderProvider == null) {
                continue;
            }

            List<UniformBatchGroup> uniformBatches = collectIndependentUniformBatches(graphicsBatch, shaderProvider);
            if (setting.shouldSwitchRenderState() && !uniformBatches.isEmpty()) {
                applyRenderSetting(manager, context, setting);
            }

            for (UniformBatchGroup batch : uniformBatches) {
                renderIndependentUniformBatch(batch, setting, graphicsBatch, context);
            }
        }
    }

    private void renderIndependentUniformBatch(UniformBatchGroup batch, RenderSetting setting,
                                               GraphicsBatch<C> graphicsBatch, C context) {
        if (batch.isEmpty()) {
            return;
        }
        context.set(Identifier.of("rendered"), true);

        ShaderProvider shader = context.shaderProvider();
        batch.getUniformSnapshot().applyTo(shader.getUniformHookGroup());

        List<VertexResourcePair> pairs = graphicsBatch.fillIndependentVertexForBatch(batch.getInstances());

        for (VertexResourcePair pair : pairs) {
            pair.drawCommand().execute(pair.resource());
        }

        for (GraphicsInstance instance : batch.getInstances()) {
            instance.afterDraw(context);
        }
    }

    private void renderCustomResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsBatch<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsBatch<C> graphicsBatch = entry.getValue();

            if (setting.renderParameter().isInvalid() || !graphicsBatch.containsCustom()) {
                continue;
            }

            ShaderProvider shaderProvider = null;
            ResourceReference<ShaderProvider> reference = ((ShaderState) setting.renderState().get(ResourceTypes.SHADER_PROGRAM)).shader();
            if (reference.isAvailable()) {
                shaderProvider = reference.get();
            }

            if (shaderProvider == null) {
                continue;
            }

            List<UniformBatchGroup> uniformBatches = collectCustomUniformBatches(graphicsBatch, shaderProvider);
            if (setting.shouldSwitchRenderState() && !uniformBatches.isEmpty()) {
                applyRenderSetting(manager, context, setting);
            }

            for (UniformBatchGroup batch : uniformBatches) {
                renderCustomUniformBatch(batch, graphicsBatch, context);
            }
        }
    }

    private void renderCustomUniformBatch(UniformBatchGroup uniformBatch, GraphicsBatch<C> graphicsBatch, C context) {
        if (uniformBatch.isEmpty()) {
            return;
        }

        context.set(Identifier.of("rendered"), true);
        ShaderProvider shader = context.shaderProvider();
        uniformBatch.getUniformSnapshot().applyTo(shader.getUniformHookGroup());
        graphicsBatch.executeCustomBatch(uniformBatch.getInstances(), context);
    }

    protected void applyRenderSetting(RenderStateManager manager, C context, RenderSetting setting) {
        manager.accept(setting, context);
        ShaderProvider shader = context.shaderProvider();
        shader.getUniformHookGroup().updateUniforms(context);
    }

    public GraphicsBatch<C> getBatch(RenderSetting setting) {
        return groups.get(setting);
    }

    public Collection<GraphicsBatch<C>> getBatches() {
        return groups.values();
    }

    public void clear() {
        groups.clear();
    }

    // Note: Async configuration methods removed, use AsyncRenderManager.getInstance().getConfig() instead

    public StageStats getStageStats() {
        int totalInstances = 0;
        int totalBatches = groups.size();
        int sharedInstances = 0;
        int independentInstances = 0;
        int customInstances = 0;

        for (GraphicsBatch<C> graphicsBatch : groups.values()) {
            Collection<GraphicsInstance> shared = graphicsBatch.getSharedInstances();
            Collection<GraphicsInstance> independent = graphicsBatch.getIndependentInstances();
            Collection<GraphicsInstance> custom = graphicsBatch.getCustomInstances();

            sharedInstances += shared.size();
            independentInstances += independent.size();
            customInstances += custom.size();
            totalInstances += shared.size() + independent.size() + custom.size();
        }

        return new StageStats(
                stageIdentifier,
                totalBatches,
                totalInstances,
                sharedInstances,
                independentInstances,
                customInstances
        );
    }

    public record StageStats(
            Identifier stageIdentifier,
            int totalBatches,
            int totalInstances,
            int sharedInstances,
            int independentInstances,
            int customInstances
    ) {
        @Override
        public String toString() {
            return String.format(
                    "Stage[%s]: %d batches, %d instances (shared=%d, independent=%d, custom=%d)",
                    stageIdentifier, totalBatches, totalInstances,
                    sharedInstances, independentInstances, customInstances
            );
        }
    }

    /**
     * Cleanup discarded instances and return them to the pool
     */
    public void cleanupDiscardedInstances() {
        for (GraphicsBatch<C> graphicsBatch : groups.values()) {
            graphicsBatch.cleanupDiscardedInstances(poolManager);
        }
    }

    /**
     * Collect render information from all graphics instances in this stage
     * This method provides the RenderSetting from the graphicsBatch group
     */
    public List<GraphicsInstanceInformation> collectRenderData(C context) {
        List<GraphicsInstanceInformation> allData = new ArrayList<>();

        for (Map.Entry<RenderSetting, GraphicsBatch<C>> entry : groups.entrySet()) {
            RenderSetting renderSetting = entry.getKey();
            GraphicsBatch<C> graphicsBatch = entry.getValue();

            // Collect all instances from this graphicsBatch
            Collection<GraphicsInstance> allInstances = graphicsBatch.getAllInstances();

            // Use InfoCollector with the RenderSetting from this graphicsBatch group
            List<GraphicsInstanceInformation> graphicsBatchData = InfoCollector.collectRenderInfo(
                    allInstances, renderSetting, context);
            allData.addAll(graphicsBatchData);
        }

        return allData;
    }

    /**
     * Create render commands from the graphics information in this stage
     */
    public List<RenderCommand> createRenderCommands(C context) {
        try {
            // Collect render data from this stage
            List<GraphicsInstanceInformation> collectedData = collectRenderData(context);

            // Organize into batches
            RenderList renderList = RenderList.organize(collectedData);

            // Fill vertex buffers asynchronously
            var filledResourcesFuture = asyncVertexFiller.fillVertexBuffersAsync(renderList);
            var filledResources = filledResourcesFuture.join();

            // Create render commands
            List<RenderCommand> commands = new ArrayList<>();
            for (AsyncVertexFiller.PreparedVertexResource filledResource : filledResources) {
                AsyncVertexFiller.PreallocatedResources resources = filledResource.getResources();
                if (resources.vertexFiller() != null) {
                    filledResource.getVertexResource().uploadFromVertexFiller(resources.vertexFiller());
                }
                if (resources.staticFiller() != null) {
                    resources.vertexResource().uploadStaticFromVertexFiller(resources.staticFiller());
                }
                if (resources.dynamicFiller() != null) {
                    resources.vertexResource().uploadDynamicFromVertexFiller(resources.dynamicFiller());
                }

                RenderCommand command = RenderCommand.createFromRenderBatch(
                        filledResource.getVertexResource(),
                        filledResource.getRenderBatch(),
                        stageIdentifier
                );
                commands.add(command);
            }

            return commands;
        } catch (Exception e) {
            // Return empty list if something goes wrong
            return new ArrayList<>();
        }
    }
}