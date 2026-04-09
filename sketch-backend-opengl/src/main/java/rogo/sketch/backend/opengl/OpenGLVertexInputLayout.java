package rogo.sketch.backend.opengl;

import rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout;
import rogo.sketch.backend.opengl.internal.OpenGLRuntimeSupport;

/**
 * Cached OpenGL VAO for one geometry binding and one resolved shader vertex
 * layout.
 */
public final class OpenGLVertexInputLayout implements AutoCloseable {
    private final int vao;
    private final ActiveShaderVertexLayout shaderLayout;
    private boolean disposed;

    OpenGLVertexInputLayout(int vao, ActiveShaderVertexLayout shaderLayout) {
        this.vao = vao;
        this.shaderLayout = shaderLayout;
    }

    public int handle() {
        return vao;
    }

    public ActiveShaderVertexLayout shaderLayout() {
        return shaderLayout;
    }

    public void bind() {
        OpenGLRuntimeSupport.vertexArrayStrategy().bindVertexArray(vao);
    }

    public void unbind() {
        OpenGLRuntimeSupport.vertexArrayStrategy().bindVertexArray(0);
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void close() {
        if (disposed) {
            return;
        }
        OpenGLRuntimeSupport.vertexArrayStrategy().deleteVertexArray(vao);
        disposed = true;
    }
}
