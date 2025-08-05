package rogo.sketchrender.util;

public interface ShaderPackLoader {
    int getFrameBufferID();

    boolean renderingShadowPass();

    boolean enabledShader();

    void bindDefaultFrameBuffer();
}
