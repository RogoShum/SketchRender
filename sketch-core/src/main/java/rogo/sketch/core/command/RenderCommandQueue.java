package rogo.sketch.core.command;

import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.api.ShaderProvider;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.driver.GraphicsAPI;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.event.GraphicsPipelineStageEvent;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.core.pipeline.*;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.state.gl.ShaderState;
import rogo.sketch.core.state.snapshot.GLStateSnapshot;
import rogo.sketch.core.state.snapshot.SnapshotScope;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Queue of render commands organized by vertex resource for efficient batch
 * rendering
 */
public class RenderCommandQueue<C extends RenderContext> {
    private final GraphicsPipeline<C> graphicsPipeline;
    // Pipeline type -> RenderSetting -> List<RenderCommand>
    private final Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> commandsByPipeline;
    // Pipeline type -> StageId -> RenderSetting -> List<RenderCommand>
    private final Map<PipelineType, Map<KeyId, Map<RenderSetting, List<RenderCommand>>>> commandsByPipelineAndStage;
    private final Set<KeyId> commandedStages = new HashSet<>();

    public RenderCommandQueue(GraphicsPipeline<C> graphicsPipeline) {
        this.graphicsPipeline = graphicsPipeline;
        this.commandsByPipeline = new LinkedHashMap<>();
        this.commandsByPipelineAndStage = new LinkedHashMap<>();

        // Initialize for default pipeline types
        for (PipelineType type : graphicsPipeline.getPipelineTypes()) {
            initializePipeline(type);
        }
    }

    private void initializePipeline(PipelineType pipelineType) {
        commandsByPipeline.put(pipelineType, new LinkedHashMap<>());
        commandsByPipelineAndStage.put(pipelineType, new LinkedHashMap<>());
    }

    /**
     * Add a render command to a specific pipeline
     */
    public void addCommand(PipelineType pipelineType, RenderCommand command) {
        RenderSetting renderSetting = command.getRenderSetting();
        KeyId stageId = command.getStageId();
        commandedStages.add(stageId);
        // Add to pipeline grouping
        Map<RenderSetting, List<RenderCommand>> pipelineCommands = commandsByPipeline.computeIfAbsent(pipelineType,
                pt -> {
                    initializePipeline(pt);
                    return commandsByPipeline.get(pt);
                });
        pipelineCommands.computeIfAbsent(renderSetting, k -> new ArrayList<>()).add(command);

        // Add to pipeline+stage grouping
        Map<KeyId, Map<RenderSetting, List<RenderCommand>>> stageMap = commandsByPipelineAndStage
                .computeIfAbsent(pipelineType, pt -> {
                    initializePipeline(pt);
                    return commandsByPipelineAndStage.get(pt);
                });
        stageMap.computeIfAbsent(stageId, k -> new HashMap<>()).computeIfAbsent(renderSetting, r -> new ArrayList<>()).add(command);
    }

    /**
     * Add multiple render commands to a specific pipeline
     */
    public void addCommands(PipelineType pipelineType, Map<RenderSetting, List<RenderCommand>> commands) {
        for (Map.Entry<RenderSetting, List<RenderCommand>> entry : commands.entrySet()) {
            for (RenderCommand command : entry.getValue()) {
                addCommand(pipelineType, command);
            }
        }
    }

    // Deferred translucent commands for DEDICATED_STAGES and FLEXIBLE modes
    private final Map<PipelineType, Map<RenderSetting, List<RenderCommand>>> deferredTranslucentCommands = new LinkedHashMap<>();

    /**
     * Execute render commands for a specific stage.
     * Executes commands based on the configured translucent rendering strategy.
     */
    public void executeStage(KeyId stageId, RenderStateManager manager, C context) {
        //SketchRender.COMMAND_TIMER.start("execute command -> " + stageId);
        PipelineConfig config = graphicsPipeline.getConfig();
        PipelineConfig.TranslucencyStrategy strategy = config.getTranslucencyStrategy();

        // Get GraphicsStage for stage-specific configuration
        GraphicsStage stage = graphicsPipeline.getStage(stageId);

        // Get all pipeline types and sort by priority
        List<PipelineType> pipelineTypes = graphicsPipeline.getPipelineTypes();

        // Create GL state snapshot before executing commands
        // Only captures states and bindings that will actually be modified
        GLStateSnapshot snapshot = null;
        GraphicsAPI api = GraphicsDriver.getCurrentAPI();
        if (commandedStages.contains(stageId)) {
            SnapshotScope scope = collectSnapshotScopeForStage(stageId);
            if (!scope.isEmpty()) {
                snapshot = api.snapshot(scope);
            }
        }
        manager.reset();

        EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageId, context, GraphicsPipelineStageEvent.Phase.PRE));
        context.preStage(stageId);

        switch (strategy) {
            case INTERLEAVED:
                // Execute all pipeline types in priority order for this stage
                for (PipelineType pipelineType : pipelineTypes) {
                    executeStageForPipeline(pipelineType, stageId, manager, context);
                }
                break;

            case DEDICATED_STAGES:
                // Execute non-translucent pipelines normally
                for (PipelineType pipelineType : pipelineTypes) {
                    if (pipelineType.equals(PipelineType.TRANSLUCENT)) {
                        // Accumulate translucent commands instead of executing
                        accumulateTranslucentCommands(pipelineType, stageId);
                    } else {
                        executeStageForPipeline(pipelineType, stageId, manager, context);
                    }
                }

                // Check if this is a dedicated translucent stage
                boolean isDedicated = stage != null && stage.isDedicatedTranslucentStage()
                        || config.isDedicatedTranslucentStage(stageId);

                if (isDedicated) {
                    // Flush all deferred translucent commands
                    flushDeferredTranslucentCommands(manager, context);
                }
                break;

            case FLEXIBLE:
                // Execute non-translucent pipelines normally
                for (PipelineType pipelineType : pipelineTypes) {
                    if (!pipelineType.equals(PipelineType.TRANSLUCENT)) {
                        executeStageForPipeline(pipelineType, stageId, manager, context);
                    }
                }

                // Determine if translucent should follow solid for this stage
                boolean followsSolid = shouldTranslucentFollowSolid(stage, stageId, config);

                if (followsSolid) {
                    // Execute translucent immediately for this stage
                    executeStageForPipeline(PipelineType.TRANSLUCENT, stageId, manager, context);
                } else {
                    // Defer translucent commands
                    accumulateTranslucentCommands(PipelineType.TRANSLUCENT, stageId);
                }
                break;
        }

        // Restore GL state from snapshot
        if (snapshot != null) {
            manager.reset();
            api.restore(snapshot);
        }

        context.postStage(stageId);
        EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageId, context, GraphicsPipelineStageEvent.Phase.POST));
        //SketchRender.COMMAND_TIMER.end("execute command -> " + stageId);
    }

    /**
     * Execute all remaining deferred translucent commands.
     * Call this at the end of the render pipeline for FLEXIBLE mode.
     */
    public void flushRemainingTranslucentCommands(RenderStateManager manager, C context) {
        if (!deferredTranslucentCommands.isEmpty()) {
            GraphicsAPI api = GraphicsDriver.getCurrentAPI();
            SnapshotScope scope = collectSnapshotScopeFromDeferredCommands();
            GLStateSnapshot snapshot = null;
            if (!scope.isEmpty()) {
                snapshot = api.snapshot(scope);
            }

            flushDeferredTranslucentCommands(manager, context);

            if (snapshot != null) {
                manager.reset();
                api.restore(snapshot);
            }
        }
    }

    /**
     * Accumulate translucent commands for later execution.
     */
    private void accumulateTranslucentCommands(PipelineType pipelineType, KeyId stageId) {
        Map<KeyId, Map<RenderSetting, List<RenderCommand>>> stageMap = commandsByPipelineAndStage.get(pipelineType);
        if (stageMap == null) {
            return;
        }

        Map<RenderSetting, List<RenderCommand>> stageCommands = stageMap.get(stageId);
        if (stageCommands == null || stageCommands.isEmpty()) {
            return;
        }

        // Accumulate commands per pipeline type
        Map<RenderSetting, List<RenderCommand>> deferred = deferredTranslucentCommands.computeIfAbsent(pipelineType, k -> new LinkedHashMap<>());

        for (Map.Entry<RenderSetting, List<RenderCommand>> entry : stageCommands.entrySet()) {
            deferred.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
        }
    }

    /**
     * Flush all deferred translucent commands.
     */
    private void flushDeferredTranslucentCommands(RenderStateManager manager, C context) {
        // Get pipeline types in priority order
        List<PipelineType> pipelineTypes = new ArrayList<>(deferredTranslucentCommands.keySet());
        pipelineTypes.sort(Comparator.comparingInt(PipelineType::getPriority));

        for (PipelineType pipelineType : pipelineTypes) {
            Map<RenderSetting, List<RenderCommand>> commands = deferredTranslucentCommands.get(pipelineType);
            if (commands != null && !commands.isEmpty()) {
                executeCommandMap(commands, manager, context);
            }
        }

        // Clear deferred commands after flushing
        deferredTranslucentCommands.clear();
    }

    /**
     * Determine if translucent rendering should follow solid for a stage (FLEXIBLE
     * mode).
     */
    private boolean shouldTranslucentFollowSolid(GraphicsStage stage, KeyId stageId, PipelineConfig config) {
        // Check stage-specific configuration first
        if (stage != null && stage.getTranslucentFollowsSolid() != null) {
            return stage.getTranslucentFollowsSolid();
        }

        // Check config per-stage setting
        Boolean configSetting = config.getStageTranslucentFollowsSolid(stageId);
        if (configSetting != null) {
            return configSetting;
        }

        // Default: translucent follows solid (INTERLEAVED behavior)
        return true;
    }

    /**
     * Execute commands from a command map.
     */
    private void executeCommandMap(Map<RenderSetting, List<RenderCommand>> commandMap, RenderStateManager manager, C context) {
        for (Map.Entry<RenderSetting, List<RenderCommand>> entry : commandMap.entrySet()) {
            RenderSetting setting = entry.getKey();
            List<RenderCommand> commands = entry.getValue();

            if (setting.renderParameter().isInvalid()) {
                continue;
            }

            // Apply render setting once for all commands with the same setting
            if (setting.shouldSwitchRenderState()) {
                applyRenderSetting(manager, context, setting);
            }

            // Execute each render command
            for (RenderCommand command : commands) {
                executeRenderCommand(command, context);
            }
        }
    }

    /**
     * Execute render commands for a specific stage and pipeline type.
     */
    private void executeStageForPipeline(PipelineType pipelineType, KeyId stageId, RenderStateManager manager, C context) {
        Map<KeyId, Map<RenderSetting, List<RenderCommand>>> stageMap = commandsByPipelineAndStage.get(pipelineType);
        if (stageMap == null) {
            return;
        }

        Map<RenderSetting, List<RenderCommand>> stageCommands = stageMap.get(stageId);
        if (stageCommands == null || stageCommands.isEmpty()) {
            return;
        }

        executeCommandMap(stageCommands, manager, context);
    }

    /**
     * Execute a render command immediately (skipping the queue).
     * Useful for immediate rendering scenarios.
     */
    public void executeImmediate(RenderCommand command, RenderSetting setting, RenderStateManager manager, RenderContext context) {
        if (setting.renderParameter().isInvalid()) {
            return;
        }

        // Apply render setting once for all commands with the same setting
        if (setting.shouldSwitchRenderState()) {
            applyRenderSetting(manager, context, setting);
        }

        // Execute each render command
        executeRenderCommand(command, context);
    }

    /**
     * Execute a single render command with uniform batching support
     */
    private void executeRenderCommand(RenderCommand command, RenderContext context) {
        List<UniformBatchGroup> uniformBatches = command.getUniformBatches();
        if (command.requiresResourceBinding()) {
            command.bindResources();
        }

        try {
            if (uniformBatches.isEmpty()) {
                // No uniform batching, execute directly
                command.execute(context);
            } else {
                // Execute with uniform batching
                for (UniformBatchGroup batch : uniformBatches) {
                    executeUniformBatch(batch, command, context);
                }
            }
        } finally {
            command.unbindResources();
        }
    }

    /**
     * Execute a uniform batch within a render command
     */
    private void executeUniformBatch(UniformBatchGroup batch, RenderCommand command, RenderContext context) {
        if (batch.isEmpty()) {
            return;
        }

        // Apply uniform values
        ShaderProvider shader = context.shaderProvider();
        batch.getUniformSnapshot().applyTo(shader.getUniformHookGroup());

        // Execute the render command
        command.execute(context);

        // Mark that something was rendered
        context.set(KeyId.of("rendered"), true);

        // Call afterDraw for all instances in this batch
        for (Graphics instance : batch.getInstances()) {
            instance.afterDraw(context);
        }
    }

    protected void applyRenderSetting(RenderStateManager manager, RenderContext context, RenderSetting setting) {
        manager.accept(setting, context);
        ShaderProvider shader = context.shaderProvider();
        shader.getUniformHookGroup().updateUniforms(context);
    }

    /**
     * Collect snapshot scope for a specific stage by analyzing all RenderSettings used in that stage.
     * Only captures states and bindings that will actually be modified during command execution.
     */
    private SnapshotScope collectSnapshotScopeForStage(KeyId stageId) {
        Set<RenderSetting> allSettings = new HashSet<>();

        // Collect all RenderSettings from all pipeline types for this stage
        for (Map<KeyId, Map<RenderSetting, List<RenderCommand>>> stageMap : commandsByPipelineAndStage.values()) {
            Map<RenderSetting, List<RenderCommand>> stageCommands = stageMap.get(stageId);
            if (stageCommands != null) {
                allSettings.addAll(stageCommands.keySet());
            }
        }

        if (allSettings.isEmpty()) {
            // No commands for this stage, but we still need to capture VAO and FBO
            // as they might be modified by other operations
            return SnapshotScope.builder()
                    .addState(SnapshotScope.StateType.VAO)
                    .addState(SnapshotScope.StateType.FBO)
                    .build();
        }

        return collectSnapshotScopeFromSettings(allSettings);
    }

    /**
     * Collect snapshot scope from a collection of RenderSettings.
     * Extracts states from render states and bindings from shaders.
     */
    private SnapshotScope collectSnapshotScopeFromSettings(Set<RenderSetting> settings) {
        List<SnapshotScope> scopes = new ArrayList<>();

        for (RenderSetting setting : settings) {
            if (setting == null || setting.renderParameter().isInvalid()) {
                continue;
            }

            // Create base scope from RenderSetting
            SnapshotScope baseScope = SnapshotScope.fromRenderSetting(setting);
            scopes.add(baseScope);

            // Extract shader from render state and add shader bindings
            if (setting.renderState() != null) {
                try {
                    RenderStateComponent shaderComponent =
                            setting.renderState().get(ResourceTypes.SHADER_TEMPLATE);
                    if (shaderComponent instanceof ShaderState shaderState) {
                        ResourceReference<ShaderTemplate> shaderRef = shaderState.getTemplate();
                        if (shaderRef != null && shaderRef.isAvailable()) {
                            ShaderProvider shaderProvider = shaderRef.get();
                            if (shaderProvider != null) {
                                SnapshotScope shaderScope = SnapshotScope.fromShader(shaderProvider);
                                scopes.add(shaderScope);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore errors when extracting shader - continue with base scope
                }
            }
        }

        // Combine all scopes
        SnapshotScope combined;
        if (scopes.isEmpty()) {
            combined = SnapshotScope.empty();
        } else {
            combined = SnapshotScope.combine(scopes.toArray(new SnapshotScope[0]));
        }

        // Always ensure VAO and FBO are captured as they are commonly modified
        // Add them to the combined scope if not already present
        if (combined.shouldCaptureAll()) {
            return combined; // Already full, no need to add
        }

        SnapshotScope.Builder finalBuilder = SnapshotScope.builder();

        // Copy existing state types
        for (SnapshotScope.StateType type : combined.getStateTypes()) {
            finalBuilder.addState(type);
        }

        // Ensure VAO and FBO are included
        finalBuilder.addState(SnapshotScope.StateType.VAO);
        finalBuilder.addState(SnapshotScope.StateType.FBO);

        // Copy existing bindings
        for (int unit : combined.getTextureUnits()) {
            finalBuilder.addTextureUnit(unit);
        }
        for (int binding : combined.getSSBOBindings()) {
            finalBuilder.addSSBOBinding(binding);
        }
        for (int binding : combined.getUBOBindings()) {
            finalBuilder.addUBOBinding(binding);
        }
        for (int binding : combined.getImageBindings()) {
            finalBuilder.addImageBinding(binding);
        }

        return finalBuilder.build();
    }

    /**
     * Collect snapshot scope from deferred translucent commands.
     */
    private SnapshotScope collectSnapshotScopeFromDeferredCommands() {
        Set<RenderSetting> allSettings = new HashSet<>();

        for (Map<RenderSetting, List<RenderCommand>> commands : deferredTranslucentCommands.values()) {
            allSettings.addAll(commands.keySet());
        }

        if (allSettings.isEmpty()) {
            return SnapshotScope.builder()
                    .addState(SnapshotScope.StateType.VAO)
                    .addState(SnapshotScope.StateType.FBO)
                    .build();
        }

        return collectSnapshotScopeFromSettings(allSettings);
    }

    /**
     * Clear all commands from the queue
     */
    public void clear() {
        for (Map<RenderSetting, List<RenderCommand>> commands : commandsByPipeline.values()) {
            commands.clear();
        }
        for (Map<KeyId, Map<RenderSetting, List<RenderCommand>>> stageMap : commandsByPipelineAndStage.values()) {
            stageMap.clear();
        }
        commandedStages.clear();
    }

    public boolean isEmpty() {
        for (Map<RenderSetting, List<RenderCommand>> commands : commandsByPipeline.values()) {
            if (!commands.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Queue statistics record
     */
    public record QueueStats(
            int totalCommands,
            int totalInstances,
            int totalVertices,
            int uniqueVertexResources) {
        @Override
        public String toString() {
            return String.format(
                    "QueueStats[commands=%d, instances=%d, vertices=%d, vresources=%d]",
                    totalCommands, totalInstances, totalVertices, uniqueVertexResources);
        }
    }
}