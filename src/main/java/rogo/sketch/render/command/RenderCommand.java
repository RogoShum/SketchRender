package rogo.sketch.render.command;

import rogo.sketch.render.DrawCommand;
import rogo.sketch.render.RenderSetting;
import rogo.sketch.render.information.GraphicsInformation;
import rogo.sketch.render.resource.buffer.VertexResource;
import rogo.sketch.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A render command that combines vertex resources with instance parameters for batch rendering
 */
public class RenderCommand {
    private final VertexResource vertexResource;
    private final RenderSetting renderSetting;
    private final List<InstanceData> instances;
    private final Identifier stageId;
    
    public RenderCommand(VertexResource vertexResource, 
                        RenderSetting renderSetting,
                        List<InstanceData> instances,
                        Identifier stageId) {
        this.vertexResource = vertexResource;
        this.renderSetting = renderSetting;
        this.instances = List.copyOf(instances);
        this.stageId = stageId;
    }
    
    /**
     * Execute this render command using batch rendering
     */
    public void execute() {
        // Don't bind/unbind here - let the command queue handle that for efficiency
        
        // Apply render setting (shader, blend state, etc.)
        // This would be handled by the render state manager in the actual execution
        
        if (instances.size() == 1) {
            // Single instance - use direct rendering
            executeSingleInstance(instances.get(0));
        } else {
            // Multiple instances - use batch rendering
            executeMultiDrawBatch();
        }
    }
    
    /**
     * Execute a single instance directly
     */
    private void executeSingleInstance(InstanceData instanceData) {
        DrawCommand drawCommand = instanceData.getDrawCommand();
        
        // Set instance-specific uniforms if needed
        // This would involve setting uniforms for the specific instance
        
        // Execute the draw command with offset and count
        executeDrawCommand(drawCommand, instanceData);
    }
    
    /**
     * Execute multiple instances using multi-draw batch rendering
     */
    private void executeMultiDrawBatch() {
        // Check if this is instanced rendering
        boolean isInstancedRendering = instances.get(0).getGraphicsInfo().isInstancedRendering();
        
        if (isInstancedRendering) {
            executeInstancedBatch();
        } else {
            executeRegularMultiDrawBatch();
        }
    }
    
    /**
     * Execute regular multi-draw batch using baseVertex approach
     */
    private void executeRegularMultiDrawBatch() {
        // Group by mesh to use baseVertex efficiently
        Map<Object, List<InstanceData>> meshGroups = groupInstancesByMesh();
        
        for (Map.Entry<Object, List<InstanceData>> entry : meshGroups.entrySet()) {
            List<InstanceData> meshInstances = entry.getValue();
            if (meshInstances.isEmpty()) continue;
            
            // For each mesh, draw all its instances using baseVertex
            for (InstanceData instanceData : meshInstances) {
                DrawCommand drawCommand = instanceData.getDrawCommand();
                int baseVertex = instanceData.getVertexOffset();
                int vertexCount = instanceData.getVertexCount();
                
                // Execute draw command with baseVertex
                executeDrawCommandWithBaseVertex(drawCommand, baseVertex, vertexCount);
            }
        }
        
        // Call afterDraw for all instances
        for (InstanceData instanceData : instances) {
            // Instance-specific post-draw operations would go here
            // Could call instanceData.getGraphicsInfo().getInstance().afterDraw(context) here
        }
    }
    
    /**
     * Execute instanced batch rendering using baseVertex and baseInstance
     */
    private void executeInstancedBatch() {
        // For instanced rendering, we need to handle mesh batching with baseVertex/baseInstance
        Map<Object, List<InstanceData>> meshGroups = groupInstancesByMesh();
        
        int baseInstance = 0;
        for (Map.Entry<Object, List<InstanceData>> entry : meshGroups.entrySet()) {
            List<InstanceData> meshInstances = entry.getValue();
            if (meshInstances.isEmpty()) continue;
            
            // Get mesh information from first instance
            InstanceData firstInstance = meshInstances.get(0);
            DrawCommand drawCommand = firstInstance.getDrawCommand();
            int baseVertex = firstInstance.getVertexOffset();
            int vertexCount = firstInstance.getVertexCount();
            int instanceCount = meshInstances.size();
            
            // Execute instanced draw command with baseVertex and baseInstance
            executeInstancedDrawCommandWithBase(drawCommand, baseVertex, vertexCount, 
                                              baseInstance, instanceCount);
            
            baseInstance += instanceCount;
        }
        
        // Call afterDraw for all instances
        for (InstanceData instanceData : instances) {
            // Instance-specific post-draw operations would go here
        }
    }
    
    /**
     * Execute a draw command for a specific instance
     */
    private void executeDrawCommand(DrawCommand drawCommand, InstanceData instanceData) {
        int vertexOffset = instanceData.getVertexOffset();
        int vertexCount = instanceData.getVertexCount();
        
        // Create a modified draw command with offset
        DrawCommand offsetCommand = new DrawCommand(
                drawCommand.primitiveType(),
                drawCommand.drawMode(),
                drawCommand.instanceCount(),
                drawCommand.useIndexBuffer(),
                drawCommand.enableSorting()
        );
        
        // Execute with offset parameters
        if (drawCommand.useIndexBuffer()) {
            // Use element array rendering with offset
            executeElementsDrawCommand(offsetCommand, vertexOffset, vertexCount);
        } else {
            // Use array rendering with offset
            executeArrayDrawCommand(offsetCommand, vertexOffset, vertexCount);
        }
    }
    
    private void executeElementsDrawCommand(DrawCommand command, int vertexOffset, int vertexCount) {
        // Implementation depends on your OpenGL wrapper
        // This would call something like glDrawElementsBaseVertex or glDrawElements with offset
    }
    
    private void executeArrayDrawCommand(DrawCommand command, int vertexOffset, int vertexCount) {
        // Implementation depends on your OpenGL wrapper
        // This would call something like glDrawArrays with first = vertexOffset, count = vertexCount
    }
    
    /**
     * Execute multi-draw command for batch rendering
     */
    private void executeMultiDrawCommand(DrawCommand command, int[] offsets, int[] counts) {
        // Implementation depends on your OpenGL wrapper
        // This would call something like glMultiDrawArrays with arrays of offsets and counts
        // For now, fall back to individual draw calls
        for (int i = 0; i < offsets.length; i++) {
            executeArrayDrawCommand(command, offsets[i], counts[i]);
        }
    }
    
    /**
     * Execute instanced draw command
     */
    private void executeInstancedDrawCommand(DrawCommand command, int instanceCount) {
        // Implementation depends on your OpenGL wrapper
        // This would call something like glDrawArraysInstanced or glDrawElementsInstanced
        // The exact implementation depends on your graphics context and OpenGL wrapper
        
        // For elements-based rendering
        if (command.useIndexBuffer()) {
            // glDrawElementsInstanced equivalent
            executeInstancedElementsDrawCommand(command, instanceCount);
        } else {
            // glDrawArraysInstanced equivalent  
            executeInstancedArrayDrawCommand(command, instanceCount);
        }
    }
    
    /**
     * Execute instanced array draw command
     */
    private void executeInstancedArrayDrawCommand(DrawCommand command, int instanceCount) {
        // Implementation depends on your OpenGL wrapper
        // This would call something like glDrawArraysInstanced
        // Parameters: primitive type, first vertex, vertex count, instance count
    }
    
    /**
     * Execute instanced elements draw command
     */
    private void executeInstancedElementsDrawCommand(DrawCommand command, int instanceCount) {
        // Implementation depends on your OpenGL wrapper
        // This would call something like glDrawElementsInstanced
        // Parameters: primitive type, index count, index type, indices offset, instance count
    }
    
    /**
     * Group instances by their mesh reference for efficient batching
     */
    private Map<Object, List<InstanceData>> groupInstancesByMesh() {
        Map<Object, List<InstanceData>> meshGroups = new LinkedHashMap<>();
        
        for (InstanceData instanceData : instances) {
            GraphicsInformation info = instanceData.getGraphicsInfo();
            Object meshRef = null;
            
            if (info.hasModelMesh()) {
                meshRef = info.getModelMesh();
            } else if (info.hasMesh()) {
                meshRef = info.getMesh();
            }
            
            meshGroups.computeIfAbsent(meshRef, k -> new ArrayList<>()).add(instanceData);
        }
        
        return meshGroups;
    }
    
    /**
     * Execute draw command with baseVertex
     */
    private void executeDrawCommandWithBaseVertex(DrawCommand command, int baseVertex, int vertexCount) {
        // Implementation depends on your OpenGL wrapper
        // This would call something like glDrawArrays or glDrawElements with baseVertex
        if (command.useIndexBuffer()) {
            // glDrawElementsBaseVertex equivalent
            executeElementsDrawCommandWithBaseVertex(command, baseVertex, vertexCount);
        } else {
            // glDrawArrays with offset
            executeArrayDrawCommand(command, baseVertex, vertexCount);
        }
    }
    
    /**
     * Execute elements draw command with baseVertex
     */
    private void executeElementsDrawCommandWithBaseVertex(DrawCommand command, int baseVertex, int vertexCount) {
        // Implementation depends on your OpenGL wrapper
        // This would call something like glDrawElementsBaseVertex
        // Parameters: primitive type, index count, index type, indices offset, baseVertex
    }
    
    /**
     * Execute instanced draw command with baseVertex and baseInstance
     */
    private void executeInstancedDrawCommandWithBase(DrawCommand command, int baseVertex, int vertexCount,
                                                    int baseInstance, int instanceCount) {
        // Implementation depends on your OpenGL wrapper
        if (command.useIndexBuffer()) {
            // glDrawElementsInstancedBaseVertexBaseInstance equivalent
            executeInstancedElementsDrawCommandWithBase(command, baseVertex, vertexCount, baseInstance, instanceCount);
        } else {
            // glDrawArraysInstancedBaseInstance equivalent
            executeInstancedArrayDrawCommandWithBase(command, baseVertex, vertexCount, baseInstance, instanceCount);
        }
    }
    
    /**
     * Execute instanced array draw command with baseVertex and baseInstance
     */
    private void executeInstancedArrayDrawCommandWithBase(DrawCommand command, int baseVertex, int vertexCount,
                                                         int baseInstance, int instanceCount) {
        // Implementation depends on your OpenGL wrapper
        // This would call something like glDrawArraysInstancedBaseInstance
        // Parameters: primitive type, baseVertex, vertexCount, instanceCount, baseInstance
    }
    
    /**
     * Execute instanced elements draw command with baseVertex and baseInstance
     */
    private void executeInstancedElementsDrawCommandWithBase(DrawCommand command, int baseVertex, int vertexCount,
                                                           int baseInstance, int instanceCount) {
        // Implementation depends on your OpenGL wrapper  
        // This would call something like glDrawElementsInstancedBaseVertexBaseInstance
        // Parameters: primitive type, index count, index type, indices offset, instanceCount, baseVertex, baseInstance
    }
    
    // Getters
    public VertexResource getVertexResource() { return vertexResource; }
    public RenderSetting getRenderSetting() { return renderSetting; }
    public List<InstanceData> getInstances() { return instances; }
    public Identifier getStageId() { return stageId; }
    
    public int getGraphicsInstanceCount() { return instances.size(); }
    public int getTotalVertexCount() { 
        return instances.stream().mapToInt(InstanceData::getVertexCount).sum(); 
    }
    
    @Override
    public String toString() {
        return "RenderCommand{" +
                "stageId=" + stageId +
                ", instanceCount=" + instances.size() +
                ", totalVertices=" + getTotalVertexCount() +
                '}';
    }
    
    /**
     * Data for a single instance within a render command
     */
    public static class InstanceData {
        private final GraphicsInformation graphicsInfo;
        private final DrawCommand drawCommand;
        private final int vertexOffset;
        private final int vertexCount;
        
        public InstanceData(GraphicsInformation graphicsInfo, 
                           DrawCommand drawCommand,
                           int vertexOffset,
                           int vertexCount) {
            this.graphicsInfo = graphicsInfo;
            this.drawCommand = drawCommand;
            this.vertexOffset = vertexOffset;
            this.vertexCount = vertexCount;
        }
        
        public GraphicsInformation getGraphicsInfo() { return graphicsInfo; }
        public DrawCommand getDrawCommand() { return drawCommand; }
        public int getVertexOffset() { return vertexOffset; }
        public int getVertexCount() { return vertexCount; }
        
        @Override
        public String toString() {
            return "InstanceData{" +
                    "instance=" + graphicsInfo.getInstance().getIdentifier() +
                    ", vertexOffset=" + vertexOffset +
                    ", vertexCount=" + vertexCount +
                    '}';
        }
    }
    
    /**
     * Factory method to create render commands from filled vertex resources
     */
    public static RenderCommand createFromFilledResource(
            VertexResource vertexResource,
            List<GraphicsInformation> instances,
            Identifier stageId) {
        
        if (instances.isEmpty()) {
            throw new IllegalArgumentException("Cannot create render command with no instances");
        }
        
        // Use the render setting from the first instance (all should be compatible)
        RenderSetting renderSetting = instances.get(0).getRenderSetting();
        
        // Create instance data for each graphics instance
        List<InstanceData> instanceDataList = instances.stream()
                .map(info -> {
                    DrawCommand drawCommand = createDrawCommand(info);
                    return new InstanceData(
                            info,
                            drawCommand,
                            info.getVertexOffset(),
                            info.getVertexCount()
                    );
                })
                .toList();
        
        return new RenderCommand(vertexResource, renderSetting, instanceDataList, stageId);
    }
    
    /**
     * Create a draw command for a graphics information
     */
    private static DrawCommand createDrawCommand(GraphicsInformation info) {
        // Default draw command - this should be customized based on the instance type
        return new DrawCommand(
                info.getRenderSetting().renderParameter().primitiveType().getGLType(),
                DrawCommand.DrawMode.ARRAYS,
                1, // instance count - for instanced rendering this will be managed at batch level
                false, // use index buffer
                info.getRenderSetting().renderParameter().enableSorting()
        );
    }
}
