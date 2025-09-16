package rogo.sketch.render.command;

import rogo.sketch.api.ShaderProvider;
import rogo.sketch.api.graphics.GraphicsInstance;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.render.pipeline.RenderStateManager;
import rogo.sketch.render.pipeline.UniformBatchGroup;
import rogo.sketch.render.resource.buffer.VertexResource;
import rogo.sketch.util.Identifier;

import java.util.*;

/**
 * Queue of render commands organized by vertex resource for efficient batch rendering
 */
public class RenderCommandQueue {
    private final Map<RenderSetting, List<RenderCommand>> commandsByVertexResource;
    private final Map<Identifier, Map<RenderSetting, List<RenderCommand>>> commandsByStage;
    private final List<Identifier> stageOrder;

    public RenderCommandQueue() {
        this.commandsByVertexResource = new LinkedHashMap<>();
        this.commandsByStage = new LinkedHashMap<>();
        this.stageOrder = new ArrayList<>();
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
        commandsByStage.computeIfAbsent(stageId, k -> new HashMap<>()).computeIfAbsent(renderSetting, r -> new ArrayList<>()).add(command);

        // Track stage order
        if (!stageOrder.contains(stageId)) {
            stageOrder.add(stageId);
        }
    }

    /**
     * Add multiple render commands
     */
    public void addCommands(Collection<RenderCommand> commands) {
        commands.forEach(this::addCommand);
    }

    /**
     * Execute all render commands in stage order
     */
    public void executeAll(RenderStateManager manager, RenderContext context) {
        for (Identifier stageId : stageOrder) {
            executeStage(stageId, manager, context);
        }
    }

    /**
     * Execute render commands for a specific stage
     */
    public void executeStage(Identifier stageId, RenderStateManager manager, RenderContext context) {
        Map<RenderSetting, List<RenderCommand>> stageCommands = commandsByStage.get(stageId);
        if (stageCommands == null || stageCommands.isEmpty()) {
            return;
        }

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
    }
    
    /**
     * Execute a single render command with uniform batching support
     */
    private void executeRenderCommand(RenderCommand command, RenderContext context) {
        VertexResource vertexResource = command.getVertexResource();
        List<UniformBatchGroup> uniformBatches = command.getUniformBatches();
        
        // Bind vertex resource
        vertexResource.bind();
        
        try {
            if (uniformBatches.isEmpty()) {
                // No uniform batching, execute directly
                //command.execute();
            } else {
                // Execute with uniform batching
                for (UniformBatchGroup batch : uniformBatches) {
                    executeUniformBatch(batch, command, context);
                }
            }
        } finally {
            // Unbind vertex resource
            vertexResource.unbind();
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
        command.execute();
        
        // Mark that something was rendered
        context.set(Identifier.of("rendered"), true);

        // Call afterDraw for all instances in this batch
        for (GraphicsInstance instance : batch.getInstances()) {
            instance.afterDraw(context);
        }
    }

    protected void applyRenderSetting(RenderStateManager manager, RenderContext context, RenderSetting setting) {
        manager.accept(setting, context);
        ShaderProvider shader = context.shaderProvider();
        shader.getUniformHookGroup().updateUniforms(context);
    }

    /**
     * Execute all commands for a specific vertex resource (minimizes vertex buffer binding)
     */
    private void executeVertexResourceGroup(VertexResource vertexResource, List<RenderCommand> commands) {
        if (commands.isEmpty()) {
            return;
        }

        // Bind vertex resource once for all commands
        vertexResource.bind();

        try {
            // Execute each command (they share the same vertex resource)
            for (RenderCommand command : commands) {
                command.execute();
            }
        } finally {
            // Unbind vertex resource
            vertexResource.unbind();
        }
    }

    /**
     * Execute render commands between two stages (exclusive)
     */
    public void executeStagesBetween(Identifier fromStageId, Identifier toStageId, RenderStateManager manager, RenderContext context) {
        int fromIndex = stageOrder.indexOf(fromStageId);
        int toIndex = stageOrder.indexOf(toStageId);

        if (fromIndex == -1 || toIndex == -1 || fromIndex >= toIndex) {
            return;
        }

        for (int i = fromIndex + 1; i < toIndex; i++) {
            executeStage(stageOrder.get(i), manager, context);
        }
    }

    /**
     * Execute render commands before a specific stage
     */
    public void executeStagesBefore(Identifier stageId, RenderStateManager manager, RenderContext context) {
        int index = stageOrder.indexOf(stageId);
        if (index == -1) {
            return;
        }

        for (int i = 0; i < index; i++) {
            executeStage(stageOrder.get(i), manager, context);
        }
    }

    /**
     * Execute render commands after a specific stage
     */
    public void executeStagesAfter(Identifier stageId, RenderStateManager manager, RenderContext context) {
        int index = stageOrder.indexOf(stageId);
        if (index == -1) {
            return;
        }

        for (int i = index + 1; i < stageOrder.size(); i++) {
            executeStage(stageOrder.get(i), manager, context);
        }
    }

    /**
     * Clear all commands from the queue
     */
    public void clear() {
        commandsByVertexResource.clear();
        commandsByStage.clear();
        stageOrder.clear();
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

    /**
     * Get all stages in order
     */
    public List<Identifier> getStageOrder() {
        return new ArrayList<>(stageOrder);
    }

    /**
     * Get queue statistics
     */
    public QueueStats getStats() {
        int totalCommands = commandsByVertexResource.values().stream()
                .mapToInt(List::size)
                .sum();

        int totalGraphicsInstances = commandsByVertexResource.values().stream()
                .flatMap(List::stream)
                .mapToInt(cmd -> cmd.getInstances().size())
                .sum();

        int totalVertices = commandsByVertexResource.values().stream()
                .flatMap(List::stream)
                .mapToInt(RenderCommand::getTotalVertexCount)
                .sum();

        return new QueueStats(
                totalCommands,
                totalGraphicsInstances,
                totalVertices,
                commandsByVertexResource.size(),
                stageOrder.size()
        );
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
            int uniqueVertexResources,
            int totalStages
    ) {
        @Override
        public String toString() {
            return String.format(
                    "QueueStats[commands=%d, instances=%d, vertices=%d, vresources=%d, stages=%d]",
                    totalCommands, totalInstances, totalVertices, uniqueVertexResources, totalStages
            );
        }
    }
}
