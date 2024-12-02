package rogo.sketchrender.shader.uniform;

import net.minecraft.core.Vec3i;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UBO {
    private int uboId;
    private int stride;
    private final String blockName;
    private final Map<Integer, Vec3i> shaderBinding = new HashMap<>();

    public UBO(String blockName, List<Variable> variables) {
        this.stride = calculateStride(variables);
        this.uboId = createUBO();
        this.blockName = blockName;
    }

    private int createUBO() {
        int id = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, id);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, stride, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
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
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, bindingPoint, uboId);
        GL31.glUniformBlockBinding(shaderId, blockIndex, bindingPoint);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, bindingPoint, 0);
        shaderBinding.put(shaderId, new Vec3i(blockIndex, bindingPoint, 0));
    }

    public void drawBind(int shaderId) {
        try {
            Vec3i binding = shaderBinding.get(shaderId);
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding.getY(), uboId);
        } catch (Exception e) {
            throw new IllegalStateException("Can't binding Uniform block: " + blockName);
        }
    }

    public void updateData(FloatBuffer buffer) {
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uboId);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
    }

    public int getStride() {
        return stride;
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

