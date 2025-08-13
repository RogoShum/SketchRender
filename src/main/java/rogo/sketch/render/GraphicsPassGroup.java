package rogo.sketch.render;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.render.async.AsyncRenderExecutor;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.uniform.UniformValueSnapshot;
import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.render.vertex.VertexResource;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.render.vertex.VertexResourcePair;
import rogo.sketch.util.Identifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GraphicsPassGroup<C extends RenderContext> {
    private final Identifier stageIdentifier;
    private final Map<RenderSetting, GraphicsPass<C>> groups = new LinkedHashMap<>();
    private final VertexResourceManager vertexResourceManager = VertexResourceManager.getInstance();
    
    // Lazy initialization to avoid initialization order issues
    private AsyncRenderExecutor asyncExecutor;
    private boolean enableAsyncTick = true;
    private boolean enableAsyncUniformCollection = true;
    private int asyncThreshold = 32;

    public GraphicsPassGroup(Identifier stageIdentifier) {
        this.stageIdentifier = stageIdentifier;
    }

    /**
     * Get AsyncRenderExecutor with lazy initialization
     */
    private AsyncRenderExecutor getAsyncExecutor() {
        if (asyncExecutor == null) {
            asyncExecutor = AsyncRenderExecutor.getInstance();
        }
        return asyncExecutor;
    }

    public void addGraphInstance(GraphicsInstance instance, RenderSetting setting) {
        GraphicsPass<C> group = groups.computeIfAbsent(setting, s -> new GraphicsPass<>());
        group.addGraphInstance(instance);
    }

    public void tick(C context) {
        if (enableAsyncTick) {
            tickAsync(context);
        } else {
            tickSync(context);
        }
    }

    private void tickSync(C context) {
        groups.values().forEach((group) -> {
            group.tick(context);
        });
    }

    private void tickAsync(C context) {
        List<CompletableFuture<Void>> tickTasks = new ArrayList<>();

        for (GraphicsPass<C> pass : groups.values()) {
            Collection<GraphicsInstance> allInstances = pass.getAllInstances();

            if (getAsyncExecutor().shouldUseAsync(allInstances.size())) {
                CompletableFuture<Void> task = getAsyncExecutor().tickInstancesAsync(allInstances, context);
                tickTasks.add(task);
            } else {
                pass.tick(context);
            }
        }

        if (!tickTasks.isEmpty()) {
            CompletableFuture.allOf(tickTasks.toArray(new CompletableFuture[0])).join();
        }
    }

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

        if (enableAsyncUniformCollection && getAsyncExecutor().shouldUseAsync(instances.size())) {
            return collectUniformBatchesAsync(instances, context);
        } else {
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

    private List<UniformBatchGroup> collectUniformBatchesAsync(Collection<GraphicsInstance> instances, C context) {
        ShaderProvider shader = context.shaderProvider();

        CompletableFuture<List<AsyncRenderExecutor.UniformCollectionResult>> futureResults =
                getAsyncExecutor().collectUniformsAsync(instances, instance ->
                        UniformValueSnapshot.captureFrom(shader.getUniformHookGroup(), instance));

        List<AsyncRenderExecutor.UniformCollectionResult> results = futureResults.join();
        Map<UniformValueSnapshot, UniformBatchGroup> batches = new HashMap<>();

        for (AsyncRenderExecutor.UniformCollectionResult result : results) {
            if (result.isSuccess()) {
                batches.computeIfAbsent(result.snapshot(), UniformBatchGroup::new)
                        .addInstance(result.instance());
            } else {
                System.err.println("Failed to collect uniform for instance " +
                        result.instance().getIdentifier() + ": " + result.error().getMessage());
            }
        }

        return new ArrayList<>(batches.values());
    }

    private List<UniformBatchGroup> collectIndependentUniformBatches(GraphicsPass<C> pass, C context) {
        Collection<GraphicsInstance> instances = pass.getIndependentInstances();

        if (enableAsyncUniformCollection && getAsyncExecutor().shouldUseAsync(instances.size())) {
            return collectUniformBatchesAsync(instances, context);
        } else {
            return collectUniformBatchesSync(instances, context);
        }
    }

    private List<UniformBatchGroup> collectCustomUniformBatches(GraphicsPass<C> pass, C context) {
        Collection<GraphicsInstance> instances = pass.getCustomInstances();

        if (enableAsyncUniformCollection && getAsyncExecutor().shouldUseAsync(instances.size())) {
            return collectUniformBatchesAsync(instances, context);
        } else {
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

    public void setAsyncTickEnabled(boolean enabled) {
        this.enableAsyncTick = enabled;
    }

    public void setAsyncUniformCollectionEnabled(boolean enabled) {
        this.enableAsyncUniformCollection = enabled;
    }

    public void setAsyncThreshold(int threshold) {
        this.asyncThreshold = threshold;
    }

    public boolean isAsyncTickEnabled() {
        return enableAsyncTick;
    }

    public boolean isAsyncUniformCollectionEnabled() {
        return enableAsyncUniformCollection;
    }

    public int getAsyncThreshold() {
        return asyncThreshold;
    }

    public AsyncRenderExecutor.AsyncPerformanceStats getAsyncPerformanceStats() {
        return getAsyncExecutor().getPerformanceStats();
    }

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
}