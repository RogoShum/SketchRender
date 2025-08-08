package rogo.sketch.render.shader.uniform;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UnsafeUniformMap {
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final Set<ResourceLocation> operateRecord = new HashSet<>();
    private final int programId;
    public static final FloatBuffer tampMatrixBuffer = MemoryUtil.memAllocFloat(16);

    public UnsafeUniformMap(int shaderId) {
        this.programId = shaderId;
    }

    public boolean containsOperate(ResourceLocation operate) {
        return operateRecord.contains(operate);
    }

    public int getProgramId() {
        return programId;
    }

    public void createUniforms(ResourceLocation owner, String[] names) {
        if (!operateRecord.contains(owner)) {
            for (String name : names) {
                int uniform = GL20.glGetUniformLocation(programId, name);
                uniforms.put(name, uniform);
            }
            operateRecord.add(owner);
        }
    }

    public void setUniform(String name, int value) {
        int handle = getUniform(name);
        if (handle > -1) {
            GL20.glUniform1i(handle, value);
        }
    }

    public void setUniform(String name, float value) {
        int handle = getUniform(name);
        if (handle > -1) {
            GL20.glUniform1f(handle, value);
        }
    }

    public void setUniform(String name, float[] value) {
        int handle = getUniform(name);
        if (handle > -1) {
            GL20.glUniform1fv(handle, value);
        }
    }

    public void setUniform(String name, Vec3 value) {
        int handle = getUniform(name);
        if (handle > -1) {
            GL20.glUniform3f(handle, (float) value.x, (float) value.y, (float) value.z);
        }
    }

    public void setUniform(String name, Vec3i value) {
        int handle = getUniform(name);
        if (handle > -1) {
            GL20.glUniform3i(handle, value.getX(), value.getY(), value.getZ());
        }
    }

    public void setUniform(String name, Vector2i value) {
        int handle = getUniform(name);
        if (handle > -1) {
            GL20.glUniform2i(handle, value.x(), value.y());
        }
    }

    public void setUniform(String name, Vector3f value) {
        int handle = getUniform(name);
        if (handle > -1) {
            GL20.glUniform3f(handle, value.x, value.y, value.z);
        }
    }

    public void setUniform(String name, Vector2f value) {
        int handle = getUniform(name);
        if (handle > -1) {
            GL20.glUniform2f(handle, value.x, value.y);
        }
    }

    public void setUniform(String name, Matrix4f matrix4f) {
        tampMatrixBuffer.position(0);
        matrix4f.get(tampMatrixBuffer);
        tampMatrixBuffer.position(0);
        setUniformMatrix4f(name, tampMatrixBuffer);
    }

    public void setUniformMatrix4f(String name, FloatBuffer value) {
        int handle = getUniform(name);
        if (handle > -1) {
            GL20.glUniformMatrix4fv(handle, false, value);
        }
    }

    public void tryInsertUniform(String uniform, Runnable leftFunc) {
        int handle = GL20.glGetUniformLocation(programId, uniform);
        if (handle > -1) {
            leftFunc.run();
        }
    }

    private int getUniform(String name) {
        return uniforms.getOrDefault(name, -1);
    }
}
