package rogo.sketch.render.command;

import rogo.sketch.SketchRender;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.event.GraphicsPipelineStageEvent;
import rogo.sketch.event.bridge.EventBusBridge;
import rogo.sketch.render.pipeline.*;
import rogo.sketch.render.state.FullRenderState;
import rogo.sketch.render.state.RenderStateSnapshotUtils;
import rogo.sketch.util.Identifier;

import java.util.*;

/**
 * Queue of render commands organized by vertex resource for efficient batch
 * rendering
 */
public class RenderCommandQueue<C extends RenderContext> {
    private final GraphicsPipeline<C> graphicsPipeline;
    private final Map<RenderSetting, List<RenderCommand>> commandsByVertexResource;
    private final Map<Identifier, Map<RenderSetting, List<RenderCommand>>> commandsByStage;

    public RenderCommandQueue(GraphicsPipeline<C> graphicsPipeline) {
        this.graphicsPipeline = graphicsPipeline;
        this.commandsByVertexResource = new LinkedHashMap<>();
        this.commandsByStage = new LinkedHashMap<>();
    }

    /**
     * Add a render command to the queue
     */
    public void addCommand(RenderCommand command) {
        RenderSetting renderSetting = command.getRenderSetting();
        Identifier stageId = command.getStageId();

        // Add to vertex resource grouping
        commandsByVertexResource.computeIfAbsent(renderSetting, k -> new ArrayList<>()).add(command);

        // Add to stage grouping
        commandsByStage.computeIfAbsent(stageId, k -> new HashMap<>())
                .computeIfAbsent(renderSetting, r -> new ArrayList<>()).add(command);
    }

    /**
     * Add multiple render commands
     */
    public void addCommands(Collection<RenderCommand> commands) {
        commands.forEach(this::addCommand);
    }

    /**
     * Add multiple render commands grouped by RenderSetting
     */
    public void addCommands(Map<RenderSetting, List<RenderCommand>> commands) {
        for (Map.Entry<RenderSetting, List<RenderCommand>> entry : commands.entrySet()) {
            RenderSetting setting = entry.getKey();
            List<RenderCommand> cmdList = entry.getValue();

            // Add to vertex resource grouping directly
            commandsByVertexResource.computeIfAbsent(setting, k -> new ArrayList<>()).addAll(cmdList);

            // Add to stage grouping
            // Since all commands in the list should share the same Stage ID (as per
            // createRenderCommands contract usually),
            // we can optimization this. However, to be safe and strictly correct per
            // command:
            for (RenderCommand command : cmdList) {
                Identifier stageId = command.getStageId();
                commandsByStage.computeIfAbsent(stageId, k -> new HashMap<>())
                        .computeIfAbsent(setting, r -> new ArrayList<>()).add(command);
            }
        }
    }

    /**
     * Execute render commands for a specific stage
     */
    public void executeStage(Identifier stageId, RenderStateManager manager, C context) {
        Map<RenderSetting, List<RenderCommand>> stageCommands = commandsByStage.get(stageId);

        EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageId, context,
                GraphicsPipelineStageEvent.Phase.PRE));
        context.preStage(stageId);

        if (stageCommands != null && !stageCommands.isEmpty()) {
            FullRenderState snapshot = RenderStateSnapshotUtils.createSnapshot();
            manager.changeState(snapshot, context);

            for (Map.Entry<RenderSetting, List<RenderCommand>> entry : stageCommands.entrySet()) {
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

            manager.changeState(snapshot, context);
        }

        context.postStage(stageId);
        EventBusBridge.post(new GraphicsPipelineStageEvent<>(graphicsPipeline, stageId, context,
                GraphicsPipelineStageEvent.Phase.POST));
    }

    /**
     * Execute a render command immediately (skipping the queue).
     * Useful for immediate rendering scenarios.
     */
    public void executeImmediate(RenderCommand command, RenderSetting setting, RenderStateManager manager,
            RenderContext context) {
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
        context.set(Identifier.of("rendered"), true);

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
        commandsByVertexResource.clear();
        commandsByStage.clear();
    }

    /**
     * Get commands for a specific vertex resource
     */
    public List<RenderCommand> getCommandsForVertexResource(RenderSetting renderSetting) {
        return commandsByVertexResource.getOrDefault(renderSetting, Collections.emptyList());
    }

    /**
     * Get all vertex resources in the queue
     */
    public Set<RenderSetting> getVertexResources() {
        return new HashSet<>(commandsByVertexResource.keySet());
    }

    public boolean isEmpty() {
        return commandsByVertexResource.isEmpty();
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