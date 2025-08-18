package rogo.sketch.render.resource;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.core.Vec3i;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import rogo.sketch.api.BindingResource;
import rogo.sketch.api.DataResourceObject;
import rogo.sketch.render.data.DataType;
import rogo.sketch.util.Identifier;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UniformBlock implements DataResourceObject, BindingResource {
    private final int handle;
    private final long stride;
    private final String blockName;
    private final Map<Integer, Vec3i> shaderBinding = new HashMap<>();
    private boolean disposed = false;

    public UniformBlock(String blockName, List<Variable> variables) {
        this.stride = calculateStride(variables);
        this.handle = createUBO();
        this.blockName = blockName;
    }

    private int createUBO() {
        int id = GL15.glGenBuffers();
        GlStateManager._glBindBuffer(GL31.GL_UNIFORM_BUFFER, id);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, stride, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
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
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, bindingPoint, handle);
        GL31.glUniformBlockBinding(shaderId, blockIndex, bindingPoint);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, bindingPoint, 0);
        shaderBinding.put(shaderId, new Vec3i(blockIndex, bindingPoint, 0));
    }

    public void drawBind(int shaderId) {
        try {
            Vec3i binding = shaderBinding.get(shaderId);
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding.getY(), handle);
        } catch (Exception e) {
            throw new IllegalStateException("Can't binding Uniform block: " + blockName);
        }
    }

    public void updateData(FloatBuffer buffer) {
        GlStateManager._glBindBuffer(GL31.GL_UNIFORM_BUFFER, handle);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
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
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void bind(Identifier resourceType, int binding) {
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding, handle);
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

