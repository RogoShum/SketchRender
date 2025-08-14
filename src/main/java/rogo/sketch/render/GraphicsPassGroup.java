package rogo.sketch.render;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.render.async.AsyncRenderManager;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.render.shader.uniform.UniformValueSnapshot;
import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.render.vertex.VertexResource;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.render.vertex.VertexResourcePair;
import rogo.sketch.util.Identifier;

import java.util.*;

public class GraphicsPassGroup<C extends RenderContext> {
    private final Identifier stageIdentifier;
    private final Map<RenderSetting, GraphicsPass<C>> groups = new LinkedHashMap<>();
    private final VertexResourceManager vertexResourceManager = VertexResourceManager.getInstance();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();
    
    // Note: AsyncRenderExecutor is deprecated, using AsyncRenderManager instead

    public GraphicsPassGroup(Identifier stageIdentifier) {
        this.stageIdentifier = stageIdentifier;
    }



    public void addGraphInstance(GraphicsInstance instance, RenderSetting setting) {
        GraphicsPass<C> group = groups.computeIfAbsent(setting, s -> new GraphicsPass<>());
        group.addGraphInstance(instance);
    }

    public void tick(C context) {
        // Use the global async manager to determine execution mode
        Collection<GraphicsInstance> allInstances = groups.values().stream()
            .flatMap(pass -> pass.getAllInstances().stream())
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

    // Note: tickAsync method removed, using AsyncRenderManager directly in tick() method

    public void render(RenderStateManager manager, C context) {
        context.preStage(stageIdentifier);

        renderSharedResources(manager, context);
        renderInstanceResources(manager, context);
        renderCustomResources(manager, context);

        context.postStage(stageIdentifier);
    }

    private void renderSharedResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsPass<C> pass = entry.getValue();

            if (setting.renderParameter() == RenderParameter.EMPTY || !pass.containsShared()) {
                continue;
            }

            applyRenderSetting(manager, context, setting);
            VertexResource resource = vertexResourceManager.getOrCreateVertexResource(setting);

            List<UniformBatchGroup> uniformBatches = collectUniformBatches(pass, context);

            for (UniformBatchGroup batch : uniformBatches) {
                renderUniformBatch(batch, resource, setting, pass, context);
            }
        }
    }

    private List<UniformBatchGroup> collectUniformBatches(GraphicsPass<C> pass, C context) {
        Collection<GraphicsInstance> instances = pass.getSharedInstances();

        try {
            return asyncManager.collectUniformsAsync(
                () -> collectUniformBatchesSync(instances, context),
                instances.size()
            ).join();
        } catch (Exception e) {
            // Fallback to sync if async fails
            return collectUniformBatchesSync(instances, context);
        }
    }

    private List<UniformBatchGroup> collectUniformBatchesSync(Collection<GraphicsInstance> instances, C context) {
        Map<UniformValueSnapshot, UniformBatchGroup> batches = new HashMap<>();
        ShaderProvider shader = context.shaderProvider();

        for (GraphicsInstance instance : instances) {
            if (instance.shouldRender()) {
                UniformValueSnapshot snapshot = UniformValueSnapshot.captureFrom(
                        shader.getUniformHookGroup(), instance);

                batches.computeIfAbsent(snapshot, UniformBatchGroup::new).addInstance(instance);
            }
        }

        return new ArrayList<>(batches.values());
    }

    // Note: collectUniformBatchesAsync method removed, using AsyncRenderManager instead

    private List<UniformBatchGroup> collectIndependentUniformBatches(GraphicsPass<C> pass, C context) {
        Collection<GraphicsInstance> instances = pass.getIndependentInstances();

        try {
            return asyncManager.collectUniformsAsync(
                () -> collectUniformBatchesSync(instances, context),
                instances.size()
            ).join();
        } catch (Exception e) {
            return collectUniformBatchesSync(instances, context);
        }
    }

    private List<UniformBatchGroup> collectCustomUniformBatches(GraphicsPass<C> pass, C context) {
        Collection<GraphicsInstance> instances = pass.getCustomInstances();

        try {
            return asyncManager.collectUniformsAsync(
                () -> collectUniformBatchesSync(instances, context),
                instances.size()
            ).join();
        } catch (Exception e) {
            return collectUniformBatchesSync(instances, context);
        }
    }

    private void renderUniformBatch(UniformBatchGroup batch, VertexResource resource,
                                    RenderSetting setting, GraphicsPass<C> pass, C context) {
        if (batch.isEmpty()) {
            return;
        }

        ShaderProvider shader = context.shaderProvider();
        batch.getUniformSnapshot().applyTo(shader.getUniformHookGroup());

        VertexFiller filler = resource.beginFill();

        if (setting.renderParameter().enableIndexBuffer()) {
            filler.enableIndexBuffer();
        }
        if (setting.renderParameter().enableSorting()) {
            filler.enableSorting();
        }

        boolean hasData = pass.fillSharedVertexForBatch(filler, batch.getInstances());

        if (hasData) {
            resource.endFill();
            VertexRenderer.render(resource);
            for (GraphicsInstance instance : batch.getInstances()) {
                instance.endDraw();
            }
        }
    }

    private void renderInstanceResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsPass<C> pass = entry.getValue();

            if (setting.renderParameter() == RenderParameter.EMPTY || !pass.containsIndependent()) {
                continue;
            }

            applyRenderSetting(manager, context, setting);
            List<UniformBatchGroup> uniformBatches = collectIndependentUniformBatches(pass, context);

            for (UniformBatchGroup batch : uniformBatches) {
                renderIndependentUniformBatch(batch, setting, pass, context);
            }
        }
    }

    private void renderIndependentUniformBatch(UniformBatchGroup batch, RenderSetting setting,
                                               GraphicsPass<C> pass, C context) {
        if (batch.isEmpty()) {
            return;
        }

        ShaderProvider shader = context.shaderProvider();
        batch.getUniformSnapshot().applyTo(shader.getUniformHookGroup());

        List<VertexResourcePair> pairs = pass.fillIndependentVertexForBatch(batch.getInstances());

        for (VertexResourcePair pair : pairs) {
            pair.drawCommand().execute(pair.resource());
        }

        for (GraphicsInstance instance : batch.getInstances()) {
            instance.endDraw();
        }
    }

    private void renderCustomResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsPass<C> pass = entry.getValue();

            if (setting.renderParameter() == RenderParameter.EMPTY || !pass.containsCustom()) {
                continue;
            }

            if (setting.shouldSwitchRenderState()) {
                applyRenderSetting(manager, context, setting);
            }

            List<UniformBatchGroup> uniformBatches = collectCustomUniformBatches(pass, context);

            for (UniformBatchGroup batch : uniformBatches) {
                renderCustomUniformBatch(batch, setting, pass, context);
            }
        }
    }

    private void renderCustomUniformBatch(UniformBatchGroup batch, RenderSetting setting,
                                          GraphicsPass<C> pass, C context) {
        if (batch.isEmpty()) {
            return;
        }

        ShaderProvider shader = context.shaderProvider();
        batch.getUniformSnapshot().applyTo(shader.getUniformHookGroup());
        pass.executeCustomBatch(batch.getInstances(), context);
    }

    protected void applyRenderSetting(RenderStateManager manager, C context, RenderSetting setting) {
        manager.accept(setting, context);
        ShaderProvider shader = context.shaderProvider();
        shader.getUniformHookGroup().updateUniforms(context);
    }

    public GraphicsPass<C> getPass(RenderSetting setting) {
        return groups.get(setting);
    }

    public Collection<GraphicsPass<C>> getPasses() {
        return groups.values();
    }

    public void clear() {
        groups.clear();
    }

    // Note: Async configuration methods removed, use AsyncRenderManager.getInstance().getConfig() instead

    public StageStats getStageStats() {
        int totalInstances = 0;
        int totalPasses = groups.size();
        int sharedInstances = 0;
        int independentInstances = 0;
        int customInstances = 0;

        for (GraphicsPass<C> pass : groups.values()) {
            Collection<GraphicsInstance> shared = pass.getSharedInstances();
            Collection<GraphicsInstance> independent = pass.getIndependentInstances();
            Collection<GraphicsInstance> custom = pass.getCustomInstances();

            sharedInstances += shared.size();
            independentInstances += independent.size();
            customInstances += custom.size();
            totalInstances += shared.size() + independent.size() + custom.size();
        }

        return new StageStats(
                stageIdentifier,
                totalPasses,
                totalInstances,
                sharedInstances,
                independentInstances,
                customInstances
        );
    }

    public record StageStats(
            Identifier stageIdentifier,
            int totalPasses,
            int totalInstances,
            int sharedInstances,
            int independentInstances,
            int customInstances
    ) {
        @Override
        public String toString() {
            return String.format(
                    "Stage[%s]: %d passes, %d instances (shared=%d, independent=%d, custom=%d)",
                    stageIdentifier, totalPasses, totalInstances,
                    sharedInstances, independentInstances, customInstances
            );
        }
    }
    
    /**
     * Cleanup discarded instances and return them to the pool
     */
    public void cleanupDiscardedInstances() {
        for (GraphicsPass<C> pass : groups.values()) {
            pass.cleanupDiscardedInstances(poolManager);
        }
    }
}