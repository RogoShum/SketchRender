package rogo.sketchrender.shader;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

public class ComputeShader {
    private final int program;

    public ComputeShader(String computeShaderSource) {
        int computeShader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(computeShader, computeShaderSource);
        GL20.glCompileShader(computeShader);

        if (GL20.glGetShaderi(computeShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("Error compiling compute shader: " + GL20.glGetShaderInfoLog(computeShader));
        }

        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, computeShader);
        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.err.println("Error linking compute shader program: " + GL20.glGetProgramInfoLog(program));
        }

        GL43.glDeleteShader(computeShader);
    }

    public void execute(int xWorkGroups, int yWorkGroups, int zWorkGroups) {
        GL20.glUseProgram(program);
        GL43.glDispatchCompute(xWorkGroups, yWorkGroups, zWorkGroups);
        GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    public void discard() {
        GL20.glDeleteProgram(program);
    }
}
