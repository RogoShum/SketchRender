package rogo.sketchrender.shader;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.ShaderCollector;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.stream.Collectors;

public class ComputeShader implements ShaderCollector {
    private final int program;
    private final int computeShader;

    public ComputeShader(ResourceProvider resourceProvider, ResourceLocation shaderLocation) throws IOException {
        ResourceLocation resourcelocation = new ResourceLocation(shaderLocation.getNamespace(), "shaders/core/" + shaderLocation.getPath() + ".csh");

        BufferedReader reader = resourceProvider.openAsReader(resourcelocation);
        String content = reader.lines().collect(Collectors.joining("\n"));

        computeShader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(computeShader, content);
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
    }

    public void execute(int xWorkGroups, int yWorkGroups, int zWorkGroups) {
        GL20.glUseProgram(program);
        GL43.glDispatchCompute(xWorkGroups, yWorkGroups, zWorkGroups);
        GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    public void discard() {
        GL43.glDeleteShader(computeShader);
        GL20.glDeleteProgram(program);
    }

    @Override
    public void close() {
        discard();
    }

    @Override
    public void onShadeCreate() {
        SketchRender.getShaderManager().onShaderLoad(this);
    }
}
