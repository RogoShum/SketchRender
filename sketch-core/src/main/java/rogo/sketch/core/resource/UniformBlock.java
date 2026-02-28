package rogo.sketch.core.resource;

import org.joml.Vector3i;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import rogo.sketch.core.api.BindingResource;
import rogo.sketch.core.api.DataResourceObject;
import rogo.sketch.core.data.DataType;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.internal.IGLBufferStrategy;
import rogo.sketch.core.util.KeyId;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uniform Buffer Object (UBO) for sharing data between shaders.
 * Uses GraphicsAPI buffer strategy for DSA/Legacy abstraction.
 */
public class UniformBlock implements DataResourceObject, BindingResource {
    private final int handle;
    private final long stride;
    private final String blockName;
    private final Map<Integer, Vector3i> shaderBinding = new HashMap<>();
    private boolean disposed = false;

    /**
     * Get the buffer strategy from the current graphics API
     */
    private static IGLBufferStrategy getBufferStrategy() {
        return GraphicsDriver.getCurrentAPI().getBufferStrategy();
    }

    public UniformBlock(String blockName, List<Variable> variables) {
        this.stride = calculateStride(variables);
        this.handle = createUBO();
        this.blockName = blockName;
    }

    private int createUBO() {
        IGLBufferStrategy strategy = getBufferStrategy();
        int id = strategy.createBuffer();
        // Allocate with null data
        strategy.bufferData(id, stride, 0L, GL15.GL_DYNAMIC_DRAW);
        return id;
    }

    private int calculateStride(List<Variable> variables) {
        int stride = 0;
        for (Variable var : variables) {
            stride += var.getDataType().getStride();
        }
        return stride;
    }

    public void bindShader(int shaderId, int bindingPoint) {
        int blockIndex = GL31.glGetUniformBlockIndex(shaderId, blockName);
        if (blockIndex == GL31.GL_INVALID_INDEX) {
            throw new IllegalStateException("Uniform block not found: " + blockName);
        }
        IGLBufferStrategy strategy = getBufferStrategy();
        strategy.bindBufferBase(GL31.GL_UNIFORM_BUFFER, bindingPoint, handle);
        GL31.glUniformBlockBinding(shaderId, blockIndex, bindingPoint);
        strategy.bindBufferBase(GL31.GL_UNIFORM_BUFFER, bindingPoint, 0);
        shaderBinding.put(shaderId, new Vector3i(blockIndex, bindingPoint, 0));
    }

    public void drawBind(int shaderId) {
        try {
            Vector3i binding = shaderBinding.get(shaderId);
            getBufferStrategy().bindBufferBase(GL31.GL_UNIFORM_BUFFER, binding.y(), handle);
        } catch (Exception e) {
            throw new IllegalStateException("Can't binding Uniform block: " + blockName);
        }
    }

    public void updateData(FloatBuffer buffer) {
        getBufferStrategy().bufferData(handle, buffer, GL15.GL_DYNAMIC_DRAW);
    }

    @Override
    public long getDataCount() {
        return 0;
    }

    @Override
    public long getCapacity() {
        return 0;
    }

    @Override
    public long getStride() {
        return stride;
    }

    @Override
    public long getMemoryAddress() {
        return 0;
    }

    @Override
    public int getHandle() {
        return handle;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            getBufferStrategy().deleteBuffer(handle);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        getBufferStrategy().bindBufferBase(GL31.GL_UNIFORM_BUFFER, binding, handle);
    }

    public static class Variable {
        private String name;
        private DataType dataType;

        public Variable(String name, DataType dataType) {
            this.name = name;
            this.dataType = dataType;
        }

        public String getName() {
            return name;
        }

        public DataType getDataType() {
            return dataType;
        }
    }
}
