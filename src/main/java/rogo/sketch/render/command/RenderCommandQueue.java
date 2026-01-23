package rogo.sketch.render.command;

import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.event.GraphicsPipelineStageEvent;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.render.pipeline.*;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.render.state.RenderStateSnapshotUtils;
import rogo.sketch.util.KeyId;

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
        initializePipeline(PipelineType.COMPUTE);
        initializePipeline(PipelineType.RASTERIZATION);
        initializePipeline(PipelineType.TRANSLUCENT);
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
        stageMap.computeIfAbsent(stageId, k -> new HashMap<>())
                .computeIfAbsent(renderSetting, r -> new ArrayList<>()).add(command);
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

        FullRenderState snapshot = null;
        //todo need check
        if (commandedStages.contains(stageId)) {
            snapshot = RenderStateSnapshotUtils.createSnapshot();
            manager.changeState(snapshot, context, false);
        }

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

        if (snapshot != null) {
            manager.reset();
            manager.changeState(snapshot, context);
        }

        context.postStage(stageId);
        EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageId, context,
                GraphicsPipelineStageEvent.Phase.POST));
        //SketchRender.COMMAND_TIMER.end("execute command -> " + stageId);
    }

    /**
     * Execute all remaining deferred translucent commands.
     * Call this at the end of the render pipeline for FLEXIBLE mode.
     */
    public void flushRemainingTranslucentCommands(RenderStateManager manager, C context) {
        if (!deferredTranslucentCommands.isEmpty()) {
            FullRenderState snapshot = RenderStateSnapshotUtils.createSnapshot();
            manager.changeState(snapshot, context, false);

            flushDeferredTranslucentCommands(manager, context);

            manager.reset();
            manager.changeState(snapshot, context);
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