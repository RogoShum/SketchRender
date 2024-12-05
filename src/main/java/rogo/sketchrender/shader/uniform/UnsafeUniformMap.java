package rogo.sketchrender.shader.uniform;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL20.*;

public class UnsafeUniformMap {
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final Set<ResourceLocation> operateRecord = new HashSet<>();
    private final int programId;
    public static final FloatBuffer tampMatrixBuffer = MemoryUtil.memAllocFloat(16);

    public UnsafeUniformMap(int shaderId) {
        this.programId = shaderId;
        //printActiveUniforms(this.programId);
    }

    public static void printActiveUniforms(int programID) {
        // 获取活动 uniform 的数量
        int[] uniformCount = new int[1];
        glGetProgramiv(programID, GL_ACTIVE_UNIFORMS, uniformCount);

        System.out.println("Active Uniforms in Program " + programID + ": " + uniformCount[0]);

        // 获取 uniform 名称的最大长度
        int[] maxNameLength = new int[1];
        glGetProgramiv(programID, GL_ACTIVE_UNIFORM_MAX_LENGTH, maxNameLength);

        // 用于存储 uniform 名称的缓冲区
        ByteBuffer nameBuffer = ByteBuffer.allocateDirect(maxNameLength[0])
                .order(ByteOrder.nativeOrder());
        IntBuffer nameLengthBuffer = BufferUtils.createIntBuffer(1);
        nameLengthBuffer.put(maxNameLength[0]);
        nameLengthBuffer.flip();
        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);

        // 遍历所有 uniform
        for (int i = 0; i < uniformCount[0]; i++) {
            // 查询 uniform 的信息
            glGetActiveUniform(programID, i, nameLengthBuffer, sizeBuffer, typeBuffer, nameBuffer);
            nameBuffer.position(0); // 重置 buffer
            String name = MemoryUtil.memUTF8(nameBuffer);

            int location = glGetUniformLocation(programID, name); // 获取 uniform 的位置
            int size = sizeBuffer.get(0);                        // 获取 uniform 的大小
            int type = typeBuffer.get(0);                        // 获取 uniform 的类型

            // 打印信息
            System.out.println("Uniform #" + i + ":");
            System.out.println("  Name: " + name);
            System.out.println("  Location: " + location);
            System.out.println("  Size: " + size);
            System.out.println("  Type: " + getTypeString(type)); // 类型描述
        }
    }

    // 将 OpenGL uniform 类型转为人类可读的字符串
    private static String getTypeString(int type) {
        switch (type) {
            case GL_FLOAT: return "float";
            case GL_FLOAT_VEC2: return "vec2";
            case GL_FLOAT_VEC3: return "vec3";
            case GL_FLOAT_VEC4: return "vec4";
            case GL_INT: return "int";
            case GL_INT_VEC2: return "ivec2";
            case GL_INT_VEC3: return "ivec3";
            case GL_INT_VEC4: return "ivec4";
            case GL_BOOL: return "bool";
            case GL_BOOL_VEC2: return "bvec2";
            case GL_BOOL_VEC3: return "bvec3";
            case GL_BOOL_VEC4: return "bvec4";
            case GL_FLOAT_MAT2: return "mat2";
            case GL_FLOAT_MAT3: return "mat3";
            case GL_FLOAT_MAT4: return "mat4";
            case GL_SAMPLER_2D: return "sampler2D";
            case GL_SAMPLER_CUBE: return "samplerCube";
            default: return "Unknown";
        }
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
