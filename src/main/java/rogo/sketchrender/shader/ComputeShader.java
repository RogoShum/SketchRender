package rogo.sketchrender.shader;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.ExtraUniform;
import rogo.sketchrender.api.ShaderCollector;
import rogo.sketchrender.event.ProgramEvent;
import rogo.sketchrender.shader.uniform.UnsafeUniformMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL43.*;

public class ComputeShader implements ShaderCollector, ExtraUniform {
    private final UnsafeUniformMap unsafeUniformMap;
    private final int program;

    public ComputeShader(ResourceProvider resourceProvider, ResourceLocation shaderLocation) throws IOException {
        ResourceLocation resourcelocation = new ResourceLocation(shaderLocation.getNamespace(), "shaders/compute/" + shaderLocation.getPath() + ".comp");

        BufferedReader reader = resourceProvider.openAsReader(resourcelocation);
        String content = reader.lines().collect(Collectors.joining("\n"));

        int computeShader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(computeShader, content);
        GL20.glCompileShader(computeShader);

        if (GL20.glGetShaderi(computeShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("Error compiling compute shader: " + GL20.glGetShaderInfoLog(computeShader));
        }

        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, computeShader);
        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IOException("Error linking compute shader program: " + GL20.glGetProgramInfoLog(program));
        }

        GL43.glDeleteShader(computeShader);
        unsafeUniformMap = new UnsafeUniformMap(program);
        MinecraftForge.EVENT_BUS.post(new ProgramEvent.Init(this.getUniforms().getProgramId(), this));
        onShadeCreate();
    }

    public void bindUniforms() {
        GL20.glUseProgram(program);
        MinecraftForge.EVENT_BUS.post(new ProgramEvent.Bind(this.getUniforms().getProgramId(), this));
    }

    public void bind() {
        GL20.glUseProgram(program);
    }

    public void execute(int xWorkGroups, int yWorkGroups, int zWorkGroups) {
        GL43.glDispatchCompute(xWorkGroups, yWorkGroups, zWorkGroups);
    }

    public void memoryBarrier(int bit) {
        GL43.glMemoryBarrier(bit);
    }

    public void bindSSBO(int ssboId, int binding) {
        GL20.glUseProgram(program);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, ssboId);
        GL20.glUseProgram(0);
    }

    public void discard() {
        GL20.glDeleteProgram(program);
    }

    public int getId() {
        return program;
    }

    @Override
    public void close() {
        discard();
    }

    @Override
    public void onShadeCreate() {
        SketchRender.getShaderManager().onShaderLoad(this);
    }

    @Override
    public UnsafeUniformMap getUniforms() {
        return unsafeUniformMap;
    }
}
