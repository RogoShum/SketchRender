package rogo.sketch.render.command;

import rogo.sketch.render.resource.buffer.VertexResource;
import rogo.sketch.util.Identifier;

import java.util.*;

/**
 * Queue of render commands organized by vertex resource for efficient batch rendering
 */
public class RenderCommandQueue {
    private final Map<VertexResource, List<RenderCommand>> commandsByVertexResource;
    private final Map<Identifier, List<RenderCommand>> commandsByStage;
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
        VertexResource vertexResource = command.getVertexResource();
        Identifier stageId = command.getStageId();
        
        // Add to vertex resource grouping
        commandsByVertexResource.computeIfAbsent(vertexResource, k -> new ArrayList<>()).add(command);
        
        // Add to stage grouping
        commandsByStage.computeIfAbsent(stageId, k -> new ArrayList<>()).add(command);
        
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
    public void executeAll() {
        for (Identifier stageId : stageOrder) {
            executeStage(stageId);
        }
    }
    
    /**
     * Execute render commands for a specific stage
     */
    public void executeStage(Identifier stageId) {
        List<RenderCommand> stageCommands = commandsByStage.get(stageId);
        if (stageCommands == null || stageCommands.isEmpty()) {
            return;
        }
        
        // Group commands by vertex resource to minimize state changes
        Map<VertexResource, List<RenderCommand>> resourceGroups = new LinkedHashMap<>();
        for (RenderCommand command : stageCommands) {
            VertexResource resource = command.getVertexResource();
            resourceGroups.computeIfAbsent(resource, k -> new ArrayList<>()).add(command);
        }
        
        // Execute each vertex resource group
        for (Map.Entry<VertexResource, List<RenderCommand>> entry : resourceGroups.entrySet()) {
            executeVertexResourceGroup(entry.getKey(), entry.getValue());
        }
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
    public void executeStagesBetween(Identifier fromStageId, Identifier toStageId) {
        int fromIndex = stageOrder.indexOf(fromStageId);
        int toIndex = stageOrder.indexOf(toStageId);
        
        if (fromIndex == -1 || toIndex == -1 || fromIndex >= toIndex) {
            return;
        }
        
        for (int i = fromIndex + 1; i < toIndex; i++) {
            executeStage(stageOrder.get(i));
        }
    }
    
    /**
     * Execute render commands before a specific stage
     */
    public void executeStagesBefore(Identifier stageId) {
        int index = stageOrder.indexOf(stageId);
        if (index == -1) {
            return;
        }
        
        for (int i = 0; i < index; i++) {
            executeStage(stageOrder.get(i));
        }
    }
    
    /**
     * Execute render commands after a specific stage
     */
    public void executeStagesAfter(Identifier stageId) {
        int index = stageOrder.indexOf(stageId);
        if (index == -1) {
            return;
        }
        
        for (int i = index + 1; i < stageOrder.size(); i++) {
            executeStage(stageOrder.get(i));
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
    public List<RenderCommand> getCommandsForVertexResource(VertexResource vertexResource) {
        return commandsByVertexResource.getOrDefault(vertexResource, Collections.emptyList());
    }
    
    /**
     * Get commands for a specific stage
     */
    public List<RenderCommand> getCommandsForStage(Identifier stageId) {
        return commandsByStage.getOrDefault(stageId, Collections.emptyList());
    }
    
    /**
     * Get all vertex resources in the queue
     */
    public Set<VertexResource> getVertexResources() {
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
