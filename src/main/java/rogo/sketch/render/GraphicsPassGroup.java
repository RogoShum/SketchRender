package rogo.sketch.render;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.event.GraphicsPipelineStageEvent;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.render.async.AsyncRenderManager;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.render.resource.ResourceReference;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.render.shader.uniform.UniformValueSnapshot;
import rogo.sketch.render.state.gl.ShaderState;
import rogo.sketch.render.vertex.VertexRenderer;
import rogo.sketch.render.vertex.VertexResource;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.render.vertex.VertexResourcePair;
import rogo.sketch.util.Identifier;

import java.util.*;

public class GraphicsPassGroup<C extends RenderContext> {
    private final GraphicsPipeline<C> graphicsPipeline;
    private final Identifier stageIdentifier;
    private final Map<RenderSetting, GraphicsPass<C>> groups = new LinkedHashMap<>();
    private final VertexResourceManager vertexResourceManager = VertexResourceManager.getInstance();
    private final InstancePoolManager poolManager = InstancePoolManager.getInstance();
    private final AsyncRenderManager asyncManager = AsyncRenderManager.getInstance();
    
    // Enhanced features for RenderSetting reloading
    private final Map<RenderSetting, RenderSetting> settingMappings = new LinkedHashMap<>();

    // Note: AsyncRenderExecutor is deprecated, using AsyncRenderManager instead

    public GraphicsPassGroup(GraphicsPipeline<C> graphicsPipeline, Identifier stageIdentifier) {
        this.graphicsPipeline = graphicsPipeline;
        this.stageIdentifier = stageIdentifier;
    }

    public void addGraphInstance(GraphicsInstance instance, RenderSetting setting) {
        // Register reload listener if the setting is reloadable
        if (setting.isReloadable()) {
            setting.addUpdateListener(this::onRenderSettingUpdate);
            settingMappings.put(setting, setting);
        }
        
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
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsPass<C> pass = entry.getValue();

            if (setting.renderParameter() == RenderParameter.EMPTY || !pass.containsShared()) {
                continue;
            }

            VertexResource resource = vertexResourceManager.getOrCreateVertexResource(setting);

            ShaderProvider shaderProvider = null;
            ResourceReference<ShaderProvider> reference = ((ShaderState) setting.renderState().get(ResourceTypes.SHADER_PROGRAM)).shader();
            if (reference.isAvailable()) {
                shaderProvider = reference.get();
            }

            if (shaderProvider == null) {
                continue;
            }

            List<UniformBatchGroup> uniformBatches = collectUniformBatches(pass, shaderProvider);
            if (setting.shouldSwitchRenderState() && !uniformBatches.isEmpty()) {
                applyRenderSetting(manager, context, setting);
            }

            for (UniformBatchGroup batch : uniformBatches) {
                renderUniformBatch(batch, resource, setting, pass, context);
            }
        }
    }

    private List<UniformBatchGroup> collectUniformBatches(GraphicsPass<C> pass, ShaderProvider shaderProvider) {
        Collection<GraphicsInstance> instances = pass.getSharedInstances();

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

    private List<UniformBatchGroup> collectIndependentUniformBatches(GraphicsPass<C> pass, ShaderProvider shaderProvider) {
        Collection<GraphicsInstance> instances = pass.getIndependentInstances();

        try {
            return asyncManager.collectUniformsAsync(
                    () -> collectUniformBatchesSync(instances, shaderProvider),
                    instances.size()
            ).join();
        } catch (Exception e) {
            return collectUniformBatchesSync(instances, shaderProvider);
        }
    }

    private List<UniformBatchGroup> collectCustomUniformBatches(GraphicsPass<C> pass, ShaderProvider shaderProvider) {
        Collection<GraphicsInstance> instances = pass.getCustomInstances();

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
            context.set(Identifier.of("rendered"), true);
            resource.endFill();
            VertexRenderer.render(resource);
            for (GraphicsInstance instance : batch.getInstances()) {
                instance.afterDraw(context);
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

            ShaderProvider shaderProvider = null;
            ResourceReference<ShaderProvider> reference = ((ShaderState) setting.renderState().get(ResourceTypes.SHADER_PROGRAM)).shader();
            if (reference.isAvailable()) {
                shaderProvider = reference.get();
            }

            if (shaderProvider == null) {
                continue;
            }

            List<UniformBatchGroup> uniformBatches = collectIndependentUniformBatches(pass, shaderProvider);
            if (setting.shouldSwitchRenderState() && !uniformBatches.isEmpty()) {
                applyRenderSetting(manager, context, setting);
            }

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
        context.set(Identifier.of("rendered"), true);

        ShaderProvider shader = context.shaderProvider();
        batch.getUniformSnapshot().applyTo(shader.getUniformHookGroup());

        List<VertexResourcePair> pairs = pass.fillIndependentVertexForBatch(batch.getInstances());

        for (VertexResourcePair pair : pairs) {
            pair.drawCommand().execute(pair.resource());
        }

        for (GraphicsInstance instance : batch.getInstances()) {
            instance.afterDraw(context);
        }
    }

    private void renderCustomResources(RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, GraphicsPass<C>> entry : groups.entrySet()) {
            RenderSetting setting = entry.getKey();
            GraphicsPass<C> pass = entry.getValue();

            if (setting.renderParameter() == RenderParameter.EMPTY || !pass.containsCustom()) {
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

            List<UniformBatchGroup> uniformBatches = collectCustomUniformBatches(pass, shaderProvider);
            if (setting.shouldSwitchRenderState() && !uniformBatches.isEmpty()) {
                applyRenderSetting(manager, context, setting);
            }

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

        context.set(Identifier.of("rendered"), true);
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
    
    /**
     * Handle RenderSetting update - migrate instances to new setting while preserving batching
     */
    private void onRenderSettingUpdate(RenderSetting newSetting) {
        // Find the old setting that was updated
        RenderSetting oldSetting = null;
        for (Map.Entry<RenderSetting, RenderSetting> entry : settingMappings.entrySet()) {
            if (entry.getValue().getSourcePartialSetting() != null && 
                newSetting.getSourcePartialSetting() != null &&
                Objects.equals(entry.getValue().getSourcePartialSetting().getSourceIdentifier(),
                              newSetting.getSourcePartialSetting().getSourceIdentifier())) {
                oldSetting = entry.getKey();
                break;
            }
        }
        
        if (oldSetting != null) {
            // Get the pass with all instances
            GraphicsPass<C> oldPass = groups.get(oldSetting);
            if (oldPass != null) {
                // Remove old setting and pass
                groups.remove(oldSetting);
                settingMappings.remove(oldSetting);
                
                // Create new pass with updated setting
                GraphicsPass<C> newPass = new GraphicsPass<>();
                
                // Migrate all instances to new pass
                Collection<GraphicsInstance> allInstances = oldPass.getAllInstances();
                for (GraphicsInstance instance : allInstances) {
                    newPass.addGraphInstance(instance);
                }
                
                // Register new setting
                groups.put(newSetting, newPass);
                settingMappings.put(newSetting, newSetting);
                
                // Setup reload listener for new setting
                if (newSetting.isReloadable()) {
                    newSetting.addUpdateListener(this::onRenderSettingUpdate);
                }
                
                System.out.println("RenderSetting updated in stage " + stageIdentifier + 
                                 ", migrated " + allInstances.size() + " instances");
            }
        }
    }
    
    /**
     * Force reload all reloadable render settings
     */
    public void forceReloadRenderSettings() {
        for (RenderSetting setting : new ArrayList<>(settingMappings.keySet())) {
            if (setting.isReloadable()) {
                setting.forceReload();
            }
        }
    }
    
    /**
     * Get statistics about reloadable settings
     */
    public ReloadableSettingsStats getReloadableStats() {
        int totalSettings = groups.size();
        int reloadableSettings = (int) settingMappings.keySet().stream()
                .filter(RenderSetting::isReloadable)
                .count();
        
        return new ReloadableSettingsStats(stageIdentifier, totalSettings, reloadableSettings);
    }
    
    public record ReloadableSettingsStats(
            Identifier stageIdentifier,
            int totalSettings,
            int reloadableSettings
    ) {
        @Override
        public String toString() {
            return String.format("Stage[%s]: %d total settings, %d reloadable",
                    stageIdentifier, totalSettings, reloadableSettings);
        }
    }
}